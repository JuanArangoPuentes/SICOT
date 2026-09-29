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
            List<String> lineas = List.of(
                    "Firmado electrónicamente en SICOT",
                    firmante != null ? firmante : "",
                    "Firma " + (codigoFirma != null ? codigoFirma : ""),
                    momento.format(FECHA_HORA));
            // La letra se ajusta al hueco: en la GIL-F-010 el hueco es bajo.
            float tam = Math.min(8f, (alto - 4f) / (lineas.size() * 1.2f));
            float anchoTexto = 0;
            for (String l : lineas) {
                anchoTexto = Math.max(anchoTexto, FuentesDelDocumento.ancho(negrita, tam, fuentes.escribible(negrita, l)));
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
                for (int i = 0; i < lineas.size(); i++) {
                    PDFont f = i == 1 ? negrita : normal;
                    cs.beginText();
                    cs.setNonStrokingColor(i == 0 ? VERDE : GRIS);
                    cs.setFont(f, tam);
                    cs.newLineAtOffset(x + 5f, base);
                    cs.showText(fuentes.escribible(f, lineas.get(i)));
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
}
