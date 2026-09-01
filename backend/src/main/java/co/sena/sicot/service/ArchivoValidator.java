package co.sena.sicot.service;

import co.sena.sicot.entity.enums.TipoDocumento;
import co.sena.sicot.exception.BusinessException;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
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

    private static final Logger log = LoggerFactory.getLogger(ArchivoValidator.class);

    /**
     * Tope por archivo. Debe coincidir con
     * {@code spring.servlet.multipart.max-file-size} en application.properties:
     * si el de Spring fuera mayor, un archivo intermedio se recibiría entero en
     * memoria para después rechazarlo aquí — se pagaría el costo de la subida
     * sin quedarse con nada. Si fuera menor, este mensaje de error nunca se
     * vería y el usuario recibiría el genérico de Spring.
     */
    public static final long TAMANIO_MAXIMO_BYTES = 20L * 1024 * 1024; // 20 MB

    private static final Map<String, TipoDocumento> EXTENSIONES_PERMITIDAS = Map.of(
            "pdf", TipoDocumento.PDF,
            "docx", TipoDocumento.DOCX,
            "xlsx", TipoDocumento.XLSX
    );

    /**
     * MIME canónico de cada tipo aceptado. Es el ÚNICO valor que se guarda en
     * la base y el único que se devuelve al descargar: ver
     * {@link #contentTypeDe(TipoDocumento)}.
     */
    private static final Map<TipoDocumento, String> MIME_CANONICO = Map.of(
            TipoDocumento.PDF, "application/pdf",
            TipoDocumento.DOCX, "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            TipoDocumento.XLSX, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
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

    /**
     * MIME que se guarda y que se devolverá al descargar.
     *
     * <p>Antes este método recibía además el {@code Content-Type} que mandó el
     * navegador y lo devolvía tal cual si no venía vacío. Eso tenía dos
     * consecuencias, las dos malas. La primera: ese valor lo elige por completo
     * quien sube el archivo, se guardaba en la base y se devolvía como cabecera
     * de la descarga — un archivo que Tika acepta como PDF podía quedar
     * almacenado como {@code text/html}. Hoy eso no es explotable porque la
     * descarga fuerza {@code Content-Disposition: attachment} y Spring Security
     * añade {@code nosniff}, pero dejaba el sistema a un cambio de distancia de
     * un XSS almacenado sobre un dominio institucional. La segunda, peor porque
     * era un fallo seguro y no potencial: un {@code Content-Type} sin barra
     * ("foo") o con caracteres inválidos hacía que
     * {@code MediaType.parseMediaType} lanzara al descargar, y ese documento
     * quedaba <b>permanentemente indescargable</b> con un error 500, sin
     * endpoint para borrarlo ni corregirlo.
     *
     * <p>La corrección es dejar de preguntarle al cliente: el tipo ya se
     * determinó por los bytes reales del archivo, y de ahí sale el MIME.
     */
    public String contentTypeDe(TipoDocumento tipo) {
        return MIME_CANONICO.getOrDefault(tipo, MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    /**
     * Convierte a {@link MediaType} lo que haya guardado en la columna
     * {@code content_type}, sin posibilidad de lanzar.
     *
     * <p>Los registros nuevos siempre llevan un MIME canónico, así que en la
     * práctica esto no se activa. Está por las filas anteriores a esta
     * corrección, que pueden guardar cualquier cosa que el navegador enviara:
     * sin esta red, esos documentos seguirían respondiendo 500 al descargarlos.
     */
    public static MediaType mediaTypeSeguro(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            log.warn("Content-Type inválido almacenado ('{}'). Se sirve como binario genérico.", contentType);
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private String extensionDe(String nombreArchivo) {
        if (nombreArchivo == null) return "";
        int punto = nombreArchivo.lastIndexOf('.');
        return punto < 0 ? "" : nombreArchivo.substring(punto + 1).toLowerCase(Locale.ROOT);
    }
}
