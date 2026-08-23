package co.sena.sicot.service;

import co.sena.sicot.entity.enums.TipoDocumento;
import co.sena.sicot.exception.BusinessException;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reglas de validación de archivos cargados, compartidas entre
 * DocumentoService (evidencias de un contrato) y FormatoDocumentalService
 * (catálogo de formatos oficiales) — ambos aceptan los mismos tipos y el
 * mismo tamaño máximo, así que la regla vive en un solo lugar.
 */
@Component
public class ArchivoValidator {

    private static final long TAMANIO_MAXIMO_BYTES = 20L * 1024 * 1024; // 20 MB

    private static final Map<String, TipoDocumento> EXTENSIONES_PERMITIDAS = Map.of(
            "pdf", TipoDocumento.PDF,
            "docx", TipoDocumento.DOCX,
            "xlsx", TipoDocumento.XLSX
    );

    // Tipos MIME reales (detectados por contenido/bytes mágicos, no por la
    // extensión del nombre) que aceptamos para cada TipoDocumento declarado.
    // Evita que un archivo malicioso renombrado (p. ej. un .exe guardado como
    // "informe.pdf") pase la validación solo por su extensión o por el
    // Content-Type que manda el navegador, que el cliente puede falsear.
    private static final Map<TipoDocumento, Set<String>> MIME_REALES_PERMITIDOS = Map.of(
            TipoDocumento.PDF, Set.of("application/pdf"),
            TipoDocumento.DOCX, Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            TipoDocumento.XLSX, Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    );

    private final Tika tika = new Tika();

    public void validarTamanio(MultipartFile archivo) {
        if (archivo.getSize() > TAMANIO_MAXIMO_BYTES) {
            throw new BusinessException("El archivo supera el tamaño máximo permitido de 20 MB.");
        }
    }

    public TipoDocumento tipoDeArchivo(MultipartFile archivo) {
        String extension = extensionDe(archivo.getOriginalFilename());
        TipoDocumento tipo = EXTENSIONES_PERMITIDAS.get(extension);
        if (tipo == null) {
            throw new BusinessException("Formato de archivo no permitido. Solo se aceptan PDF, DOCX o XLSX.");
        }
        validarContenidoReal(archivo, tipo);
        return tipo;
    }

    private void validarContenidoReal(MultipartFile archivo, TipoDocumento tipoDeclarado) {
        String mimeReal;
        try (InputStream contenido = archivo.getInputStream()) {
            mimeReal = tika.detect(contenido, archivo.getOriginalFilename());
        } catch (IOException e) {
            throw new BusinessException("No se pudo leer el archivo cargado.");
        }
        Set<String> mimesEsperados = MIME_REALES_PERMITIDOS.get(tipoDeclarado);
        if (mimesEsperados == null || !mimesEsperados.contains(mimeReal)) {
            throw new BusinessException(
                    "El contenido del archivo no coincide con su extensión. Verifique que el archivo "
                            + "no esté corrupto o haya sido renombrado a un formato distinto.");
        }
    }

    public String contentTypeDe(TipoDocumento tipo, String contentTypeDetectado) {
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

    private String extensionDe(String nombreArchivo) {
        if (nombreArchivo == null) return "";
        int punto = nombreArchivo.lastIndexOf('.');
        return punto < 0 ? "" : nombreArchivo.substring(punto + 1).toLowerCase(Locale.ROOT);
    }
}
