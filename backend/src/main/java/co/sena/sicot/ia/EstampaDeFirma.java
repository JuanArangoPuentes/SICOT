package co.sena.sicot.ia;

import co.sena.sicot.ia.BloqueDocumento.Estilo;
import co.sena.sicot.ia.FormatoInstitucional.Familia;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.GregorianCalendar;
import java.util.List;

/**
 * Estampa la firma electrónica en el PDF que SICOT generó, en el hueco que el
 * propio documento reservó para ella.
 *
 * <h2>Por qué</h2>
 * Hasta el 28-09-2026 firmar solo cambiaba el estado en la base: el PDF que se
 * descargaba después seguía idéntico al borrador, sin ninguna marca. Un
 * supervisor que abría su acta firmada no veía la firma por ningún lado, y
 * quien recibiera el archivo tampoco. Los formatos reales llevan la firma
 * encima del nombre; aquí va un recuadro con quién firmó, el código de su
 * firma electrónica y la fecha y hora.
 *
 * <h2>La integridad no se rompe</h2>
 * El estampado ocurre DENTRO de la firma, antes de calcular la huella SHA-256:
 * lo que queda registrado como firmado es el PDF con la firma ya visible. Lo
 * que no se puede hacer —y no se hace— es tocar el contenido después.
 *
 * <p>Solo se estampa si el PDF trae el ancla ({@link PdfInstitucional#PROPIEDAD_ANCLA_FIRMA}),
 * es decir, si lo generó SICOT. Un PDF cargado desde fuera se firma tal cual:
 * no se sabe dónde va la firma y modificarlo sería alterar un documento ajeno.
 */
@Component
public class EstampaDeFirma {

    private static final Logger log = LoggerFactory.getLogger(EstampaDeFirma.class);
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color VERDE = new Color(0x39, 0xA9, 0x00);
    private static final Color GRIS = new Color(0x40, 0x40, 0x40);

    private final Clock reloj;

    public EstampaDeFirma(Clock reloj) {
        this.reloj = reloj;
    }

    /**
     * @return el PDF con la firma estampada, o el mismo arreglo si el PDF no
     *         trae ancla (no lo generó SICOT) o no se puede leer.
     */
    /**
     * El id del usuario para quien se generó el documento (su nombre es el
     * del bloque de firma), o {@code null} si el PDF no lo dice: uno cargado
     * desde fuera, o uno generado antes de que SICOT lo guardara.
     */
    public Long firmantePrevisto(byte[] pdf) {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            String valor = documento.getDocumentInformation()
                    .getCustomMetadataValue(PdfInstitucional.PROPIEDAD_FIRMANTE);
            return valor == null || valor.isBlank() ? null : Long.valueOf(valor.strip());
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public byte[] estampar(byte[] pdf, String firmante, String codigoFirma, Instant cuando) {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            String ancla = documento.getDocumentInformation()
                    .getCustomMetadataValue(PdfInstitucional.PROPIEDAD_ANCLA_FIRMA);
            if (ancla == null) {
                return pdf;
            }
            String[] partes = ancla.split(";");
            int pagina = Integer.parseInt(partes[0]);
            float x = Float.parseFloat(partes[1]);
            float y = Float.parseFloat(partes[2]);
            float ancho = Float.parseFloat(partes[3]);
            float alto = Float.parseFloat(partes[4]);
            if (pagina < 0 || pagina >= documento.getNumberOfPages()) {
                return pdf;
            }
            PDPage page = documento.getPage(pagina);
            FuentesDelDocumento fuentes = new FuentesDelDocumento(documento);
            PDFont negrita = fuentes.fuente(Familia.CALIBRI, Estilo.NEGRITA);
            PDFont normal = fuentes.fuente(Familia.CALIBRI, Estilo.NORMAL);
            ZonedDateTime momento = cuando.atZone(reloj.getZone());
            List<Renglon> contenido = List.of(
                    new Renglon("Firmado electrónicamente en SICOT", normal, VERDE),
                    new Renglon(firmante != null ? firmante : "", negrita, GRIS),
                    new Renglon("Firma " + (codigoFirma != null ? codigoFirma : ""), normal, GRIS),
                    new Renglon(momento.format(FECHA_HORA), normal, GRIS));
            // La letra se ajusta al hueco, en alto y en ancho: en la GIL-F-010
            // el hueco es bajo, y un nombre largo («María Alejandra Gutiérrez
            // Castañeda de Restrepo Villegas») se salía del recuadro y, con unos
            // 70 caracteres, de la hoja. Primero se reduce la letra; si ni con
            // la mínima cabe, el nombre se parte en dos renglones.
            float tam = Math.min(8f, (alto - 4f) / (contenido.size() * 1.2f));
            List<Renglon> lineas = contenido;
            float anchoTexto = anchoMaximo(fuentes, lineas, tam);
            while (anchoTexto + 10f > ancho && tam > TAMANO_MINIMO) {
                tam = Math.max(TAMANO_MINIMO, tam - 0.25f);
                anchoTexto = anchoMaximo(fuentes, lineas, tam);
            }
            if (anchoTexto + 10f > ancho) {
                lineas = partirAlAncho(fuentes, contenido, tam, ancho - 10f);
                // Un renglón más tiene que seguir cabiendo en el alto del hueco.
                tam = Math.max(TAMANO_MINIMO, Math.min(tam, (alto - 4f) / (lineas.size() * 1.2f)));
                anchoTexto = anchoMaximo(fuentes, lineas, tam);
            }
            float anchoRecuadro = Math.min(ancho, anchoTexto + 10f);
            float altoRecuadro = lineas.size() * tam * 1.2f + 4f;
            float yRecuadro = y + (alto - altoRecuadro) / 2;
            try (PDPageContentStream cs = new PDPageContentStream(documento, page,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                cs.setStrokingColor(VERDE);
                cs.setLineWidth(0.8f);
                cs.addRect(x, yRecuadro, anchoRecuadro, altoRecuadro);
                cs.stroke();
                float base = yRecuadro + altoRecuadro - 2f - tam * 0.95f;
                for (Renglon r : lineas) {
                    cs.beginText();
                    cs.setNonStrokingColor(r.color());
                    cs.setFont(r.fuente(), tam);
                    cs.newLineAtOffset(x + 5f, base);
                    cs.showText(fuentes.escribible(r.fuente(), r.texto()));
                    cs.endText();
                    base -= tam * 1.2f;
                }
            }
            documento.getDocumentInformation().setModificationDate(GregorianCalendar.from(momento));
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException | RuntimeException e) {
            // Sin estampa el documento sigue siendo válido y su huella se
            // calcula igual: no se bloquea la firma por esto.
            log.warn("No se pudo estampar la firma en el PDF; se firma sin estampa: {}", e.getMessage());
            return pdf;
        }
    }

    /** Por debajo de 4,5 pt la estampa ya no se lee impresa. */
    private static final float TAMANO_MINIMO = 4.5f;

    private record Renglon(String texto, PDFont fuente, Color color) {
    }

    private static float anchoMaximo(FuentesDelDocumento fuentes, List<Renglon> lineas, float tam)
            throws IOException {
        float maximo = 0;
        for (Renglon r : lineas) {
            maximo = Math.max(maximo,
                    FuentesDelDocumento.ancho(r.fuente(), tam, fuentes.escribible(r.fuente(), r.texto())));
        }
        return maximo;
    }

    /** Reparte cada renglón por palabras para que ninguno pase del ancho. */
    private static List<Renglon> partirAlAncho(FuentesDelDocumento fuentes, List<Renglon> lineas, float tam,
                                               float ancho) throws IOException {
        List<Renglon> resultado = new java.util.ArrayList<>();
        for (Renglon r : lineas) {
            StringBuilder actual = new StringBuilder();
            for (String palabra : r.texto().split(" ")) {
                String prueba = actual.isEmpty() ? palabra : actual + " " + palabra;
                if (!actual.isEmpty()
                        && FuentesDelDocumento.ancho(r.fuente(), tam, fuentes.escribible(r.fuente(), prueba)) > ancho) {
                    resultado.add(new Renglon(actual.toString(), r.fuente(), r.color()));
                    actual.setLength(0);
                    actual.append(palabra);
                } else {
                    actual.setLength(0);
                    actual.append(prueba);
                }
            }
            resultado.add(new Renglon(actual.toString(), r.fuente(), r.color()));
        }
        return resultado;
    }
}
