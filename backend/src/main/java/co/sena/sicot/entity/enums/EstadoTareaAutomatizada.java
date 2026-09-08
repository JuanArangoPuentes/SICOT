package co.sena.sicot.entity.enums;

/**
 * En qué punto de su vida está una tarea de la cola.
 *
 * <p>La distinción entre {@link #FALLIDA} y {@link #DESCARTADA} es deliberada y
 * es la que sostiene la utilidad de la pantalla de operación. «Falló» significa
 * que algo va mal y alguien debería mirarlo. «Descartada» significa que la tarea
 * dejó de tener sentido —el correo no está configurado, el contrato se borró—
 * y no hay nada que arreglar. Mezclarlas convierte la lista de fallos en ruido,
 * y una lista de fallos que siempre tiene entradas es una lista que nadie mira.
 */
public enum EstadoTareaAutomatizada {

    /** Esperando su turno. Solo es elegible cuando {@code ejecutar_en} ya pasó. */
    PENDIENTE,

    /** Reclamada por un trabajador. Nadie más puede tomarla. */
    EN_PROCESO,

    /** Terminó bien. */
    COMPLETADA,

    /** Agotó los reintentos. Requiere intervención humana; el motivo está en {@code ultimo_error}. */
    FALLIDA,

    /** Dejó de tener sentido ejecutarla. No es un error y no debe alarmar a nadie. */
    DESCARTADA
}
