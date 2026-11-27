package co.sena.sicot.service;

import co.sena.sicot.exception.DemasiadasSolicitudesException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Pone un techo a cuántos archivos se mueven a la vez. Es al almacenamiento lo
 * que {@code LimitadorDeUsoIa} es a Ollama: la defensa del backend contra su
 * propio recurso finito, que aquí no es el tiempo sino <b>el heap</b>.
 *
 * <h2>El problema</h2>
 * Los bytes de un documento viven dentro de la fila ({@code documentos.contenido},
 * {@code bytea}) y todo el camino los maneja enteros en memoria:
 * <ul>
 *   <li>{@code DocumentoService.subir} hace {@code archivo.getBytes()}: un
 *       {@code byte[]} de hasta {@code spring.servlet.multipart.max-file-size}
 *       (20 MB) por petición.</li>
 *   <li>{@code DocumentoService.firmar} estampa la firma visible, y PDFBox
 *       produce un segundo arreglo con el PDF resultante: el pico es el doble.</li>
 *   <li>{@code DocumentoController.descargar} responde {@code byte[]}, y antes
 *       recorre esos mismos bytes para la huella de integridad.</li>
 * </ul>
 *
 * <p>Enfrente hay <b>un heap acotado</b>: {@code docker-compose.yml} limita el
 * backend a 1 GB y {@code MaxRAMPercentage=75} deja unos 768 MB. El grupo de
 * hilos de Tomcat es de 200 por defecto y lo comparte toda la aplicación, así
 * que sin este freno la memoria que una sola ruta puede exigir es
 * {@code 200 × 20 MB = 4 GB}. No hace falta mala fe para llegar ahí: tres
 * supervisores subiendo evidencia fotográfica desde el teléfono en bodega van
 * por el mismo camino, y basta con unas decenas de peticiones para agotarlo.
 *
 * <p>Y el final es el peor posible: {@code -XX:+ExitOnOutOfMemoryError}
 * convierte el {@code OutOfMemoryError} en la muerte del proceso —lo correcto,
 * porque una JVM después de un OOM no se recupera—, de modo que lo que empieza
 * como una subida lenta termina como el backend reiniciándose y perdiendo todo
 * lo que tenía en vuelo. El endpoint que lo alcanza,
 * {@code POST /api/contratos/&#123;id&#125;/documentos}, está abierto al rol
 * SUPERVISOR, el de menor privilegio.
 *
 * <h2>Por qué un semáforo y no un límite por usuario</h2>
 * Porque lo que se agota es un recurso <b>global</b>. Un tope por cuenta no
 * impide que diez cuentas legítimas coincidan, que es justo el caso real. El
 * semáforo acota el número de peticiones <i>en vuelo</i>, y con ello acota el
 * peor caso de memoria a una cifra que se puede calcular:
 * {@code permisos × 20 MB × 2} ≈ 160 MB con el valor por defecto de 4, holgado
 * dentro de 768 MB.
 *
 * <p>Lo que sobra se rechaza <b>rápido</b> con 429 y {@code Retry-After} en vez
 * de encolarse: una petición esperando sigue costando un hilo de Tomcat, que es
 * el otro recurso que no hay que gastar. La espera es de dos segundos —lo
 * suficiente para absorber una ráfaga corta de varias personas guardando a la
 * vez, no para disimular una saturación real.
 *
 * <h2>Lo que esto NO arregla</h2>
 * El pico de memoria por petición sigue siendo el archivo entero: esto lo
 * multiplica por una constante conocida, no lo elimina. Eliminarlo exige sacar
 * los bytes de la fila y servirlos como flujo, que es exactamente la migración
 * que ADR-003 deja condicionada al umbral de 5 GB de la tabla
 * {@code documentos} (hoy 27,6 MB) y que
 * {@code VigilanciaDeAlmacenamiento} vigila. Mientras ese umbral no se alcance,
 * acotar la concurrencia es la respuesta proporcionada; el día que se alcance,
 * la migración quita el problema de raíz y este limitador se puede relajar.
 *
 * <p>Tampoco cubre el tamaño total de la petición, que ya acota
 * {@code spring.servlet.multipart.max-request-size}: por eso una ruta que recibe
 * <i>varios</i> archivos (la extracción de la ficha del contrato) consume un
 * solo permiso, porque entre todos no pueden pasar de ese tope.
 */
@Component
public class LimitadorDeArchivos {

    private static final Logger log = LoggerFactory.getLogger(LimitadorDeArchivos.class);

    /**
     * Cuánto se espera por un permiso antes de rechazar.
     *
     * <p>Dos segundos. Mover 20 MB desde PostgreSQL y escribirlos en la
     * respuesta es cuestión de decenas de milisegundos en el servidor del
     * Centro, así que esta espera cubre varias peticiones por delante. Si ni
     * así hay cupo, el sistema está saturado y decirlo es mejor que dejar a la
     * persona con la rueda girando.
     */
    private static final long ESPERA_MS = 2_000;

    /** Qué se le dice al cliente que espere, en segundos (cabecera Retry-After). */
    private static final int REINTENTAR_EN_SEGUNDOS = 5;

    private final Semaphore concurrencia;

    public LimitadorDeArchivos(@Value("${sicot.archivos.max-concurrentes:4}") int maxConcurrentes) {
        // Semáforo JUSTO (true): sin esa garantía, una ráfaga continua puede
        // dejar a una petición esperando indefinidamente mientras otras que
        // llegaron después entran, y el supervisor que sube una foto en bodega
        // sería siempre el último. Con cola FIFO, el que lleva más tiempo
        // esperando entra primero.
        this.concurrencia = new Semaphore(Math.max(1, maxConcurrentes), true);
    }

    /**
     * Ejecuta {@code tarea} solo si hay cupo para mover un archivo; si no,
     * lanza {@link DemasiadasSolicitudesException} (HTTP 429) <b>sin llegar a
     * leer ni un byte</b>, que es el punto entero.
     *
     * <p>El permiso se libera siempre en el {@code finally}: si una excepción
     * escapara sin liberarlo, el cupo se reduciría de forma permanente hasta el
     * siguiente reinicio y el sistema acabaría rechazando toda subida sin
     * ningún motivo visible — el mismo razonamiento que en
     * {@code LimitadorDeUsoIa.ejecutar}.
     */
    public <T> T ejecutar(String operacion, Supplier<T> tarea) {
        boolean adquirido;
        try {
            adquirido = concurrencia.tryAcquire(ESPERA_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DemasiadasSolicitudesException(
                    "La transferencia del archivo se interrumpió. Vuelva a intentarlo.", REINTENTAR_EN_SEGUNDOS);
        }
        if (!adquirido) {
            log.warn("Sin cupo para mover archivos: se rechaza '{}' con 429.", operacion);
            throw new DemasiadasSolicitudesException(
                    "El servidor está moviendo varios archivos en este momento. "
                            + "Espere unos segundos y vuelva a intentarlo; su trabajo no se perdió.",
                    REINTENTAR_EN_SEGUNDOS);
        }
        try {
            return tarea.get();
        } finally {
            concurrencia.release();
        }
    }

    /** Cuántos permisos quedan libres. Para las pruebas y para la pantalla de salud. */
    public int cupoDisponible() {
        return concurrencia.availablePermits();
    }
}
