package co.sena.sicot.service;

import co.sena.sicot.dto.documento.DocumentoResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DocumentoService {

    private static final long TAMANIO_MAXIMO_BYTES = 20L * 1024 * 1024; // 20 MB

    private static final Map<String, TipoDocumento> EXTENSIONES_PERMITIDAS = Map.of(
            "pdf", TipoDocumento.PDF,
            "docx", TipoDocumento.DOCX,
            "xlsx", TipoDocumento.XLSX
    );

    private final DocumentoRepository documentoRepository;
    private final ContratoService contratoService;
    private final SubetapaRepository subetapaRepository;
    private final FirmaElectronicaRepository firmaElectronicaRepository;
    private final RegistroService registroService;

    public DocumentoService(DocumentoRepository documentoRepository, ContratoService contratoService,
                             SubetapaRepository subetapaRepository, FirmaElectronicaRepository firmaElectronicaRepository,
                             RegistroService registroService) {
        this.documentoRepository = documentoRepository;
        this.contratoService = contratoService;
        this.subetapaRepository = subetapaRepository;
        this.firmaElectronicaRepository = firmaElectronicaRepository;
        this.registroService = registroService;
    }

    @Transactional(readOnly = true)
    public List<DocumentoResponse> listarPorContrato(Long contratoId) {
        contratoService.buscar(contratoId);
        return documentoRepository.findByContratoIdOrderByFechaSubidaDesc(contratoId).stream()
                .map(DocumentoMapper::toResponse)
                .toList();
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
        if (archivo.getSize() > TAMANIO_MAXIMO_BYTES) {
            throw new BusinessException("El archivo supera el tamaño máximo permitido de 20 MB.");
        }
        TipoDocumento tipo = tipoDeArchivo(archivo.getOriginalFilename());

        Documento documento = new Documento();
        documento.setContrato(contrato);
        if (subetapaId != null) {
            documento.setSubetapa(buscarSubetapa(subetapaId));
        }
        documento.setNombre(nombreLimpio);
        documento.setTipo(tipo);
        documento.setContentType(contentTypeDe(tipo, archivo.getContentType()));
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
        return documentoRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Documento", id));
    }

    @Transactional
    public DocumentoResponse firmar(Long id) {
        Documento documento = documentoRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Documento", id));
        if (documento.getFirmaId() != null) {
            throw new BusinessException("Este documento ya fue firmado.");
        }
        var usuario = SecurityUtils.currentUsuario();
        var contratoSupervisor = documento.getContrato().getSupervisor();
        boolean esSupervisorDelContrato = contratoSupervisor != null && contratoSupervisor.getId().equals(usuario.getId());
        if (usuario.getRol().name().equals("SUPERVISOR") && !esSupervisorDelContrato) {
            throw new BusinessException("Solo el supervisor asignado a este contrato puede firmar este documento.");
        }
        FirmaElectronica firma = firmaElectronicaRepository.findFirstByUsuarioIdAndActivaTrue(usuario.getId())
                .orElseThrow(() -> new BusinessException(
                        "No tiene una firma electrónica activa asignada. Solicítela al Administrador."));

        documento.setFirmaId(firma.getFirmaId());
        documento.setFechaFirma(Instant.now());
        documento.setEstado(EstadoDocumento.APROBADO);
        Documento guardado = documentoRepository.save(documento);

        registroService.registrar(documento.getContrato(), "DOCUMENTO_FIRMADO",
                documento.getNombre() + " firmado por " + usuario.getNombre() + " (" + firma.getFirmaId() + ").");
        return DocumentoMapper.toResponse(guardado);
    }

    private Subetapa buscarSubetapa(Long subetapaId) {
        return subetapaRepository.findById(subetapaId)
                .orElseThrow(() -> ResourceNotFoundException.of("Subetapa", subetapaId));
    }

    private TipoDocumento tipoDeArchivo(String nombreArchivo) {
        String extension = extensionDe(nombreArchivo);
        TipoDocumento tipo = EXTENSIONES_PERMITIDAS.get(extension);
        if (tipo == null) {
            throw new BusinessException("Formato de archivo no permitido. Solo se aceptan PDF, DOCX o XLSX.");
        }
        return tipo;
    }

    private String extensionDe(String nombreArchivo) {
        if (nombreArchivo == null) return "";
        int punto = nombreArchivo.lastIndexOf('.');
        return punto < 0 ? "" : nombreArchivo.substring(punto + 1).toLowerCase(Locale.ROOT);
    }

    private String contentTypeDe(TipoDocumento tipo, String contentTypeDetectado) {
        if (contentTypeDetectado != null && !contentTypeDetectado.isBlank()
                && !contentTypeDetectado.equals("application/octet-stream")) {
            return contentTypeDetectado;
        }
        return switch (tipo) {
            case PDF -> "application/pdf";
            case DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/octet-stream";
        };
    }
}
