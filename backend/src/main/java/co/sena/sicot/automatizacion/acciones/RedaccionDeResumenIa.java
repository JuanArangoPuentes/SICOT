package co.sena.sicot.automatizacion.acciones;

import co.sena.sicot.automatizacion.AccionDeTarea;
import co.sena.sicot.automatizacion.PayloadJson;
import co.sena.sicot.automatizacion.FotoDelContrato;
import co.sena.sicot.automatizacion.LectorDeContratos;
import co.sena.sicot.automatizacion.PayloadDeTarea;
import co.sena.sicot.automatizacion.ResultadoDeAccion;
import co.sena.sicot.automatizacion.TareaEnEjecucion;
import co.sena.sicot.entity.Registro;
import co.sena.sicot.entity.enums.PrioridadAlerta;
import co.sena.sicot.entity.enums.TipoAlerta;
import co.sena.sicot.entity.enums.TipoTareaAutomatizada;
import co.sena.sicot.ia.OllamaClient;
import co.sena.sicot.repository.RegistroRepository;
import co.sena.sicot.service.AlertaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Redacta el resumen del periodo para el supervisor, con el modelo local.
 *
 * <h2>Qué hace exactamente el modelo, y qué no</h2>
 * <b>No decide nada.</b> Los hechos —qué se movió, qué falta, cuánto plazo
 * queda— se leen de la base antes de escribir el prompt, y se le entregan ya
 * calculados. Lo único que aporta el modelo es la redacción en español
 * administrativo.
 *
 * <p>Es la segunda regla invariante de ADR-008 llevada al código: la regla
 * decide, la IA solo redacta. En un sistema de contratación pública, dejar que
 * un modelo de 7B calcule si un contrato va atrasado sería sustituir aritmética
 * verificable por una conjetura que nadie puede auditar. El prompt se lo dice
 * explícitamente: no añadir, no inferir, no recomendar.
 *
 * <h2>El carril propio de concurrencia</h2>
 * {@code LimitadorDeUsoIa} pone un techo global de dos peticiones simultáneas y
 * una ventana por usuario. Pero la ventana por usuario <b>no aplica aquí</b>: un
 * hilo del motor no tiene usuario autenticado, así que esa mitad del límite se
 * salta y solo queda el techo global. Una tanda nocturna de cuarenta contratos
 * competiría por los mismos dos permisos que necesitan los supervisores
 * conectados.
 *
 * <p>De ahí el semáforo de un permiso de esta clase: como mucho una tarea
 * automática usa uno de los dos cupos globales, y siempre queda el otro libre
 * para una persona. Es un techo <i>encima</i> del de {@code LimitadorDeUsoIa},
 * no un reemplazo — la llamada sigue pasando por {@code OllamaClient}, que es
 * la única puerta al modelo.
 */
@Component
public class RedaccionDeResumenIa implements AccionDeTarea {

    private static final Logger log = LoggerFactory.getLogger(RedaccionDeResumenIa.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Un permiso, no más. Ver el encabezado: esto deja siempre libre al menos
     * uno de los dos cupos globales de Ollama para una persona.
     */
    private final Semaphore carrilAutomatico = new Semaphore(1, true);

    private final OllamaClient ollamaClient;
    private final LectorDeContratos lectorDeContratos;
    private final RegistroRepository registroRepository;
    private final AlertaService alertaService;
    private final PayloadJson payloadJson;

    public RedaccionDeResumenIa(OllamaClient ollamaClient,
                                LectorDeContratos lectorDeContratos,
                                RegistroRepository registroRepository,
                                AlertaService alertaService,
                                PayloadJson payloadJson) {
        this.ollamaClient = ollamaClient;
        this.lectorDeContratos = lectorDeContratos;
        this.registroRepository = registroRepository;
        this.alertaService = alertaService;
        this.payloadJson = payloadJson;
    }

    @Override
    public TipoTareaAutomatizada tipo() {
        return TipoTareaAutomatizada.REDACTAR_RESUMEN_IA;
    }

    @Override
    public ResultadoDeAccion ejecutar(TareaEnEjecucion tarea) {
        PayloadDeTarea.RedactarResumenIa datos =
                payloadJson.leer(tarea.payload(), PayloadDeTarea.RedactarResumenIa.class);

        if (tarea.contratoId() == null) {
            return ResultadoDeAccion.descartada("La tarea no tiene contrato asociado; no hay nada que resumir.");
        }

        // Se releen los hechos AHORA y no cuando la regla encoló la tarea. Entre
        // una cosa y otra pueden pasar horas: un resumen que describe el
        // contrato tal como estaba anoche, presentado hoy como el estado actual,
        // sería una afirmación falsa producida por el propio sistema.
        Optional<FotoDelContrato> foto = lectorDeContratos.porId(tarea.contratoId());
        if (foto.isEmpty()) {
            return ResultadoDeAccion.descartada("El contrato ya no existe.");
        }

        Instant desde = Instant.now().minus(Duration.ofDays(datos.diasDelPeriodo()));
        List<Registro> movimientos = registroRepository
                .findByContratoIdOrderByFechaDesc(tarea.contratoId()).stream()
                .filter(r -> r.getFecha().isAfter(desde))
                .limit(30)
                .toList();

        if (movimientos.isEmpty()) {
            // Un resumen de un periodo sin actividad no es un resumen: es una
            // frase vacía consumiendo un cupo del modelo y ocupando un renglón
            // en la bandeja del supervisor.
            return ResultadoDeAccion.descartada(
                    "Sin movimientos en los últimos " + datos.diasDelPeriodo() + " días; no hay nada que resumir.");
        }

        String texto = redactar(foto.get(), movimientos, datos.diasDelPeriodo());
        alertaService.crearDelSistema(tarea.contratoId(), TipoAlerta.IA, PrioridadAlerta.BAJA, texto);
        return ResultadoDeAccion.hecha();
    }

    private String redactar(FotoDelContrato contrato, List<Registro> movimientos, int dias) {
        String prompt = construirPrompt(contrato, movimientos, dias);

        boolean adquirido;
        try {
            // Espera acotada: si el carril está ocupado más de un minuto, esta
            // ronda se salta y la tarea se reintenta. Bloquear indefinidamente
            // retendría un hilo del motor sin límite.
            adquirido = carrilAutomatico.tryAcquire(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Redacción de resumen interrumpida.", e);
        }
        if (!adquirido) {
            throw new IllegalStateException(
                    "El carril de IA automática está ocupado; se reintentará más tarde.");
        }
        try {
            String respuesta = ollamaClient.generar(prompt, false);
            log.info("Resumen de IA generado para el contrato {} ({} movimientos del periodo).",
                    contrato.numeroContrato(), movimientos.size());
            return "Resumen automático de los últimos " + dias + " días (redactado por la IA local "
                    + "a partir de los datos registrados en SICOT):\n\n" + respuesta.trim();
        } finally {
            carrilAutomatico.release();
        }
    }

    /**
     * El prompt lleva los hechos ya resueltos y una instrucción explícita de no
     * añadir nada. No es una precaución teórica: un modelo al que se le pide
     * «resume el estado de este contrato» rellena con recomendaciones
     * procedimentales plausibles y falsas, y en este dominio una recomendación
     * falsa con aspecto oficial es peor que no decir nada.
     */
    private String construirPrompt(FotoDelContrato contrato, List<Registro> movimientos, int dias) {
        String avance = contrato.fraccionDeAvance() == null
                ? "sin subetapas registradas"
                : String.format("%d de %d subetapas completadas (%.0f%%)",
                        contrato.subetapasCompletadas(), contrato.subetapasTotales(),
                        contrato.fraccionDeAvance() * 100);

        String actividad = movimientos.stream()
                .map(r -> "- " + FECHA.format(r.getFecha().atZone(ZoneId.systemDefault()))
                        + " · " + r.getAccion() + ": " + (r.getDescripcion() == null ? "" : r.getDescripcion()))
                .collect(Collectors.joining("\n"));

        return """
                Eres un asistente administrativo del SENA. Redacta un resumen breve (máximo 120 \
                palabras) en español administrativo, dirigido al supervisor del contrato.

                REGLAS ESTRICTAS:
                - Usa ÚNICAMENTE los datos listados abajo. No agregues hechos, fechas, códigos de \
                formato ni nombres que no aparezcan aquí.
                - No des recomendaciones, no interpretes normativa y no sugieras trámites.
                - No inventes el estado de nada que no esté en la lista de actividad.
                - Escribe en prosa continua, sin viñetas y sin encabezados.

                DATOS DEL CONTRATO
                Número: %s
                Objeto: %s
                Supervisor: %s
                Plazo: %s a %s
                Avance: %s

                ACTIVIDAD REGISTRADA EN LOS ÚLTIMOS %d DÍAS
                %s
                """.formatted(
                contrato.numeroContrato(),
                contrato.objeto(),
                contrato.supervisorNombre() == null ? "sin asignar" : contrato.supervisorNombre(),
                contrato.fechaInicio() == null ? "sin definir" : FECHA.format(contrato.fechaInicio()),
                contrato.fechaFin() == null ? "sin definir" : FECHA.format(contrato.fechaFin()),
                avance,
                dias,
                actividad);
    }
}
