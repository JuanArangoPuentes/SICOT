package co.sena.sicot.ia;

import co.sena.sicot.entity.Usuario;
import co.sena.sicot.exception.DemasiadasSolicitudesException;
import co.sena.sicot.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Pone un techo al uso del Copiloto de IA. Es la única defensa del backend
 * contra su propia dependencia más lenta.
 *
 * <h2>El problema</h2>
 * Cada llamada a Ollama retiene un hilo de Tomcat mientras espera la respuesta
 * —hasta el tiempo de espera configurado, que era de <b>900 segundos</b>—. El
 * grupo de hilos de Tomcat es de 200 por defecto y lo comparte
 * <i>absolutamente toda</i> la aplicación. Con unas pocas decenas de peticiones
 * de IA simultáneas, ya sean malintencionadas o simplemente varios supervisores
 * usando el chat a la vez, el backend se queda sin hilos para atender el resto:
 * login, listados, descargas. La IA no se degrada sola, se lleva por delante
 * todo lo demás. Y no había ningún límite de frecuencia en ninguna ruta.
 *
 * <h2>Los dos frenos</h2>
 * <ol>
 *   <li><b>Concurrencia global.</b> Un semáforo acota cuántas peticiones de IA
 *       pueden estar en vuelo a la vez. El número es pequeño a propósito:
 *       Ollama atiende un modelo prácticamente en serie, así que dejar entrar
 *       más peticiones no las hace terminar antes — solo hace que más hilos de
 *       Tomcat esperen. Lo que sobra se rechaza <i>rápido</i> con 429 en vez de
 *       encolarse, porque una petición encolada sigue costando un hilo.</li>
 *   <li><b>Frecuencia por usuario.</b> Una ventana deslizante por cuenta, para
 *       que una sola persona (o un token robado) no consuma toda la
 *       concurrencia disponible mientras el resto del centro queda sin
 *       copiloto.</li>
 * </ol>
 *
 * <p>Ambos límites son configurables por entorno: en una máquina con GPU tiene
 * sentido subir la concurrencia, y en un despliegue de un solo formador tiene
 * sentido bajarla.
 */
@Component
public class LimitadorDeUsoIa {

    private static final Logger log = LoggerFactory.getLogger(LimitadorDeUsoIa.class);

    private static final Duration VENTANA = Duration.ofMinutes(1);
    private static final int MAX_USUARIOS_VIGILADOS = 1_000;

    private final int peticionesPorMinuto;
    private final Semaphore concurrencia;
    private final ConcurrentHashMap<Long, Ventana> ventanaPorUsuario = new ConcurrentHashMap<>();

    public LimitadorDeUsoIa(
            @Value("${sicot.ia.max-concurrentes:2}") int maxConcurrentes,
            @Value("${sicot.ia.peticiones-por-minuto:10}") int peticionesPorMinuto) {
        this.concurrencia = new Semaphore(Math.max(1, maxConcurrentes), true);
        this.peticionesPorMinuto = Math.max(1, peticionesPorMinuto);
    }

    /**
     * Ejecuta {@code tarea} solo si hay cupo. Si no lo hay, lanza
     * {@link DemasiadasSolicitudesException} (HTTP 429) sin llegar a llamar a
     * Ollama.
     *
     * <p>El permiso se libera siempre en el {@code finally}: si una excepción
     * escapara sin liberarlo, el cupo se reduciría de forma permanente hasta el
     * siguiente reinicio, y el sistema acabaría rechazando toda petición de IA
     * sin ningún motivo visible.
     */
    public <T> T ejecutar(String operacion, Supplier<T> tarea) {
        verificarFrecuencia(operacion);

        boolean adquirido;
        try {
            // Espera corta y acotada: si en dos segundos no hay cupo, el sistema
            // está saturado y es mejor decirlo que dejar al usuario colgado.
            adquirido = concurrencia.tryAcquire(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IaNoDisponibleException("La solicitud al asistente de IA fue interrumpida.", e);
        }
        if (!adquirido) {
            log.warn("Capacidad de IA agotada: se rechaza '{}' con 429.", operacion);
            throw new DemasiadasSolicitudesException(
                    "El asistente de IA está atendiendo otras solicitudes en este momento. "
                            + "Espere unos segundos y vuelva a intentarlo.", 15);
        }
        try {
            return tarea.get();
        } finally {
            concurrencia.release();
        }
    }

    /**
     * Olvida la frecuencia acumulada por usuario.
     *
     * <p>Mismo motivo que {@code LoginAttemptService.reiniciar()}: la ventana
     * deslizante vive en memoria y la comparten todas las pruebas de
     * integración, así que una que agote el cupo a propósito dejaba a la
     * siguiente recibiendo 429.
     *
     * <p>No toca el semáforo de concurrencia: sus permisos se liberan siempre
     * en el {@code finally} de {@link #ejecutar}, de modo que ya está
     * equilibrado al terminar cada prueba. Reponerlo a mano aquí escondería
     * precisamente el fallo de una fuga de permisos.
     */
    public void reiniciar() {
        ventanaPorUsuario.clear();
    }

    private void verificarFrecuencia(String operacion) {
        Usuario usuario = SecurityUtils.currentUsuario();
        if (usuario == null) {
            // Sin usuario autenticado no hay a quién contarle las peticiones;
            // el semáforo de concurrencia sigue aplicando. En la práctica no
            // ocurre: todas las rutas de IA exigen autenticación.
            return;
        }
        if (ventanaPorUsuario.size() >= MAX_USUARIOS_VIGILADOS) {
            Instant ahora = Instant.now();
            ventanaPorUsuario.values().removeIf(v -> v.expirada(ahora));
        }

        Ventana ventana = ventanaPorUsuario.compute(usuario.getId(), (id, actual) -> {
            Instant ahora = Instant.now();
            if (actual == null || actual.expirada(ahora)) {
                return new Ventana(1, ahora);
            }
            return new Ventana(actual.usos() + 1, actual.inicio());
        });

        if (ventana.usos() > peticionesPorMinuto) {
            long espera = Duration.between(Instant.now(), ventana.inicio().plus(VENTANA)).toSeconds();
            log.warn("Usuario {} superó el límite de {} peticiones de IA por minuto en '{}'.",
                    usuario.getId(), peticionesPorMinuto, operacion);
            throw new DemasiadasSolicitudesException(
                    "Ha hecho demasiadas consultas al asistente de IA en poco tiempo. "
                            + "Espere un momento antes de volver a preguntar.", espera);
        }
    }

    private record Ventana(int usos, Instant inicio) {
        boolean expirada(Instant ahora) {
            return inicio.plus(VENTANA).isBefore(ahora);
        }
    }
}
