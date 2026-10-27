package co.sena.sicot.service;

import java.security.SecureRandom;

/**
 * La clave con la que entra por primera vez un supervisor al que Gestión acaba
 * de poner en un contrato.
 *
 * <h2>Por qué no la escribe quien invita</h2>
 * Si la eligiera Gestión, en el Centro acabarían siendo todas la misma —la
 * del Centro, la del año, el número del contrato—, y quien invita conocería la
 * clave de quien firma documentos oficiales. Se genera aquí, al azar, y solo
 * la ve el supervisor en su correo.
 *
 * <h2>El alfabeto</h2>
 * Sin los caracteres que se confunden al leerlos en voz alta o copiarlos de un
 * correo impreso: la O y el 0, la l minúscula, la I mayúscula y el 1. Una clave
 * que hay que dictar por teléfono y se teclea mal no es más segura, es más
 * frágil. Quedan 58 símbolos; con 16 de largo son unos 94 bits de entropía, de
 * sobra para una clave que además se cambia al entrar.
 */
final class ClaveGenerada {

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int LARGO = 16;
    private static final SecureRandom AZAR = new SecureRandom();

    private ClaveGenerada() {
    }

    static String nueva() {
        StringBuilder clave = new StringBuilder(LARGO);
        for (int i = 0; i < LARGO; i++) {
            clave.append(ALFABETO.charAt(AZAR.nextInt(ALFABETO.length())));
        }
        return clave.toString();
    }
}
