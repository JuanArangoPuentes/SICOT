package co.sena.sicot.automatizacion;

import co.sena.sicot.dto.automatizacion.TareaAutomatizadaResponse;
import co.sena.sicot.entity.TareaAutomatizada;
import co.sena.sicot.entity.enums.EstadoTareaAutomatizada;
import co.sena.sicot.repository.ContratoRepository;
import co.sena.sicot.repository.TareaAutomatizadaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Dueño de las escrituras sobre la cola. Cada operación es su propia
 * transacción, corta y cerrada.
 *
 * <h2>Por qué las transacciones están aquí y no en el ejecutor</h2>
 * El ciclo de una tarea son tres pasos que <b>no</b> pueden compartir
 * transacción: reservarla, ejecutar el efecto y anotar el resultado. Si los tres
 * fueran una sola transacción, un fallo al enviar el correo desharía también el
 * incremento de {@code intentos} — y la tarea volvería a estar pendiente con
 * cero intentos, para siempre. Un bucle infinito que además parece una cola sana.
 *
 * <p>Separarlos exige que cada paso confirme por su cuenta. En Spring, un método
 * {@code @Transactional} que llama a otro del mismo objeto no pasa por el proxy
 * y comparte transacción con el primero, así que las tres operaciones tienen que
 * vivir en un bean distinto del que las orquesta. Eso es esta clase.
 */
@Service
public class AlmacenDeTareas {

    private static final Logger log = LoggerFactory.getLogger(AlmacenDeTareas.class);

    /**
     * Tope del listado de operación. Mismo motivo que en {@code AlertaService} y
     * {@code RegistroService}: una cola sana acumula filas COMPLETADA sin
     * límite, y «ver las tareas» no puede significar cargar la tabla entera.
     */
    private static final int MAX_TAREAS_LISTADO = 200;

    private final TareaAutomatizadaRepository tareaRepository;
    private final ContratoRepository contratoRepository;
    private final PayloadJson payloadJson;
    private final AutomatizacionProperties propiedades;

    public AlmacenDeTareas(TareaAutomatizadaRepository tareaRepository,
                           ContratoRepository contratoRepository,
                           PayloadJson payloadJson,
                           AutomatizacionProperties propiedades) {
        this.tareaRepository = tareaRepository;
        this.contratoRepository = contratoRepository;
        this.payloadJson = payloadJson;
        this.propiedades = propiedades;
    }

    /**
     * Guarda lo que pidió una regla, si no estaba ya pedido.
     *
     * <h2>Dos defensas contra el duplicado, no una</h2>
     * Se consulta primero por la clave —barato y evita ruido en el log— y aun
     * así se captura la violación de unicidad. La consulta previa sola no basta:
     * entre el {@code SELECT} y el {@code INSERT} hay una ventana en la que otro
     * hilo puede insertar la misma clave. Es poco probable y ocurriría igual, y
     * la primera vez sería un correo duplicado a un supervisor.
     *
     * <p>Que la segunda inserción la rechace la base y no el código es lo que
     * convierte la idempotencia en una garantía en vez de en una intención.
     *
     * <p>{@code REQUIRES_NEW} porque quien llama suele ser un oyente de eventos
     * que corre <b>después</b> de confirmar la transacción de negocio: en ese
     * punto no hay transacción activa que reutilizar.
     *
     * @return la tarea creada, o vacío si ya existía
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<TareaAutomatizada> encolar(TareaSolicitada solicitud) {
        if (tareaRepository.existsByClaveIdempotencia(solicitud.claveIdempotencia())) {
            return Optional.empty();
        }

        TareaAutomatizada tarea = new TareaAutomatizada();
        tarea.setRegla(solicitud.regla());
        tarea.setTipo(solicitud.payload().tipo());
        tarea.setClaveIdempotencia(solicitud.claveIdempotencia());
        tarea.setPayload(payloadJson.escribir(solicitud.payload()));
        tarea.setEjecutarEn(solicitud.ejecutarEn());
        if (solicitud.contratoId() != null) {
            // getReferenceById y no findById: solo hace falta la clave foránea,
            // y traer el contrato entero para descartarlo sería una consulta
            // por cada tarea encolada.
            tarea.setContrato(contratoRepository.getReferenceById(solicitud.contratoId()));
        }

        try {
            return Optional.of(tareaRepository.saveAndFlush(tarea));
        } catch (DataIntegrityViolationException e) {
            // La otra mitad de la carrera ganó. No es un error: el trabajo ya
            // está encolado, que es exactamente lo que se quería.
            log.debug("Tarea duplicada descartada por la base: {}", solicitud.claveIdempotencia());
            return Optional.empty();
        }
    }

    /**
     * Reserva la tarea y devuelve lo que la acción necesita para ejecutarla.
     *
     * <p>Reservar y leer van en la <b>misma</b> transacción a propósito. Si la
     * lectura fuera aparte, entre una y otra podría cambiar la fila, y sobre
     * todo la entidad quedaría desatachada al cerrarse la sesión: el
     * {@code contrato} perezoso reventaría después, dentro de la acción y lejos
     * de la causa. El record se construye aquí dentro, con la sesión abierta.
     *
     * @return vacío si otro trabajador se adelantó
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<TareaEnEjecucion> reclamar(Long id) {
        if (tareaRepository.reclamar(id) != 1) {
            return Optional.empty();
        }
        return tareaRepository.findById(id).map(TareaEnEjecucion::de);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Long> idsPendientes(Instant ahora) {
        return tareaRepository.idsPendientes(ahora, PageRequest.of(0, propiedades.tamanoDelLote()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarCompletada(Long id) {
        tareaRepository.findById(id).ifPresent(tarea -> {
            tarea.setEstado(EstadoTareaAutomatizada.COMPLETADA);
            tarea.setUltimoError(null);
            tareaRepository.save(tarea);
        });
    }

    /**
     * Deja constancia de que la tarea ya no tiene sentido y no volverá a
     * intentarse. No es un fallo y no debe aparecer como tal: ver
     * {@link EstadoTareaAutomatizada#DESCARTADA}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarDescartada(Long id, String motivo) {
        tareaRepository.findById(id).ifPresent(tarea -> {
            tarea.setEstado(EstadoTareaAutomatizada.DESCARTADA);
            tarea.setUltimoError(motivo);
            tareaRepository.save(tarea);
        });
    }

    /**
     * Programa un reintento, o se rinde si ya se intentó demasiadas veces.
     *
     * <p>La espera crece de forma exponencial ({@code base × 2^intentos}).
     * Reintentar de inmediato contra un servidor SMTP caído no lo levanta:
     * lo mantiene ocupado rechazando conexiones mientras la cola entera se
     * atasca detrás.
     *
     * @return {@code true} si quedó programada para reintentar
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registrarFallo(Long id, String error) {
        Optional<TareaAutomatizada> encontrada = tareaRepository.findById(id);
        if (encontrada.isEmpty()) {
            return false;
        }
        TareaAutomatizada tarea = encontrada.get();
        tarea.setUltimoError(recortar(error));

        if (tarea.getIntentos() >= propiedades.maximoDeIntentos()) {
            tarea.setEstado(EstadoTareaAutomatizada.FALLIDA);
            tareaRepository.save(tarea);
            log.error("Automatización '{}' (tarea {}) agotó {} intentos y queda FALLIDA: {}",
                    tarea.getRegla(), id, tarea.getIntentos(), tarea.getUltimoError());
            return false;
        }

        Duration espera = propiedades.esperaBase().multipliedBy(1L << tarea.getIntentos());
        tarea.setEstado(EstadoTareaAutomatizada.PENDIENTE);
        tarea.setEjecutarEn(Instant.now().plus(espera));
        tareaRepository.save(tarea);
        log.warn("Automatización '{}' (tarea {}) falló en el intento {}; reintento en {}: {}",
                tarea.getRegla(), id, tarea.getIntentos(), espera, tarea.getUltimoError());
        return true;
    }

    /**
     * Rescata las tareas que quedaron reservadas por un proceso que ya no
     * existe.
     *
     * <p>Es la fuga clásica de toda cola basada en estado: matar el backend
     * mientras ejecuta una tarea la deja EN_PROCESO para siempre — nadie la
     * reclama porque ya no está pendiente, y nadie la termina porque quien la
     * tenía murió. No se manifiesta hasta el primer reinicio brusco en
     * producción, y para entonces son varias.
     *
     * @return cuántas se liberaron
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int liberarAbandonadas() {
        Instant limite = Instant.now().minus(propiedades.abandonoTrasVer());
        int liberadas = tareaRepository.liberarTareasAbandonadas(limite);
        if (liberadas > 0) {
            log.warn("Se devolvieron a la cola {} tarea(s) que quedaron EN_PROCESO sin terminar "
                    + "(probablemente por un reinicio a mitad de ejecución).", liberadas);
        }
        return liberadas;
    }

    /**
     * Listado para la pantalla de operación, ya convertido a DTO.
     *
     * <p>La conversión ocurre <b>dentro</b> de la transacción a propósito. Con
     * {@code spring.jpa.open-in-view=false}, devolver entidades dejaría el
     * {@code contrato} perezoso sin sesión y el controlador reventaría al
     * mapearlo. Es el mismo criterio que sigue el resto del backend: de un
     * servicio salen DTO, no entidades.
     */
    @Transactional(readOnly = true)
    public List<TareaAutomatizadaResponse> listar(EstadoTareaAutomatizada estado) {
        PageRequest limite = PageRequest.of(0, MAX_TAREAS_LISTADO);
        List<TareaAutomatizada> tareas = estado == null
                ? tareaRepository.findAllByOrderByFechaCreacionDesc(limite)
                : tareaRepository.findByEstadoOrderByFechaCreacionDesc(estado, limite);
        return tareas.stream().map(AlmacenDeTareas::aRespuesta).toList();
    }

    private static TareaAutomatizadaResponse aRespuesta(TareaAutomatizada t) {
        return new TareaAutomatizadaResponse(
                t.getId(),
                t.getRegla(),
                t.getTipo(),
                t.getEstado(),
                t.getContrato() != null ? t.getContrato().getId() : null,
                t.getIntentos(),
                t.getEjecutarEn(),
                t.getUltimoError(),
                t.getFechaCreacion(),
                t.getFechaActualizacion());
    }

    @Transactional(readOnly = true)
    public long contar(EstadoTareaAutomatizada estado) {
        return tareaRepository.countByEstado(estado);
    }

    /**
     * El mensaje de error va a una columna TEXT sin límite, pero una traza de
     * varios miles de caracteres por fila convierte la tabla en un almacén de
     * trazas. Lo que hace falta para diagnosticar está al principio.
     */
    private String recortar(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= 1000 ? error : error.substring(0, 1000) + "…";
    }
}
