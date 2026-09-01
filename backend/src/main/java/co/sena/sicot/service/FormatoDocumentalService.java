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

@Service
public class FormatoDocumentalService {

    private final FormatoDocumentalRepository formatoRepository;
    private final ArchivoValidator archivoValidator;

    public FormatoDocumentalService(FormatoDocumentalRepository formatoRepository, ArchivoValidator archivoValidator) {
        this.formatoRepository = formatoRepository;
        this.archivoValidator = archivoValidator;
    }

    /**
     * Catálogo completo, sin traer los archivos — ver
     * {@code FormatoDocumentalRepository.listarCatalogo}.
     */
    @Transactional(readOnly = true)
    public List<FormatoDocumentalResponse> listar() {
        return formatoRepository.listarCatalogo();
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
        archivoValidator.validarTamanio(archivo);

        TipoDocumento tipo = archivoValidator.tipoDeArchivo(archivo);

        FormatoDocumental formato = formatoRepository.findByCodigoIgnoreCase(codigoLimpio)
                .orElseGet(FormatoDocumental::new);
        boolean esNuevo = formato.getId() == null;

        formato.setCodigo(codigoLimpio);
        formato.setNombre(nombreLimpio);
        formato.setVersion(siguienteVersion(esNuevo ? null : formato.getVersion()));
        formato.setTipoArchivo(tipo);
        formato.setNombreArchivo(archivo.getOriginalFilename());
        formato.setContentType(archivoValidator.contentTypeDe(tipo));
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

    private String siguienteVersion(String versionActual) {
        if (versionActual == null || !versionActual.matches("v\\d+")) {
            return "v1";
        }
        int numero = Integer.parseInt(versionActual.substring(1));
        return "v" + (numero + 1);
    }
}
