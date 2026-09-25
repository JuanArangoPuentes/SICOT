package co.sena.sicot.ia;

import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los cinco formatos, armados con un contrato completo. Se comprueba sobre el
 * texto del PDF final, que es lo que el supervisor firma.
 */
class RedactorDeDocumentosTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 24);

    private static Contrato contrato() {
        Usuario sup = new Usuario();
        sup.setNombre("Paola Andrea Mejía");
        sup.setEmail("paola@soy.sena.edu.co");
        Contrato c = new Contrato();
        c.setNumeroContrato("CO1.PCCNTR.9100001");
        c.setTipoContrato("Compraventa");
        c.setObjeto("ADQUISICIÓN DE HERRAMIENTA MANUAL Y ELÉCTRICA");
        c.setValor(new BigDecimal("120450000"));
        c.setFechaInicio(LocalDate.of(2026, 9, 2));
        c.setFechaFin(LocalDate.of(2026, 12, 31));
        c.setContratista("FERRETERÍA INDUSTRIAL LOS ANDES S.A.S.");
        c.setContratistaNit("901.234.567-8");
        c.setRepresentanteLegal("MARTA LUCÍA OSPINA GÓMEZ");
        c.setLugarEjecucion("CARRERA 50 NO. 37-20, ITAGÜÍ");
        c.setNumeroRegistroPresupuestal("71204");
        c.setFechaRegistroPresupuestal(LocalDate.of(2026, 8, 28));
        c.setCentroCosto("9301");
        c.setSupervisor(sup);
        return c;
    }

    private static String pdf(String tipo, Contrato c, String obs) {
        PlantillaDocumentoIA p = PlantillaDocumentoIA.CATALOGO.get(tipo);
        List<BloqueDocumento> bloques = RedactorDeDocumentos.componer(p, c, HOY, obs);
        SimplePdfWriter writer = new SimplePdfWriter(
                Clock.fixed(Instant.parse("2026-09-24T15:00:00Z"), ZoneId.of("America/Bogota")));
        byte[] bytes = writer.generar(p.nombre(), p.codigo(), c.getNumeroContrato(), "Paola Andrea Mejía",
                "Supervisor del contrato", bloques, "Generado en SICOT");
        // El texto extraído parte las líneas largas; se normalizan los espacios
        // para comprobar frases completas.
        return new PdfTextExtractor().extraerTexto(bytes).lines().map(String::strip)
                .collect(Collectors.joining(" ")).replaceAll("\\s+", " ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTA_INICIO", "INFORME_SUPERVISION", "ACTA_RECIBO", "CERTIFICACION_CUMPLIMIENTO",
            "INFORME_FINAL"})
    void cadaFormatoLlevaLosDatosExactosDelContrato(String tipo) {
        String t = pdf(tipo, contrato(), null);
        assertThat(t)
                .contains("CO1.PCCNTR.9100001")
                .contains("FERRETERÍA INDUSTRIAL LOS ANDES S.A.S.")
                .contains("901.234.567-8")
                .contains("$120.450.000")
                .contains("CIENTO VEINTE MILLONES CUATROCIENTOS CINCUENTA MIL PESOS M/CTE")
                // Nada de lo que el modelo inventó el 24-09-2026.
                .doesNotContain("Bs ")
                .doesNotContain("Secretaría")
                .doesNotContain("Osipina")
                .doesNotContain("Ospona");
    }

    @Test
    void elActaDeInicioSigueElFormatoGcconF018() {
        String t = pdf("ACTA_INICIO", contrato(), null);
        assertThat(t)
                .contains("GCCON-F-018")
                .contains("SUPERVISOR DESIGNADO")
                .contains("71204 del 28/08/2026")
                .contains("El día 24 de septiembre de 2026")
                .contains("MARTA LUCÍA OSPINA GÓMEZ")
                .contains("Se deja constancia de la verificación de los documentos")
                // SICOT no conoce la fecha de aprobación de las garantías: no la inventa.
                .contains("[dato pendiente: fecha de aprobación de las garantías]");
    }

    @Test
    void laCertificacionNoInventaLaFacturaNiElSaldo() {
        String t = pdf("CERTIFICACION_CUMPLIMIENTO", contrato(), null);
        assertThat(t)
                .contains("CERTIFICA:")
                .contains("comenzó el día 2 de septiembre de 2026")
                .contains("[dato pendiente: número y fecha de la factura]")
                .contains("[dato pendiente: saldo por ejecutar]");
    }

    @Test
    void sinNotasLasObservacionesQuedanPendientesYConNotasVanAlDocumento() {
        assertThat(pdf("ACTA_RECIBO", contrato(), null)).contains("[dato pendiente: bienes recibidos y sus cantidades]");
        assertThat(pdf("ACTA_RECIBO", contrato(), "Se recibieron 26 cajas completas."))
                .contains("Se recibieron 26 cajas completas.")
                .doesNotContain("bienes recibidos y sus cantidades]");
    }

    @Test
    void unContratoConDatosIncompletosLosMarcaPendientesEnVezDeInventarlos() {
        Contrato c = contrato();
        c.setContratistaNit(null);
        c.setRepresentanteLegal(null);
        c.setFechaInicio(null);
        String t = pdf("INFORME_FINAL", c, null);
        assertThat(t).contains("[dato pendiente]").doesNotContain("null");
    }

    @Test
    void elMembreteYElPieLlevanTildes() {
        String t = pdf("ACTA_INICIO", contrato(), null);
        assertThat(t)
                .contains("CENTRO TECNOLÓGICO DEL MOBILIARIO")
                .contains("Página 1 de")
                .contains("Gestión y Acompañamiento");
    }
}
