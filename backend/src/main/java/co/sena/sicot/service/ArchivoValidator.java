package co.sena.sicot.service;

import co.sena.sicot.entity.enums.TipoDocumento;
import co.sena.sicot.exception.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
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
            "xlsx", TipoDocumento.XLSX,
            "jpg", TipoDocumento.IMAGEN,
            "jpeg", TipoDocumento.IMAGEN,
            "png", TipoDocumento.IMAGEN
    );

    /**
     * Lo que acepta el catálogo de formatos oficiales y la extracción de datos:
     * documentos de ofimática. Una foto no es un formato documental ni tiene
     * texto que extraer, así que ahí sigue sin entrar.
     */
    public static final Set<TipoDocumento> OFIMATICOS =
            Set.of(TipoDocumento.PDF, TipoDocumento.DOCX, TipoDocumento.XLSX);

    /**
     * Lo que puede entrar al expediente de un contrato. Incluye la foto porque
     * la evidencia de la entrega en bodega (subetapa 3.2) llega desde la cámara
     * del teléfono, y hasta hoy el expediente la rechazaba.
     */
    public static final Set<TipoDocumento> EVIDENCIAS_DEL_EXPEDIENTE =
            Set.of(TipoDocumento.PDF, TipoDocumento.DOCX, TipoDocumento.XLSX, TipoDocumento.IMAGEN);

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
            TipoDocumento.XLSX, Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            // Solo JPEG y PNG: son lo que produce la cámara de Android y lo que
            // cualquier visor abre. Formatos como HEIC o WebP se dejan fuera
            // mientras nada los necesite, porque cada formato aceptado es un
            // decodificador más que tiene que leer un archivo de fuera.
            TipoDocumento.IMAGEN, Set.of("image/jpeg", "image/png")
    );

    private final Tika tika = new Tika();

    public void validarTamanio(MultipartFile archivo) {
        if (archivo.getSize() > TAMANIO_MAXIMO_BYTES) {
            throw new BusinessException("El archivo supera el tamaño máximo permitido de 20 MB.");
        }
    }

    /**
     * Qué resultó ser el archivo: su tipo y el MIME con el que se guardará y se
     * devolverá al descargarlo.
     *
     * <p>Los dos valores viajan juntos porque para una imagen no basta el tipo:
     * JPG y PNG son el mismo {@link TipoDocumento#IMAGEN}, y devolver un MIME
     * canónico único haría que un PNG se descargara diciendo que es JPEG.
     */
    public record ArchivoAceptado(TipoDocumento tipo, String contentType) {
    }

    /**
     * Acepta el archivo solo si es de uno de los tipos {@code permitidos}. Cada
     * uso declara los suyos: el expediente de un contrato admite fotos
     * ({@link #EVIDENCIAS_DEL_EXPEDIENTE}), el catálogo de formatos oficiales no
     * ({@link #OFIMATICOS}).
     */
    public ArchivoAceptado aceptar(MultipartFile archivo, Set<TipoDocumento> permitidos) {
        String extension = extensionDe(archivo.getOriginalFilename());
        TipoDocumento tipo = EXTENSIONES_PERMITIDAS.get(extension);
        if (tipo == null || !permitidos.contains(tipo)) {
            throw new BusinessException("Formato de archivo no permitido. Solo se aceptan "
                    + descripcionDe(permitidos) + ".");
        }
        // Los bytes se leen una vez y sirven para las dos comprobaciones: de qué
        // tipo es (la cabecera) y si se abre de verdad (todo el archivo). El
        // tamaño ya lo limitó validarTamanio en quien llama.
        byte[] contenido = bytesDe(archivo);
        String mimeReal = validarContenidoReal(contenido, archivo.getOriginalFilename(), tipo);
        validarQueSeAbre(contenido, tipo, archivo.getOriginalFilename());
        return new ArchivoAceptado(tipo, tipo == TipoDocumento.IMAGEN ? mimeReal : contentTypeDe(tipo));
    }

    public TipoDocumento tipoDeArchivo(MultipartFile archivo) {
        return aceptar(archivo, OFIMATICOS).tipo();
    }

    private String descripcionDe(Set<TipoDocumento> permitidos) {
        return permitidos.contains(TipoDocumento.IMAGEN)
                ? "PDF, DOCX, XLSX o fotos JPG y PNG"
                : "PDF, DOCX o XLSX";
    }

    private byte[] bytesDe(MultipartFile archivo) {
        try {
            return archivo.getBytes();
        } catch (IOException e) {
            throw new BusinessException("No se pudo leer el archivo cargado.");
        }
    }

    private String validarContenidoReal(byte[] contenido, String nombreArchivo, TipoDocumento tipoDeclarado) {
        String mimeReal = tika.detect(contenido, nombreArchivo);
        Set<String> mimesEsperados = MIME_REALES_PERMITIDOS.get(tipoDeclarado);
        if (mimesEsperados == null || !mimesEsperados.contains(mimeReal)) {
            throw new BusinessException(
                    "El contenido del archivo no coincide con su extensión. Verifique que el archivo "
                            + "no esté corrupto o haya sido renombrado a un formato distinto.");
        }
        return mimeReal;
    }

    /**
     * Comprueba que el archivo <b>se abra</b>, y no solo que empiece como si lo
     * fuera.
     *
     * <h2>Por qué hace falta además de la cabecera</h2>
     * Tika decide por los primeros bytes. Un archivo de texto de 44 bytes cuya
     * primera línea es «%PDF-1.4» se detecta como {@code application/pdf} y
     * pasaba la validación: así entró al expediente del contrato de prueba el
     * 15-10-2026 —SICOT lo guardó con un 201, lo listó como PDF de 44 bytes y
     * lo sirvió en la descarga— y el lector del supervisor lo daba por dañado
     * (revisión del 20-10-2026). Al mismo sitio llega un PDF o una foto cuya
     * subida se cortó a mitad: la cabecera está completa y el archivo no.
     *
     * <p>Rechazarlo al cargarlo, y no al descargarlo, es la diferencia entre
     * un mensaje que dice qué pasa y un expediente con un documento que nadie
     * puede abrir y que SICOT afirma tener. Un documento del expediente se
     * firma, se le calcula la huella y se publica: no puede ser un archivo
     * ilegible.
     *
     * <h2>Cómo se comprueba</h2>
     * Con el mismo lector que usará quien lo descargue: PDFBox para los PDF
     * —que además tienen que tener alguna página— e ImageIO para las fotos.
     * Un DOCX o un XLSX no necesitan este paso: para reconocerlos, Tika ya
     * abrió el ZIP y leyó dentro su declaración de tipo.
     */
    private void validarQueSeAbre(byte[] contenido, TipoDocumento tipo, String nombreArchivo) {
        if (tipo == TipoDocumento.PDF) {
            validarQueElPdfSeAbre(contenido, nombreArchivo);
        } else if (tipo == TipoDocumento.IMAGEN) {
            validarQueLaImagenSeAbre(contenido, nombreArchivo);
        }
    }

    private void validarQueElPdfSeAbre(byte[] contenido, String nombreArchivo) {
        int paginas;
        try (PDDocument documento = Loader.loadPDF(contenido)) {
            paginas = documento.getNumberOfPages();
        } catch (InvalidPasswordException e) {
            // Un PDF con contraseña SÍ es un PDF: PDFBox leyó su estructura y
            // por eso sabe que está cifrado. Quien lo descargue lo abrirá con
            // la contraseña que tenga, y eso no es asunto de SICOT.
            return;
        } catch (IOException | RuntimeException e) {
            // RuntimeException además de IOException porque un PDF roto no
            // falla siempre igual: según por dónde esté cortado, PDFBox lanza
            // al leer la tabla de objetos y no una IOException.
            log.warn("PDF rechazado: no se pudo abrir «{}» ({}).", nombreArchivo, e.toString());
            throw new BusinessException("El archivo empieza como un PDF pero ningún lector puede abrirlo: "
                    + "está dañado o la subida se cortó a mitad. Vuelva a exportarlo y cárguelo de nuevo.");
        }
        if (paginas < 1) {
            throw new BusinessException("El PDF cargado no tiene ninguna página. "
                    + "Vuelva a exportarlo y cárguelo de nuevo.");
        }
    }

    private void validarQueLaImagenSeAbre(byte[] contenido, String nombreArchivo) {
        BufferedImage imagen;
        try {
            imagen = ImageIO.read(new ByteArrayInputStream(contenido));
        } catch (IOException | RuntimeException e) {
            log.warn("Foto rechazada: no se pudo abrir «{}» ({}).", nombreArchivo, e.toString());
            imagen = null;
        }
        if (imagen == null || imagen.getWidth() < 1 || imagen.getHeight() < 1) {
            throw new BusinessException("La foto cargada no se puede mostrar: está dañada o la subida se "
                    + "cortó a mitad. Vuelva a tomarla o a exportarla y cárguela de nuevo.");
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
