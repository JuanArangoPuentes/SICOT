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

@Service
public class DocumentoService {

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
        archivoValidator.validarTamanio(archivo);
        TipoDocumento tipo = archivoValidator.tipoDeArchivo(archivo);

        Documento documento = new Documento();
        documento.setContrato(contrato);
        if (subetapaId != null) {
            documento.setSubetapa(buscarSubetapaDelContrato(subetapaId, contrato));
        }
        documento.setNombre(nombreLimpio);
        documento.setTipo(tipo);
        documento.setContentType(archivoValidator.contentTypeDe(tipo, archivo.getContentType()));
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

    @Transactional
    public DocumentoResponse firmar(Long id) {
        Documento documento = documentoRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Documento", id));
        if (documento.getFirmaId() != null) {
            throw new BusinessException("Este documento ya fue firmado.");
        }
        SecurityUtils.verificarAccesoAlContrato(documento.getContrato());
        var usuario = SecurityUtils.currentUsuario();
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

    private Subetapa buscarSubetapaDelContrato(Long subetapaId, Contrato contrato) {
        // La pertenencia al contrato es parte de la consulta, no una
        // comprobación posterior — ver SubetapaRepository.
        return subetapaRepository.findByIdAndEtapaContratoId(subetapaId, contrato.getId())
                .orElseThrow(() -> new BusinessException(
                        "La subetapa indicada no existe o no pertenece a este contrato."));
    }

}
