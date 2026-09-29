package co.sena.sicot.ia;

import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Usuario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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

    /** Un PDF que no generó SICOT no trae el hueco de la firma: se firma tal cual, sin tocarlo. */
    @Test
    void unPdfCargadoDesdeFueraNoSeModifica() {
        byte[] ajeno = new PdfInstitucional(RELOJ).generar(new DocumentoFormal(
                PlantillaDocumentoIA.CATALOGO.get("INFORME_SUPERVISION").formato(), "ajeno", "n", null, null,
                List.of(new BloqueDocumento.Parrafo("Sin firmas."))));

        assertThat(estampa.estampar(ajeno, "X", "F", Instant.now())).isSameAs(ajeno);
    }

    @Test
    void unArchivoQueNoEsPdfSeDevuelveIgual() {
        byte[] basura = "no es un pdf".getBytes(StandardCharsets.UTF_8);

        assertThat(estampa.estampar(basura, "X", "F", Instant.now())).isSameAs(basura);
    }
}
