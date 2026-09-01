package co.sena.sicot.exception;

/**
 * El cliente superó un límite de frecuencia y debe esperar.
 *
 * <p>Cubre los dos frenos del sistema: el bloqueo por intentos fallidos de
 * inicio de sesión y el límite de peticiones al Copiloto de IA. Los dos
 * respondían <b>400</b>, que es engañoso —no hay nada malo en la petición, solo
 * llegó demasiado pronto— y además impide que un cliente reaccione de forma
 * automática. <b>429 Too Many Requests</b> es el código que corresponde, y
 * {@code segundosDeEspera} alimenta la cabecera {@code Retry-After} para que el
 * cliente sepa cuánto esperar en vez de reintentar a ciegas.
 */
public class DemasiadasSolicitudesException extends RuntimeException {

    private final long segundosDeEspera;

    public DemasiadasSolicitudesException(String message, long segundosDeEspera) {
        super(message);
        this.segundosDeEspera = Math.max(1, segundosDeEspera);
    }

    public long getSegundosDeEspera() {
        return segundosDeEspera;
    }
}
