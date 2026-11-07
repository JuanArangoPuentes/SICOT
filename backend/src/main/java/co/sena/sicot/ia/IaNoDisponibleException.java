package co.sena.sicot.ia;

/** El servicio de IA local (Ollama) no respondió. Nunca se atrapa para fingir un resultado. */
public class IaNoDisponibleException extends RuntimeException {

    /**
     * Por qué no hubo respuesta. Quien atrapa la excepción para seguir sin la
     * IA (la generación de documentos) se lo dice al supervisor con la causa
     * verdadera: con un solo motivo, «el Copiloto no respondió a tiempo»
     * cubría también un Ollama apagado o un modelo sin descargar, que fallan
     * en milisegundos, y el supervisor reintentaba creyendo que el equipo
     * estaba lento en vez de avisar a sistemas (auditoría del 02-10-2026).
     */
    public enum Causa {
        /** Ollama no contesta, contesta con error o sin texto. */
        NO_DISPONIBLE,
        /** El modelo respondía, pero más despacio que el tiempo límite. */
        TIEMPO_AGOTADO,
        /** El modelo llegó al tope de tokens de la llamada y su texto quedó cortado. */
        RESPUESTA_CORTADA
    }

    private final Causa causa;

    public IaNoDisponibleException(String message) {
        this(Causa.NO_DISPONIBLE, message, null);
    }

    public IaNoDisponibleException(String message, Throwable cause) {
        this(Causa.NO_DISPONIBLE, message, cause);
    }

    public IaNoDisponibleException(Causa causa, String message, Throwable cause) {
        super(message, cause);
        this.causa = causa;
    }

    public Causa getCausa() {
        return causa;
    }
}
