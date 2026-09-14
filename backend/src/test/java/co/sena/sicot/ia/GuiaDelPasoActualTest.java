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
}
