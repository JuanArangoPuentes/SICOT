package co.sena.sicot.security;

import java.nio.charset.StandardCharsets;

/**
 * Tope de longitud que BCrypt impone a una contraseña: 72 bytes en UTF-8.
 *
 * <p>No es una política de SICOT sino un límite del algoritmo, y se cuenta en
 * <b>bytes</b>, no en caracteres: cada tilde o eñe ocupa dos. Desde
 * spring-security-crypto 6.5, {@code BCryptPasswordEncoder.encode} lanza
 * {@code IllegalArgumentException} si se pasa del tope, y esa excepción caía en
 * el manejador genérico: una frase de paso de 80 caracteres, o una de 40 con
 * tildes, salía como «error interno del servidor» sin decir qué corregir.
 *
 * <p>Quien vaya a codificar una contraseña elegida por una persona lo comprueba
 * antes con {@link #cabe(String)} y responde con un mensaje que se pueda
 * atender.
 */
public final class LimiteDeBcrypt {

    public static final int MAXIMO_BYTES = 72;

    public static final String MENSAJE =
            "La contraseña no puede superar " + MAXIMO_BYTES + " bytes (las tildes y eñes cuentan doble). "
                    + "Use una más corta.";

    private LimiteDeBcrypt() {
    }

    public static boolean cabe(String password) {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= MAXIMO_BYTES;
    }
}
