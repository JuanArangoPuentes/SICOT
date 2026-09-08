package co.sena.sicot.automatizacion;

import co.sena.sicot.entity.enums.TipoTareaAutomatizada;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Consume la cola: reserva tareas, las ejecuta y anota cómo fueron.
 *
 * <h2>Por qué los hilos son propios y no los de Tomcat</h2>
 * Es la misma lección que dejó escrita {@code LimitadorDeUsoIa}. Los hilos de
 * Tomcat los comparte toda la aplicación, y una tarea lenta —un correo contra un
 * servidor que no responde, una llamada al modelo— retendría uno de ellos
 * durante minutos. Con suficientes tareas lentas a la vez, el backend se queda
 * sin hilos para atender <i>logins</i>. Un grupo propio y pequeño acota el daño
 * al motor: si se satura, se atrasan las automatizaciones y nada más.
 *
 * <h2>Por qué el sondeo no se solapa consigo mismo</h2>
 * El planificador usa {@code fixedDelay}, que cuenta desde que <b>termina</b> la
 * ronda anterior. Con {@code fixedRate} una ronda lenta se solaparía con la
 * siguiente, dos rondas competirían por las mismas tareas y el único motivo por
 * el que no se ejecutarían dos veces sería la reserva atómica — que existe para
 * el caso de varias instancias, no para tapar un solapamiento evitable.
 *
 * <h2>Qué pasa si una tarea deja el proceso muerto</h2>
 * Antes de cada ronda se devuelven a la cola las que llevan demasiado tiempo
 * EN_PROCESO. Sin eso, matar el backend a mitad de una ejecución deja esa tarea
 * bloqueada para siempre: nadie la reclama porque ya no está pendiente, y nadie
 * la termina porque quien la tenía murió.
 */
@Service
public class EjecutorDeTareas {

    private static final Logger log = LoggerFactory.getLogger(EjecutorDeTareas.class);

    private final AlmacenDeTareas almacen;
    private final Map<TipoTareaAutomatizada, AccionDeTarea> accionesPorTipo = new EnumMap<>(TipoTareaAutomatizada.class);
    private final ExecutorService trabajadores;
    private final Counter completadas;
    private final Counter descartadas;
    private final Counter fallidas;

    public EjecutorDeTareas(AlmacenDeTareas almacen,
                            List<AccionDeTarea> acciones,
                            AutomatizacionProperties propiedades,
                            MeterRegistry registry) {
        this.almacen = almacen;
        for (AccionDeTarea accion : acciones) {
            AccionDeTarea previa = accionesPorTipo.put(accion.tipo(), accion);
            if (previa != null) {
                // Dos ejecutores para el mismo tipo significa que uno de los dos
                // no se ejecutaría nunca, y averiguar cuál cuesta una tarde.
                // Mejor no arrancar.
                throw new IllegalStateException("Hay dos acciones registradas para el tipo " + accion.tipo()
                        + ": " + previa.getClass().getSimpleName() + " y " + accion.getClass().getSimpleName());
            }
        }

        this.trabajadores = Executors.newFixedThreadPool(
                Math.max(1, propiedades.trabajadores()), nombrarHilos());
        this.completadas = Counter.builder("sicot.automatizacion.tareas.completadas").register(registry);
        this.descartadas = Counter.builder("sicot.automatizacion.tareas.descartadas").register(registry);
        this.fallidas = Counter.builder("sicot.automatizacion.tareas.fallidas")
                .description("Tareas que agotaron los reintentos y requieren intervención")
                .register(registry);

        // Un tipo sin acción es una tarea que se encolaría y nunca se ejecutaría,
        // acumulándose en silencio. Es un aviso y no un fallo de arranque: puede
        // ser un tipo recién añadido cuya acción llega en el siguiente commit.
        for (TipoTareaAutomatizada tipo : TipoTareaAutomatizada.values()) {
            if (!accionesPorTipo.containsKey(tipo)) {
                log.warn("No hay ninguna AccionDeTarea registrada para el tipo {}. "
                        + "Las tareas de ese tipo se quedarían en cola sin ejecutarse.", tipo);
            }
        }
    }

    /**
     * Una ronda completa: rescatar abandonadas, reservar un lote y ejecutarlo.
     *
     * <p>No lleva {@code @Scheduled}. El disparo periódico vive en
     * {@link PlanificadorDeAutomatizaciones}, que se puede apagar por
     * configuración; así la suite de pruebas invoca este método cuando quiere y
     * comprueba el resultado, en vez de esperar a que salte un temporizador.
     * Una prueba que depende del reloj es una prueba que falla en la máquina
     * lenta de otra persona.
     *
     * @return cuántas tareas se procesaron (con cualquier desenlace)
     */
    public int procesarPendientes() {
        almacen.liberarAbandonadas();

        List<Long> candidatas = almacen.idsPendientes(Instant.now());
        if (candidatas.isEmpty()) {
            return 0;
        }

        List<CompletableFuture<Boolean>> enCurso = candidatas.stream()
                .map(id -> CompletableFuture.supplyAsync(() -> procesarUna(id), trabajadores))
                .toList();

        // Se espera al lote antes de devolver el control: con fixedDelay, eso
        // garantiza que la siguiente ronda no empiece hasta que esta termine.
        CompletableFuture.allOf(enCurso.toArray(CompletableFuture[]::new)).join();

        int procesadas = (int) enCurso.stream().filter(CompletableFuture::join).count();
        if (procesadas > 0) {
            log.info("Ronda de automatizaciones: {} de {} tarea(s) candidatas procesadas.",
                    procesadas, candidatas.size());
        }
        return procesadas;
    }

    /** @return {@code true} si esta ronda llegó a ejecutarla (aunque acabara mal). */
    private boolean procesarUna(Long id) {
        TareaEnEjecucion tarea = almacen.reclamar(id).orElse(null);
        if (tarea == null) {
            // Otro trabajador —u otra instancia— se adelantó. Es lo normal en un
            // sistema con varios consumidores, no un error.
            return false;
        }

        AccionDeTarea accion = accionesPorTipo.get(tarea.tipo());
        if (accion == null) {
            almacen.marcarDescartada(id, "No hay ninguna acción registrada para este tipo de tarea.");
            descartadas.increment();
            return true;
        }

        try {
            ResultadoDeAccion resultado = accion.ejecutar(tarea);
            if (resultado.ejecutada()) {
                almacen.marcarCompletada(id);
                completadas.increment();
            } else {
                almacen.marcarDescartada(id, resultado.motivoDeDescarte());
                descartadas.increment();
                log.info("Automatización '{}' (tarea {}) descartada: {}",
                        tarea.regla(), id, resultado.motivoDeDescarte());
            }
        } catch (Exception e) {
            // Cualquier excepción es un candidato a reintento. El almacén decide
            // si aún quedan intentos o si toca rendirse.
            if (!almacen.registrarFallo(id, mensajeDe(e))) {
                fallidas.increment();
            }
        }
        return true;
    }

    private String mensajeDe(Exception e) {
        String mensaje = e.getMessage();
        return (mensaje == null || mensaje.isBlank())
                ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + mensaje;
    }

    private ThreadFactory nombrarHilos() {
        AtomicInteger contador = new AtomicInteger(1);
        return tarea -> {
            Thread hilo = new Thread(tarea, "sicot-automatizacion-" + contador.getAndIncrement());
            // Demonio: un hilo del motor no debe impedir que la JVM termine
            // cuando se apaga el servicio.
            hilo.setDaemon(true);
            return hilo;
        };
    }

    /**
     * Da margen a las tareas en vuelo antes de cortar.
     *
     * <p>Sin esto, un despliegue interrumpe a mitad las que estén corriendo y las
     * deja EN_PROCESO hasta que el rescate de abandonadas las recupere — minutos
     * después, y con un aviso en el log que parece un incidente sin serlo.
     */
    @PreDestroy
    public void detener() {
        trabajadores.shutdown();
        try {
            if (!trabajadores.awaitTermination(20, TimeUnit.SECONDS)) {
                log.warn("Quedaron tareas de automatización en vuelo al apagar; se recuperarán al arrancar.");
                trabajadores.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            trabajadores.shutdownNow();
        }
    }
}
