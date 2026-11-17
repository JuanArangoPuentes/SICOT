package co.sena.sicot.config;

import co.sena.sicot.salud.AvisoDeSalud;
import co.sena.sicot.salud.Servicio;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
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

    /**
     * A quién se le cuenta lo que esta vigilancia descubre.
     *
     * <p>Es el arreglo de lo que pasó el 11-11-2026. Ese día esta clase llevaba
     * días detectando el problema y escribiéndolo con todo detalle —«el más
     * reciente tiene 119 h de antigüedad»— y no sirvió de nada, porque un
     * {@code log.error} solo lo ve quien ya está mirando el log. Ahora el
     * hallazgo entra en {@link co.sena.sicot.salud.SaludDelSistema} y de ahí
     * sale un informe que va a buscar al técnico.
     */
    private final AvisoDeSalud salud;

    /** Sin avisar a nadie: la forma que usan las pruebas de esta clase. */
    public VigilanciaDelRespaldo(MeterRegistry registry, String directorio, int rpoHoras) {
        this(registry, directorio, rpoHoras, AvisoDeSalud.NINGUNO);
    }

    @Autowired
    public VigilanciaDelRespaldo(MeterRegistry registry,
                                 @Value("${sicot.respaldo.directorio:}") String directorio,
                                 @Value("${sicot.respaldo.rpo-horas:24}") int rpoHoras,
                                 AvisoDeSalud salud) {
        this.salud = salud;
        this.directorio = (directorio == null || directorio.isBlank()) ? null : Path.of(directorio);
        this.rpo = Duration.ofHours(rpoHoras);

        // `strongReference(true)` porque `registry.gauge(...)` guarda una referencia
        // DÉBIL al objeto que alimenta la métrica: si nadie más lo sostiene, una
        // recolección de basura deja el gauge en NaN y la antigüedad del respaldo
        // desaparece sin que nada lo avise. Aquí el bean es un singleton y en la
        // práctica vive siempre, pero la métrica no debería depender de quién
        // sostiene el objeto — y el CI ya lo cobró una vez, con una prueba que
        // fallaba solo cuando el recolector pasaba en el momento justo.
        Gauge.builder("sicot.respaldo.antiguedad.horas", antiguedadEnHoras, AtomicLong::get)
                .strongReference(true)
                .register(registry);
    }

    /**
     * Cuánto se espera después de arrancar antes de dar el respaldo por caído.
     *
     * <p>El temporizador {@code sicot-respaldo.timer} tiene {@code Persistent=true}:
     * si el equipo estuvo apagado a la hora del respaldo, lo hace al encender, con
     * hasta cinco minutos de espera al azar ({@code RandomizedDelaySec=300}) y unos
     * segundos de {@code pg_dump}. Quince minutos cubren eso con margen y siguen
     * siendo «el mismo rato» para quien prendió el equipo.
     *
     * <p>Constante y no propiedad: es un dato del temporizador, no del despliegue,
     * y si cambia, cambia aquí junto con él.
     */
    static final String MARGEN_AL_ARRANCAR = "PT15M";

    /**
     * Al arrancar se mide y se escribe en el log del despliegue —que es cuando
     * alguien lo está mirando—, pero un respaldo viejo <b>todavía no se cuenta
     * como caída</b>.
     *
     * <h2>Por qué no se avisa ya</h2>
     * Porque al arrancar la respuesta casi siempre es falsa. El 17-11-2026 llegó al
     * técnico «falló el respaldo de la base de datos: 138 h» a las 08:59, y el
     * respaldo se hizo solo a las 09:00:31: el equipo había estado apagado del 12
     * al 16 y el temporizador se puso al día al encender, como está diseñado. El
     * backend arranca en un minuto; el temporizador espera hasta cinco. Esta
     * comprobación le ganaba la carrera cada vez, así que <b>cualquier encendido
     * después de más de 24 h apagado mandaba una falsa alarma</b>: cada lunes y
     * cada festivo. Un aviso que llega siempre enseña a no leerlo, y entonces no
     * sirve el día que es verdad.
     *
     * <p>Además, el tiempo que el equipo pasa apagado no es trabajo sin respaldar:
     * con SICOT apagado nadie escribe en la base. La pregunta que importa es si el
     * temporizador consigue ponerse al día, y esa se contesta en
     * {@link #comprobarCadaTantasHoras()}, {@value #MARGEN_AL_ARRANCAR} después.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void comprobarAlArrancar() {
        comprobar(false);
    }

    /**
     * Cada tres horas, y no a una hora fija.
     *
     * <p>Antes era {@code @Scheduled(cron = "0 0 4 * * *")}: las 04:00, dos
     * horas después del respaldo de las 02:00 que promete ADR-002. Esa hora
     * tenía el mismo defecto que el cron que vigila, y se vio en el servidor
     * del Centro el 11-11-2026: <b>ese equipo está apagado a las 04:00</b>. Se
     * prende por la mañana y se apaga por la noche, así que la comprobación
     * diaria nunca llegaba a correr y la vigilancia se reducía a lo que
     * avisaba al arrancar, una vez al día.
     *
     * <p>Un intervalo no depende de a qué hora esté encendido el equipo: en
     * una jornada normal comprueba tres o cuatro veces, y en un servidor que
     * sí se queda encendido, ocho. El respaldo se arregló de otra manera —un
     * temporizador con {@code Persistent=true}, ver
     * {@code scripts/systemd/sicot-respaldo.timer}— porque ahí sí importa la
     * hora; aquí no importa cuándo se mire, solo que se mire.
     *
     * <p>La primera vez corre {@value #MARGEN_AL_ARRANCAR} después de arrancar, no
     * tres horas después: es la que confirma o descarta lo que se vio al arrancar
     * (ver {@link #comprobarAlArrancar()}). Con tres horas, un respaldo que de
     * verdad no se puso al día habría tardado eso en llegar al técnico.
     */
    @Scheduled(fixedDelayString = "PT3H", initialDelayString = MARGEN_AL_ARRANCAR)
    public void comprobarCadaTantasHoras() {
        comprobar(true);
    }

    /**
     * @param contarComoCaida si un respaldo ausente o viejo se le cuenta a
     *                        {@link AvisoDeSalud} —y de ahí sale el informe para
     *                        el técnico— o solo se deja en el log porque todavía
     *                        se está dentro del margen del arranque.
     */
    private void comprobar(boolean contarComoCaida) {
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
            if (!contarComoCaida) {
                avisarQueSeConfirmaDespues("no hay ningún respaldo en " + directorio);
                return;
            }
            salud.fallo(Servicio.RESPALDO, "no hay ningún respaldo en " + directorio);
            log.error("""
                    ═══════════════════════════════════════════════════════════════
                     RESPALDO: no hay NINGÚN respaldo en {}.

                     ADR-002 compromete un RPO de {} h. Ahora mismo, la pérdida
                     ante un fallo de disco sería TODA la base de datos.

                     Revisar en el servidor:
                       systemctl list-timers sicot-respaldo.timer
                       tail -30 /var/log/sicot-respaldo.log
                       Si el temporizador no está: sudo ./scripts/instalar-respaldo.sh <carpeta>
                    ═══════════════════════════════════════════════════════════════""",
                    directorio, rpo.toHours());
            return;
        }

        Duration antiguedad = Duration.between(ultimo.get(), Instant.now());
        antiguedadEnHoras.set(antiguedad.toHours());

        if (antiguedad.compareTo(rpo) > 0) {
            String detalle = "el respaldo más reciente tiene %d h y el RPO comprometido es de %d h"
                    .formatted(antiguedad.toHours(), rpo.toHours());
            if (!contarComoCaida) {
                avisarQueSeConfirmaDespues(detalle);
                return;
            }
            salud.fallo(Servicio.RESPALDO, detalle);
            log.error("""
                    ═══════════════════════════════════════════════════════════════
                     RESPALDO: el más reciente tiene {} h de antigüedad y el RPO
                     comprometido en ADR-002 es de {} h, y el temporizador no se
                     puso al día después de arrancar.

                     Revisar en el servidor:
                       systemctl list-timers sicot-respaldo.timer
                       systemctl is-failed sicot-respaldo.service
                       tail -30 /var/log/sicot-respaldo.log
                    ═══════════════════════════════════════════════════════════════""",
                    antiguedad.toHours(), rpo.toHours());
        } else {
            salud.exito(Servicio.RESPALDO);
            log.info("Respaldo verificado: el más reciente tiene {} h (RPO comprometido: {} h, ADR-002).",
                    antiguedad.toHours(), rpo.toHours());
        }
    }

    /**
     * WARN y no ERROR: todavía no es una avería, y quien lea el log del arranque
     * tiene que saber que hay una segunda mirada en camino y cuándo.
     */
    private void avisarQueSeConfirmaDespues(String detalle) {
        log.warn("Respaldo al arrancar: {}. Todavía no se avisa al técnico: si el equipo estuvo "
                        + "apagado, sicot-respaldo.timer se pone al día en los primeros minutos. "
                        + "Se vuelve a mirar en {} min.",
                detalle, Duration.parse(MARGEN_AL_ARRANCAR).toMinutes());
    }

    /**
     * Fecha del volcado más reciente del directorio.
     *
     * <p>Se mira la fecha del archivo y no un registro propio a propósito: así
     * la vigilancia funciona igual aunque el respaldo lo ejecute un cron, una
     * tarea programada de Windows o una persona a mano, sin que el script tenga
     * que colaborar de ninguna forma. Un mecanismo de vigilancia que exige
     * modificar lo vigilado es un mecanismo que se desactiva al primer cambio.
     *
     * <p>Pero solo cuentan los {@code .dump} con contenido, que es lo que deja
     * un respaldo terminado (el script y el procedimiento manual de
     * BACKUP_Y_RESTAURACION.md). Antes contaba cualquier archivo, y lo que deja
     * un respaldo que FALLA —un volcado a medias, uno vacío, el log del cron—
     * es siempre de esta noche: apagaba el aviso justo cuando hacía falta.
     */
    private Optional<Instant> fechaDelRespaldoMasReciente() {
        if (!Files.isDirectory(directorio)) {
            return Optional.empty();
        }
        try (Stream<Path> archivos = Files.list(directorio)) {
            return archivos
                    .filter(Files::isRegularFile)
                    .filter(VigilanciaDelRespaldo::esUnVolcadoTerminado)
                    .map(this::fechaDe)
                    .flatMap(Optional::stream)
                    .max(Comparator.naturalOrder());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el directorio de respaldos " + directorio, e);
        }
    }

    private static boolean esUnVolcadoTerminado(Path archivo) {
        if (!archivo.getFileName().toString().endsWith(".dump")) {
            return false;
        }
        try {
            return Files.size(archivo) > 0;
        } catch (IOException e) {
            log.debug("No se pudo leer el tamaño de {}: {}", archivo, e.getMessage());
            return false;
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
