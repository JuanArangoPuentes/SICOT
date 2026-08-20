package co.sena.sicot.ia;

/** El servicio de IA local (Ollama) no respondió. Nunca se atrapa para fingir un resultado. */
public class IaNoDisponibleException extends RuntimeException {

    public IaNoDisponibleException(String message) {
        super(message);
    }

    public IaNoDisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
