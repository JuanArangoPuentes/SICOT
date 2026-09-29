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
        assertThat(FidelidadDeRedaccion.conservaLasCifras(REDACCION_REAL, NOTAS_REALES, List.of())).isFalse();
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
                "3.1 verifiqué en bodega la entrega de 5 camas; 3.2 cargué las fotos", List.of())).isFalse();
        assertThat(FidelidadDeRedaccion.conservaLasCifras(
                "Verifiqué en bodega la entrega de las 5 camas y cargué las fotos.",
                "3.1 verifiqué en bodega la entrega de 5 camas; 3.2 cargué las fotos", List.of())).isTrue();
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
    // ── Batería de la revisión final del 29-09-2026 ─────────────────────────
    //
    // Las dos comprobaciones anteriores descartaban 5 de 20 redacciones fieles
    // del modelo real (qwen2.5:7b) y 14 de 28 escritas a mano. Estos casos
    // fijan que lo fiel pasa y lo alterado no. Los marcados «modelo» son
    // redacciones reales de qwen2.5:7b con el prompt de SICOT.

    private static final List<String> DATOS = List.of("CO1.PCCNTR.71204", "EVENTOS SUPERNOVA S.A.S.", "900123456-7",
            "RP-2026-0451");

    private static boolean fiel(String redactado, String notas) {
        return !FidelidadDeRedaccion.enOtraEscritura(redactado, notas)
                && FidelidadDeRedaccion.sinCifrasInventadas(redactado, notas, DATOS)
                && FidelidadDeRedaccion.conservaLasCifras(redactado, notas, DATOS)
                && FidelidadDeRedaccion.sinPalabrasCambiadas(redactado, notas, DATOS)
                && FidelidadDeRedaccion.sinSentidoInvertido(redactado, notas)
                && FidelidadDeRedaccion.sinAfirmacionesAgregadas(redactado, notas);
    }

    @Test
    void lasRedaccionesFielesPasan() {
        String[][] fieles = {
                // modelo #02
                {"revisé la factura del mes de agosto y está todo correcto, pero falta la planilla de seguridad social",
                        "Se ha revisado la factura correspondiente al mes de agosto, confirmándose su corrección. Sin"
                                + " embargo, se observa que falta presentar la planilla de seguridad social."},
                // modelo #03: fecha en forma larga
                {"el 15/09/2026 fui a la sede de Tuluá y el contratista entregó 20 sillas ergonómicas",
                        "El día 15 de septiembre de 2026, se verificó la entrega de 20 sillas ergonómicas en las"
                                + " instalaciones de Tuluá por parte del contratista."},
                // modelo #06: ordinal en letras
                {"hice visita al 2do piso del taller, los equipos están instalados y funcionando",
                        "Durante la visita a la instalación, se observó que los equipos del segundo piso del taller han"
                                + " sido correctamente instalados y se encuentran en funcionamiento."},
                // modelo #08: corrige la ortografía
                {"rebisé el informe de actividades del contratista, cumple con lo pactado",
                        "El informe de actividades presentado por el contratista se revisó y cumplió con los acuerdos"
                                + " estipulados en el contrato."},
                // modelo #10: la cifra en letras
                {"verificamos la instalación de 15 lámparas en el ambiente de carpintería",
                        "Se verificó la instalación de quince lámparas en el ambiente de carpintería."},
                // modelo #15: lista numerada pasada a párrafo
                {"1. revisé las facturas 2. revisé las planillas de pago 3. todo en orden",
                        "Se efectuó la revisión de las facturas y las planillas de pago, encontrándose todo en orden."},
                // modelo #17
                {"visita de seguimiento el 29-09-2026, sin novedad en la obra",
                        "En la visita de seguimiento realizada el 29 de septiembre de 2026, no se observaron novedades"
                                + " significativas en la ejecución de la obra."},
                // modelo #18
                {"se recibió la 1ra entrega de 25 sillas, falta la segunda",
                        "Se ha recibido la primera entrega de 25 sillas, siendo necesaria la recepción de la segunda"
                                + " entrega."},
                // modelo #19: sub-pasos
                {"4.1 revisé el avance físico, va en 60%; 4.2 revisé el avance financiero, va en 45%",
                        "Se ha verificado que el avance físico del proyecto se encuentra en un 60%, mientras que el"
                                + " avance financiero está en un 45%."},
                {"resibí a satisfacción los 10 computadores portátiles",
                        "Recibí a satisfacción los 10 computadores portátiles."},
                {"todo llegó bien, 40 resmas", "Las 40 resmas se recibieron en buen estado."},
                // «sede» sigue en el texto: «sido» no la reemplaza
                {"fui a la sede y todo bien", "Se visitó la sede; todo ha sido normal."},
                {"el contratista entrega las 30 mesas", "El contratista entregó las treinta mesas."},
                {"el contrato 71204 va al día", "El contrato se encuentra al día."},
                // la misma afirmación con otra forma de la palabra no es nueva
                {"el contratista cumple las especificaciones y hay retraso en la pintura",
                        "El contratista cumplió con las especificaciones técnicas; se presentan retrasos en la"
                                + " pintura."},
                // Revisión del 29-09-2026: lo que la versión anterior de esta
                // comprobación descartaba sin razón.
                {"revisé los documentos del programa de formación, están completos",
                        "Se revisó la información y los documentos del programa de formación, encontrándose completos."},
                {"se entregaron los muebles en la sede", "Se entregaron los muebles en el inmueble de la sede."},
                {"me desplacé a la obra, el contratista va dentro del plazo previsto",
                        "El contratista se encuentra en cumplimiento con el plazo establecido."},
                {"1. revisé las facturas 2. revisé las planillas", "Se revisaron las facturas y las planillas."},
                {"medí la resistencia de tierra, 10 Ω, dentro de lo exigido",
                        "Se midió la resistencia de puesta a tierra, con un valor de 10 Ω, dentro de lo exigido."},
                {"llegaron 31 sillas", "Se recibieron treinta y una sillas."},
                {"sin novedades en la entrega", "No se observaron novedades en la entrega."},
                {"el contratista cumplió con el objeto y no hubo multas",
                        "El contratista cumplió con el objeto, sin que se haya incurrido en ninguna multa."},
                {"la obra va bien, pero hay retraso en la pintura",
                        "La obra avanza en buen estado general; sin embargo, se presenta retraso en la pintura."},
                {"el contratista instaló los puestos de trabajo como estaba acordado",
                        "El contratista cumplió con la instalación de los puestos de trabajo conforme a lo acordado."},
                {"el contrato 71204 va al día, revisado el 01/10/2026",
                        "El contrato se encuentra al día, según revisión del 1 de octubre de 2026."},
                // modelo #12: el valor en letras
                {"recibí la factura FE-4521 por $4.500.000 del periodo de agosto",
                        "Se recibió la factura FE-4521 por un monto de cuatro millones quinientos mil pesos"
                                + " correspondiente al período de agosto."},
        };
        for (String[] par : fieles) {
            assertThat(fiel(par[1], par[0])).as(par[0]).isTrue();
        }
    }

    @Test
    void lasRedaccionesAlteradasSeDescartan() {
        String[][] alteradas = {
                // el caso real
                {NOTAS_REALES, REDACCION_REAL},
                // modelo #04: pierde las 12 mesas
                {"llegaron 12 mesas pero 2 tenían rayones, se pidió el cambio al contratista",
                        "Se verificó la recepción de las mesas y se constató la presencia de dos unidades con rayones."},
                // modelo #14 y #01: texto en chino
                {"el material llegó mojado, se devolvieron 4 cajas y quedan 16 en bodega",
                        "Se recibió el material en estado mojado. Se procedió al退货四箱，现存仓库十六箱。"},
                {"se revisó la entrega", "Se revisó la entrega. 这个改进后的回答更加详细。"},
                {"instalaron las carpas", "Se instalaron las cargas."},
                {"se recibió la cama en la bodega", "Se recibió la caja en la bodega."},
                {"verifiqué el pago de la factura", "Verifiqué el paso de la factura."},
                {"llegaron 5 camas", "Llegaron seis camas."},
                {"el 15/09/2026 fui a la sede", "El 16 de septiembre de 2026 se visitó la sede."},
                {"se recibió la 1ra entrega", "Se recibió la segunda entrega."},
                // el sentido invertido, en los dos sentidos
                {"el contratista cumplió con la entrega", "El contratista incumplió con la entrega."},
                {"el contratista incumplió el plazo", "El contratista cumplió el plazo."},
                // Revisión del 29-09-2026: excepciones que eran por piezas
                {"llegaron 7 computadores portátiles, todos funcionando",
                        "Se recibieron los computadores portátiles, todos en funcionamiento."},
                {"camas recibidas: 5. mesas recibidas: 12. todo en buen estado",
                        "Se recibieron las camas y las mesas, todo en buen estado."},
                {"llegaron las sillas, en total 20. estaban en buen estado",
                        "Llegaron las sillas y estaban en buen estado."},
                {"llegaron 31 sillas", "Se recibieron treinta sillas."},
                {"llegaron 35 sillas y 5 mesas", "Llegaron treinta sillas y cinco mesas."},
                {"el 15/09/2026 llegaron las sillas, algunas con rayones",
                        "El 15 de septiembre de 2026 se recibieron las sillas, dos de ellas con rayones."},
                {"recibí la factura por $4.500.000 y las sillas", "Recibí la factura por $4.500.000 y cuatro sillas."},
                {"el 05/09/2026 llegaron 12 sillas", "El 12 de septiembre de 2026 llegaron 5 sillas."},
                {"visita el 15/09/2026, entrega el 30/09/2026",
                        "La visita fue el 30 de septiembre de 2026 y la entrega el 15 de septiembre de 2026."},
                {"se instalaron los equipos del 2do piso; quedan pendientes los del 1er piso",
                        "Se instalaron los equipos del primer piso; quedan pendientes los del segundo piso."},
                {"el contratista cumplió con la entrega", "El contratista no cumplió con la entrega."},
                {"no se entregaron las sillas", "Se entregaron las sillas."},
                // prueba en vivo: agrega que la entrega fue a tiempo
                {"el contratista cumplió con el objeto, entregó la 2da parte de los bienes y no hubo multas",
                        "El contratista cumplió con el objeto del contrato, entregando la segunda parte de los bienes"
                                + " en el término establecido, sin que se hayan incurrido en ninguna multa."},
                {"se recibieron las sillas", "Se recibieron las sillas a satisfacción y conforme a lo pactado."},
        };
        for (String[] par : alteradas) {
            assertThat(fiel(par[1], par[0])).as(par[0]).isFalse();
        }
    }
}
