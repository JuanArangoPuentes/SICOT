package co.sena.sicot.ia;

import co.sena.sicot.ia.BloqueDocumento.Estilo;
import co.sena.sicot.ia.FormatoInstitucional.Familia;
import co.sena.sicot.ia.FormatoInstitucional.Imagen;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Las fuentes y los logos de UN documento PDF.
 *
 * <p>Los bytes de las fuentes y las imágenes se leen una sola vez para toda la
 * aplicación; lo que es de cada documento —la fuente incrustada, la imagen—
 * se crea aquí, porque PDFBox los ata al {@link PDDocument} en que se usan.
 *
 * <p>Las fuentes se incrustan (solo los caracteres usados). Además de dar el
 * aspecto de Calibri y Arial, eso evita dos defectos de la Helvetica estándar
 * que se usaba antes: el documento se veía distinto según el visor, y todo
 * carácter fuera de Latin-1 se cambiaba por «?» en el documento firmado.
 */
final class FuentesDelDocumento {

    private static final Map<String, byte[]> BYTES = new ConcurrentHashMap<>();
    private static final Map<Imagen, BufferedImage> IMAGENES = new ConcurrentHashMap<>();

    private final PDDocument pdf;
    private final Map<Familia, Map<Estilo, PDFont>> fuentes = new EnumMap<>(Familia.class);
    private final Map<Imagen, PDImageXObject> logos = new EnumMap<>(Imagen.class);
    private final Map<PDFont, Map<Integer, Boolean>> glifos = new IdentityHashMap<>();

    FuentesDelDocumento(PDDocument pdf) {
        this.pdf = pdf;
    }

    PDFont fuente(Familia familia, Estilo estilo) throws IOException {
        Map<Estilo, PDFont> deLaFamilia = fuentes.computeIfAbsent(familia, f -> new EnumMap<>(Estilo.class));
        PDFont f = deLaFamilia.get(estilo);
        if (f == null) {
            TrueTypeFont ttf = new TTFParser().parse(new RandomAccessReadBuffer(bytesDeFuente(familia, estilo)));
            // Sin ligaduras. PDFBox 3 aplica por su cuenta las de la fuente
            // (Carlito, como Calibri, une «ti», «fi», «ft»…) y el dibujo sale
            // bien, pero la capa de texto no: al extraerlo, «Antioquia» salía
            // «-oquia» y «septiembre» «embre». Eso rompía buscar y copiar en
            // el PDF y que SICOT volviera a leer sus propias actas. Word
            // tampoco aplica esas ligaduras por defecto.
            ttf.setEnableGsub(false);
            pdf.registerTrueTypeFontForClosing(ttf);
            f = PDType0Font.load(pdf, ttf, true);
            deLaFamilia.put(estilo, f);
        }
        return f;
    }

    PDImageXObject logo(Imagen imagen) throws IOException {
        PDImageXObject img = logos.get(imagen);
        if (img == null) {
            img = LosslessFactory.createFromImage(pdf, imagen(imagen));
            logos.put(imagen, img);
        }
        return img;
    }

    /** Ancho en puntos de un texto ya escribible. */
    static float ancho(PDFont f, float tamano, String texto) throws IOException {
        return f.getStringWidth(texto) / 1000 * tamano;
    }

    /**
     * El texto que la fuente sabe escribir. Carlito y Liberation cubren el
     * español y la tipografía habitual (comillas, rayas, «…»); lo que no
     * tengan —un emoji que devolvió el modelo, un símbolo pegado de otro
     * documento— se cambia por un equivalente o por «?», en vez de hacer
     * fallar la generación: mejor un carácter sustituido que perder el
     * documento completo. Los caracteres de control (un tabulador pegado de
     * Excel) pasan a espacio: PDFBox no los puede escribir y lanzaba.
     */
    String escribible(PDFont f, String texto) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        String normal = Normalizer.normalize(texto, Normalizer.Form.NFC);
        StringBuilder sb = new StringBuilder(normal.length());
        Map<Integer, Boolean> cache = glifos.computeIfAbsent(f, k -> new HashMap<>());
        for (int i = 0; i < normal.length(); ) {
            int cp = normal.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isISOControl(cp) || cp == 0x00A0) {
                sb.append(' ');
                continue;
            }
            // Invisibles de formato (espacio de ancho cero, BOM, marcas de
            // dirección) que llegan al pegar desde Word o la web: no se ven, así
            // que se quitan en vez de dibujarlos como «?».
            if (Character.getType(cp) == Character.FORMAT) {
                continue;
            }
            if (tieneGlifo(f, cp, cache)) {
                sb.appendCodePoint(cp);
                continue;
            }
            String sustituto = switch (cp) {
                case '‘', '’', '‚', '′' -> "'";
                case '“', '”', '„', '″' -> "\"";
                case '–', '—', '−', '‑' -> "-";
                case '…' -> "...";
                case '•', '●', '▪', '◦' -> "-";
                case '≥' -> ">=";
                case '≤' -> "<=";
                case 0x2705, 0x2714, 0x2713 -> "[OK]";
                case 0x274C, 0x2717 -> "[X]";
                default -> "?";
            };
            for (int j = 0; j < sustituto.length(); j++) {
                char s = sustituto.charAt(j);
                sb.append(tieneGlifo(f, s, cache) ? s : '?');
            }
        }
        return sb.toString();
    }

    private static boolean tieneGlifo(PDFont f, int cp, Map<Integer, Boolean> cache) {
        return cache.computeIfAbsent(cp, c -> {
            try {
                f.encode(new String(Character.toChars(c)));
                return true;
            } catch (IllegalArgumentException | IOException e) {
                return false;
            }
        });
    }

    // ── Recursos compartidos ────────────────────────────────────────────────

    static byte[] bytesDeFuente(Familia familia, Estilo estilo) {
        String archivo = switch (familia) {
            case CALIBRI -> switch (estilo) {
                case NORMAL -> "Carlito-Regular.ttf";
                case NEGRITA -> "Carlito-Bold.ttf";
                case ITALICA -> "Carlito-Italic.ttf";
                case NEGRITA_ITALICA -> "Carlito-BoldItalic.ttf";
            };
            // Ni el certificado ESUCON ni la GIL-F-010 usan cursiva: se incluyen
            // solo la normal y la negrita de Liberation Sans.
            case ARIAL -> switch (estilo) {
                case NORMAL, ITALICA -> "LiberationSans-Regular.ttf";
                case NEGRITA, NEGRITA_ITALICA -> "LiberationSans-Bold.ttf";
            };
        };
        return BYTES.computeIfAbsent("documentos/fuentes/" + archivo, FuentesDelDocumento::leer);
    }

    static BufferedImage imagen(Imagen imagen) {
        return IMAGENES.computeIfAbsent(imagen, i -> {
            String ruta = switch (i) {
                case VERDE -> "documentos/logo-sena-verde.png";
                case VERDE_ACTA_DE_INICIO -> "documentos/logo-sena-acta-de-inicio.png";
                case NEGRO -> "documentos/logo-sena-negro.png";
            };
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(leer(ruta)));
                if (img == null) {
                    throw new IllegalStateException("El logo " + ruta + " no es una imagen legible.");
                }
                return img;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static byte[] leer(String ruta) {
        try (InputStream in = FuentesDelDocumento.class.getClassLoader().getResourceAsStream(ruta)) {
            if (in == null) {
                throw new IllegalStateException("Falta el recurso " + ruta
                        + " en el backend: sin él no se pueden generar los documentos formales.");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
