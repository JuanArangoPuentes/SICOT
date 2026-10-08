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
        return FidelidadDeRedaccion.motivoDeInfidelidad(redactado, notas, DATOS) == null;
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
                // Redacciones reales de la sexta revisión que no deben descartarse
                {"a las 8 am llegaron los computadores, a las 2 pm quedaron instalados",
                        "A las 8:00 am se recibieron los equipos y a las 14:00 horas quedaron instalados."},
                {"llegaron 12 mesas y 5 sillas, 2 sillas con rayones, se pidió el cambio de esas 2",
                        "Se recibieron 12 mesas y 5 sillas; 2 sillas presentaban daños y se solicitó su cambio."},
                {"la factura FE-118 es por $4.500.000 y la FE-119 por $3.200.000, las dos corresponden a lo entregado",
                        "Se emitieron las facturas FE-118 por $4.500.000 y FE-119 por $3.200.000, ambas relacionadas"
                                + " con la entrega realizada."},
                {"hice visita el 02/10/2026, algunas lámparas del ambiente no prenden",
                        "El 2 de octubre de 2026 se observó que algunas lámparas del ambiente no encendían"
                                + " correctamente."},
                {"no hubo multas", "No se presentaron multas."},
                {"se recibió la 1ra entrega de 25 sillas, falta la segunda",
                        "Se ha recibido la primera entrega de 25 sillas; falta la segunda entrega."},
                {"revisé los computadores entregados, en total 7. funcionan todos",
                        "Se revisaron los siete equipos entregados y todos funcionan correctamente."},
                {"llegaron 20 colchonetas y 12 balones, no llegaron los conos",
                        "Se recibieron 20 colchonetas y 12 balones, mientras que los conos no fueron entregados."},
                {"el contratista no ha pagado la seguridad social de agosto, se le pidió por correo",
                        "El contratista no ha cumplido con el pago de la seguridad social de agosto; se le solicitó"
                                + " por correo."},
                // modelo #12: el valor en letras
                {"recibí la factura FE-4521 por $4.500.000 del periodo de agosto",
                        "Se recibió la factura FE-4521 por un monto de cuatro millones quinientos mil pesos"
                                + " correspondiente al período de agosto."},
                // Medición del 01-10-2026: lo mismo escrito de otra forma.
                {"a las 3 pm se hizo la entrega de los insumos en el almacén",
                        "A las tres de la tarde se efectuó la entrega de insumos en el almacén."},
                {"se instalaron 120 m2 de piso en el ambiente de gastronomía",
                        "Se instalaron 120 metros cuadrados de piso en el ambiente de gastronomía."},
                {"la factura de agosto es por $4'500.000 y está correcta",
                        "La factura de agosto por cuatro millones quinientos mil pesos está correcta."},
                {"se revisó el 1er piso y el 2do piso, en el 2do piso faltan 3 lámparas",
                        "Se inspeccionaron los pisos primero y segundo. En el segundo piso faltan tres lámparas."},
                {"están instalados: 2 hornos, 1 nevera y 4 mesones",
                        "Se confirmó la instalación de dos hornos, una nevera y cuatro mesones."},
                {"se revisó el 1er piso y el 2do piso, en el 2do piso faltan 3 lámparas",
                        "Se inspeccionaron los primeros y segundos pisos. En el segundo piso faltan tres lámparas."},
                {"el contratista no presentó reclamaciones y el saldo a liberar es 0",
                        "El contratista no presentó reclamaciones y el saldo pendiente de liberación es cero."},
                // «se reprograma» dicho como lo que es: una decisión, sin fecha.
                {"no se ha podido hacer la visita porque el ambiente estaba cerrado, se reprograma",
                        "No fue posible realizar la visita porque el ambiente estaba cerrado; se procede a"
                                + " reprogramarla."},
                // La calificación del estado que sí está en las notas.
                {"se recibieron 25 sillas en la sede de Tuluá, todo en buen estado",
                        "Se recibieron 25 sillas en buen estado en la sede de Tuluá."},
                {"los equipos funcionan bien", "Los equipos se encuentran en buen funcionamiento."},
                {"el informe del mes pasado está completo", "El informe del mes pasado está completo."},
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
                // Reprogramar habla del calendario, pero no de que se cumpliera,
                // y una reprogramación que las notas no dicen es un hecho nuevo.
                {"no se pudo hacer la visita, se reprograma",
                        "No se pudo hacer la visita; se reprogramó y se cumplió el plazo previsto."},
                {"se hizo la visita a la sede", "Se hizo la visita a la sede, que se había reprogramado."},
                // Revisión ciega del 01-10-2026 de redacciones que pasaban: «se
                // reprograma» no es que ya haya una fecha nueva.
                {"no se ha podido hacer la visita porque el ambiente estaba cerrado, se reprograma",
                        "No fue posible realizar la visita debido a que el ambiente estaba cerrado; se ha programado"
                                + " una nueva fecha para efectuarla."},
                // ni «todo completo», «en orden» o «vigentes» son «en buen estado»,
                {"recibí 6 cajas de tóner y 30 resmas de papel, todo completo",
                        "Se recibieron seis cajas de tóner y treinta resmas de papel en buen estado."},
                {"revisé los soportes de pago de los aprendices, todo en orden",
                        "Los soportes de pago para los aprendices se encuentran en buen estado."},
                {"los equipos no funcionan, el contratista no ha venido a revisarlos",
                        "Los equipos no se encuentran en buen funcionamiento y el contratista aún no ha realizado la"
                                + " revisión."},
                // ni una entrega es «sin incidencias» porque no hubo multas,
                {"el contratista entregó todos los bienes y no hubo multas ni sanciones",
                        "El contratista entregó todos los bienes sin incidencias. No hubo multas ni sanciones."},
                // ni «la otra semana» (la próxima) es «la semana anterior».
                {"se acordó con el contratista entregar los faltantes la otra semana",
                        "Se acordó con el contratista la entrega de los materiales faltantes la semana anterior."},
                // Lo que sigue siendo distinto aunque se escriba de otra forma.
                {"a las 3 pm se hizo la entrega", "A las cuatro de la tarde se hizo la entrega."},
                {"se instalaron 120 m2 de piso", "Se instalaron 12 metros cuadrados de piso."},
                {"la factura es por $4'500.000", "La factura es por $4.600.000."},
                {"llegaron 2 neveras", "Llegó una nevera."},
                {"la factura de agosto es por $4'500.000 y está correcta",
                        "La factura de agosto por cuatro millones quinientos mil pesos es correcta según las"
                                + " regulaciones vigentes."},
                // Medición del 01-10-2026: valoraciones infladas por el modelo.
                {"se recibieron 25 sillas y 10 mesas en la sede de Tuluá, todo en buen estado",
                        "Se recibieron 25 sillas y 10 mesas en excelente estado en la sede de Tuluá."},
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
                // Sexta revisión del 29-09-2026 (banco del revisor, una clase por renglón)
                {"llegaron 12 mesas y 5 sillas para el ambiente 204",
                        "Se recibieron 5 mesas y 12 sillas para el ambiente 204."},
                {"llegaron 5 sillas y 5 mesas", "Se recibieron 5 sillas y las mesas."},
                {"el 02/10/2026 llegaron las sillas, algunas con rayones",
                        "El 2 de octubre de 2026 se recibieron las sillas, dos de ellas con rayones."},
                {"a las 8 am llegaron los computadores del ambiente",
                        "A las 8 a. m. llegaron los 8 computadores del ambiente."},
                {"llegaron los computadores portátiles, todos funcionando",
                        "Llegaron 7 computadores portátiles, todos en funcionamiento."},
                {"la entrega fue el 30 de septiembre de 2026", "La entrega se realizó el 30 de noviembre de 2026."},
                {"el lunes hice la visita a la sede", "El martes realicé la visita a la sede."},
                {"la visita fue el 15/09 y la entrega el 30/09", "La visita fue el 30/09 y la entrega el 15/09."},
                {"llegaron los equipos a las 10:30 am", "Los equipos llegaron a las 10:30 p. m."},
                {"se recibió la primera entrega de 25 sillas", "Se recibió la segunda entrega de 25 sillas."},
                {"el contratista no ha cumplido con la entrega de las mesas",
                        "El contratista ha cumplido con la entrega de las mesas."},
                {"se entregaron las pólizas", "No se han entregado las pólizas."},
                {"no hay saldo para el siguiente pago", "Hay saldo para el siguiente pago."},
                {"las sillas llegaron en mal estado", "Las sillas llegaron en buen estado."},
                {"las mesas llegaron con rayones", "Las mesas llegaron sin rayones."},
                {"el contratista va fuera del plazo", "El contratista va dentro del plazo."},
                {"entregó los bienes", "Entregó la totalidad de los bienes."},
                {"se instalaron los equipos", "Se instalaron los equipos correctamente."},
                {"revisé la factura", "Revisé la factura, la cual queda aprobada para pago."},
                {"el contratista entregó las sillas fuera del plazo, se le hizo requerimiento",
                        "El contratista entregó las sillas a entera satisfacción y en cumplimiento del plazo."},
                {"se recibieron 2.5 toneladas de cemento", "Se recibió el cemento."},
                {"el 15/09/2026 llegaron las sillas, algunas con rayones",
                        "El 15 de septiembre de 2026 llegaron las sillas, un par de ellas con rayones."},
                {"el contratista entregó la mitad de los bienes", "El contratista entregó los bienes."},
                // en vivo, en el panel, el 29-09-2026
                {"se recibieron en bodega las 20 carpas completas y en buen estado, el almacenista firmó el recibido",
                        "Se recibieron 20 carpas completas y en buen estado, being la recepción confirmada por el"
                                + " almacenista."},
                {"el almacenista recibió los bienes, faltan los cargadores",
                        "Los bienes fueron recibidos por el almacenista; however, the chargers are yet to arrive."},
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

    // ── Auditoría del 02-10-2026: el año completado ─────────────────────────

    private static final String NOTAS_CON_Y_SIN_ANIO =
            "el 12 de octubre de 2026 visité la obra, entregará antes del 20 de octubre";

    /** En vivo con qwen2.5:7b: completa el año de la fecha que el supervisor escribió sin él. */
    @Test
    void elAnioQueLaRedaccionLeAgregaAUnaFechaSeQuitaYLaRedaccionPasa() {
        String limpia = FidelidadDeRedaccion.quitarAniosAgregados(
                "El 12 de octubre de 2026 se visitó la obra; entregará antes del 20 de octubre de 2026.",
                NOTAS_CON_Y_SIN_ANIO);

        assertThat(limpia).isEqualTo("El 12 de octubre de 2026 se visitó la obra; entregará antes del 20 de octubre.");
        assertThat(fiel(limpia, NOTAS_CON_Y_SIN_ANIO)).isTrue();
        assertThat(FidelidadDeRedaccion.quitarAniosAgregados("Entregará antes del 20/10/2026.", NOTAS_CON_Y_SIN_ANIO))
                .isEqualTo("Entregará antes del 20/10.");
        // Sin ningún año en las notas también: el año supuesto no es un dato del supervisor.
        String sinAnio = FidelidadDeRedaccion.quitarAniosAgregados("Entregará antes del 20 de octubre de 2026.",
                "entregará antes del 20 de octubre");
        assertThat(sinAnio).isEqualTo("Entregará antes del 20 de octubre.");
        assertThat(fiel(sinAnio, "entregará antes del 20 de octubre")).isTrue();
    }

    @Test
    void laComprobacionNoAceptaAniosSupuestosNiOtraFecha() {
        // Sin el paso que lo quita, el año completado sigue siendo una cifra que no estaba.
        assertThat(fiel("El 12 de octubre de 2026 se visitó la obra; entregará antes del 20 de octubre de 2026.",
                NOTAS_CON_Y_SIN_ANIO)).isFalse();
        // Otro día con año no es la fecha de las notas: no se toca y se descarta.
        String otroDia = FidelidadDeRedaccion.quitarAniosAgregados(
                "El 12 de octubre de 2026 se visitó la obra; entregará antes del 21 de octubre de 2026.",
                NOTAS_CON_Y_SIN_ANIO);
        assertThat(otroDia).contains("21 de octubre de 2026");
        assertThat(fiel(otroDia, NOTAS_CON_Y_SIN_ANIO)).isFalse();
        // La fecha que las notas sí escriben con año se queda con el suyo.
        assertThat(FidelidadDeRedaccion.quitarAniosAgregados("Se visitó la obra el 12 de octubre de 2026.",
                NOTAS_CON_Y_SIN_ANIO)).isEqualTo("Se visitó la obra el 12 de octubre de 2026.");
    }

    // ── Auditoría del 02-10-2026: la negación dicha con otras palabras ──────

    @Test
    void unaNegacionDichaConOtrasPalabrasNoEsDecirLoContrario() {
        String[][] fieles = {
                // en vivo con qwen2.5:7b
                {"no había personal de almacén para recibir",
                        "Ante la ausencia de personal del almacén no se pudo recibir."},
                {"no había personal de almacén", "La falta de personal de almacén."},
                {"no había personal de almacén", "No se contaba con personal del almacén."},
                {"no había energía en el ambiente", "El ambiente se encontraba sin energía."},
                {"el contratista no ha pagado la seguridad social",
                        "El contratista sigue sin que haya pagado la seguridad social."},
        };
        for (String[] par : fieles) {
            assertThat(fiel(par[1], par[0])).as(par[0]).isTrue();
        }
    }

    @Test
    void unaNegacionQuitadaSeSigueViendoAunqueLaRedaccionNieguePorOtroLado() {
        String[][] alteradas = {
                {"no había personal de almacén", "Había personal de almacén."},
                // «sin embargo» y «no obstante» no niegan nada
                {"no se entregaron las sillas", "Sin embargo se entregaron las sillas."},
                {"no se entregaron las sillas", "No obstante se entregaron las sillas."},
                // una negación de otra cláusula no alcanza a esta
                {"no se entregaron las sillas", "Falta firmar el acta y se entregaron las sillas."},
        };
        for (String[] par : alteradas) {
            assertThat(fiel(par[1], par[0])).as(par[1]).isFalse();
        }
    }

    // ── Auditoría del 02-10-2026: nombres agregados ─────────────────────────

    @Test
    void unNombreQueNiLasNotasNiElContratoDanSeDescarta() {
        String[][] alteradas = {
                {"el almacenista firmó el recibido de las 20 carpas",
                        "Se recibieron 20 carpas; el almacenista Carlos Pérez firmó el recibido."},
                {"se recibió el informe de la interventoría",
                        "Se recibió el informe de la interventoría de Consultores S.A.S."},
                {"la ingeniera revisó la instalación", "La ingeniera Martínez revisó la instalación."},
        };
        for (String[] par : alteradas) {
            assertThat(FidelidadDeRedaccion.motivoDeInfidelidad(par[1], par[0], DATOS)).as(par[1])
                    .isEqualTo("agregaba nombres que no estaban en sus notas");
        }
    }

    @Test
    void unNombreDeLasNotasODelContratoAunqueCambieLaMayusculaNoEsAgregado() {
        String[][] fieles = {
                {"le entregué los bienes a juan ospina", "Se entregaron los bienes a Juan Ospina."},
                {"se recibió en el almacén", "Se recibió en el almacén del SENA."},
                {"el contratista entregó las sillas", "El contratista Eventos Supernova S.A.S. entregó las sillas."},
                {"hice visita el 02/10/2026", "La visita se realizó el 2 de Octubre de 2026."},
                {"se revisó el informe de supervisión", "Se revisó el Informe de Supervisión."},
        };
        for (String[] par : fieles) {
            assertThat(fiel(par[1], par[0])).as(par[1]).isTrue();
        }
    }

    @Test
    void unNombreDeLasNotasQueLaRedaccionPierdeSeDescarta() {
        String[][] alteradas = {
                {"recibí con el almacenista Jorge Restrepo 25 computadores", "Se recibieron 25 computadores."},
                {"le entregué los bienes al coordinador, Juan Ospina", "Se entregaron los bienes al coordinador."},
                {"Revisé las sillas con la interventoría de Consultores Andinos",
                        "Se revisaron las sillas con la interventoría."},
        };
        for (String[] par : alteradas) {
            assertThat(FidelidadDeRedaccion.motivoDeInfidelidad(par[1], par[0], DATOS)).as(par[1])
                    .isEqualTo("perdía nombres de sus notas");
        }
    }

    @Test
    void conservarParteDelNombreOPerderMayusculasQueNoSonNombresNoEsPerderlo() {
        String[][] fieles = {
                // basta con una de las palabras del nombre
                {"recibí con el almacenista Jorge Restrepo 25 computadores",
                        "Se recibieron 25 computadores con el almacenista Restrepo."},
                // la mayúscula de principio de frase, de renglón o tras una viñeta no es un nombre
                {"Revisé las sillas. Llegaron completas", "Se revisaron las sillas, que llegaron completas."},
                {"- Llegaron 5 sillas\n- Faltan 2 mesas", "Llegaron 5 sillas y faltan 2 mesas."},
                // ni las notas en mayúsculas
                {"NO LLEGARON LOS VIDEOBEAM", "No llegaron los videobeam."},
                // ni los datos del contrato, que el prompt pide no repetir
                {"el contratista Eventos Supernova entregó las sillas", "El contratista entregó las sillas."},
                // ni los nombres de la casa, los meses o los días
                {"lo debe resolver el Centro antes del Lunes", "Lo debe resolver antes del lunes."},
        };
        for (String[] par : fieles) {
            assertThat(FidelidadDeRedaccion.sinNombresPerdidos(par[1], par[0], DATOS)).as(par[1]).isTrue();
        }
    }

    // ── Medición del 02-10-2026: reparar o reemplazar ───────────────────────

    @Test
    void cambiarUnReemplazoPorUnaReparacionOAlRevesSeDescarta() {
        String[][] alteradas = {
                {"2 sillas tenían el tapizado rasgado y el contratista se comprometió a cambiarlas",
                        "Dos sillas tenían el tapizado rasgado; el contratista se comprometió a repararlas."},
                {"el contratista va a reparar la mesa dañada", "El contratista va a reemplazar la mesa dañada."},
        };
        for (String[] par : alteradas) {
            assertThat(FidelidadDeRedaccion.motivoDeInfidelidad(par[1], par[0], DATOS)).as(par[1])
                    .isEqualTo("cambiaba un reemplazo por una reparación, o al revés");
        }
    }

    @Test
    void decirElMismoReemplazoOLaMismaReparacionConOtraPalabraPasa() {
        String[][] fieles = {
                {"el contratista se comprometió a cambiarlas",
                        "El contratista se comprometió a efectuar los cambios necesarios."},
                {"el contratista se comprometió a cambiarlas", "El contratista se comprometió a reemplazarlas."},
                {"el contratista va a reparar la mesa", "El contratista va a arreglar la mesa."},
                // sin decir cuál de las dos, no hay nada que cambiar
                {"el contratista quedó de solucionarlo", "El contratista se comprometió a repararlo."},
                {"se hizo el reparto de los bienes y se cambió la fecha", "Se realizó el reparto de los bienes."},
        };
        for (String[] par : fieles) {
            assertThat(FidelidadDeRedaccion.sinReparacionPorReemplazo(par[1], par[0])).as(par[1]).isTrue();
        }
    }

    // ── Medición del 02-10-2026: el «bien» o el «mal» de las notas ──────────

    @Test
    void perderLoQueLasNotasCalificanDeBienODeMalSeDescarta() {
        String[][] alteradas = {
                {"la factura está bien liquidada pero no trae el soporte de pago",
                        "La factura liquidada no trae el soporte de pago."},
                {"las sillas llegaron mal empacadas", "Las sillas llegaron empacadas."},
        };
        for (String[] par : alteradas) {
            assertThat(FidelidadDeRedaccion.motivoDeInfidelidad(par[1], par[0], DATOS)).as(par[1])
                    .isEqualTo("perdía lo que sus notas califican de bien o de mal");
        }
    }

    @Test
    void decirElBienOElMalConOtraPalabraPasa() {
        String[][] fieles = {
                {"la factura está bien liquidada", "La factura se encuentra correctamente liquidada."},
                {"las sillas llegaron bien", "Las sillas llegaron en buen estado."},
                {"las sillas llegaron mal empacadas", "Las sillas llegaron con un empaque defectuoso."},
                // «si bien» no califica nada, y «los bienes» o «debido a» no dicen «bien»
                {"si bien llegaron tarde, se recibieron", "Aunque llegaron tarde, se recibieron."},
        };
        for (String[] par : fieles) {
            assertThat(FidelidadDeRedaccion.sinValoracionPerdida(par[1], par[0])).as(par[1]).isTrue();
        }
        assertThat(FidelidadDeRedaccion.sinValoracionPerdida("Se recibieron los bienes debido a la visita.",
                "los bienes llegaron bien")).isFalse();
    }

    // ── Auditoría del 02-10-2026: la cosa que cuenta una cantidad ───────────

    @Test
    void cambiarLaCosaOLaUnidadDeUnaCantidadOIntercambiarlasSeDescarta() {
        String[][] alteradas = {
                {"llegaron 5 camas", "Llegaron 5 literas."},
                {"se recibieron 2.5 toneladas de cemento", "Se recibieron 2.5 kilogramos de cemento."},
                {"llegaron 12 mesas y 5 sillas", "Llegaron 12 sillas y 5 mesas."},
        };
        for (String[] par : alteradas) {
            assertThat(FidelidadDeRedaccion.motivoDeInfidelidad(par[1], par[0], DATOS)).as(par[1])
                    .isEqualTo("cambiaba lo que cuentan las cifras de sus notas");
        }
    }

    @Test
    void unVerboTrasLaCantidadOLaMismaCosaDichaOtraVezNoEsCambiarLaCosa() {
        String[][] fieles = {
                {"se probaron los 6 hornos; 5 funcionaron y 1 no calentó",
                        "Se probaron los 6 hornos; 5 operaron y 1 no calentó."},
                {"de las 40 sillas, 2 tenían el tapizado rasgado",
                        "De las 40 sillas, 2 sillas tenían el tapizado rasgado."},
                {"llegaron 5 portátiles", "Llegaron 5 computadores portátiles."},
                // medición del 02-10-2026: la unidad escrita de otra forma
                {"se recibieron 120 m2 de piso laminado y 30 cajas de zócalo",
                        "Se recibieron 120 m² de piso laminado y 30 cajas de zócalo."},
        };
        for (String[] par : fieles) {
            assertThat(fiel(par[1], par[0])).as(par[1]).isTrue();
        }
    }

    /** Medición del 02-10-2026 con qwen2.5:7b: «1 no calentó» redactado «uno no calentó». */
    @Test
    void unoSueltoEsElNumeroYNoUnArticulo() {
        String notas = "se probaron los 6 hornos; 5 funcionaron y 1 no calentó";
        assertThat(fiel("Se probaron los seis hornos; cinco funcionaron y uno no calentó.", notas)).isTrue();
        assertThat(fiel("De las 4 sillas, una no tenía patas.", "de las 4 sillas, 1 no tenía patas")).isTrue();
        // Sin el «uno», el 1 sigue perdiéndose.
        assertThat(fiel("Se probaron los seis hornos; cinco funcionaron y otro no calentó.", notas)).isFalse();
    }

    /** Medición del 02-10-2026 con qwen2.5:7b: «faltan 10 cajas» redactado «no se han recibido 10 cajas». */
    @Test
    void negarLoQueLasNotasYaNieganConOtrasPalabrasNoEsDecirLoContrario() {
        String notas = "se recibieron 120 m2 de piso laminado y 30 cajas de zócalo. Faltan 10 cajas de zócalo";
        assertThat(fiel("Se recibieron 120 m² de piso laminado y 30 cajas de zócalo; no se han recibido 10 cajas"
                + " de zócalo.", notas)).isTrue();
        assertThat(fiel("Se recibieron los computadores; no se recibieron los cargadores.",
                "se recibieron los computadores y faltan los cargadores")).isTrue();
        // Negar lo que las notas afirman sigue siendo decir lo contrario.
        assertThat(fiel("No se recibieron 30 cajas de zócalo; faltan 10 cajas de zócalo.", notas)).isFalse();
        assertThat(fiel("No se recibieron los computadores y faltan los cargadores.",
                "se recibieron los computadores y faltan los cargadores")).isFalse();
    }

    // ── Medición del 02-10-2026: salidas reales de qwen2.5:7b ───────────────
    //
    // Las notas son las seis de la medición (fechas, horas, cantidades,
    // negaciones, un nombre). Las redacciones son lo que respondió el modelo,
    // tal cual, con el prompt de antes («antes») o el de ahora; pasan por la
    // misma depuración que en el servicio (envoltorio, nombres, año agregado).

    private static final String N1 = "El 12 de octubre de 2026 a las 9:30 a. m. recibí en el almacén 40 sillas y 5"
            + " camas. Revisé las 40 sillas una por una: 2 tenían el tapizado rasgado y el contratista se comprometió"
            + " a cambiarlas antes del 20 de octubre. No revisé las camas porque no había personal de almacén.";
    private static final String N2 = "Visita de seguimiento el 07/10/2026. El avance físico va en 65% y el financiero"
            + " en 40%. El contratista no ha entregado la planilla de seguridad social de septiembre; se le envió"
            + " requerimiento por correo el 08/10/2026. Faltan por instalar 3 de los 12 mesones del ambiente de"
            + " gastronomía.";
    private static final String N3 = "recibí con el almacenista Jorge Restrepo 25 computadores portátiles y 25"
            + " cargadores, todos encienden. Las cajas venían selladas. No llegaron los 2 videobeam que estaban en la"
            + " orden.";
    private static final String N4 = "revisé la factura FE-2231 por $8.750.000 del mes de septiembre, está bien"
            + " liquidada pero no trae el soporte de pago de parafiscales. La devolví al contratista el lunes para que"
            + " la corrija.";
    private static final String N5 = "a las 2 pm del 15 de octubre se hizo la prueba de los 6 hornos instalados en el"
            + " taller de panadería; 5 funcionaron y 1 no calentó. El técnico de la empresa quedó de volver el viernes."
            + " Todavía no hay energía trifásica en el bloque B, eso lo debe resolver el Centro.";
    private static final String N6 = "se recibieron 120 m2 de piso laminado y 30 cajas de zócalo. Faltan 10 cajas de"
            + " zócalo, el contratista dice que llegan la otra semana. Sin novedades en la calidad.";

    private static boolean fielTrasDepurar(String redactado, String notas) {
        return fiel(GeneracionDocumentoService.depurar(redactado, notas, List.of()), notas);
    }

    @Test
    void lasRedaccionesRealesFielesDeLaMedicionPasan() {
        String[][] fieles = {
                // a temperatura 0, con el prompt de antes: el año completado y
                // «la falta de personal» la hacían descartar
                {N1, "En la fecha 12 de octubre de 2026, a las 9:30 a. m., se recibieron en el almacén 40 sillas y 5"
                        + " camas. Se realizó una revisión detallada de las 40 sillas, identificándose que 2 presentaban"
                        + " daño en el tapizado. El contratista se comprometió a efectuar los cambios necesarios antes"
                        + " del 20 de octubre de 2026. No se procedió a la revisión de las camas debido a la falta de"
                        + " personal de almacén."},
                // antes
                {N4, "Se revisó la factura FE-2231 por $8.750.000 correspondiente al mes de septiembre, encontrándose"
                        + " bien liquidada; no obstante, carece del soporte del pago de parafiscales. Fue devuelta al"
                        + " contratista el lunes para su corrección."},
                // antes: «uno no alcanzó» se descartaba por perder el 1
                {N5, "En la prueba realizada a las 14:00 del 15 de octubre, se verificaron los seis hornos instalados"
                        + " en el taller de panadería; cinco operaron correctamente mientras que uno no alcanzó el"
                        + " calentamiento necesario. El técnico de la empresa se comprometió a retornar el viernes para"
                        + " solucionar dicho problema. Además, persiste la falta de energía trifásica en el bloque B,"
                        + " cuya resolución corresponde al Centro."},
                {N6, "Se recibieron 120 m² de piso laminado y 30 cajas de zócalo. Faltan 10 cajas de zócalo, según el"
                        + " contratista llegarán la próxima semana. La calidad no presenta novedades."},
                // ahora: «no se han recibido 10 cajas» y «120 m²» se descartaban
                {N6, "Se recibieron 120 m² de piso laminado y 30 cajas de zócalo; no se han recibido 10 cajas de"
                        + " zócalo, según el contratista llegarán la próxima semana. La calidad no presenta novedades."},
                {N6, "Se recibieron 120 m² de piso laminado y 30 cajas de zócalo; no se han entregado 10 cajas de"
                        + " zócalo, según el contratista llegarán la próxima semana. La calidad no presenta novedades."},
                {N5, "Se realizó una prueba a las 2 pm del 15 de octubre a los seis hornos instalados en el taller de"
                        + " panadería; se verificó que cinco funcionaron correctamente y uno no calentó. El técnico de"
                        + " la empresa se comprometió a retornar el viernes para resolver el problema. Además, aún no se"
                        + " ha proporcionado energía trifásica al bloque B, lo cual debe ser atendido por el Centro."},
                // ahora, pidiendo no omitir ningún hecho: «los cambios necesarios» es el mismo compromiso
                {N1, "Se recibieron en el almacén 40 sillas y 5 camas el 12 de octubre de 2026 a las 9:30 a. m. Se"
                        + " revisaron todas las sillas, encontrándose que dos presentaban rasgaduras en el tapizado; el"
                        + " contratista se comprometió a efectuar los cambios necesarios antes del 20 de octubre. No se"
                        + " realizó la revisión de las camas debido a la falta de personal de almacén."},
        };
        for (String[] par : fieles) {
            assertThat(fielTrasDepurar(par[1], par[0])).as(par[1]).isTrue();
        }
    }

    @Test
    void lasRedaccionesRealesInfielesDeLaMedicionSeDescartan() {
        String[][] alteradas = {
                // antes: pierde las dos fechas y que el contratista no ha entregado la planilla
                {N2, "El avance físico asciende al 65%, mientras que el financiero alcanza un 40%. Se ha enviado"
                        + " requerimiento para la entrega de la planilla de seguridad social correspondiente a"
                        + " septiembre. Además, se encuentran pendientes tres de los doce mesones en el área de"
                        + " gastronomía."},
                // ahora: pierde la fecha de la visita
                {N2, "El avance físico se encuentra en un 65%, mientras que el financiero está en un 40%. El"
                        + " contratista no ha presentado la planilla de seguridad social correspondiente al mes de"
                        + " septiembre; se le había enviado requerimiento mediante correo electrónico el día 8 de"
                        + " octubre de 2026. Se reportan faltantes de tres de los doce mesones del área gastronómica."},
                // ahora: pierde que la factura se revisó y está bien liquidada
                {N4, "no se presentó el soporte de pago de parafiscales correspondiente a la factura FE-2231 por"
                        + " $8.750.000 liquidada en septiembre; la misma fue devuelta al contratista el lunes para su"
                        + " corrección."},
                // ahora: el contratista se comprometió a cambiarlas, no a repararlas
                {N1, "Se recibieron 40 sillas y 5 camas en el almacén el 12 de octubre de 2026 a las 9:30 a. m. Se"
                        + " verificaron todas las sillas, encontrándose que 2 presentaban daños en el tapizado; el"
                        + " contratista se comprometió a repararlas antes del 20 de octubre. No se revisaron las camas"
                        + " debido a la falta de personal de almacén."},
                // ahora: la forma impersonal se come al almacenista que recibió con el supervisor
                {N3, "Se recibieron 25 computadores portátiles y 25 cargadores, todos funcionales. Las cajas"
                        + " estaban selladas. Falta el equipamiento de los 2 videobeam indicados en la orden."},
                {N3, "Se recibieron 25 computadores portátiles y 25 cargadores, todos funcionales. Las cajas"
                        + " estaban selladas. Falta el ingreso de 2 videobeam como consta en la orden original."},
                // con una variante del prompt que se probó y no se dejó: inventa «las 40 cajas esperadas»
                {N6, "Se recibieron 120 m² de piso laminado y 30 cajas de zócalo; no se han recibido las 40 cajas"
                        + " esperadas, el contratista indica que el envío restante llegará la próxima semana. La"
                        + " calidad no presenta novedades."},
        };
        for (String[] par : alteradas) {
            assertThat(fielTrasDepurar(par[1], par[0])).as(par[1]).isFalse();
        }
    }
}
