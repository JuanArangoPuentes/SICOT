package co.sena.sicot.ia;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las dos defensas que se aplican a lo que redacta el modelo antes de que
 * entre en un documento firmado. Los casos vienen de la prueba integral del
 * 24-09-2026.
 */
class FidelidadDeRedaccionTest {

    private static final List<String> NOMBRES = List.of(
            "FERRETERÍA INDUSTRIAL LOS ANDES S.A.S.", "MARTA LUCÍA OSPINA GÓMEZ", "Paola Andrea Mejía");

    @Test
    void corrigeLasDosVariantesQueEscribioElModelo() {
        assertThat(FidelidadDeRedaccion.corregirNombres("firmado por Marta Lucía Osipina Gómez, representante", NOMBRES))
                .isEqualTo("firmado por MARTA LUCÍA OSPINA GÓMEZ, representante");
        assertThat(FidelidadDeRedaccion.corregirNombres("Nombre: Marta Lucía Ospona Gómez", NOMBRES))
                .isEqualTo("Nombre: MARTA LUCÍA OSPINA GÓMEZ");
    }

    @Test
    void loQueYaEstaBienNoSeToca() {
        String texto = "Marta Lucía Ospina Gómez firmó con Paola Andrea Mejía.";
        assertThat(FidelidadDeRedaccion.corregirNombres(texto, NOMBRES)).isEqualTo(texto);
    }

    @Test
    void unNombreDistintoNoSeConfundeConUnoConocido() {
        String texto = "Asistió Carlos Mario Reina Mejía.";
        assertThat(FidelidadDeRedaccion.corregirNombres(texto, NOMBRES)).isEqualTo(texto);
    }

    @Test
    void unaCifraQueEstaEnLasNotasEsFiel() {
        assertThat(FidelidadDeRedaccion.sinCifrasInventadas(
                "Se recibieron las 26 unidades.", "llegaron 26 unidades", List.of())).isTrue();
    }

    @Test
    void unValorEscritoConPuntosCuentaComoElMismoValor() {
        assertThat(FidelidadDeRedaccion.sinCifrasInventadas(
                "El valor de $120.450.000 se ejecutó.", "sin novedades", List.of("120450000"))).isTrue();
    }

    @Test
    void unaCifraQueNoEstabaEsInventada() {
        assertThat(FidelidadDeRedaccion.sinCifrasInventadas(
                "Se recibieron 124.510.000 pesos el 31 de diciembre.", "se recibieron los bienes",
                List.of("120450000"))).isFalse();
    }

    // ── Hallazgos de la revisión adversarial del 24-09-2026 ─────────────────

    @Test
    void unNombreDeDosPalabrasNoSeCorrige() {
        List<String> nombres = List.of("Andrés Ospina", "Ana Mesa");
        assertThat(FidelidadDeRedaccion.corregirNombres("La señora Andrea Ospina recibió los bienes.", nombres))
                .isEqualTo("La señora Andrea Ospina recibió los bienes.");
        assertThat(FidelidadDeRedaccion.corregirNombres("Se entregó una mesa en la sede.", nombres))
                .isEqualTo("Se entregó una mesa en la sede.");
    }

    @Test
    void unNombreExactoDeOtraPersonaNoSeCambiaPorUnoParecido() {
        List<String> nombres = List.of("Juan Pablo Pérez", "Juana Pablo Pérez");
        assertThat(FidelidadDeRedaccion.corregirNombres("reunión con Juan Pablo Pérez y Juana Pablo Pérez", nombres))
                .isEqualTo("reunión con Juan Pablo Pérez y Juana Pablo Pérez");
    }

    @Test
    void unNumeroInventadoEnLetrasSeDetecta() {
        assertThat(FidelidadDeRedaccion.sinCifrasInventadas(
                "Se recibieron veinticinco sillas por ciento veinticuatro millones de pesos.", "Recibí las sillas.",
                List.of("120450000"))).isFalse();
    }

    @Test
    void elValorDelContratoBienEscritoNoSeTomaPorInventado() {
        assertThat(FidelidadDeRedaccion.sinCifrasInventadas(
                "El contrato por $120.450.000 se ejecutó.", "se ejecutó", List.of("120450000"))).isTrue();
        assertThat(FidelidadDeRedaccion.sinCifrasInventadas(
                "Por ciento veinte millones cuatrocientos cincuenta mil pesos.", "se ejecutó",
                List.of("CIENTO VEINTE MILLONES CUATROCIENTOS CINCUENTA MIL PESOS M/CTE"))).isTrue();
    }

    @Test
    void unDecimalNoSeConfundeConOtroNumero() {
        assertThat(FidelidadDeRedaccion.sinCifrasInventadas(
                "Se recibieron 25 toneladas.", "llegaron 2,5 toneladas", List.of())).isFalse();
    }

    // ── Caso real del 29-09-2026: «5 camas» salió como «las cunas» ──────────

    private static final String NOTAS_REALES = "3.1 verifiqué en bodega la entrega de 5 camas; 3.2 cargué las fotos; "
            + "3.3 comparé cantidades y calidad con la ficha técnica y coinciden.";
    private static final String REDACCION_REAL = "He verificado la recepción de las cunas en la bodega, comprobando su "
            + "conformidad con los datos detallados en la ficha técnica. Asimismo, he registrado las fotografías "
            + "correspondientes a esta inspección y confirmo que tanto las cantidades como las características "
            + "presentan coincidencia con los estándares contractuales.";

    @Test
    void unaCantidadQueSePierdeEnLaRedaccionSeDetecta() {
        assertThat(FidelidadDeRedaccion.conservaLasCifras(REDACCION_REAL, NOTAS_REALES)).isFalse();
    }

    @Test
    void unaPalabraCambiadaPorOtraParecidaSeDetecta() {
        assertThat(FidelidadDeRedaccion.sinPalabrasCambiadas(REDACCION_REAL, NOTAS_REALES, List.of())).isFalse();
        assertThat(FidelidadDeRedaccion.sinPalabrasCambiadas("Se instalaron las cargas.", "instalaron las carpas",
                List.of())).isFalse();
    }

    @Test
    void losNumerosDeSubPasoNoSonCifrasQueSePierdan() {
        assertThat(FidelidadDeRedaccion.conservaLasCifras(
                "Verifiqué en bodega la entrega de las camas y cargué las fotos.",
                "3.1 verifiqué en bodega la entrega de 5 camas; 3.2 cargué las fotos")).isFalse();
        assertThat(FidelidadDeRedaccion.conservaLasCifras(
                "Verifiqué en bodega la entrega de las 5 camas y cargué las fotos.",
                "3.1 verifiqué en bodega la entrega de 5 camas; 3.2 cargué las fotos")).isTrue();
    }

    @Test
    void cambiarLaConjugacionNoEsCambiarLaPalabra() {
        assertThat(FidelidadDeRedaccion.sinPalabrasCambiadas(
                "El contratista entregó las 5 camas y recibo a satisfacción.",
                "el contratista entrega 5 camas, recibí a satisfacción", List.of())).isTrue();
    }

    @Test
    void unaPalabraQueVieneDeLosDatosDelContratoNoSeTomaPorCambiada() {
        assertThat(FidelidadDeRedaccion.sinPalabrasCambiadas("Se recibieron las carpas de Eventos Supernova.",
                "se recibieron las carpas de eventos", List.of("EVENTOS SUPERNOVA S.A.S."))).isTrue();
    }
}
