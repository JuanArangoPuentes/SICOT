package co.sena.sicot.ia;

import co.sena.sicot.dto.documento.DocumentoResponse;
import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Documento;
import co.sena.sicot.entity.Subetapa;
import co.sena.sicot.entity.enums.EstadoDocumento;
import co.sena.sicot.entity.enums.TipoDocumento;
import co.sena.sicot.exception.BusinessException;
import co.sena.sicot.mapper.DocumentoMapper;
import co.sena.sicot.repository.DocumentoRepository;
import co.sena.sicot.repository.SubetapaRepository;
import co.sena.sicot.service.ContratoService;
import co.sena.sicot.service.RegistroService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Genera el borrador (estado PENDIENTE) de un documento formal del supervisor.
 * El supervisor lo firma después con {@code DocumentoService.firmar}.
 *
 * <h2>Qué hace el código y qué hace la IA</h2>
 * El documento lo arma {@link RedactorDeDocumentos} con los formatos reales y
 * los datos exactos del contrato: ningún dato del contrato pasa por el modelo.
 * Así fue desde que la prueba integral del 24-09-2026 mostró al modelo
 * alterando nombres, valores y entidades en los cinco documentos (el detalle
 * está en {@link RedactorDeDocumentos}).
 *
 * <p>La IA hace una sola cosa, y solo si el supervisor escribió notas sobre lo
 * que hizo en el paso: pasarlas a redacción formal para el apartado de
 * observaciones. Esa redacción se revisa con {@link FidelidadDeRedaccion}
 * antes de usarse, y si trae cifras que no estaban, o si el modelo no
 * responde, van las notas tal cual. Un documento nunca deja de generarse
 * porque la IA falle: los datos que lo hacen válido no dependen de ella.
 */
@Service
public class GeneracionDocumentoService {

    private static final Logger log = LoggerFactory.getLogger(GeneracionDocumentoService.class);

    /**
     * Tope de las notas que se mandan al modelo. Con más texto la redacción en
     * un equipo sin GPU se va de los minutos, y unas observaciones de un paso
     * no necesitan más.
     */
    static final int MAX_NOTAS = 2000;

    private final ContratoService contratoService;
    private final SubetapaRepository subetapaRepository;
    private final DocumentoRepository documentoRepository;
    private final OllamaClient ollamaClient;
    private final SimplePdfWriter pdfWriter;
    private final RegistroService registroService;
    private final Clock reloj;

    public GeneracionDocumentoService(ContratoService contratoService, SubetapaRepository subetapaRepository,
                                      DocumentoRepository documentoRepository, OllamaClient ollamaClient,
                                      SimplePdfWriter pdfWriter, RegistroService registroService, Clock reloj) {
        this.contratoService = contratoService;
        this.subetapaRepository = subetapaRepository;
        this.documentoRepository = documentoRepository;
        this.ollamaClient = ollamaClient;
        this.pdfWriter = pdfWriter;
        this.registroService = registroService;
        this.reloj = reloj;
    }

    public DocumentoResponse generar(Long contratoId, Long subetapaId, String tipoClave) {
        return generar(contratoId, subetapaId, tipoClave, null);
    }

    // Sin @Transactional a propósito — mismo motivo que CopilotoChatService.responder:
    // la redacción con el modelo puede tardar minutos y no debe retener una
    // conexión del pool de Hikari todo ese tiempo. contratoService.buscar,
    // subetapaRepository y documentoRepository.save abren y cierran sus propias
    // transacciones cortas.
    public DocumentoResponse generar(Long contratoId, Long subetapaId, String tipoClave, String notas) {
        PlantillaDocumentoIA plantilla = PlantillaDocumentoIA.CATALOGO.get(tipoClave);
        if (plantilla == null) {
            throw new BusinessException("Tipo de documento no reconocido: " + tipoClave);
        }
        Contrato contrato = contratoService.buscar(contratoId);

        // La consulta exige que la subetapa sea de ESTE contrato: sin esa
        // condición, un supervisor podía generar un documento en su contrato
        // apuntando a la subetapa de otro, contaminando el avance de un
        // contrato ajeno.
        Subetapa subetapa = null;
        if (subetapaId != null) {
            subetapa = subetapaRepository.findByIdAndEtapaContratoId(subetapaId, contrato.getId())
                    .orElseThrow(() -> new BusinessException(
                            "La subetapa indicada no existe o no pertenece a este contrato."));
        }

        Observaciones obs = observaciones(plantilla, contrato, notas);
        LocalDate hoy = LocalDate.now(reloj);
        List<BloqueDocumento> bloques = RedactorDeDocumentos.componer(plantilla, contrato, hoy, obs.texto());

        String firmante = contrato.getSupervisor() != null ? contrato.getSupervisor().getNombre() : null;
        String origen = obs.conIa() ? "Generado en SICOT con apoyo del Copiloto IA" : "Generado en SICOT";
        byte[] pdf = pdfWriter.generar(plantilla.nombre(), plantilla.codigo(), contrato.getNumeroContrato(),
                firmante, "Supervisor del contrato", bloques, origen);

        Documento documento = new Documento();
        documento.setContrato(contrato);
        documento.setSubetapa(subetapa);
        documento.setNombre(plantilla.nombre() + " — " + contrato.getNumeroContrato());
        documento.setTipo(TipoDocumento.PDF);
        documento.setContentType("application/pdf");
        documento.setContenido(pdf);
        documento.setTamanioBytes((long) pdf.length);
        documento.setEstado(EstadoDocumento.PENDIENTE);
        // «generadoPorIa» conserva el nombre de la columna, pero lo que marca es
        // que el documento lo produjo SICOT y no se cargó desde fuera: el panel
        // del supervisor lo usa para reconocer los documentos formales del
        // proceso. Si la IA intervino lo dice el registro, que es donde queda
        // la trazabilidad.
        documento.setGeneradoPorIa(true);

        Documento guardado = documentoRepository.save(documento);

        registroService.registrar(contrato, "DOCUMENTO_GENERADO",
                plantilla.nombre() + " (" + plantilla.codigo() + ") generado por SICOT con los datos del contrato"
                        + obs.descripcion()
                        + (subetapa != null ? " en la subetapa " + subetapa.getCodigo() : "")
                        + "; queda pendiente de firma.");
        return DocumentoMapper.toResponse(guardado);
    }

    /** El apartado de observaciones y cómo se obtuvo, para el registro. */
    private record Observaciones(String texto, boolean conIa, String descripcion) {
    }

    private Observaciones observaciones(PlantillaDocumentoIA plantilla, Contrato contrato, String notas) {
        if (notas == null || notas.isBlank()) {
            return new Observaciones(null, false, "");
        }
        String recortadas = notas.strip();
        if (recortadas.length() > MAX_NOTAS) {
            recortadas = recortadas.substring(0, MAX_NOTAS);
        }

        String prompt = """
                Eres el Copiloto de SICOT. Vas a redactar el apartado de observaciones del documento "%s" (%s).
                Convierte en uno o dos párrafos formales, en primera persona del supervisor y en español \
                institucional, las NOTAS DEL SUPERVISOR que aparecen más abajo.

                Reglas obligatorias:
                - Usa únicamente los hechos que dicen las notas. No agregues cifras, fechas, nombres, \
                entidades, cantidades ni verificaciones que no estén en ellas.
                - No repitas los datos del contrato (número, valor, fechas, contratista): ya van en la ficha.
                - Si las notas son breves, el texto también debe serlo.
                - Responde solo con el texto final, sin títulos, sin viñetas y sin markdown.

                %s

                %s
                """.formatted(plantilla.nombre(), plantilla.codigo(), EntradaNoConfiable.INSTRUCCION,
                EntradaNoConfiable.bloque("NOTAS DEL SUPERVISOR", recortadas));

        String redactado;
        try {
            long inicio = System.currentTimeMillis();
            redactado = ollamaClient.generar(prompt, false).strip();
            log.info("Observaciones de '{}' redactadas en {} ms", plantilla.nombre(), System.currentTimeMillis() - inicio);
        } catch (IaNoDisponibleException e) {
            // El documento no depende de la IA: si no responde, van las notas
            // tal cual y el supervisor no pierde el paso.
            log.warn("No se pudieron redactar las observaciones de '{}' con la IA; se usan las notas tal cual: {}",
                    plantilla.nombre(), e.getMessage());
            return new Observaciones(recortadas, false,
                    "; las observaciones van tal como las escribió el supervisor (la IA no respondió)");
        }

        List<String> nombres = new ArrayList<>();
        nombres.add(contrato.getContratista());
        nombres.add(contrato.getRepresentanteLegal());
        if (contrato.getSupervisor() != null) {
            nombres.add(contrato.getSupervisor().getNombre());
        }
        String corregido = FidelidadDeRedaccion.corregirNombres(redactado, nombres);

        List<String> datos = new ArrayList<>(nombres);
        datos.add(contrato.getNumeroContrato());
        datos.add(contrato.getContratistaNit());
        datos.add(contrato.getNumeroRegistroPresupuestal());
        datos.add(contrato.getValor() != null ? contrato.getValor().toPlainString() : null);
        if (corregido.isBlank() || !FidelidadDeRedaccion.sinCifrasInventadas(corregido, recortadas, datos)) {
            log.warn("La redacción de '{}' traía cifras que no están en las notas; se usan las notas tal cual.",
                    plantilla.nombre());
            return new Observaciones(recortadas, false,
                    "; las observaciones van tal como las escribió el supervisor (la redacción de la IA agregaba"
                            + " cifras que no estaban en sus notas)");
        }
        return new Observaciones(corregido, true,
                "; las observaciones del supervisor se redactaron con el copiloto");
    }
}
