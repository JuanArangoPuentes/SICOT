package co.sena.sicot.ia;

import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Usuario;
import co.sena.sicot.exception.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La firma visible en el PDF firmado. Antes de esto, el PDF que se descargaba
 * después de firmar era idéntico al borrador: nadie que lo abriera veía que
 * estaba firmado.
 */
class EstampaDeFirmaTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-09-28T20:15:00Z"), ZoneId.of("America/Bogota"));
    private final EstampaDeFirma estampa = new EstampaDeFirma(RELOJ);

    private static byte[] documento(String tipo) {
        Usuario sup = new Usuario();
        sup.setNombre("Laura Carolina Restrepo Toro");
        sup.setEmail("lcrestrepo@sena.edu.co");
        Contrato c = new Contrato();
        c.setNumeroContrato("CO1.PCCNTR.7986334");
        c.setObjeto("SUMINISTRO DE TOLDOS");
        c.setValor(new BigDecimal("10000000"));
        c.setFechaInicio(LocalDate.of(2025, 6, 17));
        c.setFechaFin(LocalDate.of(2025, 12, 17));
        c.setContratista("EVENTOS SUPERNOVA S.A.S.");
        c.setSupervisor(sup);
        PlantillaDocumentoIA p = PlantillaDocumentoIA.CATALOGO.get(tipo);
        return new PdfInstitucional(RELOJ).generar(new DocumentoFormal(p.formato(), p.nombre(), c.getNumeroContrato(),
                sup.getNombre(), "Generado en SICOT", RedactorDeDocumentos.componer(p, c, LocalDate.of(2026, 9, 28), null)));
    }

    @Test
    void cadaDocumentoGeneradoQuedaConLaFirmaVisible() {
        for (String tipo : PlantillaDocumentoIA.CATALOGO.keySet()) {
            byte[] firmado = estampa.estampar(documento(tipo), "Laura Carolina Restrepo Toro", "FIRMA-2026-0042",
                    Instant.parse("2026-09-28T20:15:00Z"));

            assertThat(new PdfTextExtractor().extraerTexto(firmado)).as(tipo)
                    .contains("Firmado electrónicamente en SICOT")
                    .contains("Firma FIRMA-2026-0042")
                    // La hora es la del Centro, no la de la JVM.
                    .contains("28/09/2026 15:15");
        }
    }

    /**
     * Con un nombre largo, el texto de la estampa se salía de su recuadro y,
     * hacia los 70 caracteres, de la hoja (revisión del 29-09-2026). La letra
     * se reduce y, si no alcanza, el nombre se parte en dos renglones.
     */
    @Test
    void unNombreLargoNoSeSaleDelHuecoDeLaFirma() throws Exception {
        String nombre = "MARÍA ALEJANDRA GUTIÉRREZ CASTAÑEDA DE RESTREPO VILLEGAS OCHOA DE LA TORRE";
        for (String tipo : PlantillaDocumentoIA.CATALOGO.keySet()) {
            byte[] borrador = documento(tipo);
            byte[] firmado = estampa.estampar(borrador, nombre, "FIRMA-2026-0042", Instant.parse("2026-09-28T20:15:00Z"));
            try (org.apache.pdfbox.pdmodel.PDDocument d = org.apache.pdfbox.Loader.loadPDF(firmado)) {
                String[] ancla = d.getDocumentInformation()
                        .getCustomMetadataValue(PdfInstitucional.PROPIEDAD_ANCLA_FIRMA).split(";");
                float derecha = Float.parseFloat(ancla[1]) + Float.parseFloat(ancla[3]);
                // El hueco medido desde arriba, que es como mide el extractor de texto.
                float altoPagina = d.getPage(Integer.parseInt(ancla[0])).getMediaBox().getHeight();
                float techo = altoPagina - (Float.parseFloat(ancla[2]) + Float.parseFloat(ancla[4]));
                float suelo = altoPagina - Float.parseFloat(ancla[2]);
                java.util.List<Float> finales = new java.util.ArrayList<>();
                java.util.List<float[]> alturas = new java.util.ArrayList<>();
                new org.apache.pdfbox.text.PDFTextStripper() {
                    @Override
                    protected void writeString(String text,
                                               java.util.List<org.apache.pdfbox.text.TextPosition> posiciones) {
                        // Solo las palabras de la estampa, que no están en el borrador.
                        if (text.contains("CASTAÑEDA") || text.contains("VILLEGAS") || text.contains("TORRE")
                                || text.contains("electrónicamente") || text.contains("FIRMA-2026-0042")) {
                            posiciones.forEach(p -> {
                                finales.add(p.getXDirAdj() + p.getWidthDirAdj());
                                alturas.add(new float[]{p.getYDirAdj() - p.getHeightDir(), p.getYDirAdj()});
                            });
                        }
                    }
                }.getText(d);
                assertThat(finales).as(tipo).isNotEmpty()
                        .allSatisfy(x -> assertThat(x).isLessThanOrEqualTo(derecha + 0.5f));
                assertThat(alturas).as(tipo).allSatisfy(a -> {
                    assertThat(a[0]).isGreaterThanOrEqualTo(techo - 0.5f);
                    assertThat(a[1]).isLessThanOrEqualTo(suelo + 0.5f);
                });
                // Y el recuadro verde tampoco sale por arriba ni por abajo: en la
                // GIL-F-010 la primera corrección lo hacía 5,6 pt más alto que el
                // hueco, y los filetes de la hoja lo tachaban.
                float xAncla = Float.parseFloat(ancla[1]);
                float yAncla = Float.parseFloat(ancla[2]);
                float altoHueco = Float.parseFloat(ancla[4]);
                float[] recuadro = recuadroEn(d.getPage(Integer.parseInt(ancla[0])), xAncla);
                assertThat(recuadro).as(tipo + ": recuadro de la estampa").isNotNull();
                assertThat(recuadro[1]).as(tipo).isGreaterThanOrEqualTo(yAncla - 0.5f);
                assertThat(recuadro[1] + recuadro[3]).as(tipo).isLessThanOrEqualTo(yAncla + altoHueco + 0.5f);
            }
            assertThat(new PdfTextExtractor().extraerTexto(firmado).replaceAll("\\s+", " ")).as(tipo)
                    .contains("VILLEGAS OCHOA");
        }
    }

    /** El último rectángulo («re») de la página que empieza en esa x: el recuadro de la estampa. */
    private static float[] recuadroEn(org.apache.pdfbox.pdmodel.PDPage pagina, float x) throws java.io.IOException {
        List<Object> fichas = new org.apache.pdfbox.pdfparser.PDFStreamParser(pagina).parse();
        float[] encontrado = null;
        for (int i = 4; i < fichas.size(); i++) {
            if (fichas.get(i) instanceof org.apache.pdfbox.contentstream.operator.Operator op
                    && op.getName().equals("re")) {
                float[] r = new float[4];
                boolean numeros = true;
                for (int k = 0; k < 4; k++) {
                    if (fichas.get(i - 4 + k) instanceof org.apache.pdfbox.cos.COSNumber n) {
                        r[k] = n.floatValue();
                    } else {
                        numeros = false;
                    }
                }
                if (numeros && Math.abs(r[0] - x) < 0.01f) {
                    encontrado = r;
                }
            }
        }
        return encontrado;
    }

    /** Un PDF que no generó SICOT no trae el hueco de la firma: se firma tal cual, sin tocarlo. */
    @Test
    void unPdfCargadoDesdeFueraNoSeModifica() {
        byte[] ajeno = new PdfInstitucional(RELOJ).generar(new DocumentoFormal(
                PlantillaDocumentoIA.CATALOGO.get("INFORME_SUPERVISION").formato(), "ajeno", "n", null, null,
                List.of(new BloqueDocumento.Parrafo("Sin firmas."))));

        assertThat(estampa.estampar(ajeno, "X", "F", Instant.now())).isSameAs(ajeno);
    }

    /**
     * Si no se puede estampar, no se firma. Antes se devolvía el PDF sin
     * estampa y la firma seguía: el acta quedaba firmada, íntegra y con el
     * hueco vacío, sin arreglo posible (auditoría del 02-10-2026).
     */
    @Test
    void unArchivoQueNoSeLeeComoPdfNoSeFirma() {
        byte[] basura = "no es un pdf".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> estampa.estampar(basura, "X", "F", Instant.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No se pudo poner la firma visible");
    }

    @Test
    void unHuecoDeFirmaFueraDelDocumentoODanadoNoSeFirma() throws Exception {
        for (String ancla : List.of("99;70;100;200;60", "0;no-es-un-numero;1;1;1", "0;10")) {
            byte[] pdf = conAncla(documento("ACTA_INICIO"), ancla);

            assertThatThrownBy(() -> estampa.estampar(pdf, "X", "F", Instant.now())).as(ancla)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("el borrador sigue pendiente");
        }
    }

    /** El mismo PDF con otra ancla de firma, como uno que se dañó al guardarse. */
    static byte[] conAncla(byte[] pdf, String ancla) throws IOException {
        try (PDDocument d = Loader.loadPDF(pdf)) {
            d.getDocumentInformation().setCustomMetadataValue(PdfInstitucional.PROPIEDAD_ANCLA_FIRMA, ancla);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            d.save(salida);
            return salida.toByteArray();
        }
    }
}
