package co.sena.sicot.ia;

import co.sena.sicot.exception.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Extrae el texto plano de un PDF real para pasárselo a la IA como contexto. */
@Component
public class PdfTextExtractor {

    public String extraerTexto(byte[] contenido) {
        try (PDDocument documento = Loader.loadPDF(contenido)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(documento);
        } catch (IOException e) {
            throw new BusinessException("No se pudo leer el contenido del PDF: " + e.getMessage());
        }
    }
}
