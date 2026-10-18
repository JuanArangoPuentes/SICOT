package co.sena.sicot.ia;

/**
 * Cómo se firma en SICOT un documento formal, dicho una sola vez para todo lo
 * que lo explica: el prompt del Copiloto, la ficha de cada documento, la guía
 * del paso y las respuestas a «firma el acta».
 *
 * <h2>Por qué una sola constante</h2>
 * Hasta el 2-10-2026 lo decían cuatro textos distintos, y ninguno describía la
 * interfaz: que «lo que SICOT no sabe (facturas, pólizas, pagos) queda como dato
 * pendiente», cuando la interfaz se los pide al supervisor antes de armar el
 * documento, y que él «lo revisa antes de firmarlo» sin decir dónde. El
 * Copiloto y la guía del tutorial se contradecían en el mismo sub-paso, que es
 * lo que el §29 de las instrucciones del proyecto prohíbe.
 *
 * <p>El texto sigue, paso por paso, el flujo acordado para la interfaz
 * («Firmar documento» → datos que faltan → «Revisar antes de firmar» →
 * «Firmar» o «Cancelar»). Si ese flujo cambia, se cambia aquí y cambia en
 * todas partes a la vez.
 */
final class FlujoDeFirma {

    private FlujoDeFirma() {
    }

    /*
     * Los dos empiezan en minúscula porque siempre van tras dos puntos («Cómo
     * se hace en SICOT: en el sub-paso…», «SICOT arma el acta con los datos
     * exactos del contrato: le pide…»).
     */

    /** El flujo completo, para la ficha del documento, las órdenes y el prompt del modelo. */
    static final String COMPLETO = "en el sub-paso del documento, pulse «Firmar documento». SICOT le pide los datos "
            + "que el contrato no tiene (y sus notas, si el formato lleva observaciones), arma el borrador y se lo "
            + "muestra en «Revisar antes de firmar»; ahí puede abrirlo con «Ver borrador completo (PDF)» y, si el "
            + "Copiloto redactó sus notas, ver qué cambió frente a lo que usted escribió. Lo que deje sin diligenciar "
            + "sale marcado como «dato pendiente». La firma solo se aplica cuando usted pulsa «Firmar»; «Cancelar» "
            + "cierra sin firmar y el borrador queda pendiente.";

    /** La versión corta, para cuando la frase ya dijo qué documento arma SICOT. */
    static final String CORTO = "le pide los datos que el contrato no tiene, le muestra el borrador en «Revisar "
            + "antes de firmar» y solo se firma cuando usted pulsa «Firmar».";
}
