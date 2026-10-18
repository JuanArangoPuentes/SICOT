package co.sena.sicot.ia;

import co.sena.sicot.exception.DemasiadasSolicitudesException;

/**
 * El Copiloto estaba atendiendo otras consultas y no había cupo: es el 429 de
 * la concurrencia, distinto del tope de consultas por minuto de un usuario.
 * Sigue siendo un {@link DemasiadasSolicitudesException} para la API; la
 * subclase existe para que quien sigue sin la IA diga la causa verdadera
 * («estaba atendiendo otra consulta») y no la de otro freno.
 */
public class IaOcupadaException extends DemasiadasSolicitudesException {

    public IaOcupadaException(String message, long segundosDeEspera) {
        super(message, segundosDeEspera);
    }
}
