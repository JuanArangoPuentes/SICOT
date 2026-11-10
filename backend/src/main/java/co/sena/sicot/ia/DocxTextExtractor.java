package co.sena.sicot.ia;

import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.zip.ZipFile;

/**
 * Texto de un documento de Word (.docx), para la extracción de datos del
 * contrato.
 *
 * <h2>Por qué existe</h2>
 * La carga de Gestión acepta DOCX, pero la extracción solo leía PDF: un acta en
 * Word pasaba la validación y se ignoraba en silencio, y el formulario volvía
 * vacío sin decir por qué. Las actas del Centro se redactan en Word; es
 * habitual que lleguen así.
 *
 * <h2>Por qué no Apache POI</h2>
 * Un DOCX es un ZIP con el texto en {@code word/document.xml}. Leerlo con StAX,
 * que trae el JDK, basta para sacar el texto; POI añadiría unos 15 MB de
 * dependencias al backend para lo mismo.
 *
 * <h2>Las tablas se aplanan como las lee PDFBox</h2>
 * Cada fila de una tabla sale en una línea, con sus celdas separadas por un
 * espacio: «CONTRATISTA EVENTOS SUPERNOVA S.A.S.». Es la forma que ya leen los
 * patrones de {@link ExtraccionDeterminista} sobre un PDF, así que un acta en
 * Word y la misma acta en PDF se leen igual.
 */
@Component
public class DocxTextExtractor {

    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    /** Tope del XML descomprimido: un ZIP pequeño puede expandirse a gigabytes. */
    private static final long MAX_XML = 20L * 1024 * 1024;

    /**
     * Se lee con {@link ZipFile} desde un archivo temporal y no con
     * {@code ZipInputStream}: para llegar a {@code word/document.xml},
     * {@code ZipInputStream.getNextEntry()} descomprime entera cada entrada
     * anterior, sin tope. Un .docx de 2 MB con 2 GB de ceros en una entrada
     * previa tardaba 6,4 s (revisión del 24-09-2026); con los 20 MB que admite
     * la carga, un minuto de CPU por archivo. {@code ZipFile} salta directo a
     * la entrada por el índice central y solo descomprime esa.
     */
    public String extraerTexto(byte[] docx) {
        Path temporal = null;
        try {
            temporal = Files.createTempFile("sicot-docx-", ".zip");
            Files.write(temporal, docx);
            try (ZipFile zip = new ZipFile(temporal.toFile())) {
                ZipEntry entrada = zip.getEntry("word/document.xml");
                if (entrada == null) {
                    return "";
                }
                try (InputStream xml = zip.getInputStream(entrada)) {
                    return leer(new LimitadoInputStream(xml, MAX_XML));
                }
            }
        } catch (IOException | XMLStreamException e) {
            return "";
        } finally {
            if (temporal != null) {
                try {
                    Files.deleteIfExists(temporal);
                } catch (IOException ignorada) {
                    // Un temporal que no se pudo borrar lo limpia el sistema; no
                    // es motivo para perder la lectura del documento.
                }
            }
        }
    }

    private String leer(InputStream xml) throws XMLStreamException {
        XMLInputFactory fabrica = XMLInputFactory.newFactory();
        // Sin DTD ni entidades externas: el archivo lo sube un usuario.
        fabrica.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        fabrica.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XMLStreamReader r = fabrica.createXMLStreamReader(xml);

        StringBuilder salida = new StringBuilder();
        // Pila de filas: una tabla dentro de una celda abre una fila nueva sin
        // cerrar la exterior. Con una sola variable, la fila interior pisaba a
        // la exterior y se perdía su rótulo («CONTRATISTA | [tabla: …]»).
        Deque<StringBuilder> filas = new ArrayDeque<>();
        StringBuilder parrafo = new StringBuilder();
        int profundidadTabla = 0;
        while (r.hasNext()) {
            int evento = r.next();
            if (evento == XMLStreamConstants.START_ELEMENT && W.equals(r.getNamespaceURI())) {
                switch (r.getLocalName()) {
                    case "tbl" -> profundidadTabla++;
                    case "tr" -> filas.push(new StringBuilder());
                    case "tab" -> parrafo.append(' ');
                    case "br", "cr" -> parrafo.append(profundidadTabla > 0 ? ' ' : '\n');
                    case "t" -> parrafo.append(r.getElementText());
                    default -> { }
                }
            } else if (evento == XMLStreamConstants.END_ELEMENT && W.equals(r.getNamespaceURI())) {
                switch (r.getLocalName()) {
                    case "p" -> {
                        String texto = parrafo.toString().strip();
                        parrafo.setLength(0);
                        if (!filas.isEmpty()) {
                            StringBuilder fila = filas.peek();
                            if (!texto.isEmpty()) {
                                if (!fila.isEmpty()) fila.append(' ');
                                fila.append(texto);
                            }
                        } else {
                            salida.append(texto).append('\n');
                        }
                    }
                    case "tr" -> {
                        if (!filas.isEmpty()) {
                            StringBuilder fila = filas.pop();
                            if (filas.isEmpty()) {
                                salida.append(fila).append('\n');
                            } else if (!fila.isEmpty()) {
                                StringBuilder exterior = filas.peek();
                                if (!exterior.isEmpty()) exterior.append(' ');
                                exterior.append(fila);
                            }
                        }
                    }
                    case "tbl" -> profundidadTabla--;
                    default -> { }
                }
            }
        }
        return salida.toString();
    }

    /** Corta la lectura al pasar el tope, en vez de descomprimir sin fin. */
    private static final class LimitadoInputStream extends InputStream {
        private final InputStream origen;
        private long restante;

        LimitadoInputStream(InputStream origen, long limite) {
            this.origen = origen;
            this.restante = limite;
        }

        @Override
        public int read() throws IOException {
            if (restante <= 0) throw new IOException("El documento es demasiado grande para leerlo.");
            int b = origen.read();
            if (b >= 0) restante--;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (restante <= 0) throw new IOException("El documento es demasiado grande para leerlo.");
            int n = origen.read(b, off, (int) Math.min(len, restante));
            if (n > 0) restante -= n;
            return n;
        }
    }
}
