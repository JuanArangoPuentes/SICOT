package co.sena.sicot.ia;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Genera el PDF real de un documento formal redactado por el Copiloto IA:
 * membrete institucional (verde SENA, igual a la identidad del resto de
 * SICOT), título y código de formato, cuerpo del texto, bloque de firma y
 * pie de página con numeración. No es una plantilla oficial escaneada del
 * SENA — es un documento generado por el sistema, con formato profesional,
 * a partir de datos reales del contrato.
 */
@Component
public class SimplePdfWriter {

    private static final float MARGEN = 50;
    private static final float ANCHO_PAGINA = PDRectangle.LETTER.getWidth();
    private static final float ALTO_PAGINA = PDRectangle.LETTER.getHeight();
    private static final float ANCHO_UTIL = ANCHO_PAGINA - 2 * MARGEN;

    private static final float ALTO_MEMBRETE = 44;
    private static final float ALTO_PIE = 30;
    private static final float LIMITE_INFERIOR = MARGEN + ALTO_PIE;

    private static final Color VERDE_SENA = new Color(57, 181, 74);
    private static final Color VERDE_ENFASIS = new Color(44, 154, 60);
    private static final Color GRIS_TENUE = new Color(122, 171, 128);
    private static final Color NEGRO_TEXTO = new Color(12, 26, 14);

    private static final DateTimeFormatter FECHA_LARGA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.of("es", "CO"));

    public byte[] generar(String titulo, List<String> parrafos) {
        return generar(titulo, null, null, null, null, parrafos);
    }

    /**
     * @param codigo          código del formato (ej. GCCON-F-018), o null/"PENDIENTE_DE_DEFINIR" si no aplica.
     * @param numeroContrato  número del contrato al que pertenece el documento.
     * @param firmante        nombre de quien lo suscribe (el supervisor asignado).
     * @param cargoFirmante   cargo institucional de quien lo suscribe.
     */
    public byte[] generar(String titulo, String codigo, String numeroContrato,
                           String firmante, String cargoFirmante, List<String> parrafos) {
        try (PDDocument documento = new PDDocument()) {
            PDType1Font fuenteTitulo = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fuenteTexto = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font fuenteTextoItalica = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

            List<PDPage> paginas = new ArrayList<>();
            PDPage pagina = nuevaPagina(documento, paginas);
            PDPageContentStream stream = new PDPageContentStream(documento, pagina);

            dibujarMembrete(stream, fuenteTitulo, fuenteTexto);
            float y = ALTO_PAGINA - ALTO_MEMBRETE - 34;

            y = escribirLinea(stream, fuenteTitulo, 15, titulo, MARGEN, y, VERDE_ENFASIS);
            y -= 4;
            String metaLinea = construirLineaMeta(codigo, numeroContrato);
            if (metaLinea != null) {
                y = escribirLinea(stream, fuenteTexto, 9.5f, metaLinea, MARGEN, y, GRIS_TENUE);
            }
            y -= 6;
            y = dibujarRegla(stream, y, GRIS_TENUE);
            y -= 18;

            for (String parrafo : parrafos) {
                for (String linea : envolver(parrafo, fuenteTexto, 10.5f, ANCHO_UTIL)) {
                    if (y < LIMITE_INFERIOR) {
                        stream.close();
                        pagina = nuevaPagina(documento, paginas);
                        stream = new PDPageContentStream(documento, pagina);
                        y = ALTO_PAGINA - MARGEN;
                    }
                    stream.beginText();
                    stream.setNonStrokingColor(NEGRO_TEXTO);
                    stream.setFont(fuenteTexto, 10.5f);
                    stream.newLineAtOffset(MARGEN, y);
                    stream.showText(linea);
                    stream.endText();
                    y -= 14;
                }
                y -= 7;
            }

            // ── Bloque de firma — si no hay espacio suficiente, pasa a una página nueva ──
            float altoBloqueFirma = 92;
            if (y - altoBloqueFirma < LIMITE_INFERIOR) {
                stream.close();
                pagina = nuevaPagina(documento, paginas);
                stream = new PDPageContentStream(documento, pagina);
                y = ALTO_PAGINA - MARGEN;
            }
            dibujarBloqueFirma(stream, fuenteTexto, fuenteTextoItalica, y, firmante, cargoFirmante);
            stream.close();

            dibujarPies(documento, paginas, fuenteTexto, fuenteTextoItalica);

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF del documento.", e);
        }
    }

    private PDPage nuevaPagina(PDDocument documento, List<PDPage> paginas) {
        PDPage pagina = new PDPage(PDRectangle.LETTER);
        documento.addPage(pagina);
        paginas.add(pagina);
        return pagina;
    }

    /** Membrete verde SENA en la parte superior de cada página — misma identidad visual que el resto de SICOT. */
    private void dibujarMembrete(PDPageContentStream stream, PDType1Font fuenteTitulo, PDType1Font fuenteTexto) throws IOException {
        stream.setNonStrokingColor(VERDE_SENA);
        stream.addRect(0, ALTO_PAGINA - ALTO_MEMBRETE, ANCHO_PAGINA, ALTO_MEMBRETE);
        stream.fill();

        stream.beginText();
        stream.setNonStrokingColor(Color.WHITE);
        stream.setFont(fuenteTitulo, 13);
        stream.newLineAtOffset(MARGEN, ALTO_PAGINA - 27);
        stream.showText("SICOT");
        stream.endText();

        stream.beginText();
        stream.setNonStrokingColor(new Color(255, 255, 255, 210));
        stream.setFont(fuenteTexto, 8);
        stream.newLineAtOffset(MARGEN, ALTO_PAGINA - 37);
        stream.showText("CENTRO TECNOLOGICO DEL MOBILIARIO . SENA");
        stream.endText();

        String derecha = "Generado por el Copiloto IA - " + LocalDate.now().format(FECHA_LARGA);
        float anchoDerecha = fuenteTexto.getStringWidth(derecha) / 1000 * 8.5f;
        stream.beginText();
        stream.setNonStrokingColor(new Color(255, 255, 255, 210));
        stream.setFont(fuenteTexto, 8.5f);
        stream.newLineAtOffset(ANCHO_PAGINA - MARGEN - anchoDerecha, ALTO_PAGINA - 27);
        stream.showText(derecha);
        stream.endText();
    }

    private String construirLineaMeta(String codigo, String numeroContrato) {
        boolean tieneCodigo = codigo != null && !codigo.isBlank() && !codigo.equals("PENDIENTE_DE_DEFINIR");
        boolean tieneContrato = numeroContrato != null && !numeroContrato.isBlank();
        if (!tieneCodigo && !tieneContrato) return null;
        StringBuilder sb = new StringBuilder();
        if (tieneCodigo) sb.append("Formato ").append(codigo);
        if (tieneCodigo && tieneContrato) sb.append("  .  ");
        if (tieneContrato) sb.append("Contrato ").append(numeroContrato);
        return sb.toString();
    }

    private void dibujarBloqueFirma(PDPageContentStream stream, PDType1Font fuenteTexto, PDType1Font fuenteItalica,
                                     float y, String firmante, String cargoFirmante) throws IOException {
        y -= 26;
        float anchoLinea = 220;
        stream.setStrokingColor(GRIS_TENUE);
        stream.setLineWidth(0.8f);
        stream.moveTo(MARGEN, y);
        stream.lineTo(MARGEN + anchoLinea, y);
        stream.stroke();
        y -= 14;

        y = escribirLinea(stream, fuenteTexto, 10, "Firma: " + (firmante != null && !firmante.isBlank() ? firmante : "[pendiente]"), MARGEN, y, NEGRO_TEXTO);
        if (cargoFirmante != null && !cargoFirmante.isBlank()) {
            y = escribirLinea(stream, fuenteTexto, 10, "Cargo: " + cargoFirmante, MARGEN, y, NEGRO_TEXTO);
        }
        y -= 4;
        escribirLinea(stream, fuenteItalica, 8.5f,
                "La firma electronica de este documento queda registrada en el sistema SICOT al momento de su aprobacion.",
                MARGEN, y, GRIS_TENUE);
    }

    /** Segunda pasada: escribe "Página X de N" en cada página ya generada (se necesita el total primero). */
    private void dibujarPies(PDDocument documento, List<PDPage> paginas, PDType1Font fuenteTexto, PDType1Font fuenteItalica) throws IOException {
        int total = paginas.size();
        for (int i = 0; i < total; i++) {
            PDPage pagina = paginas.get(i);
            try (PDPageContentStream pie = new PDPageContentStream(documento, pagina, PDPageContentStream.AppendMode.APPEND, true)) {
                pie.setStrokingColor(GRIS_TENUE);
                pie.setLineWidth(0.6f);
                pie.moveTo(MARGEN, MARGEN + 14);
                pie.lineTo(ANCHO_PAGINA - MARGEN, MARGEN + 14);
                pie.stroke();

                String texto = "SICOT . Sistema Inteligente de Gestion y Acompanamiento de Contratos";
                pie.beginText();
                pie.setNonStrokingColor(GRIS_TENUE);
                pie.setFont(fuenteItalica, 7.5f);
                pie.newLineAtOffset(MARGEN, MARGEN);
                pie.showText(texto);
                pie.endText();

                String pagTexto = "Pagina " + (i + 1) + " de " + total;
                float anchoPag = fuenteTexto.getStringWidth(pagTexto) / 1000 * 7.5f;
                pie.beginText();
                pie.setNonStrokingColor(GRIS_TENUE);
                pie.setFont(fuenteTexto, 7.5f);
                pie.newLineAtOffset(ANCHO_PAGINA - MARGEN - anchoPag, MARGEN);
                pie.showText(pagTexto);
                pie.endText();
            }
        }
    }

    private float dibujarRegla(PDPageContentStream stream, float y, Color color) throws IOException {
        stream.setStrokingColor(color);
        stream.setLineWidth(0.8f);
        stream.moveTo(MARGEN, y);
        stream.lineTo(ANCHO_PAGINA - MARGEN, y);
        stream.stroke();
        return y;
    }

    private float escribirLinea(PDPageContentStream stream, PDType1Font fuente, float tamanio,
                                 String texto, float x, float y, Color color) throws IOException {
        stream.beginText();
        stream.setNonStrokingColor(color);
        stream.setFont(fuente, tamanio);
        stream.newLineAtOffset(x, y);
        stream.showText(sanitizarParaFuente(texto));
        stream.endText();
        return y - (tamanio + 4);
    }

    /**
     * La fuente Helvetica estándar solo puede codificar WinAnsi/Latin-1. El
     * Copiloto IA (Ollama) puede devolver ocasionalmente comillas tipográficas,
     * viñetas o algún símbolo fuera de ese rango — sin este filtro, PDFBox
     * lanza una excepción y toda la generación del documento falla. Mejor
     * sustituir el carácter que perder el documento completo.
     */
    private String sanitizarParaFuente(String texto) {
        if (texto == null || texto.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c <= 0xFF) {
                sb.append(c);
                continue;
            }
            switch (c) {
                case '‘': case '’': sb.append('\''); break;
                case '“': case '”': sb.append('"'); break;
                case '–': case '—': sb.append('-'); break;
                case '…': sb.append("..."); break;
                case '•': case '●': case '▪': sb.append('-'); break;
                case '✅': case '✔': case '✓': sb.append("[OK]"); break;
                case '❌': case '✗': sb.append("[X]"); break;
                default: sb.append('?');
            }
        }
        return sb.toString();
    }

    private List<String> envolver(String texto, PDType1Font fuente, float tamanio, float anchoUtil) throws IOException {
        List<String> lineas = new ArrayList<>();
        for (String parrafoOriginal : sanitizarParaFuente(texto).split("\n")) {
            StringBuilder actual = new StringBuilder();
            for (String palabra : parrafoOriginal.split(" ")) {
                String candidata = actual.isEmpty() ? palabra : actual + " " + palabra;
                if (fuente.getStringWidth(candidata) / 1000 * tamanio > anchoUtil && !actual.isEmpty()) {
                    lineas.add(actual.toString());
                    actual = new StringBuilder(palabra);
                } else {
                    actual = new StringBuilder(candidata);
                }
            }
            lineas.add(actual.toString());
        }
        return lineas;
    }
}
