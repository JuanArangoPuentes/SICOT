package co.sena.sicot.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Calcula la huella SHA-256 de los bytes de un documento.
 *
 * <p>Es lo que ata una firma electrónica al contenido concreto que se firmó.
 * Sin ella, "firmado" era solo una etiqueta en una columna: los bytes podían
 * cambiar después y nada lo delataba. Ver
 * {@code V13__huella_de_integridad_en_la_firma.sql} para el razonamiento
 * completo y para el alcance de lo que esto SÍ y NO garantiza.
 *
 * <p>Formato fijo: hexadecimal en minúsculas, 64 caracteres. La base lo exige
 * con una restricción CHECK, así que cualquier cambio aquí (Base64, mayúsculas,
 * otro algoritmo) falla al escribir en vez de guardar en silencio algo que
 * después no verifica.
 */
public final class HuellaDeDocumento {

    private static final String ALGORITMO = "SHA-256";

    private HuellaDeDocumento() {
    }

    /**
     * @param contenido bytes del archivo; {@code null} devuelve {@code null}
     *                  (un documento sin contenido no tiene nada que firmar).
     */
    public static String calcular(byte[] contenido) {
        if (contenido == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITMO);
            return HexFormat.of().formatHex(digest.digest(contenido));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda implementación de la plataforma Java
            // desde hace dos décadas. Si falta, el entorno de ejecución está roto
            // de una forma que no tiene sentido intentar manejar aquí.
            throw new IllegalStateException("El entorno de ejecución no ofrece " + ALGORITMO + ".", e);
        }
    }

    /**
     * Comparación en tiempo constante entre la huella registrada y la
     * recalculada. {@code MessageDigest.isEqual} y no {@code String.equals}
     * porque la comparación de cadenas de Java corta en el primer carácter
     * distinto, y ese tiempo distinto es información filtrada.
     */
    public static boolean coincide(String huellaRegistrada, String huellaActual) {
        if (huellaRegistrada == null || huellaActual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                huellaRegistrada.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                huellaActual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
