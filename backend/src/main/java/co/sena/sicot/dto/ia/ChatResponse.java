package co.sena.sicot.dto.ia;

/**
 * Respuesta del Copiloto.
 *
 * @param respuesta el texto que ve el supervisor
 * @param fuente    quién lo escribió: {@link Fuente#SISTEMA} si lo compuso SICOT
 *                  con sus datos (órdenes, fichas, guía del paso, datos del
 *                  contrato) y {@link Fuente#MODELO} si lo escribió Ollama. La
 *                  interfaz no presenta como IA lo que es una plantilla, y no
 *                  manda de vuelta en el historial un texto fijo que el modelo
 *                  no necesita releer.
 * @param accion    la pantalla que el supervisor puede abrir desde la respuesta,
 *                  o {@code null}. Ninguna acción firma, marca, genera ni
 *                  modifica nada: la interfaz la muestra como un botón y solo
 *                  abre esa pantalla cuando él lo toca.
 */
public record ChatResponse(String respuesta, Fuente fuente, Accion accion) {

    public enum Fuente { SISTEMA, MODELO }

    public enum TipoAccion {
        IR_A_PASO, IR_A_SUBPASO, ABRIR_DOCUMENTO, ABRIR_EVIDENCIA, MOSTRAR_ALERTAS, MOSTRAR_DOCUMENTOS,
        DESCARGAR_DOCUMENTO, IR_A_CONFIGURACION
    }

    /**
     * Qué abrir. Los campos que no aplican van en {@code null}.
     *
     * @param subpaso       código «N.M» de la subetapa
     * @param documentoTipo clave de {@code PlantillaDocumentoIA.CATALOGO}
     * @param documentoId   solo en {@link TipoAccion#DESCARGAR_DOCUMENTO}, y
     *                      siempre de un documento que existe
     * @param etiqueta      el texto del botón
     */
    public record Accion(TipoAccion tipo, Integer paso, String subpaso, String documentoTipo, Long documentoId,
                         String etiqueta) {
    }

    public static ChatResponse delSistema(String respuesta) {
        return new ChatResponse(respuesta, Fuente.SISTEMA, null);
    }

    public static ChatResponse delSistema(String respuesta, Accion accion) {
        return new ChatResponse(respuesta, Fuente.SISTEMA, accion);
    }

    public static ChatResponse delModelo(String respuesta) {
        return new ChatResponse(respuesta, Fuente.MODELO, null);
    }
}
