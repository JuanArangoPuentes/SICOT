package co.sena.sicot.ia;

import co.sena.sicot.dto.etapa.EtapaResponse;
import co.sena.sicot.dto.etapa.SubetapaResponse;
import co.sena.sicot.entity.enums.EstadoEtapa;
import co.sena.sicot.entity.enums.EstadoSubetapa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El «¿en qué paso voy?» se contesta con los datos, no con el modelo.
 *
 * <h2>Por qué estas pruebas pueden ser tan estrictas</h2>
 * Porque el texto ya no lo escribe un modelo. Mientras lo escribía, lo único
 * afirmable era «responde algo»; ahora se puede exigir la etapa exacta, el
 * subpaso exacto por el que debe empezar y que no se cuele ningún paso ya
 * completado. Esa es, en sí misma, la mitad del argumento para haberlo sacado
 * del modelo.
 *
 * <p>La prueba que más importa es {@link #noSeInventaLaEtapaCuandoLaPrimeraYaEstaCerrada()}:
 * el 14 de septiembre de 2026, `qwen2.5:1.5b` y `qwen2.5:3b` respondieron
 * «etapa 4» a un contrato que estaba en la etapa 1, copiando el número de un
 * ejemplo del prompt. Aquí eso es imposible por construcción.
 */
class GuiaDelPasoActualTest {

    private final GuiaDelPasoActual guia = new GuiaDelPasoActual();

    private static SubetapaResponse sub(String codigo, String nombre, EstadoSubetapa estado) {
        return new SubetapaResponse(1L, codigo, nombre, null, estado, "SUPERVISOR");
    }

    private static EtapaResponse etapa(int numero, String nombre, EstadoEtapa estado,
                                       int porcentaje, List<SubetapaResponse> subs) {
        return new EtapaResponse((long) numero, numero, nombre, estado, porcentaje, subs);
    }

    private static List<EtapaResponse> contratoReciénRecibido() {
        return List.of(
                etapa(1, "INICIO — Estudios y Suscripción", EstadoEtapa.EN_CURSO, 0, List.of(
                        sub("1.1", "Revisar estudios previos", EstadoSubetapa.PENDIENTE),
                        sub("1.2", "Verificar disponibilidad presupuestal", EstadoSubetapa.PENDIENTE))),
                etapa(2, "INICIO — Acta de Inicio", EstadoEtapa.PENDIENTE, 0, List.of(
                        sub("2.1", "Elaborar acta de inicio", EstadoSubetapa.PENDIENTE))));
    }

    @Test
    @DisplayName("reconoce las formas en que un supervisor pregunta por su paso")
    void reconoceLaPregunta() {
        assertThat(guia.puedeResponder("¿En qué paso voy?")).isTrue();
        assertThat(guia.puedeResponder("que tengo que hacer")).isTrue();
        assertThat(guia.puedeResponder("¿Qué sigue?")).isTrue();
        assertThat(guia.puedeResponder("por donde empiezo")).isTrue();
    }

    /**
     * El caso que se escapó, medido contra el sistema real el 15 de septiembre
     * de 2026: con el contrato en el paso 3, el copiloto respondió que faltaba
     * cerrar el <b>paso 4</b> y mandó al supervisor a los sub-pasos 4.1 y 4.2.
     *
     * <p>No falló la plantilla: falló su puerta de entrada. La lista de señales
     * traía «qué falta», y «qué ME falta» no contiene esa subcadena, así que la
     * pregunta se fue al modelo — la ruta que esta clase existe para evitar.
     *
     * <p>Si alguien vuelve a recortar la lista de señales, esta prueba es la
     * que debe ponerse roja.
     */
    @Test
    @DisplayName("reconoce la pregunta aunque lleve pronombre: «qué ME falta»")
    void reconoceLasOrdenesCortas() {
        // 25-09-2026: «siguiente paso» a secas iba al modelo y tardaba minutos.
        assertThat(guia.puedeResponder("siguiente paso")).isTrue();
        assertThat(guia.puedeResponder("Siguiente paso")).isTrue();
        assertThat(guia.puedeResponder("próximo paso")).isTrue();
        assertThat(guia.puedeResponder("¿qué hago si el contratista no entrega la póliza?")).isFalse();
    }

    @Test
    void reconoceLasVariantesConPronombre() {
        assertThat(guia.puedeResponder("¿Qué me falta para cerrar el paso en el que estoy?")).isTrue();
        assertThat(guia.puedeResponder("¿qué me falta?")).isTrue();
        assertThat(guia.puedeResponder("¿Qué me toca ahora?")).isTrue();
        assertThat(guia.puedeResponder("¿qué me queda pendiente en este paso?")).isTrue();
        assertThat(guia.puedeResponder("¿qué falta para terminar este paso?")).isTrue();
    }

    @Test
    @DisplayName("deja pasar al modelo lo que no es una pregunta de estado")
    void noSecuestraLasPreguntasAbiertas() {
        assertThat(guia.puedeResponder("¿De dónde saco la póliza de cumplimiento?")).isFalse();
        assertThat(guia.puedeResponder("¿qué pasa si el contratista se atrasa?")).isFalse();
        assertThat(guia.puedeResponder("hola")).isFalse();
        assertThat(guia.puedeResponder(null)).isFalse();
        assertThat(guia.puedeResponder("")).isFalse();
    }

    @Test
    @DisplayName("una pregunta larga se deja al modelo aunque contenga la frase")
    void noSecuestraPreguntasLargasConMatices() {
        String larga = "que tengo que hacer " + "y ademas necesito saber el detalle de ".repeat(6);
        assertThat(larga.length()).isGreaterThan(200);
        assertThat(guia.puedeResponder(larga)).isFalse();
    }

    @Test
    @DisplayName("indica la etapa en curso, sus pendientes y por dónde empezar")
    void componeLaRespuestaConLosDatosReales() {
        String r = guia.responder(contratoReciénRecibido()).orElseThrow();

        assertThat(r).startsWith("Está en el paso 1: INICIO — Estudios y Suscripción.");
        assertThat(r).contains("1.1 Revisar estudios previos");
        assertThat(r).contains("1.2 Verificar disponibilidad presupuestal");
        assertThat(r).contains("Empiece por 1.1: Revisar estudios previos.");
        // No debe adelantar trabajo de una etapa que todavía no toca.
        assertThat(r).doesNotContain("2.1");
    }

    @Test
    @DisplayName("no se inventa la etapa cuando la primera ya está cerrada")
    void noSeInventaLaEtapaCuandoLaPrimeraYaEstaCerrada() {
        List<EtapaResponse> etapas = List.of(
                etapa(1, "INICIO — Estudios y Suscripción", EstadoEtapa.COMPLETADA, 100, List.of(
                        sub("1.1", "Revisar estudios previos", EstadoSubetapa.COMPLETADA))),
                etapa(2, "INICIO — Acta de Inicio", EstadoEtapa.EN_CURSO, 50, List.of(
                        sub("2.1", "Elaborar acta de inicio", EstadoSubetapa.COMPLETADA),
                        sub("2.2", "Firmar acta con el contratista", EstadoSubetapa.EN_CURSO))));

        String r = guia.responder(etapas).orElseThrow();

        assertThat(r).startsWith("Está en el paso 2: INICIO — Acta de Inicio (50% completado).");
        assertThat(r).contains("2.2 Firmar acta con el contratista").contains("← en curso");
        // El subpaso ya cerrado no se le vuelve a pedir.
        assertThat(r).doesNotContain("2.1");
    }

    @Test
    @DisplayName("cuando no queda nada pendiente lo dice, en vez de señalar un paso cualquiera")
    void avisaCuandoYaNoQuedaNada() {
        List<EtapaResponse> todasCerradas = List.of(
                etapa(1, "INICIO", EstadoEtapa.COMPLETADA, 100, List.of(
                        sub("1.1", "Revisar estudios previos", EstadoSubetapa.COMPLETADA))));

        assertThat(guia.responder(todasCerradas).orElseThrow())
                .contains("Ya están completados los 1 pasos del contrato");
    }

    @Test
    @DisplayName("sin etapas no responde nada, para que quien llama siga su camino normal")
    void sinEtapasNoInventa() {
        assertThat(guia.responder(List.of())).isEqualTo(Optional.empty());
        assertThat(guia.responder(null)).isEqualTo(Optional.empty());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1-10-2026: la puerta de entrada, normalizada
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("da igual la tilde, la mayúscula o un teclado que manda la tilde aparte")
    void daIgualLaTildeOLaMayuscula() {
        assertThat(guia.puedeResponder("en que paso voy")).isTrue();
        assertThat(guia.puedeResponder("EN QUÉ PASO VOY")).isTrue();
        assertThat(guia.puedeResponder("¿En qué paso voy?")).isTrue();
        assertThat(guia.puedeResponder("¿cual es el siguiente paso?")).isTrue();
        assertThat(guia.puedeResponder("¡Siguiente sub-paso!")).isTrue();
    }

    /**
     * Las dos que citó la revisión del 1-10-2026: «¿qué documento falta?» no
     * contenía «qué falta» porque el sustantivo va en medio, y «qué necesito» no
     * estaba en la lista. Las dos iban al modelo: minutos en CPU.
     */
    @Test
    @DisplayName("reconoce «¿qué documento falta?» y «¿qué necesito?»")
    void reconoceLoQueFaltaDichoPorElDocumento() {
        assertThat(guia.puedeResponder("¿Qué documento falta?")).isTrue();
        assertThat(guia.puedeResponder("¿qué documentos faltan?")).isTrue();
        assertThat(guia.puedeResponder("qué necesito")).isTrue();
        assertThat(guia.puedeResponder("¿Qué documentos necesito?")).isTrue();
        assertThat(guia.puedeResponder("¿Qué hago?")).isTrue();
        assertThat(guia.puedeResponder("¿Cuál es el paso actual?")).isTrue();
        // La sugerencia rápida del panel (SupervisorPanel.tsx, QUICK_SUGGESTIONS)
        // y la que tenía hasta el 1-10-2026.
        assertThat(guia.puedeResponder("¿Qué me falta en el paso en el que estoy y cómo lo registro en SICOT?"))
                .isTrue();
        assertThat(guia.puedeResponder("¿Qué necesito hacer en el paso en el que estoy ahora mismo? Deme el paso a "
                + "paso completo: de dónde consigo cada insumo y cómo lo registro en SICOT.")).isTrue();
    }

    @Test
    @DisplayName("«qué necesito» y «qué hago» solo se atajan si son la pregunta entera")
    void lasPreguntasCortasSoloSiSonLaPreguntaEntera() {
        assertThat(guia.puedeResponder("¿qué necesito para renovar la póliza?")).isFalse();
        assertThat(guia.puedeResponder("¿Qué hago con la factura que llegó mal?")).isFalse();
    }

    /**
     * Una pregunta condicional trae la frase pero pide consejo sobre un caso.
     * Antes «¿qué tengo que hacer si el contratista se atrasa?» se contestaba
     * con la lista de pendientes, que no es lo que preguntó.
     */
    @Test
    @DisplayName("una pregunta condicional («si…») se deja al modelo aunque traiga la frase")
    void lasCondicionalesVanAlModelo() {
        assertThat(guia.puedeResponder("¿Qué tengo que hacer si el contratista se atrasa?")).isFalse();
        assertThat(guia.puedeResponder("¿qué me falta si ya cargué la foto?")).isFalse();
    }

    @Test
    @DisplayName("«qué falta» ya no se encuentra dentro de «porque faltan»")
    void queFaltaNoSeEncuentraDentroDeOtraPalabra() {
        assertThat(guia.puedeResponder("No firmé el acta porque faltan las fotos")).isFalse();
    }

    /**
     * El texto completo, letra por letra. Lo puede afirmar porque ya no lo
     * escribe un modelo, y es lo que ve el supervisor cuando el contrato está en
     * el sub-paso de la evidencia fotográfica: la guía tiene que decirle que ahí
     * SÍ se carga la foto, igual que la guía del tutorial (guiaSubPaso.ts).
     */
    @Test
    @DisplayName("en el sub-paso de la foto, el texto exacto dice cómo cargar la evidencia")
    void elTextoExactoEnElSubPasoDeLaFoto() {
        List<EtapaResponse> etapas = List.of(
                etapa(3, "INSPECCIÓN — Monitoreo y Ejecución", EstadoEtapa.EN_CURSO, 25, List.of(
                        sub("3.1", "Verificación física de la entrega en bodega", null,
                                EstadoSubetapa.COMPLETADA, "Supervisor"),
                        sub("3.2", "Carga de evidencia fotográfica georreferenciada",
                                "Evidencia fotográfica con georreferenciación activa.",
                                EstadoSubetapa.EN_CURSO, "Supervisor"),
                        sub("3.3", "Comparación cantidad/calidad vs. ficha técnica", null,
                                EstadoSubetapa.PENDIENTE, "Supervisor"),
                        sub("3.4", "Firma del Informe de Supervisión (GCCON-F-031)", null,
                                EstadoSubetapa.PENDIENTE, "Supervisor"))));

        assertThat(guia.responder(etapas).orElseThrow()).isEqualTo("""
                Está en el paso 3: INSPECCIÓN — Monitoreo y Ejecución (25% completado).

                Lo que le falta aquí:

                3.2 Carga de evidencia fotográfica georreferenciada  ← en curso
                3.3 Comparación cantidad/calidad vs. ficha técnica
                3.4 Firma del Informe de Supervisión (GCCON-F-031)

                Empiece por 3.2: Carga de evidencia fotográfica georreferenciada. Evidencia fotográfica con \
                georreferenciación activa. Responsable: Supervisor.

                En SICOT: tome la foto con «Tomar foto de la entrega» o elija una con «Elegir una foto», pulse \
                «Cargar evidencia» y, cuando aparezca como cargada, marque el sub-paso como completado.

                Si necesita detalle de alguno de estos sub-pasos —qué documento sirve de soporte, de dónde sale \
                un insumo— pregúnteme por él y se lo explico.""");
    }

    @Test
    @DisplayName("dice qué botón pulsar según el sub-paso: firmar, cargar la foto o marcar completado")
    void diceQueBotonPulsarSegunElSubPaso() {
        assertThat(GuiaDelPasoActual.comoSeRegistra(
                sub("2.7", "Firma del Acta de Inicio (GCCON-F-018)", null, EstadoSubetapa.PENDIENTE, "Supervisor")))
                .isEqualTo("En SICOT: cuando tenga lo necesario, pulse «Firmar documento». SICOT arma «Acta de "
                        + "Inicio» (GCCON-F-018) con los datos exactos del contrato, y usted lo revisa y lo firma con "
                        + "su firma electrónica.");
        // La Certificación no tiene código oficial: no se le pone ninguno.
        assertThat(GuiaDelPasoActual.comoSeRegistra(
                sub("5.3", "Firma de la Certificación de cumplimiento", null, EstadoSubetapa.PENDIENTE, "Supervisor")))
                .isEqualTo("En SICOT: cuando tenga lo necesario, pulse «Firmar documento». SICOT arma «Certificación "
                        + "de cumplimiento» con los datos exactos del contrato, y usted lo revisa y lo firma con su "
                        + "firma electrónica.");
        assertThat(GuiaDelPasoActual.comoSeRegistra(
                sub("2.6", "Registro de garantías vigentes", null, EstadoSubetapa.PENDIENTE, "Unidad de Contratación")))
                .isEqualTo("En SICOT: este sub-paso lo realiza Unidad de Contratación; márquelo como completado "
                        + "cuando le confirmen que está hecho.");
        assertThat(GuiaDelPasoActual.comoSeRegistra(
                sub("2.2", "Verificación de datos del contratista y NIT", null, EstadoSubetapa.PENDIENTE, "Supervisor")))
                .isEqualTo("En SICOT: cuando lo haya hecho, márquelo como completado.");
    }

    private static SubetapaResponse sub(String codigo, String nombre, String descripcion,
                                        EstadoSubetapa estado, String responsable) {
        return new SubetapaResponse(1L, codigo, nombre, descripcion, estado, responsable);
    }
}
