package co.sena.sicot.ia;

import co.sena.sicot.dto.etapa.EtapaResponse;
import co.sena.sicot.dto.etapa.SubetapaResponse;
import co.sena.sicot.entity.enums.EstadoSubetapa;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

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
     *
     * <p>Se escriben una sola vez y en castellano corriente: {@link PreguntaNormalizada}
     * les quita tildes y signos igual que a la pregunta. Hasta el 1-10-2026 cada
     * frase iba dos veces, con tilde y sin ella, y la que faltaba se escapaba al
     * modelo.
     */
    private static final Set<String> SENALES = PreguntaNormalizada.normalizarTodas(
            "en qué paso", "en qué etapa", "qué paso sigue", "cuál es el siguiente paso", "qué sigue",
            "qué tengo que hacer", "qué debo hacer", "qué hago ahora", "por dónde empiezo", "en qué voy",
            "cuál es mi paso", "qué falta",
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
            "me falta", "me hace falta", "queda pendiente",
            "falta para cerrar", "falta para terminar", "falta por hacer",
            "me toca", "me corresponde",
            // ── Órdenes cortas ───────────────────────────────────────────
            //
            // 25-09-2026: en la prueba del APK el supervisor escribió
            // «siguiente paso» a secas. La lista traía «cuál es el siguiente
            // paso» pero no la orden sola, así que fue al modelo: esperó 120 s a
            // un precalentado y 163 s más a la respuesta, en un portátil sin
            // tarjeta gráfica. La respuesta era esta misma plantilla.
            "siguiente paso", "paso siguiente", "siguiente sub-paso", "siguiente subpaso", "próximo paso",
            // ── Lo que falta, dicho por el documento o por el paso ───────
            //
            // 1-10-2026: «¿qué documento falta?» no contenía «qué falta» (el
            // sustantivo va en medio) y se iba al modelo: minutos en CPU. Igual
            // «el paso en el que estoy», que es como la sugerencia rápida del
            // panel preguntaba por el paso actual.
            "qué necesito hacer", "qué documento falta", "qué documentos faltan", "documentos pendientes",
            "tengo pendiente", "hay pendiente", "paso en el que estoy", "paso en que estoy", "paso actual",
            "etapa actual");
            // «qué hago» o «continuar» como frase suelta dentro de una pregunta
            // se dejaron fuera a propósito: también aparecen en preguntas
            // abiertas («¿qué hago si no llega la póliza?») que esta plantilla
            // contestaría mal. Como pregunta COMPLETA sí son inequívocas: ver
            // PREGUNTAS_COMPLETAS.

    /**
     * Preguntas que solo se atajan si son la pregunta entera. «¿Qué necesito?»
     * a secas es el paso actual; «¿qué necesito para renovar la póliza?» es una
     * pregunta abierta que el modelo contesta mejor.
     */
    private static final Set<String> PREGUNTAS_COMPLETAS = PreguntaNormalizada.normalizarTodas(
            "qué necesito", "qué documentos necesito", "qué documento necesito", "qué hago", "qué hago aquí",
            "y ahora qué", "ahora qué hago", "siguiente");

    /**
     * Sub-pasos donde el supervisor carga fotos de la entrega. Es espejo de
     * {@code SUBETAPAS_CON_EVIDENCIA_FOTOGRAFICA} en
     * {@code frontend/src/data/contractFlow.ts}, igual que GcconP010Plantilla lo
     * es de STEPS_INITIAL: si allí cambia, aquí también, o la guía dirá «márquelo
     * como completado» en un sub-paso que pide la foto antes.
     */
    static final Set<String> SUBPASOS_CON_EVIDENCIA_FOTOGRAFICA = Set.of("3.1", "3.2");

    /**
     * ¿Es una pregunta que se puede contestar sin modelo?
     *
     * <p>Deliberadamente conservador. Una pregunta condicional («¿qué tengo que
     * hacer si el contratista se atrasa?») trae la frase, pero lo que pide es
     * consejo sobre un caso, no el estado del contrato: esa la atiende el
     * modelo.
     */
    public boolean puedeResponder(String pregunta) {
        PreguntaNormalizada p = PreguntaNormalizada.de(pregunta);
        if (p.estaVacia()) {
            return false;
        }
        // Una pregunta larga casi siempre trae contexto o matices que esta
        // plantilla no cubre; se deja pasar al modelo en vez de contestar de
        // más. El umbral es generoso: "acabo de recibir este contrato, ¿qué
        // tengo que hacer exactamente en el paso en el que está ahora?" cabe.
        if (p.largo() > 200 || p.tienePalabra("si")) {
            return false;
        }
        return p.contieneAlguna(SENALES) || p.esExactamenteAlguna(PREGUNTAS_COMPLETAS);
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
                    Ya están completados los %d pasos del contrato. No queda ningún sub-paso pendiente.

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
        sb.append("\n\n").append(comoSeRegistra(siguiente));
        sb.append("\n\nSi necesita detalle de alguno de estos sub-pasos —qué documento sirve de soporte, "
                + "de dónde sale un insumo— pregúnteme por él y se lo explico.");
        return Optional.of(sb.toString());
    }

    /**
     * Qué botón pulsar en SICOT para ese sub-paso. Es la misma regla que
     * {@code guiaDelSubPaso} en {@code frontend/src/data/guiaSubPaso.ts}: si la
     * guía del tutorial y esta dijeran cosas distintas del mismo sub-paso, el
     * supervisor no sabría a cuál creer.
     */
    static String comoSeRegistra(SubetapaResponse sub) {
        Optional<FichaDeDocumentoFormal.Documento> documento = FichaDeDocumentoFormal.delSubpaso(sub.codigo());
        if (documento.isPresent()) {
            FichaDeDocumentoFormal.Documento d = documento.get();
            return "En SICOT: cuando tenga lo necesario, pulse «Firmar documento». SICOT arma «%s»%s con los datos "
                    .formatted(d.nombre(), d.codigo() == null ? "" : " (" + d.codigo() + ")")
                    + "exactos del contrato, y usted lo revisa y lo firma con su firma electrónica.";
        }
        if (SUBPASOS_CON_EVIDENCIA_FOTOGRAFICA.contains(sub.codigo())) {
            return "En SICOT: tome la foto con «Tomar foto de la entrega» o elija una con «Elegir una foto», "
                    + "pulse «Cargar evidencia» y, cuando aparezca como cargada, marque el sub-paso como completado.";
        }
        String responsable = sub.responsable() == null ? "" : sub.responsable().strip();
        if (!responsable.isEmpty() && !responsable.toLowerCase(Locale.ROOT).contains("supervisor")) {
            return "En SICOT: este sub-paso lo realiza %s; márquelo como completado cuando le confirmen que está hecho."
                    .formatted(responsable);
        }
        return "En SICOT: cuando lo haya hecho, márquelo como completado.";
    }

    private static boolean tieneSubpasosPendientes(EtapaResponse etapa) {
        return etapa.subEtapas() != null && etapa.subEtapas().stream()
                .anyMatch(s -> s.estado() != EstadoSubetapa.COMPLETADA);
    }
}
