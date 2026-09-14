package co.sena.sicot.automatizacion.acciones;

import co.sena.sicot.automatizacion.AccionDeTarea;
import co.sena.sicot.automatizacion.FotoDelContrato;
import co.sena.sicot.automatizacion.LectorDeContratos;
import co.sena.sicot.automatizacion.PayloadDeTarea;
import co.sena.sicot.automatizacion.PayloadJson;
import co.sena.sicot.automatizacion.ResultadoDeAccion;
import co.sena.sicot.automatizacion.TareaEnEjecucion;
import co.sena.sicot.entity.Registro;
import co.sena.sicot.entity.enums.PrioridadAlerta;
import co.sena.sicot.entity.enums.TipoAlerta;
import co.sena.sicot.entity.enums.TipoTareaAutomatizada;
import co.sena.sicot.repository.RegistroRepository;
import co.sena.sicot.service.AlertaService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compone el resumen del periodo para el supervisor, a partir de los datos ya
 * registrados en SICOT.
 *
 * <h2>Por qué esto no llama al modelo local</h2>
 * Durante un tiempo lo llamaba: esta clase se llamaba {@code
 * RedaccionDeResumenIa} y el texto lo escribía Ollama a partir de un prompt con
 * los hechos ya resueltos. Se midió con el prompt real y los tres tamaños de
 * modelo que caben en un portátil corriente, y ninguno sostenía los hechos sin
 * alterarlos: cifras cambiadas, una subetapa dada por completada y por «en
 * curso» en la misma frase, y los propios códigos internos de auditoría
 * volcados al texto que lee el supervisor. Las mediciones completas están en el
 * encabezado de {@code V16__resumen_semanal_sin_modelo.sql}.
 *
 * <p>El diagnóstico que importa para el futuro: el problema no era el tamaño del
 * modelo. Era pedirle lo único que un modelo de lenguaje no garantiza —sostener
 * hechos— cuando esos hechos ya estaban calculados aquí antes de construir el
 * prompt. Si vuelve a plantearse meter un modelo en esta ruta, esa es la
 * pregunta que hay que responder primero.
 *
 * <p>La consecuencia práctica es la que perseguía ADR-008: el motor entero
 * funciona ahora en un equipo sin Ollama instalado, y el modelo local queda
 * libre para las rutas donde su trabajo no es repetir cifras —el chat del
 * copiloto y la extracción de datos de un PDF—.
 *
 * <h2>Por qué se descartan los movimientos derivados</h2>
 * Cada {@code SUBETAPA_AVANZADA} produce además un {@code ETAPA_ACTUALIZADA}
 * con el porcentaje recalculado. Es información correcta, pero es consecuencia
 * del mismo hecho: contarla aparte duplicaría cada avance en el resumen.
 */
@Component
public class RedaccionDeResumen implements AccionDeTarea {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Acciones que son consecuencia automática de otra y no un hecho propio.
     * Ver el encabezado.
     */
    private static final Set<String> DERIVADAS = Set.of("ETAPA_ACTUALIZADA", "ETAPA_RETROCEDIDA");

    /** Extrae el «1.4» de «Subetapa 1.4 (Nombre) avanzó de …». */
    private static final Pattern NUMERO_DE_SUBETAPA = Pattern.compile("^Subetapa (\\d+\\.\\d+)");

    private final LectorDeContratos lectorDeContratos;
    private final RegistroRepository registroRepository;
    private final AlertaService alertaService;
    private final PayloadJson payloadJson;

    public RedaccionDeResumen(LectorDeContratos lectorDeContratos,
                              RegistroRepository registroRepository,
                              AlertaService alertaService,
                              PayloadJson payloadJson) {
        this.lectorDeContratos = lectorDeContratos;
        this.registroRepository = registroRepository;
        this.alertaService = alertaService;
        this.payloadJson = payloadJson;
    }

    @Override
    public TipoTareaAutomatizada tipo() {
        return TipoTareaAutomatizada.REDACTAR_RESUMEN;
    }

    @Override
    public ResultadoDeAccion ejecutar(TareaEnEjecucion tarea) {
        PayloadDeTarea.RedactarResumen datos =
                payloadJson.leer(tarea.payload(), PayloadDeTarea.RedactarResumen.class);

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
                .findByContratoIdOrderByFechaDesc(tarea.contratoId(), PageRequest.of(0, 60)).stream()
                .filter(r -> r.getFecha().isAfter(desde))
                .filter(r -> !DERIVADAS.contains(r.getAccion()))
                .limit(30)
                .toList();

        if (movimientos.isEmpty()) {
            // Un resumen de un periodo sin actividad no es un resumen: es una
            // frase vacía ocupando un renglón en la bandeja del supervisor.
            return ResultadoDeAccion.descartada(
                    "Sin movimientos en los últimos " + datos.diasDelPeriodo() + " días; no hay nada que resumir.");
        }

        String texto = componer(foto.get(), movimientos, datos.diasDelPeriodo());
        // RECORDATORIO y no IA. La alerta se marcaba como IA cuando el texto lo
        // escribía el modelo; mantener esa etiqueta ahora sería afirmar en la
        // base de datos algo que dejó de ser cierto, y engañaría a cualquiera
        // que filtre las alertas por su origen. RECORDATORIO ya existe en el
        // catálogo de V1 y es lo que esto es: un aviso periódico de lo ocurrido.
        alertaService.crearDelSistema(tarea.contratoId(), TipoAlerta.RECORDATORIO, PrioridadAlerta.BAJA, texto);
        return ResultadoDeAccion.hecha();
    }

    /**
     * Arma el texto con plantillas. Cada dato que aparece aquí sale de una
     * consulta, no de una inferencia: es la propiedad que hacía falta y que la
     * redacción por modelo no podía ofrecer.
     */
    private String componer(FotoDelContrato contrato, List<Registro> movimientos, int dias) {
        List<String> frases = new ArrayList<>();

        // LinkedHashSet: si una subetapa se completó, se revirtió y se volvió a
        // completar dentro del periodo, interesa nombrarla una vez, y en el
        // orden en que ocurrió.
        Set<String> completadas = new LinkedHashSet<>();
        int revertidas = 0;
        int firmados = 0;
        int cargados = 0;
        String ultimoEstado = null;
        boolean integridad = false;

        // Los movimientos llegan de la más reciente a la más antigua; para
        // narrar el periodo hace falta el orden inverso.
        List<Registro> enOrden = new ArrayList<>(movimientos);
        java.util.Collections.reverse(enOrden);

        for (Registro r : enOrden) {
            String descripcion = r.getDescripcion() == null ? "" : r.getDescripcion();
            switch (r.getAccion()) {
                case "SUBETAPA_AVANZADA" -> {
                    Matcher m = NUMERO_DE_SUBETAPA.matcher(descripcion);
                    if (m.find() && descripcion.contains("a COMPLETADA")) {
                        completadas.add(m.group(1));
                    }
                }
                case "SUBETAPA_REVERTIDA" -> revertidas++;
                case "DOCUMENTO_FIRMADO" -> firmados++;
                case "DOCUMENTO_CARGADO" -> cargados++;
                case "ESTADO_CAMBIADO" -> ultimoEstado = descripcion;
                case "INTEGRIDAD_COMPROMETIDA" -> integridad = true;
                default -> { /* El resto no aporta al resumen del periodo. */ }
            }
        }

        if (!completadas.isEmpty()) {
            frases.add(completadas.size() == 1
                    ? "se completó la subetapa " + completadas.iterator().next()
                    : "se completaron las subetapas " + enumerar(new ArrayList<>(completadas)));
        }
        if (revertidas > 0) {
            frases.add(revertidas == 1 ? "se revirtió una subetapa" : "se revirtieron " + revertidas + " subetapas");
        }
        if (firmados > 0) {
            frases.add(firmados == 1 ? "se firmó un documento" : "se firmaron " + firmados + " documentos");
        }
        if (cargados > 0) {
            frases.add(cargados == 1 ? "se cargó un documento" : "se cargaron " + cargados + " documentos");
        }
        if (ultimoEstado != null) {
            // La descripción ya viene redactada por RegistroService como
            // «Estado del contrato: BORRADOR → ACTIVO.»
            frases.add(ultimoEstado.replace("Estado del contrato:", "el contrato pasó de")
                    .replace("→", "a").replace(".", "").trim());
        }

        StringBuilder texto = new StringBuilder()
                .append("Resumen automático de los últimos ").append(dias)
                .append(" días, compuesto por SICOT con los datos registrados:\n\n")
                .append("En el contrato ").append(contrato.numeroContrato());

        if (contrato.fechaInicio() != null && contrato.fechaFin() != null) {
            texto.append(" (plazo del ").append(FECHA.format(contrato.fechaInicio()))
                    .append(" al ").append(FECHA.format(contrato.fechaFin())).append(")");
        }
        texto.append(", durante el periodo ").append(String.join("; ", frases)).append(".");

        if (contrato.subetapasTotales() > 0) {
            long pct = Math.round(contrato.subetapasCompletadas() * 100.0 / contrato.subetapasTotales());
            texto.append(" El avance acumulado es de ").append(contrato.subetapasCompletadas())
                    .append(" de ").append(contrato.subetapasTotales())
                    .append(" subetapas (").append(pct).append("%).");
        } else {
            // Sin subetapas sembradas no se afirma un porcentaje: decir «0%»
            // sugeriría que no se ha avanzado, cuando lo cierto es que no hay
            // con qué medirlo.
            texto.append(" El contrato no tiene subetapas registradas, así que no se informa un porcentaje de avance.");
        }

        if (integridad) {
            texto.append(" Atención: en el periodo se detectó al menos un documento cuya huella de integridad "
                    + "no coincide; revíselo en la pestaña de documentos.");
        }

        return texto.toString();
    }

    /** «1.1, 1.2 y 1.3» — con «y» antes del último, como se escribe en español. */
    private String enumerar(List<String> elementos) {
        if (elementos.size() == 1) {
            return elementos.get(0);
        }
        return String.join(", ", elementos.subList(0, elementos.size() - 1))
                + " y " + elementos.get(elementos.size() - 1);
    }
}
