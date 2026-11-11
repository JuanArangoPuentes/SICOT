package co.sena.sicot.config;

import co.sena.sicot.salud.AvisoDeSalud;
import co.sena.sicot.salud.Servicio;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Vigila cuánto ocupan los archivos guardados dentro de PostgreSQL y avisa
 * antes de que el problema duela.
 *
 * <h2>Por qué existe</h2>
 * Los documentos se guardan como columnas {@code BYTEA} dentro de la propia
 * base — ver {@code docs/decisiones/ADR-003}. Es la decisión correcta hoy, pero
 * tiene un techo: PostgreSQL no es un almacén de objetos, y pasado cierto
 * tamaño cada respaldo y cada restauración se alargan hasta no caber en el
 * tiempo de recuperación comprometido en ADR-002.
 *
 * <p>El problema de ese techo es que <b>no avisa solo</b>. El sistema se degrada
 * de forma gradual y nadie lo nota hasta el día que hace falta restaurar y la
 * restauración no termina a tiempo. Escribir el umbral en un documento no
 * arregla eso: un umbral que depende de que alguien se acuerde de ir a mirarlo
 * no es un umbral, es una nota.
 *
 * <p>Esta clase convierte ese número en algo que el sistema mide por su cuenta:
 * lo publica como métrica y lo grita en el log cuando se acerca.
 *
 * <h2>Cómo se comporta fuera de PostgreSQL</h2>
 * {@code pg_total_relation_size} solo existe en PostgreSQL, y las pruebas
 * corren sobre H2. La consulta falla de forma silenciosa y deja la métrica en
 * {@code -1} en vez de tumbar el arranque: esto es instrumentación, y la
 * instrumentación nunca debe ser el motivo de que una aplicación no levante.
 */
@Component
public class VigilanciaDeAlmacenamiento {

    private static final Logger log = LoggerFactory.getLogger(VigilanciaDeAlmacenamiento.class);

    /** Fracción del umbral a partir de la cual conviene empezar la conversación, no terminarla. */
    private static final double PROPORCION_DE_AVISO = 0.80;

    private static final long BYTES_POR_GB = 1024L * 1024L * 1024L;

    private final JdbcTemplate jdbcTemplate;
    private final long umbralBytes;

    /**
     * Último tamaño medido. Es un {@link AtomicLong} y no un campo normal porque
     * Micrometer lo lee desde su propio hilo de recolección mientras la tarea
     * programada lo escribe desde otro.
     */
    private final AtomicLong tamanoDocumentos = new AtomicLong(-1);

    /** Para que pasarse del umbral llegue a una persona y no solo al log. */
    private final AvisoDeSalud salud;

    public VigilanciaDeAlmacenamiento(JdbcTemplate jdbcTemplate,
                                      MeterRegistry registry,
                                      @Value("${sicot.almacenamiento.umbral-gb:5}") int umbralGb,
                                      AvisoDeSalud salud) {
        this.jdbcTemplate = jdbcTemplate;
        this.salud = salud;
        this.umbralBytes = (long) umbralGb * BYTES_POR_GB;

        // El gauge lee el valor ya medido; NO consulta la base en cada
        // recolección. Prometheus puede raspar cada quince segundos, y un
        // pg_total_relation_size por cada raspado sería instrumentación que
        // cuesta más que lo que observa.
        // `strongReference(true)` por el mismo motivo que en VigilanciaDelRespaldo:
        // `registry.gauge(...)` sostiene el objeto con una referencia débil, y una
        // recolección de basura dejaría la métrica en NaN sin que nada lo avise.
        Gauge.builder("sicot.almacenamiento.documentos.bytes", tamanoDocumentos, AtomicLong::get)
                .strongReference(true)
                .register(registry);
    }

    /** Primera medición al arrancar, para que el aviso aparezca en el log de despliegue. */
    @EventListener(ApplicationReadyEvent.class)
    public void medirAlArrancar() {
        medirYAvisar();
    }

    /**
     * Cada seis horas, y no a una hora fija.
     *
     * <p>Era {@code cron = "0 0 3 * * *"}: las 03:00, para que el aviso cayera
     * junto a las señales de la noche. El 11-11-2026 se descubrió que en el
     * servidor del Centro esa idea no se sostiene: <b>el equipo está apagado de
     * noche</b>. Se prende por la mañana y se apaga al terminar la jornada, así
     * que la medición diaria no corría nunca y la vigilancia se quedaba en la
     * del arranque.
     *
     * <p>Era el mismo defecto que tenían el cron del respaldo y
     * {@code VigilanciaDelRespaldo}, y los tres se arreglaron el mismo día. La
     * regla que queda: en ese servidor, lo que tenga que ocurrir cada día va
     * por intervalo, o con {@code Persistent=true} si la hora importa de verdad.
     *
     * <p>Seis horas y no tres como el respaldo porque esto crece despacio: en
     * noviembre de 2026 iban 27,6 MB de 5 GB.
     */
    @Scheduled(fixedDelay = 6, initialDelay = 6, timeUnit = TimeUnit.HOURS)
    public void medirCadaTantasHoras() {
        medirYAvisar();
    }

    private void medirYAvisar() {
        Long bytes = medirTamanoDeDocumentos();
        if (bytes == null) {
            return;
        }
        tamanoDocumentos.set(bytes);

        if (bytes >= umbralBytes) {
            // Pasado el umbral sí es una incidencia: a partir de aquí las
            // subidas empiezan a fallar y el respaldo se alarga. El aviso del
            // 80% de abajo NO lo es —es «conviene planificar», no «algo está
            // roto»— y mandar un informe por eso le enseñaría al técnico que
            // los informes se pueden ignorar.
            salud.fallo(Servicio.ALMACENAMIENTO,
                    "la tabla 'documentos' ocupa " + legible(bytes) + " y el umbral de ADR-003 es "
                            + legible(umbralBytes));
            log.warn("ALMACENAMIENTO: la tabla 'documentos' ocupa {} y superó el umbral de {} "
                            + "definido en ADR-003. Toca planificar la migración de los archivos a "
                            + "almacenamiento de objetos: cada respaldo y cada restauración ya son "
                            + "más lentos de lo previsto.",
                    legible(bytes), legible(umbralBytes));
        } else if (bytes >= (long) (umbralBytes * PROPORCION_DE_AVISO)) {
            salud.exito(Servicio.ALMACENAMIENTO);
            log.warn("ALMACENAMIENTO: la tabla 'documentos' ocupa {} — por encima del {}% del "
                            + "umbral de {} (ADR-003). Conviene empezar a planificar la migración a "
                            + "almacenamiento de objetos con margen.",
                    legible(bytes), (int) (PROPORCION_DE_AVISO * 100), legible(umbralBytes));
        } else {
            salud.exito(Servicio.ALMACENAMIENTO);
            log.info("Almacenamiento de documentos: {} de {} permitidos (ADR-003).",
                    legible(bytes), legible(umbralBytes));
        }
    }

    /**
     * @return bytes que ocupa la tabla con sus índices y su almacenamiento TOAST,
     *         o {@code null} si la base no es PostgreSQL o la consulta falla.
     */
    private Long medirTamanoDeDocumentos() {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT pg_total_relation_size('documentos')", Long.class);
        } catch (Exception e) {
            // Esperado sobre H2 en las pruebas. A nivel debug a propósito: no es
            // un fallo del sistema, es una función que ese motor no tiene.
            log.debug("No se pudo medir el tamaño de 'documentos' (¿la base no es PostgreSQL?): {}",
                    e.getMessage());
            return null;
        }
    }

    private String legible(long bytes) {
        if (bytes >= BYTES_POR_GB) {
            return String.format("%.2f GB", bytes / (double) BYTES_POR_GB);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
