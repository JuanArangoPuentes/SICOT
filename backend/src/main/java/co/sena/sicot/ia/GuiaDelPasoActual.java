package co.sena.sicot.ia;

import co.sena.sicot.dto.etapa.EtapaResponse;
import co.sena.sicot.dto.etapa.SubetapaResponse;
import co.sena.sicot.entity.enums.EstadoSubetapa;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Responde «¿en qué paso voy y qué hago ahora?» sin pasar por el modelo de IA.
 *
 * <h2>Por qué existe</h2>
 * Porque el sistema ya sabe la respuesta. La etapa en curso, los subpasos
 * pendientes y el porcentaje de avance están en la base; pedirle a un modelo de
 * lenguaje que los lea del prompt y los repita es pedirle lo único que no
 * garantiza —sostener hechos— para obtener algo que el código puede componer
 * exacto. Es la misma decisión que ADR-008 tomó con el resumen periódico.
 *
 * <h2>Lo que se midió el 14 de septiembre de 2026</h2>
 * A la pregunta «acabo de recibir este contrato, ¿qué tengo que hacer en el paso
 * en el que está ahora?», sobre un contrato que estaba en la <b>etapa 1</b>:
 *
 * <table border="1">
 *   <caption>Etapa que indicó cada modelo</caption>
 *   <tr><th>Modelo</th><th>Tiempo</th><th>Respondió</th></tr>
 *   <tr><td>qwen2.5:1.5b</td><td>45,4 s</td><td>etapa 4 — <b>falso</b></td></tr>
 *   <tr><td>qwen2.5:3b</td><td>90,7 s</td><td>etapa 4 — <b>falso</b></td></tr>
 *   <tr><td>qwen2.5:7b</td><td>158,4 s</td><td>etapa 1 — correcto</td></tr>
 * </table>
 *
 * <p>Los dos modelos pequeños copiaron el «paso 4» de un ejemplo de estilo que
 * el propio prompt incluye, pese a la advertencia expresa de no copiar sus
 * datos. Al recortar ese ejemplo el de 3B dejó de decir «4»… y pasó a decir
 * «2», que sigue siendo falso. No era un problema de redacción del prompt: era
 * de capacidad.
 *
 * <p><b>En supervisión de contratos, un asistente que manda al paso equivocado
 * con seguridad es peor que no tener asistente.</b> De ahí esta clase.
 *
 * <h2>Qué gana el proyecto</h2>
 * La respuesta pasa de ~150 s a microsegundos, es correcta por construcción, sus
 * pruebas pueden afirmar el texto exacto, y funciona en un equipo sin Ollama.
 * Al modelo le quedan las preguntas abiertas —«¿de dónde saco la póliza?»,
 * «¿qué pasa si el contratista se atrasa?»—, que es donde sí aporta y donde no
 * se le está pidiendo repetir cifras.
 */
@Component
public class GuiaDelPasoActual {

    /**
     * Frases que indican que el supervisor pregunta por su situación actual.
     *
     * <p>El encaminamiento es por palabras y no por otro modelo a propósito: si
     * decidir cuándo NO usar el modelo dependiera de un modelo, volveríamos al
     * mismo problema una capa más arriba. Una lista de frases es auditable, se
     * revisa en un <i>pull request</i> y falla de forma predecible: ante la
     * duda, no encaja y la pregunta sigue su camino normal hacia el modelo.
     */
    private static final List<String> SENALES = List.of(
            "en que paso", "en qué paso", "en que etapa", "en qué etapa",
            "que paso sigue", "qué paso sigue", "cual es el siguiente paso",
            "cuál es el siguiente paso", "que sigue", "qué sigue",
            "que tengo que hacer", "qué tengo que hacer", "que debo hacer",
            "qué debo hacer", "que hago ahora", "qué hago ahora",
            "por donde empiezo", "por dónde empiezo", "en que voy", "en qué voy",
            "cual es mi paso", "cuál es mi paso", "que falta", "qué falta",
            // ── Variantes con pronombre ──────────────────────────────────
            //
            // Medido el 15 de septiembre de 2026 contra el sistema real: a la
            // pregunta «¿Qué me falta para cerrar el paso en el que estoy?»,
            // con el contrato en el paso 3, el copiloto respondió que faltaba
            // cerrar el PASO 4 y mandó al supervisor a los sub-pasos 4.1 y 4.2.
            //
            // El atajo existe justo para que esa pregunta no llegue al modelo,
            // y no la atajó por un detalle de literalidad: la lista traía
            // «qué falta», y «qué ME falta» no contiene esa subcadena. La
            // pregunta cayó en la ruta del modelo, que es la que ya se había
            // medido como poco fiable para identificar la etapa.
            //
            // Es la misma lección que motivó esta clase, aplicada a su propia
            // puerta de entrada: no basta con tener el camino determinista, hay
            // que asegurarse de que la forma en que la gente pregunta de verdad
            // entra por él. Se cubren los clíticos, no cada frase entera: «me
            // falta» atrapa «¿qué me falta?», «me falta para cerrar» y demás.
            "me falta", "me hace falta", "me queda pendiente",
            "falta para cerrar", "falta para terminar", "falta por hacer",
            "me toca", "me corresponde",
            // ── Órdenes cortas ───────────────────────────────────────────
            //
            // 25-09-2026: en la prueba del APK el supervisor escribió
            // «siguiente paso» a secas. La lista traía «cuál es el siguiente
            // paso» pero no la orden sola, así que fue al modelo: esperó 120 s a
            // un precalentado y 163 s más a la respuesta, en un portátil sin
            // tarjeta gráfica. La respuesta era esta misma plantilla.
            "siguiente paso", "paso siguiente", "siguiente sub-paso", "siguiente subpaso",
            "proximo paso", "próximo paso");
            // «qué hago» o «continuar» a secas se dejaron fuera a propósito:
            // también aparecen en preguntas abiertas («¿qué hago si no llega la
            // póliza?») que esta plantilla contestaría mal.

    /**
     * ¿Es una pregunta que se puede contestar sin modelo?
     *
     * <p>Deliberadamente conservador. Si la pregunta trae además otra cosa
     * («¿qué debo hacer y de dónde saco el formato GCCON-F-018?»), el modelo la
     * atiende igual: aquí solo se atajan las preguntas cuya respuesta completa
     * es el estado del contrato.
     */
    public boolean puedeResponder(String pregunta) {
        if (pregunta == null || pregunta.isBlank()) {
            return false;
        }
        String normalizada = pregunta.toLowerCase(Locale.ROOT).trim();
        // Una pregunta larga casi siempre trae contexto o matices que esta
        // plantilla no cubre; se deja pasar al modelo en vez de contestar de
        // más. El umbral es generoso: "acabo de recibir este contrato, ¿qué
        // tengo que hacer exactamente en el paso en el que está ahora?" cabe.
        if (normalizada.length() > 200) {
            return false;
        }
        return SENALES.stream().anyMatch(normalizada::contains);
    }

    /**
     * Compone la respuesta a partir del estado real de las etapas.
     *
     * @return el texto, o {@link Optional#empty()} si no hay etapas sobre las
     *         que decir nada — en cuyo caso quien llama debe seguir su camino
     *         normal en vez de inventar una respuesta.
     */
    public Optional<String> responder(List<EtapaResponse> etapas) {
        if (etapas == null || etapas.isEmpty()) {
            return Optional.empty();
        }

        Optional<EtapaResponse> enCurso = etapas.stream()
                .filter(e -> tieneSubpasosPendientes(e))
                .findFirst();

        if (enCurso.isEmpty()) {
            return Optional.of("""
                    Ya están completados los %d pasos del contrato. No queda ningún subpaso pendiente.

                    Si necesita revisar algo de un paso ya cerrado, dígame cuál y se lo consulto."""
                    .formatted(etapas.size()));
        }

        EtapaResponse etapa = enCurso.get();
        List<SubetapaResponse> pendientes = etapa.subEtapas().stream()
                .filter(s -> s.estado() != EstadoSubetapa.COMPLETADA)
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("Está en el paso %d: %s".formatted(etapa.numero(), etapa.nombre()));
        if (etapa.porcentaje() > 0) {
            sb.append(" (%d%% completado)".formatted(etapa.porcentaje()));
        }
        sb.append(".\n\nLo que le falta aquí:\n");

        for (SubetapaResponse s : pendientes) {
            sb.append("\n%s %s".formatted(s.codigo(), s.nombre()));
            if (s.estado() == EstadoSubetapa.EN_CURSO) {
                sb.append("  ← en curso");
            }
        }

        SubetapaResponse siguiente = pendientes.get(0);
        sb.append("\n\nEmpiece por %s: %s.".formatted(siguiente.codigo(), siguiente.nombre()));
        // La descripción y el responsable salen de la plantilla GCCON-P-010: es
        // lo que el supervisor necesita para saber qué hacer, sin modelo.
        if (siguiente.descripcion() != null && !siguiente.descripcion().isBlank()) {
            sb.append(" ").append(siguiente.descripcion().strip());
        }
        if (siguiente.responsable() != null && !siguiente.responsable().isBlank()) {
            sb.append(" Responsable: %s.".formatted(siguiente.responsable().strip()));
        }
        sb.append("\n\nSi necesita detalle de alguno de estos subpasos —qué documento sirve de soporte, "
                + "de dónde sale un insumo— pregúnteme por él y se lo explico.");
        return Optional.of(sb.toString());
    }

    private static boolean tieneSubpasosPendientes(EtapaResponse etapa) {
        return etapa.subEtapas() != null && etapa.subEtapas().stream()
                .anyMatch(s -> s.estado() != EstadoSubetapa.COMPLETADA);
    }
}
