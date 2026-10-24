package co.sena.sicot.ia;

import co.sena.sicot.dto.etapa.EtapaResponse;
import co.sena.sicot.dto.etapa.SubetapaResponse;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quita de una respuesta del modelo la indicación de cargar en SICOT algo que
 * SICOT no recibe, y pone en su lugar cómo se registra de verdad ese sub-paso.
 *
 * <h2>Por qué existe</h2>
 * Medido en vivo el 18-10-2026 contra el contrato CO1.PCCNTR.10057820 con
 * qwen2.5:7b: a «¿qué reviso al recibir la factura electrónica?» respondió
 * «envíe la factura a SICOT», y a «¿dónde reviso que las pólizas estén
 * vigentes?», «cargue la evidencia en SICOT pulsando «Cargar»». En el 4.2 y el
 * 5.1 SICOT no recibe ningún archivo: el supervisor verifica afuera y marca el
 * sub-paso. El prompt ya lo decía dos veces —en «Cómo se registra el avance»
 * y en el recordatorio final— y el modelo lo seguía mezclando con el 2.1, donde
 * sí se carga el acta. Quien lo siga busca un botón que no existe.
 *
 * <h2>Por qué así y no un atajo sin modelo</h2>
 * Lo útil de esas respuestas —qué mirar en la factura, con quién confirmar—
 * lo aporta el modelo, y la descripción del sub-paso en SICOT es una línea: un
 * atajo contestaría menos. Lo único falso es la carga, y eso sí se reconoce
 * sin ambigüedad: el sub-paso sale de lo que <b>pregunta</b> el supervisor, no
 * de lo que escribió el modelo, y si ese sub-paso recibe archivos lo decide la
 * misma regla que {@link GuiaDelPasoActual#comoSeRegistra}.
 *
 * <h2>Cuándo no toca nada</h2>
 * Hacen falta las dos cosas: un insumo reconocible en la pregunta cuyo
 * sub-paso no recibe archivos, y una frase que mande a cargar algo en SICOT.
 * Una frase que nombre el acta, las fotos o los sub-pasos donde sí se carga
 * (2.1, 3.1, 3.2) se deja, porque puede ser un recordatorio correcto del paso en
 * curso; y una que niegue («no tiene que cargar nada») también.
 */
final class CorrectorDeCargas {

    /** Un insumo que se verifica fuera de SICOT y cómo se reconoce su sub-paso por el nombre. */
    private record Insumo(Pattern enLaPregunta, String enElNombreDelSubpaso) {
    }

    // Sobre el texto ya normalizado (minúsculas, sin tildes ni signos): ver
    // PreguntaNormalizada.normalizar. Los nombres de los sub-pasos son los del
    // proceso real (V20), también normalizados, y completos a propósito: los
    // contratos de antes del 15-10-2026 tienen «aprobación de garantías» en el
    // 1.4 o «afiliaciones a seguridad social» en el 2.5, que caerían antes.
    private static final List<Insumo> INSUMOS = List.of(
            new Insumo(Pattern.compile("\\b(factura|facturas|facturacion|fev)\\b"), "factura electronica"),
            new Insumo(Pattern.compile("\\b(pila|planilla|planillas|seguridad social|aportes)\\b"),
                    "aportes a seguridad social"),
            new Insumo(Pattern.compile("\\b(poliza|polizas|garantia|garantias)\\b"), "vigencia de garantias"),
            new Insumo(Pattern.compile("\\b(orden de pago|crp|registro presupuestal)\\b"), "revision de orden de pago"));

    private static final Pattern MANDA_A_CARGAR = Pattern.compile(String.join("|",
            "\\b(cargue\\w*|cargar\\w*|suba|subala|subalo|subir\\w*|adjunt\\w*|anex\\w*)\\b.*\\bsicot\\b",
            "\\bsicot\\b.*\\b(cargue\\w*|cargar\\w*|suba|subala|subalo|subir\\w*|adjunt\\w*|anex\\w*)\\b",
            "\\b(envie|enviela|envielo|envielas|envielos|enviar\\w*)\\b.*\\b(a|en) sicot\\b"));

    /** Lo que sí se carga, o una negación: la frase puede ser correcta y se deja. */
    private static final Pattern SE_DEJA = Pattern.compile(
            "\\b(acta|actas|foto|fotos|fotografica|2 1|3 1|3 2|no|ni|nada|ningun|ninguna)\\b");

    /** Una frase, un salto de línea o una puntuación suelta: entre las tres cubren todo el texto. */
    private static final Pattern FRASE = Pattern.compile("[^.!?\\n]+[.!?]*[ \\t]*|\\n|[.!?]+[ \\t]*");

    private CorrectorDeCargas() {
    }

    /**
     * @return la respuesta sin las frases que mandan a cargar en SICOT lo que
     *     SICOT no recibe, más cómo se registra ese sub-paso; o la respuesta tal
     *     cual si no hay nada que corregir.
     */
    static String corregir(String respuesta, String pregunta, List<EtapaResponse> etapas) {
        if (respuesta == null || respuesta.isBlank()) {
            return respuesta;
        }
        Optional<SubetapaResponse> subpaso = subpasoDelInsumo(PreguntaNormalizada.normalizar(pregunta), etapas)
                .filter(sub -> !recibeArchivos(sub));
        if (subpaso.isEmpty()) {
            return respuesta;
        }
        StringBuilder corregida = new StringBuilder();
        boolean quitoAlgo = false;
        Matcher frases = FRASE.matcher(respuesta);
        while (frases.find()) {
            String frase = frases.group();
            String normalizada = PreguntaNormalizada.normalizar(frase);
            if (MANDA_A_CARGAR.matcher(normalizada).find() && !SE_DEJA.matcher(normalizada).find()) {
                quitoAlgo = true;
                continue;
            }
            corregida.append(frase);
        }
        if (!quitoAlgo) {
            return respuesta;
        }
        SubetapaResponse sub = subpaso.get();
        String nota = "En el %s (%s) SICOT no recibe ningún archivo. %s"
                .formatted(sub.codigo(), sub.nombre(), GuiaDelPasoActual.comoSeRegistra(sub));
        String limpia = corregida.toString().replaceAll("[ \\t]+\\n", "\n").replaceAll("[ \\t]{2,}", " ").strip();
        return limpia.isEmpty() ? nota : limpia + "\n\n" + nota;
    }

    private static Optional<SubetapaResponse> subpasoDelInsumo(String pregunta, List<EtapaResponse> etapas) {
        for (Insumo insumo : INSUMOS) {
            if (!insumo.enLaPregunta().matcher(pregunta).find()) {
                continue;
            }
            for (EtapaResponse etapa : etapas) {
                List<SubetapaResponse> subs = etapa.subEtapas() == null ? List.of() : etapa.subEtapas();
                for (SubetapaResponse sub : subs) {
                    if (PreguntaNormalizada.normalizar(sub.nombre()).contains(insumo.enElNombreDelSubpaso())) {
                        return Optional.of(sub);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** La misma regla que GuiaDelPasoActual.comoSeRegistra: documento formal, acta firmada o fotos. */
    static boolean recibeArchivos(SubetapaResponse sub) {
        return FichaDeDocumentoFormal.delSubpaso(sub.codigo()).isPresent()
                || FichaDeDocumentoFormal.ACTA_FIRMADA_Y_ENVIO.equals(sub.documentosRequeridos())
                || GuiaDelPasoActual.SUBPASOS_CON_EVIDENCIA_FOTOGRAFICA.contains(sub.codigo());
    }
}
