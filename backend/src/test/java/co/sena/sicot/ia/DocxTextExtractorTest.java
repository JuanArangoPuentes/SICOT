package co.sena.sicot.ia;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El acta en Word se lee igual que en PDF: cada fila de tabla en una línea, que
 * es lo que esperan los patrones de {@link ExtraccionDeterminista}.
 */
class DocxTextExtractorTest {

    private static byte[] docx(String cuerpo) throws IOException {
        String xml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>%s</w:body></w:document>
                """.formatted(cuerpo).strip();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static String p(String texto) {
        return "<w:p><w:r><w:t>" + texto + "</w:t></w:r></w:p>";
    }

    private static String fila(String etiqueta, String valor) {
        return "<w:tr><w:tc>" + p(etiqueta) + "</w:tc><w:tc>" + p(valor) + "</w:tc></w:tr>";
    }

    @Test
    void lasFilasDeUnaTablaSalenComoEtiquetaYValorEnUnaLinea() throws IOException {
        String texto = new DocxTextExtractor().extraerTexto(docx(
                p("FORMATO ACTA DE INICIO")
                        + "<w:tbl>" + fila("CONTRATISTA", "EVENTOS SUPERNOVA S.A.S.")
                        + fila("CC o NIT", "900.478.852-5") + "</w:tbl>"));

        assertThat(texto).isEqualTo("FORMATO ACTA DE INICIO\nCONTRATISTA EVENTOS SUPERNOVA S.A.S.\nCC o NIT 900.478.852-5\n");
        assertThat(new ExtraccionDeterminista().extraer(texto).proveedor()).isEqualTo("EVENTOS SUPERNOVA S.A.S.");
    }

    @Test
    void unArchivoQueNoEsUnDocxDevuelveTextoVacioSinReventar() {
        assertThat(new DocxTextExtractor().extraerTexto("no soy un zip".getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    @Test
    void unaEntidadExternaNoSeResuelve() throws IOException {
        byte[] malicioso = docx(p("hola"))
                ;
        // Un DOCX con DTD no se procesa: se devuelve vacío en vez de resolver la entidad.
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
                + "<w:p><w:r><w:t>&e;</w:t></w:r></w:p></w:body></w:document>";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertThat(new DocxTextExtractor().extraerTexto(malicioso)).contains("hola");
        assertThat(new DocxTextExtractor().extraerTexto(bytes.toByteArray())).doesNotContain("root");
    }
}
