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
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Genera el PDF real de un documento formal del proceso: membrete institucional
 * (verde SENA, igual a la identidad del resto de SICOT), título y código de
 * formato, fichas de datos, apartados, bloque de firma y pie de página con
 * numeración. No es una plantilla oficial escaneada del SENA — es un documento
 * generado por el sistema, con la estructura de los formatos reales y los datos
 * reales del contrato.
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

    private static final float ANCHO_ETIQUETA = 175;

    private static final Color VERDE_SENA = new Color(57, 181, 74);
    private static final Color VERDE_ENFASIS = new Color(44, 154, 60);
    private static final Color GRIS_TENUE = new Color(122, 171, 128);
    private static final Color NEGRO_TEXTO = new Color(12, 26, 14);
    private static final Color FONDO_ETIQUETA = new Color(237, 246, 238);
    // Ámbar para lo que falta: tiene que saltar a la vista antes de firmar.
    private static final Color AMBAR_PENDIENTE = new Color(166, 104, 0);

    private static final DateTimeFormatter FECHA_LARGA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.of("es", "CO"));

    private final Clock reloj;

    public SimplePdfWriter(Clock reloj) {
        this.reloj = reloj;
    }

    public byte[] generar(String titulo, List<String> parrafos) {
        return generar(titulo, null, null, null, null, parrafos);
    }

    /**
     * Documento de texto corrido, sin fichas. Se conserva para quien solo tiene
     * párrafos que escribir.
     */
    public byte[] generar(String titulo, String codigo, String numeroContrato,
                          String firmante, String cargoFirmante, List<String> parrafos) {
        List<BloqueDocumento> bloques = parrafos.stream()
                .<BloqueDocumento>map(BloqueDocumento.Parrafo::new)
                .toList();
        return generar(titulo, codigo, numeroContrato, firmante, cargoFirmante, bloques, "Generado por el Copiloto IA");
    }

    /**
     * @param codigo          código del formato (ej. GCCON-F-018), o null/"PENDIENTE_DE_DEFINIR" si no aplica.
     * @param numeroContrato  número del contrato al que pertenece el documento.
     * @param firmante        nombre de quien lo suscribe (el supervisor asignado).
     * @param cargoFirmante   cargo institucional de quien lo suscribe.
     * @param origen          cómo se produjo el documento, para el membrete. Se dice
     *                        con precisión: quien lee un acta tiene derecho a saber
     *                        si la redactó un modelo de IA o salió de una plantilla.
     */
    public byte[] generar(String titulo, String codigo, String numeroContrato, String firmante,
                          String cargoFirmante, List<BloqueDocumento> bloques, String origen) {
        try (PDDocument documento = new PDDocument()) {
            Lienzo l = new Lienzo(documento, origen);

            l.y = l.linea(l.negrita, 15, titulo, MARGEN, l.y, VERDE_ENFASIS);
            l.y -= 4;
            String metaLinea = construirLineaMeta(codigo, numeroContrato);
            if (metaLinea != null) {
                l.y = l.linea(l.normal, 9.5f, metaLinea, MARGEN, l.y, GRIS_TENUE);
            }
            l.y -= 6;
            l.regla(GRIS_TENUE);
            l.y -= 18;

            for (BloqueDocumento bloque : bloques) {
                switch (bloque) {
                    case BloqueDocumento.Seccion s -> l.seccion(s.titulo());
                    case BloqueDocumento.Parrafo p -> l.parrafo(p.texto());
                    case BloqueDocumento.Ficha f -> l.ficha(f.campos());
                }
            }

            // Bloque de firma: si no cabe, pasa a una página nueva.
            l.asegurarEspacio(92);
            l.bloqueFirma(firmante, cargoFirmante);
            l.stream.close();

            dibujarPies(documento, l.paginas, l.normal, l.italica);

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF del documento.", e);
        }
    }

    /** Estado de la escritura: página actual, posición vertical y fuentes. */
    private final class Lienzo {
        final PDDocument documento;
        final List<PDPage> paginas = new ArrayList<>();
        final PDType1Font negrita = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        final PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        final PDType1Font italica = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
        final String origen;
        PDPageContentStream stream;
        float y;

        Lienzo(PDDocument documento, String origen) throws IOException {
            this.documento = documento;
            this.origen = origen;
            nuevaPagina(true);
        }

        void nuevaPagina(boolean primera) throws IOException {
            if (stream != null) {
                stream.close();
            }
            PDPage pagina = new PDPage(PDRectangle.LETTER);
            documento.addPage(pagina);
            paginas.add(pagina);
            stream = new PDPageContentStream(documento, pagina);
            // El membrete va en todas las páginas, no solo en la primera: una
            // hoja suelta de un acta tiene que seguir diciendo de dónde es.
            dibujarMembrete(stream, negrita, normal, origen);
            y = ALTO_PAGINA - ALTO_MEMBRETE - (primera ? 34 : 24);
        }

        void asegurarEspacio(float alto) throws IOException {
            if (y - alto < LIMITE_INFERIOR) {
                nuevaPagina(false);
            }
        }

        float linea(PDType1Font fuente, float tamanio, String texto, float x, float yLinea, Color color)
                throws IOException {
            stream.beginText();
            stream.setNonStrokingColor(color);
            stream.setFont(fuente, tamanio);
            stream.newLineAtOffset(x, yLinea);
            stream.showText(sanitizarParaFuente(texto));
            stream.endText();
            return yLinea - (tamanio + 4);
        }

        void regla(Color color) throws IOException {
            stream.setStrokingColor(color);
            stream.setLineWidth(0.8f);
            stream.moveTo(MARGEN, y);
            stream.lineTo(ANCHO_PAGINA - MARGEN, y);
            stream.stroke();
        }

        void seccion(String titulo) throws IOException {
            // Un título solo al pie de una página, separado de su contenido, no
            // se lee: se exige espacio para el título y dos líneas más.
            asegurarEspacio(48);
            y -= 4;
            y = linea(negrita, 11, titulo, MARGEN, y, VERDE_ENFASIS);
            y -= 2;
        }

        /**
         * Texto corrido. Solo el tramo «[dato pendiente…]» va en ámbar, aunque el
         * ajuste de línea lo parta en dos: pintar la línea entera marcaba como
         * pendiente texto que sí es correcto.
         */
        void parrafo(String texto) throws IOException {
            // «[dato» y no «[dato pendiente»: el ajuste de línea puede dejar
            // «[dato» al final de una línea y «pendiente…]» en la siguiente.
            boolean dentro = false;
            for (String l : envolver(texto, normal, 10.5f, ANCHO_UTIL)) {
                asegurarEspacio(14);
                float x = MARGEN;
                int i = 0;
                while (i < l.length()) {
                    int fin;
                    if (dentro) {
                        int cierre = l.indexOf(']', i);
                        fin = cierre < 0 ? l.length() : cierre + 1;
                        x = tramo(l.substring(i, fin), x, AMBAR_PENDIENTE);
                        dentro = cierre < 0;
                    } else {
                        int apertura = l.indexOf("[dato", i);
                        fin = apertura < 0 ? l.length() : apertura;
                        x = tramo(l.substring(i, fin), x, NEGRO_TEXTO);
                        dentro = apertura >= 0;
                    }
                    i = fin;
                }
                y -= 14;
            }
            y -= 7;
        }

        private float tramo(String texto, float x, Color color) throws IOException {
            if (texto.isEmpty()) {
                return x;
            }
            linea(normal, 10.5f, texto, x, y, color);
            return x + normal.getStringWidth(sanitizarParaFuente(texto)) / 1000 * 10.5f;
        }

        void ficha(List<BloqueDocumento.Campo> campos) throws IOException {
            float anchoValor = ANCHO_UTIL - ANCHO_ETIQUETA;
            for (BloqueDocumento.Campo campo : campos) {
                List<String> etiqueta = envolver(campo.etiqueta(), negrita, 8.5f, ANCHO_ETIQUETA - 10);
                String valor = campo.valor() == null || campo.valor().isBlank() ? "[dato pendiente]" : campo.valor();
                List<String> lineasValor = envolver(valor, normal, 9.5f, anchoValor - 10);
                float alto = Math.max(etiqueta.size(), lineasValor.size()) * 12 + 8;
                asegurarEspacio(alto);

                stream.setNonStrokingColor(FONDO_ETIQUETA);
                stream.addRect(MARGEN, y - alto, ANCHO_ETIQUETA, alto);
                stream.fill();
                stream.setStrokingColor(GRIS_TENUE);
                stream.setLineWidth(0.5f);
                stream.addRect(MARGEN, y - alto, ANCHO_ETIQUETA, alto);
                stream.addRect(MARGEN + ANCHO_ETIQUETA, y - alto, anchoValor, alto);
                stream.stroke();

                float yTexto = y - 12;
                for (String l : etiqueta) {
                    linea(negrita, 8.5f, l, MARGEN + 5, yTexto, NEGRO_TEXTO);
                    yTexto -= 12;
                }
                Color colorValor = valor.startsWith("[") ? AMBAR_PENDIENTE : NEGRO_TEXTO;
                yTexto = y - 12;
                for (String l : lineasValor) {
                    linea(normal, 9.5f, l, MARGEN + ANCHO_ETIQUETA + 5, yTexto, colorValor);
                    yTexto -= 12;
                }
                y -= alto;
            }
            y -= 12;
        }

        void bloqueFirma(String firmante, String cargoFirmante) throws IOException {
            y -= 26;
            stream.setStrokingColor(GRIS_TENUE);
            stream.setLineWidth(0.8f);
            stream.moveTo(MARGEN, y);
            stream.lineTo(MARGEN + 220, y);
            stream.stroke();
            y -= 14;
            y = linea(normal, 10, "Firma: " + (firmante != null && !firmante.isBlank() ? firmante : "[pendiente]"),
                    MARGEN, y, NEGRO_TEXTO);
            if (cargoFirmante != null && !cargoFirmante.isBlank()) {
                y = linea(normal, 10, "Cargo: " + cargoFirmante, MARGEN, y, NEGRO_TEXTO);
            }
            y -= 4;
            linea(italica, 8.5f,
                    "La firma electrónica de este documento queda registrada en el sistema SICOT al momento de su aprobación.",
                    MARGEN, y, GRIS_TENUE);
        }
    }

    /** Membrete verde SENA en la parte superior de cada página — misma identidad visual que el resto de SICOT. */
    private void dibujarMembrete(PDPageContentStream stream, PDType1Font fuenteTitulo, PDType1Font fuenteTexto,
                                 String origen) throws IOException {
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
        stream.showText("CENTRO TECNOLÓGICO DEL MOBILIARIO · SENA");
        stream.endText();

        String derecha = sanitizarParaFuente(origen + " - " + LocalDate.now(reloj).format(FECHA_LARGA));
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
        if (tieneCodigo && tieneContrato) sb.append("  ·  ");
        if (tieneContrato) sb.append("Contrato ").append(numeroContrato);
        return sb.toString();
    }

    /** Segunda pasada: escribe "Página X de N" en cada página ya generada (se necesita el total primero). */
    private void dibujarPies(PDDocument documento, List<PDPage> paginas, PDType1Font fuenteTexto,
                             PDType1Font fuenteItalica) throws IOException {
        int total = paginas.size();
        for (int i = 0; i < total; i++) {
            PDPage pagina = paginas.get(i);
            try (PDPageContentStream pie = new PDPageContentStream(documento, pagina,
                    PDPageContentStream.AppendMode.APPEND, true)) {
                pie.setStrokingColor(GRIS_TENUE);
                pie.setLineWidth(0.6f);
                pie.moveTo(MARGEN, MARGEN + 14);
                pie.lineTo(ANCHO_PAGINA - MARGEN, MARGEN + 14);
                pie.stroke();

                pie.beginText();
                pie.setNonStrokingColor(GRIS_TENUE);
                pie.setFont(fuenteItalica, 7.5f);
                pie.newLineAtOffset(MARGEN, MARGEN);
                pie.showText("SICOT · Sistema Inteligente de Gestión y Acompañamiento de Contratos");
                pie.endText();

                String pagTexto = "Página " + (i + 1) + " de " + total;
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

    /**
     * La fuente Helvetica estándar solo puede codificar WinAnsi/Latin-1. El
     * Copiloto IA (Ollama) puede devolver ocasionalmente comillas tipográficas,
     * viñetas o algún símbolo fuera de ese rango — sin este filtro, PDFBox
     * lanza una excepción y toda la generación del documento falla. Mejor
     * sustituir el carácter que perder el documento completo.
     */
    private static String sanitizarParaFuente(String texto) {
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

    private static List<String> envolver(String texto, PDType1Font fuente, float tamanio, float anchoUtil)
            throws IOException {
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
