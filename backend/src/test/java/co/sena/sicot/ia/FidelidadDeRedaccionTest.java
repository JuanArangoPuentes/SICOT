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
}
