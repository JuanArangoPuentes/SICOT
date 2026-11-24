package co.sena.sicot.salud;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lo que el sistema sabe, en cada momento, sobre qué partes de sí mismo están
 * funcionando.
 *
 * <h2>Para qué existe</h2>
 * Antes de esto, cuando Ollama se caía, lo sabía únicamente la llamada que falló:
 * lanzaba {@code IaNoDisponibleException}, el usuario veía un 503 y ahí se
 * acababa. Nada más en el sistema se enteraba. La consecuencia práctica es la que
 * se vio el 11-11-2026 con el respaldo: <b>el aviso existía y nadie lo
 * recibía</b>. Un fallo que solo conoce quien lo tropezó no se arregla.
 *
 * <p>Aquí los componentes se cuentan las cosas: {@code OllamaClient},
 * {@code EmailService} y las vigilancias escriben por {@link AvisoDeSalud}, y los
 * que tienen que reaccionar leen de aquí —{@link ReparadorDeServicios} para
 * intentar levantarlo, {@link InformeParaElTecnico} para avisar a una persona, y
 * el controlador de salud para que la interfaz le diga al usuario qué no va a
 * funcionarle—.
 *
 * <h2>Las tres reglas que hacen que esto no sea ruido</h2>
 * <ol>
 *   <li><b>Un fallo suelto no es una caída</b>, y cuántos hacen falta lo dice
 *       cada servicio en {@link Servicio#fallosParaDarloPorCaido()}: tres para
 *       la IA, porque una llamada lenta no es una caída; uno para el respaldo,
 *       porque «el más reciente tiene 136 h» se sabe de cierto a la primera.</li>
 *   <li><b>El informe se manda una vez por incidencia</b>, no una vez por fallo.
 *       Un servicio que falla cada diez segundos mandaría cien informes y el
 *       técnico dejaría de leerlos, que es la forma más común de quedarse sin
 *       vigilancia.</li>
 *   <li><b>La recuperación también se cuenta.</b> Sin el camino de éxito, un
 *       servicio que vuelve seguiría marcado como caído hasta el próximo
 *       reinicio y la interfaz enseñaría un aviso falso.</li>
 * </ol>
 */
@Component
public class SaludDelSistema implements AvisoDeSalud {

    private static final Logger log = LoggerFactory.getLogger(SaludDelSistema.class);

    /**
     * Qué se sabe de un servicio ahora mismo.
     *
     * <p>Es un record —inmutable— y se reemplaza entero dentro de
     * {@code compute()}: así dos hilos que fallan a la vez no pueden dejar el
     * contador a medio escribir, sin tener que sincronizar nada a mano.
     *
     * @param estado        en qué está
     * @param fallosSeguidos cuántas veces falló sin un éxito en medio
     * @param desde         cuándo empezó esta incidencia
     * @param ultimoDetalle lo que dijo el último fallo, tal cual
     * @param reparaciones  cuántas veces se intentó levantarlo
     * @param informado     si ya se le mandó el informe al técnico
     */
    public record Incidencia(EstadoDeServicio estado, int fallosSeguidos, Instant desde,
                             String ultimoDetalle, int reparaciones, boolean informado) {

        /** Cuánto lleva mal, para el informe y para el log. */
        public Duration duracion() {
            return Duration.between(desde, Instant.now());
        }
    }

    private final Map<Servicio, Incidencia> incidencias = new ConcurrentHashMap<>();
    private final Map<Servicio, AtomicInteger> medidores = new EnumMap<>(Servicio.class);
    private final InformeParaElTecnico informe;

    public SaludDelSistema(MeterRegistry registry, InformeParaElTecnico informe) {
        this.informe = informe;

        // Una métrica por servicio, con el ordinal del estado como valor
        // (0 operativo, 1 degradado, 2 caído). La señal tiene que existir también
        // donde no la lee una persona, como en VigilanciaDelRespaldo.
        //
        // strongReference(true) por la misma razón que allí: registry.gauge
        // guarda una referencia débil, y una recolección de basura dejaría la
        // métrica en NaN sin que nada lo avisara.
        for (Servicio servicio : Servicio.values()) {
            AtomicInteger medidor = new AtomicInteger(EstadoDeServicio.OPERATIVO.ordinal());
            medidores.put(servicio, medidor);
            Gauge.builder("sicot.salud.estado", medidor, AtomicInteger::get)
                    .tag("servicio", servicio.name().toLowerCase())
                    .description("0 operativo, 1 degradado, 2 caído")
                    .strongReference(true)
                    .register(registry);
        }
    }

    @Override
    public void exito(Servicio servicio) {
        Incidencia anterior = incidencias.remove(servicio);
        medidores.get(servicio).set(EstadoDeServicio.OPERATIVO.ordinal());

        if (anterior == null) {
            return; // Lo normal: estaba bien y sigue bien. Sin ruido.
        }

        log.info("{} volvió a responder tras {} min y {} fallo(s).",
                servicio.etiqueta(), anterior.duracion().toMinutes(), anterior.fallosSeguidos());

        // Al técnico se le cuenta el final solo si se le contó el principio.
        if (anterior.informado()) {
            informe.informarRecuperacion(servicio, anterior);
        }
    }

    @Override
    public void fallo(Servicio servicio, String detalle) {
        Incidencia actual = incidencias.compute(servicio, (clave, previa) -> {
            int fallos = (previa == null ? 0 : previa.fallosSeguidos()) + 1;
            return new Incidencia(
                    fallos >= servicio.fallosParaDarloPorCaido()
                            ? EstadoDeServicio.CAIDO : EstadoDeServicio.DEGRADADO,
                    fallos,
                    previa == null ? Instant.now() : previa.desde(),
                    detalle,
                    previa == null ? 0 : previa.reparaciones(),
                    previa != null && previa.informado());
        });

        medidores.get(servicio).set(actual.estado().ordinal());

        if (actual.estado() == EstadoDeServicio.CAIDO && !actual.informado()) {
            // Se marca como informado ANTES de informar: si el envío tarda o
            // falla, dos hilos que fallen a la vez no mandan dos informes.
            incidencias.computeIfPresent(servicio, (clave, i) -> new Incidencia(
                    i.estado(), i.fallosSeguidos(), i.desde(), i.ultimoDetalle(), i.reparaciones(), true));
            informe.informarCaida(servicio, actual);
        } else {
            log.warn("{} falló ({} de {} para darlo por caído): {}",
                    servicio.etiqueta(), actual.fallosSeguidos(), servicio.fallosParaDarloPorCaido(), detalle);
        }
    }

    /** Cuenta un intento de reparación, para que el informe diga qué se probó. */
    void anotarReparacion(Servicio servicio) {
        incidencias.computeIfPresent(servicio, (clave, i) -> new Incidencia(
                i.estado(), i.fallosSeguidos(), i.desde(), i.ultimoDetalle(), i.reparaciones() + 1, i.informado()));
    }

    public EstadoDeServicio estado(Servicio servicio) {
        Incidencia incidencia = incidencias.get(servicio);
        return incidencia == null ? EstadoDeServicio.OPERATIVO : incidencia.estado();
    }

    public Optional<Incidencia> incidencia(Servicio servicio) {
        return Optional.ofNullable(incidencias.get(servicio));
    }

    /** Los servicios que ahora mismo no están operativos. */
    public Map<Servicio, Incidencia> incidenciasAbiertas() {
        return Map.copyOf(incidencias);
    }

    /**
     * Los que hay que contarle a quien está usando SICOT: caídos de verdad y con
     * algo que decirle. Un servicio degradado no entra —todavía se está
     * intentando— y uno que no cambia lo que el usuario puede hacer tampoco.
     */
    public List<Servicio> loQueDebeSaberElUsuario() {
        return incidencias.entrySet().stream()
                .filter(e -> e.getValue().estado() == EstadoDeServicio.CAIDO)
                .map(Map.Entry::getKey)
                .filter(Servicio::visibleParaElUsuario)
                .filter(s -> s.mensajeParaElUsuario() != null)
                .sorted()
                .toList();
    }
}
