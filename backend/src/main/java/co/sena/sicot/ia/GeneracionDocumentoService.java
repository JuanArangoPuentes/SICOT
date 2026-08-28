package co.sena.sicot.ia;

import co.sena.sicot.dto.documento.DocumentoResponse;
import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Documento;
import co.sena.sicot.entity.Subetapa;
import co.sena.sicot.entity.enums.EstadoDocumento;
import co.sena.sicot.entity.enums.TipoDocumento;
import co.sena.sicot.exception.BusinessException;
import co.sena.sicot.exception.ResourceNotFoundException;
import co.sena.sicot.mapper.DocumentoMapper;
import co.sena.sicot.repository.DocumentoRepository;
import co.sena.sicot.repository.SubetapaRepository;
import co.sena.sicot.service.ContratoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * El Copiloto IA redacta el contenido de un documento formal a partir de los
 * datos reales del contrato (nunca inventa el número de contrato, valores o
 * fechas — esos van directo del backend al prompt). El supervisor revisa y
 * firma después con DocumentoService.firmar; este servicio solo genera el
 * borrador (estado PENDIENTE).
 */
@Service
public class GeneracionDocumentoService {

    private static final Logger log = LoggerFactory.getLogger(GeneracionDocumentoService.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ContratoService contratoService;
    private final SubetapaRepository subetapaRepository;
    private final DocumentoRepository documentoRepository;
    private final OllamaClient ollamaClient;
    private final SimplePdfWriter pdfWriter;

    public GeneracionDocumentoService(ContratoService contratoService, SubetapaRepository subetapaRepository,
                                       DocumentoRepository documentoRepository, OllamaClient ollamaClient,
                                       SimplePdfWriter pdfWriter) {
        this.contratoService = contratoService;
        this.subetapaRepository = subetapaRepository;
        this.documentoRepository = documentoRepository;
        this.ollamaClient = ollamaClient;
        this.pdfWriter = pdfWriter;
    }

    // Sin @Transactional a propósito — mismo motivo que CopilotoChatService.responder:
    // ollamaClient.generar() puede tardar hasta 15 minutos y no debe retener
    // una conexión del pool de Hikari todo ese tiempo. contratoService.buscar,
    // subetapaRepository.findById y documentoRepository.save siguen siendo
    // transaccionales por su cuenta (cada uno abre y cierra su propia
    // transacción corta); solo la espera a Ollama queda fuera de todas ellas.
    public DocumentoResponse generar(Long contratoId, Long subetapaId, String tipoClave) {
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

        String datosContrato = """
                Contrato Nro.: %s
                Objeto: %s
                Valor del contrato: %s
                Fecha de inicio: %s
                Fecha de terminación: %s
                Contratista: %s
                NIT o CC del contratista: %s
                Representante legal: %s
                Lugar de ejecución: %s
                Número de registro presupuestal: %s
                Supervisor: %s
                """.formatted(
                        contrato.getNumeroContrato(),
                        contrato.getObjeto(),
                        contrato.getValor(),
                        contrato.getFechaInicio() != null ? contrato.getFechaInicio().format(FECHA) : "no registrada",
                        contrato.getFechaFin() != null ? contrato.getFechaFin().format(FECHA) : "no registrada",
                        contrato.getContratista(),
                        contrato.getContratistaNit(),
                        contrato.getRepresentanteLegal(),
                        contrato.getLugarEjecucion(),
                        contrato.getNumeroRegistroPresupuestal(),
                        contrato.getSupervisor() != null ? contrato.getSupervisor().getNombre() : "sin asignar");

        String prompt = """
                Eres el Copiloto IA de SICOT y vas a redactar el documento "%s" (%s) para un contrato real \
                del SENA, usando ÚNICAMENTE los datos suministrados abajo. No inventes datos que no estén \
                aquí — si falta un dato, escribe "[dato pendiente]" en su lugar.

                %s

                DATOS REALES DEL CONTRATO:
                %s

                Responde solo con el texto final del documento, en español formal institucional, en \
                párrafos de texto plano (sin markdown, sin encabezados con #).\
                """.formatted(plantilla.nombre(), plantilla.codigo(), plantilla.instruccionesCampos(), datosContrato);

        log.info("Generando '{}' para el contrato {} con Ollama...", plantilla.nombre(), contrato.getNumeroContrato());
        long inicio = System.currentTimeMillis();
        String contenidoGenerado = ollamaClient.generar(prompt, false);
        log.info("Generación de '{}' completada en {} ms", plantilla.nombre(), System.currentTimeMillis() - inicio);

        String firmante = contrato.getSupervisor() != null ? contrato.getSupervisor().getNombre() : null;
        byte[] pdf = pdfWriter.generar(plantilla.nombre(), plantilla.codigo(), contrato.getNumeroContrato(),
                firmante, "Supervisor del contrato", List.of(contenidoGenerado));

        Documento documento = new Documento();
        documento.setContrato(contrato);
        documento.setSubetapa(subetapa);
        documento.setNombre(plantilla.nombre() + " — " + contrato.getNumeroContrato());
        documento.setTipo(TipoDocumento.PDF);
        documento.setContentType("application/pdf");
        documento.setContenido(pdf);
        documento.setTamanioBytes((long) pdf.length);
        documento.setEstado(EstadoDocumento.PENDIENTE);
        documento.setGeneradoPorIa(true);

        Documento guardado = documentoRepository.save(documento);
        return DocumentoMapper.toResponse(guardado);
    }
}
