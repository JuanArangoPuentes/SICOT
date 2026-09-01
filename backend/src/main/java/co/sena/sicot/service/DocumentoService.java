package co.sena.sicot.service;

import co.sena.sicot.dto.documento.DocumentoResponse;
import co.sena.sicot.dto.documento.VerificacionIntegridadResponse;
import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Documento;
import co.sena.sicot.entity.FirmaElectronica;
import co.sena.sicot.entity.Subetapa;
import co.sena.sicot.entity.enums.EstadoDocumento;
import co.sena.sicot.entity.enums.TipoDocumento;
import co.sena.sicot.exception.BusinessException;
import co.sena.sicot.exception.ResourceNotFoundException;
import co.sena.sicot.mapper.DocumentoMapper;
import co.sena.sicot.repository.DocumentoRepository;
import co.sena.sicot.repository.FirmaElectronicaRepository;
import co.sena.sicot.repository.SubetapaRepository;
import co.sena.sicot.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;

@Service
public class DocumentoService {

    private static final Logger log = LoggerFactory.getLogger(DocumentoService.class);

    private final DocumentoRepository documentoRepository;
    private final ContratoService contratoService;
    private final SubetapaRepository subetapaRepository;
    private final FirmaElectronicaRepository firmaElectronicaRepository;
    private final RegistroService registroService;
    private final ArchivoValidator archivoValidator;

    public DocumentoService(DocumentoRepository documentoRepository, ContratoService contratoService,
                             SubetapaRepository subetapaRepository, FirmaElectronicaRepository firmaElectronicaRepository,
                             RegistroService registroService, ArchivoValidator archivoValidator) {
        this.documentoRepository = documentoRepository;
        this.contratoService = contratoService;
        this.subetapaRepository = subetapaRepository;
        this.firmaElectronicaRepository = firmaElectronicaRepository;
        this.registroService = registroService;
        this.archivoValidator = archivoValidator;
    }

    /**
     * Documentos de un contrato, sin traer un solo byte de los archivos — ver
     * {@code DocumentoRepository.listarPorContrato}. La comprobación de acceso
     * sigue estando primero: {@code contratoService.buscar} lanza 404 si el
     * usuario no es el supervisor de ese contrato.
     */
    @Transactional(readOnly = true)
    public List<DocumentoResponse> listarPorContrato(Long contratoId) {
        contratoService.buscar(contratoId);
        return documentoRepository.listarPorContrato(contratoId);
    }

    @Transactional
    public DocumentoResponse subir(Long contratoId, Long subetapaId, String nombre, MultipartFile archivo) {
        Contrato contrato = contratoService.buscar(contratoId);
        String nombreLimpio = nombre == null || nombre.isBlank()
                ? (archivo != null ? archivo.getOriginalFilename() : null)
                : nombre.trim();
        if (nombreLimpio == null || nombreLimpio.isBlank()) {
            throw new BusinessException("El nombre del documento es obligatorio.");
        }
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessException("Debe seleccionar un archivo para cargar.");
        }
        archivoValidator.validarTamanio(archivo);
        TipoDocumento tipo = archivoValidator.tipoDeArchivo(archivo);

        Documento documento = new Documento();
        documento.setContrato(contrato);
        if (subetapaId != null) {
            documento.setSubetapa(buscarSubetapaDelContrato(subetapaId, contrato));
        }
        documento.setNombre(nombreLimpio);
        documento.setTipo(tipo);
        documento.setContentType(archivoValidator.contentTypeDe(tipo));
        documento.setTamanioBytes(archivo.getSize());
        documento.setEstado(EstadoDocumento.PENDIENTE);
        documento.setSubidoPor(SecurityUtils.currentUsuario());
        try {
            documento.setContenido(archivo.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo cargado.", e);
        }

        Documento guardado = documentoRepository.save(documento);
        return DocumentoMapper.toResponse(guardado);
    }

    @Transactional(readOnly = true)
    public Documento buscarConContenido(Long id) {
        Documento documento = documentoRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Documento", id));
        SecurityUtils.verificarAccesoAlContrato(documento.getContrato());
        return documento;
    }

    /**
     * Firma el documento y registra la huella de lo que se está firmando.
     *
     * <p>El SHA-256 es lo que convierte la firma en algo comprobable: sin él,
     * "firmado" era una etiqueta que sobrevivía a cualquier modificación
     * posterior del contenido. Se calcula sobre los bytes que hay en ese
     * instante, dentro de la misma transacción que escribe la firma, para que
     * no exista ninguna ventana entre lo que se midió y lo que se firmó.
     */
    @Transactional
    public DocumentoResponse firmar(Long id) {
        Documento documento = documentoRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Documento", id));
        SecurityUtils.verificarAccesoAlContrato(documento.getContrato());
        if (documento.getFirmaId() != null) {
            throw new BusinessException("Este documento ya fue firmado.");
        }
        if (documento.getContenido() == null || documento.getContenido().length == 0) {
            throw new BusinessException("Este documento no tiene contenido: no hay nada que firmar.");
        }
        var usuario = SecurityUtils.currentUsuario();
        FirmaElectronica firma = firmaElectronicaRepository.findFirstByUsuarioIdAndActivaTrue(usuario.getId())
                .orElseThrow(() -> new BusinessException(
                        "No tiene una firma electrónica activa asignada. Solicítela al Administrador."));

        String huella = HuellaDeDocumento.calcular(documento.getContenido());

        documento.setFirmaId(firma.getFirmaId());
        documento.setFirmaHashSha256(huella);
        documento.setFirmadoPor(usuario);
        documento.setFechaFirma(Instant.now());
        documento.setEstado(EstadoDocumento.APROBADO);
        Documento guardado = documentoRepository.save(documento);

        registroService.registrar(documento.getContrato(), "DOCUMENTO_FIRMADO",
                documento.getNombre() + " firmado por " + usuario.getNombre() + " (" + firma.getFirmaId()
                        + "). Huella SHA-256: " + huella + ".");
        return DocumentoMapper.toResponse(guardado);
    }

    /**
     * Compara la huella registrada al firmar con la del contenido actual.
     *
     * <p>Sin efectos: no escribe log ni auditoría. Es la versión que puede
     * llamarse en cada descarga sin ensuciar nada. La entidad ya debe venir
     * cargada con su contenido.
     */
    @Transactional(readOnly = true)
    public VerificacionIntegridadResponse.Estado estadoDeIntegridad(Documento documento) {
        if (documento.getFirmaId() == null) {
            return VerificacionIntegridadResponse.Estado.SIN_FIRMA;
        }
        if (documento.getFirmaHashSha256() == null) {
            return VerificacionIntegridadResponse.Estado.NO_VERIFICABLE;
        }
        return HuellaDeDocumento.coincide(documento.getFirmaHashSha256(),
                HuellaDeDocumento.calcular(documento.getContenido()))
                ? VerificacionIntegridadResponse.Estado.INTEGRO
                : VerificacionIntegridadResponse.Estado.ALTERADO;
    }

    /**
     * Verificación completa, con explicación para el funcionario.
     *
     * <p>Un resultado {@code ALTERADO} se registra en el log a nivel ERROR y en
     * la auditoría del contrato: un documento oficial que cambió después de
     * firmado es un incidente, no un dato más de una respuesta HTTP. Por eso
     * este método sí escribe y {@link #estadoDeIntegridad} no.
     */
    @Transactional
    public VerificacionIntegridadResponse verificarIntegridad(Long id) {
        Documento documento = buscarConContenido(id);
        String firmadoPor = documento.getFirmadoPor() != null ? documento.getFirmadoPor().getNombre() : null;
        VerificacionIntegridadResponse.Estado estado = estadoDeIntegridad(documento);

        String hashActual = documento.getFirmaId() == null
                ? null
                : HuellaDeDocumento.calcular(documento.getContenido());

        String mensaje = switch (estado) {
            case SIN_FIRMA -> "Este documento todavía no está firmado.";
            case NO_VERIFICABLE -> "Este documento se firmó antes de que el sistema registrara la huella "
                    + "del contenido. Su integridad no se puede confirmar ni descartar.";
            case INTEGRO -> "El documento coincide exactamente con lo que se firmó.";
            case ALTERADO -> "ATENCIÓN: el contenido de este documento cambió después de haber sido "
                    + "firmado. No lo dé por válido y avise al área de sistemas.";
        };

        if (estado == VerificacionIntegridadResponse.Estado.ALTERADO) {
            log.error("INTEGRIDAD: el documento {} ('{}') del contrato {} NO coincide con la huella "
                            + "registrada al firmar. Registrada={} Actual={}",
                    documento.getId(), documento.getNombre(), documento.getContrato().getId(),
                    documento.getFirmaHashSha256(), hashActual);
            registroService.registrar(documento.getContrato(), "INTEGRIDAD_COMPROMETIDA",
                    "El documento " + documento.getNombre() + " no coincide con la huella registrada al "
                            + "firmarlo: su contenido cambió después de la firma.");
        }

        return new VerificacionIntegridadResponse(documento.getId(), documento.getNombre(), estado,
                documento.getFirmaHashSha256(), hashActual, documento.getFirmaId(),
                documento.getFechaFirma(), firmadoPor, mensaje);
    }

    private Subetapa buscarSubetapaDelContrato(Long subetapaId, Contrato contrato) {
        // La pertenencia al contrato es parte de la consulta, no una
        // comprobación posterior — ver SubetapaRepository.
        return subetapaRepository.findByIdAndEtapaContratoId(subetapaId, contrato.getId())
                .orElseThrow(() -> new BusinessException(
                        "La subetapa indicada no existe o no pertenece a este contrato."));
    }

}
