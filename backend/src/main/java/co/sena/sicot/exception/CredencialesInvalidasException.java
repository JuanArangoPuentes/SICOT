package co.sena.sicot.exception;

/**
 * Correo o contraseña incorrectos, o cuenta desactivada.
 *
 * <p>Existe para que ese caso deje de responder <b>400 Bad Request</b>. Un 400
 * dice «mandaste mal la petición», y la petición estaba perfectamente formada:
 * lo que falla es la autenticación, que es exactamente lo que significa
 * <b>401</b>. Con el 400 anterior, un cliente no podía distinguir por código de
 * estado entre un cuerpo malformado, una clave equivocada y un bloqueo por
 * intentos, y tenía que leer el texto del mensaje para decidir qué hacer.
 *
 * <p>El mensaje es deliberadamente idéntico para «no existe ese correo» y para
 * «la contraseña no es esa»: distinguirlos le confirmaría a quien prueba
 * correos cuáles corresponden a cuentas reales.
 */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException(String message) {
        super(message);
    }
}
