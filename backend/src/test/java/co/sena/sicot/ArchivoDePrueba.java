package co.sena.sicot;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Archivos de verdad —un PDF con su página, una imagen con sus píxeles— para
 * las pruebas que cargan un documento al expediente o al catálogo.
 *
 * <h2>Por qué no vale la cadena «%PDF-1.4 contenido de prueba»</h2>
 * Era lo que usaban estas pruebas, y pasaba la validación porque Tika decide
 * el tipo por los primeros bytes: cualquier texto que empiece por «%PDF-» se
 * detecta como {@code application/pdf}, y ocho bytes de cabecera PNG, como
 * {@code image/png}. El efecto secundario es que la suite nunca cargó un
 * archivo que se pudiera abrir, así que tampoco notó que SICOT los aceptaba
 * —y uno de esos acabó en el expediente de un contrato de prueba el
 * 15-10-2026, listado como PDF de 44 bytes y ofrecido para descargar
 * (revisión del 20-10-2026; ver {@code ArchivoValidator.validarQueSeAbre}).
 *
 * <p>Con archivos reales estas pruebas pasan por donde pasa la carga de un
 * supervisor: la cabecera, la estructura y el contenido.
 */
public final class ArchivoDePrueba {

    private ArchivoDePrueba() {
    }

    /**
     * Un PDF de una página con {@code texto} escrito dentro.
     *
     * <p>El texto distingue un documento de otro, que es lo que necesitan las
     * pruebas de la huella de integridad; se reduce a ASCII porque las fuentes
     * base del PDF no cubren más y lo que diga dentro no es lo que se
     * comprueba.
     */
    public static byte[] pdfDeUnaPagina(String texto) {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage(PDRectangle.LETTER);
            documento.addPage(pagina);
            try (PDPageContentStream lienzo = new PDPageContentStream(documento, pagina)) {
                lienzo.beginText();
                lienzo.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                lienzo.newLineAtOffset(72, 700);
                lienzo.showText(texto.replaceAll("[^\\x20-\\x7E]", "?"));
                lienzo.endText();
            }
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo construir el PDF de prueba.", e);
        }
    }

    /**
     * Un PDF de una página con un renglón por cada texto, para las pruebas que
     * leen lo que dice el documento (el estudio previo del paso 2.2). Admite
     * tildes y eñes: la fuente base las cubre con su codificación latina.
     */
    public static byte[] pdfConRenglones(String... renglones) {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage(PDRectangle.LETTER);
            documento.addPage(pagina);
            try (PDPageContentStream lienzo = new PDPageContentStream(documento, pagina)) {
                lienzo.beginText();
                lienzo.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                lienzo.setLeading(14);
                lienzo.newLineAtOffset(54, 740);
                for (String renglon : renglones) {
                    lienzo.showText(renglon.replaceAll("[^\\x20-\\x7E\\u00C0-\\u00FF]", "?"));
                    lienzo.newLine();
                }
                lienzo.endText();
            }
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo construir el PDF de prueba.", e);
        }
    }

    /** Un PDF con una página en blanco: lo que llega de un documento escaneado, sin texto que leer. */
    public static byte[] pdfSinTexto() {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            documento.addPage(new PDPage(PDRectangle.LETTER));
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo construir el PDF de prueba.", e);
        }
    }

    /** Una imagen real de 64×48 px en el formato pedido ({@code "png"}, {@code "jpg"}). */
    public static byte[] imagen(String formato) {
        BufferedImage imagen = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        Graphics2D lapiz = imagen.createGraphics();
        lapiz.setColor(new Color(0x00, 0x7A, 0x33));
        lapiz.fillRect(0, 0, 64, 48);
        lapiz.dispose();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            ImageIO.write(imagen, formato, salida);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo construir la imagen de prueba.", e);
        }
        return salida.toByteArray();
    }
}
