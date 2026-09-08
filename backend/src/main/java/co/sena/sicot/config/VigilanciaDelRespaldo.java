package co.sena.sicot.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Comprueba que el respaldo que sostiene el RPO de ADR-002 se esté ejecutando de
 * verdad.
 *
 * <h2>Por qué existe esta clase</h2>
 * Porque el compromiso estaba escrito y el mecanismo no estaba puesto.
 *
 * <p>ADR-002 declara un RPO de 24 horas y lo justifica con «respaldo diario
 * verificado (`scripts/respaldo-sicot.sh`, cron 02:00)». El script existe, está
 * probado y se ejercitó de punta a punta. Pero <b>nada en el repositorio instala
 * ese cron</b>: depende de que alguien lo escribiera a mano en el servidor, y no
 * hay forma de comprobar desde el sistema si ocurrió. El RPO de 24 h es, hasta
 * que esto se ejecute, una intención.
 *
 * <h2>Esta lección ya estaba aprendida</h2>
 * ADR-006 la dejó escrita con estas palabras, tras desplegar una configuración
 * que apuntaba a un modelo de IA que no estaba descargado:
 *
 * <blockquote>un paso manual escrito en un documento no es un control</blockquote>
 *
 * <p>La solución de entonces fue {@link co.sena.sicot.ia.VerificacionDelModeloIa}:
 * comprobar al arrancar y avisar en el log con el comando exacto que falta. El
 * respaldo tiene la misma forma —una promesa que depende de que alguien se
 * acuerde— y esta clase le aplica el mismo tratamiento.
 *
 * <h2>Qué hace y qué NO hace</h2>
 * Mira la antigüedad del respaldo más reciente y avisa cuando supera el RPO
 * comprometido. Publica {@code sicot.respaldo.antiguedad.horas} como métrica,
 * para que la señal exista también donde no la lee una persona.
 *
 * <p><b>No hace el respaldo.</b> Un proceso que dispara un {@code pg_dump} desde
 * dentro de la aplicación compite por la base con los usuarios y guarda el
 * archivo en el mismo disco cuya pérdida se está previniendo. El respaldo lo
 * ejecuta el cron del sistema; esto solo vigila que lo haga.
 *
 * <p><b>No impide arrancar.</b> Igual que la verificación del modelo: un aviso
 * ruidoso y visible, no un fallo total por una función que no bloquea operar.
 */
@Component
public class VigilanciaDelRespaldo {

    private static final Logger log = LoggerFactory.getLogger(VigilanciaDelRespaldo.class);

    /** Valor de la métrica cuando no se pudo determinar la antigüedad. */
    private static final long DESCONOCIDA = -1;

    private final Path directorio;
    private final Duration rpo;

    /**
     * Última antigüedad medida, en horas. {@link AtomicLong} porque Micrometer lo
     * lee desde su hilo de recolección mientras la tarea programada lo escribe
     * desde otro — el mismo motivo que en {@link VigilanciaDeAlmacenamiento}.
     */
    private final AtomicLong antiguedadEnHoras = new AtomicLong(DESCONOCIDA);

    public VigilanciaDelRespaldo(MeterRegistry registry,
                                 @Value("${sicot.respaldo.directorio:}") String directorio,
                                 @Value("${sicot.respaldo.rpo-horas:24}") int rpoHoras) {
        this.directorio = (directorio == null || directorio.isBlank()) ? null : Path.of(directorio);
        this.rpo = Duration.ofHours(rpoHoras);

        registry.gauge("sicot.respaldo.antiguedad.horas", antiguedadEnHoras, AtomicLong::get);
    }

    /**
     * Al arrancar, para que el aviso aparezca en el log del despliegue — que es
     * cuando alguien lo está mirando.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void comprobarAlArrancar() {
        comprobar();
    }

    /**
     * Cada día a las 04:00: dos horas después del respaldo de las 02:00 que
     * promete ADR-002, con margen de sobra para que un volcado lento haya
     * terminado, y antes de las 06:00 en que corren las automatizaciones.
     */
    @Scheduled(cron = "0 0 4 * * *")
    public void comprobarCadaDia() {
        comprobar();
    }

    private void comprobar() {
        if (directorio == null) {
            // Sin configurar no se puede afirmar nada, y afirmar «todo bien»
            // sería peor que callar: la métrica queda en -1, que es distinguible
            // de cualquier antigüedad real.
            log.warn("""
                    ═══════════════════════════════════════════════════════════════
                     No hay vigilancia del respaldo: falta RESPALDO_DIRECTORIO.

                     ADR-002 compromete un RPO de {} h apoyado en un respaldo
                     diario. Sin esta variable, el sistema no puede comprobar que
                     ese respaldo se esté ejecutando, y el compromiso queda sin
                     verificar.

                     Para activarla, en el .env del despliegue:
                       RESPALDO_DIRECTORIO=/ruta/donde/escribe/respaldo-sicot.sh

                     Y comprobar que el cron esté instalado en el servidor:
                       crontab -l | grep respaldo-sicot
                    ═══════════════════════════════════════════════════════════════""",
                    rpo.toHours());
            return;
        }

        Optional<Instant> ultimo = fechaDelRespaldoMasReciente();
        if (ultimo.isEmpty()) {
            antiguedadEnHoras.set(DESCONOCIDA);
            log.error("""
                    ═══════════════════════════════════════════════════════════════
                     RESPALDO: no hay NINGÚN respaldo en {}.

                     ADR-002 compromete un RPO de {} h. Ahora mismo, la pérdida
                     ante un fallo de disco sería TODA la base de datos.

                     Revisar:
                       1. Que el cron esté instalado:  crontab -l | grep respaldo-sicot
                       2. Que se pueda ejecutar a mano: ./scripts/respaldo-sicot.sh {}
                    ═══════════════════════════════════════════════════════════════""",
                    directorio, rpo.toHours(), directorio);
            return;
        }

        Duration antiguedad = Duration.between(ultimo.get(), Instant.now());
        antiguedadEnHoras.set(antiguedad.toHours());

        if (antiguedad.compareTo(rpo) > 0) {
            log.error("""
                    ═══════════════════════════════════════════════════════════════
                     RESPALDO: el más reciente tiene {} h de antigüedad y el RPO
                     comprometido en ADR-002 es de {} h.

                     El trabajo de las últimas {} h no está respaldado. Revisar el
                     cron en el servidor:  crontab -l | grep respaldo-sicot
                    ═══════════════════════════════════════════════════════════════""",
                    antiguedad.toHours(), rpo.toHours(), antiguedad.toHours());
        } else {
            log.info("Respaldo verificado: el más reciente tiene {} h (RPO comprometido: {} h, ADR-002).",
                    antiguedad.toHours(), rpo.toHours());
        }
    }

    /**
     * Fecha del archivo más reciente del directorio.
     *
     * <p>Se mira la fecha del archivo y no un registro propio a propósito: así
     * la vigilancia funciona igual aunque el respaldo lo ejecute un cron, una
     * tarea programada de Windows o una persona a mano, sin que el script tenga
     * que colaborar de ninguna forma. Un mecanismo de vigilancia que exige
     * modificar lo vigilado es un mecanismo que se desactiva al primer cambio.
     */
    private Optional<Instant> fechaDelRespaldoMasReciente() {
        if (!Files.isDirectory(directorio)) {
            return Optional.empty();
        }
        try (Stream<Path> archivos = Files.list(directorio)) {
            return archivos
                    .filter(Files::isRegularFile)
                    .map(this::fechaDe)
                    .flatMap(Optional::stream)
                    .max(Comparator.naturalOrder());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el directorio de respaldos " + directorio, e);
        }
    }

    private Optional<Instant> fechaDe(Path archivo) {
        try {
            return Optional.of(Files.getLastModifiedTime(archivo).toInstant());
        } catch (IOException e) {
            log.debug("No se pudo leer la fecha de {}: {}", archivo, e.getMessage());
            return Optional.empty();
        }
    }
}
