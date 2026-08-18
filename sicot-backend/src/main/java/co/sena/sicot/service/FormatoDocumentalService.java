package co.sena.sicot.service;

import co.sena.sicot.dto.formato.FormatoDocumentalResponse;
import co.sena.sicot.entity.FormatoDocumental;
import co.sena.sicot.entity.enums.EstadoFormato;
import co.sena.sicot.entity.enums.TipoDocumento;
import co.sena.sicot.exception.BusinessException;
import co.sena.sicot.exception.ResourceNotFoundException;
import co.sena.sicot.mapper.FormatoDocumentalMapper;
import co.sena.sicot.repository.FormatoDocumentalRepository;
import co.sena.sicot.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class FormatoDocumentalService {

    private static final long TAMANIO_MAXIMO_BYTES = 20L * 1024 * 1024; // 20 MB

    private static final Map<String, TipoDocumento> EXTENSIONES_PERMITIDAS = Map.of(
            "pdf", TipoDocumento.PDF,
            "docx", TipoDocumento.DOCX,
            "xlsx", TipoDocumento.XLSX
    );

    private final FormatoDocumentalRepository formatoRepository;

    public FormatoDocumentalService(FormatoDocumentalRepository formatoRepository) {
        this.formatoRepository = formatoRepository;
    }

    @Transactional(readOnly = true)
    public List<FormatoDocumentalResponse> listar() {
        return formatoRepository.findAllByOrderByCodigoAsc().stream()
                .map(FormatoDocumentalMapper::toResponse)
                .toList();
    }

    @Transactional
    public FormatoDocumentalResponse subir(String codigo, String nombre, MultipartFile archivo) {
        String codigoLimpio = codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
        String nombreLimpio = nombre == null ? "" : nombre.trim();
        if (codigoLimpio.isEmpty()) {
            throw new BusinessException("El código del formato es obligatorio (ej. GCCON-F-031).");
        }
        if (nombreLimpio.isEmpty()) {
            throw new BusinessException("El nombre del formato es obligatorio.");
        }
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessException("Debe seleccionar un archivo para cargar.");
        }
        if (archivo.getSize() > TAMANIO_MAXIMO_BYTES) {
            throw new BusinessException("El archivo supera el tamaño máximo permitido de 20 MB.");
        }

        TipoDocumento tipo = tipoDeArchivo(archivo.getOriginalFilename());

        FormatoDocumental formato = formatoRepository.findByCodigoIgnoreCase(codigoLimpio)
                .orElseGet(FormatoDocumental::new);
        boolean esNuevo = formato.getId() == null;

        formato.setCodigo(codigoLimpio);
        formato.setNombre(nombreLimpio);
        formato.setVersion(siguienteVersion(esNuevo ? null : formato.getVersion()));
        formato.setTipoArchivo(tipo);
        formato.setNombreArchivo(archivo.getOriginalFilename());
        formato.setContentType(contentTypeDe(tipo, archivo.getContentType()));
        formato.setTamanioBytes(archivo.getSize());
        formato.setEstado(EstadoFormato.VIGENTE);
        formato.setSubidoPor(SecurityUtils.currentUsuario());
        try {
            formato.setContenido(archivo.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo cargado.", e);
        }

        FormatoDocumental guardado = formatoRepository.save(formato);
        return FormatoDocumentalMapper.toResponse(guardado);
    }

    @Transactional(readOnly = true)
    public FormatoDocumental buscarConContenido(Long id) {
        return formatoRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Formato documental", id));
    }

    @Transactional
    public void eliminar(Long id) {
        if (!formatoRepository.existsById(id)) {
            throw ResourceNotFoundException.of("Formato documental", id);
        }
        formatoRepository.deleteById(id);
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

    private String siguienteVersion(String versionActual) {
        if (versionActual == null || !versionActual.matches("v\\d+")) {
            return "v1";
        }
        int numero = Integer.parseInt(versionActual.substring(1));
        return "v" + (numero + 1);
    }
}
