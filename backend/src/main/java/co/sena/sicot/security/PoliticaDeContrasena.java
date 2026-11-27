package co.sena.sicot.security;

import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Qué contraseña acepta SICOT, y por qué cada regla está aquí.
 *
 * <h2>Qué estaba mal</h2>
 * La única exigencia era {@code @Size(min = 8)} más «sin espacios en los
 * extremos». Es decir: <b>{@code Password123} era una contraseña válida en este
 * sistema</b>, y es exactamente la primera que prueba un rociado de
 * contraseñas.
 *
 * <p>Eso contradecía al propio sistema. {@code LoginAttemptService} documenta el
 * ataque con precisión —probar <i>una</i> contraseña común contra <i>muchas</i>
 * cuentas de un directorio institucional de correos predecibles
 * ({@code nombre.apellido@…})— y añade un contador por IP justamente para
 * frenarlo. Pero un límite de intentos solo sirve si hace falta más de un
 * intento: si la primera contraseña que se prueba es una que el sistema
 * permite, el límite nunca entra en juego. El control de frecuencia estaba; el
 * de calidad, no.
 *
 * <p>Había además una incoherencia interna: {@code AdministradorInicial} ya
 * exigía <b>12</b> caracteres a {@code SICOT_ADMIN_PASSWORD} desde el primer
 * día. La cuenta más poderosa del sistema tenía un listón más alto que las que
 * se crean después desde la propia aplicación.
 *
 * <h2>Las reglas, y las que a propósito NO están</h2>
 * <ol>
 *   <li><b>Doce caracteres como mínimo.</b> El mismo número que ya exigía el
 *       arranque, ahora en todos los caminos.</li>
 *   <li><b>No estar en la lista de bloqueo</b>
 *       ({@code security/contrasenas-prohibidas.txt}), comparando de forma
 *       normalizada: en minúsculas, sin acentos y sin los dígitos y signos del
 *       final. Así {@code Password123!}, {@code contraseña2026} y
 *       {@code Sena2024*} caen con una sola entrada cada una, en vez de obligar
 *       a listar todas las variantes.</li>
 *   <li><b>No ser un solo carácter repetido ni una secuencia</b>
 *       ({@code 123456789012}, {@code aaaaaaaaaaaa}): cumplen el largo y no
 *       aportan nada.</li>
 *   <li><b>No contener el correo de la persona.</b> Es la pista que el atacante
 *       ya tiene.</li>
 * </ol>
 *
 * <p><b>No se exige</b> mezcla de mayúsculas, dígitos y símbolos. Es
 * deliberado y sigue a OWASP (<i>Authentication Cheat Sheet</i>): las reglas de
 * composición empujan a la gente a {@code Nombre123!} —que cumple todas y es
 * trivial— y castigan las frases largas, que son mejores. Lo que se exige es
 * largo y que no sea conocida, que es lo que de verdad resiste.
 *
 * <h2>Dónde se aplica y dónde no</h2>
 * En los tres caminos por los que una persona <b>elige</b> una contraseña:
 * crear una cuenta, cambiar la propia y el restablecimiento del administrador;
 * y en el {@code SICOT_ADMIN_PASSWORD} del arranque. No se aplica a las claves
 * que <b>genera</b> SICOT ({@code ClaveGenerada}, 16 caracteres al azar sobre
 * 58 símbolos): ya son mejores que cualquier cosa que exija esta clase, y
 * hacerlas pasar por aquí solo añadiría una forma de que el arranque falle.
 *
 * <p>Tampoco se aplica a las cuentas semilla de los perfiles {@code dev} y
 * {@code test} ({@code DataInitializer}): sus contraseñas están publicadas en
 * este repositorio a propósito y no existen en ningún despliegue real, así que
 * exigirles calidad sería teatro.
 */
public final class PoliticaDeContrasena {

    /**
     * Mínimo de caracteres. Doce, el mismo que {@code AdministradorInicial} ya
     * exigía al administrador inicial: no es un número nuevo, es el que el
     * proyecto ya había elegido para la cuenta que más importa.
     */
    public static final int MINIMO_CARACTERES = 12;

    private static final String RUTA_LISTA = "security/contrasenas-prohibidas.txt";

    /**
     * Se carga una sola vez, al primer uso, y se queda. Son un par de cientos
     * de cadenas cortas: el coste en memoria es despreciable y leer el archivo
     * en cada validación convertiría crear un usuario en una operación de
     * entrada/salida.
     */
    private static final Set<String> PROHIBIDAS = cargarLista();

    private PoliticaDeContrasena() {
    }

    /**
     * @return vacío si la contraseña es aceptable; si no, <b>qué corregir</b>,
     *         en una frase que se le puede mostrar a la persona tal cual. Nunca
     *         devuelve un código ni un «contraseña inválida» a secas: quien
     *         recibe eso prueba otra variante de lo mismo.
     */
    public static Optional<String> porQueNoSirve(String contrasena, String correo) {
        if (contrasena == null || contrasena.isBlank()) {
            return Optional.of("La contraseña es obligatoria.");
        }
        if (contrasena.length() < MINIMO_CARACTERES) {
            return Optional.of("La contraseña debe tener al menos " + MINIMO_CARACTERES
                    + " caracteres. Una frase fácil de recordar y difícil de adivinar sirve mejor que una"
                    + " palabra corta con símbolos.");
        }
        if (!LimiteDeBcrypt.cabe(contrasena)) {
            return Optional.of(LimiteDeBcrypt.MENSAJE);
        }

        String normalizada = normalizar(contrasena);
        if (PROHIBIDAS.contains(normalizada)) {
            return Optional.of("Esa contraseña es una de las más usadas del mundo (o nombra al SENA o a SICOT),"
                    + " así que es la primera que alguien probaría. Elija otra: añadirle números al final no la"
                    + " cambia, porque así es como se prueba.");
        }
        if (esSecuenciaORepeticion(normalizada.isEmpty() ? contrasena.toLowerCase() : normalizada)) {
            return Optional.of("Esa contraseña es una secuencia o un carácter repetido: cumple el largo pero se"
                    + " adivina igual de rápido que una corta. Elija otra.");
        }
        if (contieneElCorreo(contrasena, correo)) {
            return Optional.of("La contraseña no puede contener su propio correo ni el nombre de su cuenta:"
                    + " es el dato que quien intentara entrar ya tiene.");
        }
        return Optional.empty();
    }

    /** ¿Sirve? Atajo para quien solo necesita el sí o el no. */
    public static boolean aceptable(String contrasena, String correo) {
        return porQueNoSirve(contrasena, correo).isEmpty();
    }

    /**
     * Minúsculas, sin acentos y sin los dígitos y signos de relleno del final.
     *
     * <p>Es lo que hace que la lista sea corta y siga sirviendo:
     * {@code Password123!}, {@code PASSWORD}, {@code contraseña2026} y
     * {@code Sena2024*} se comparan todas contra una sola entrada. Sin esta
     * normalización haría falta listar cada variante numérica, que es
     * exactamente la trampa en la que caen las listas de bloqueo inútiles.
     *
     * <p>Solo se quita el relleno del <b>final</b>, no del principio ni del
     * medio: {@code 2026Presupuesto} o {@code Casa7Verde8Lejos} son
     * contraseñas razonables y no hay por qué tocarlas.
     */
    static String normalizar(String contrasena) {
        String sinAcentos = Normalizer.normalize(contrasena.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return sinAcentos.replaceAll("[0-9!@#$%^&*._+\\-=?]+$", "");
    }

    /**
     * Un solo carácter repetido, o una tirada seguida del teclado o de los
     * dígitos, hacia delante o hacia atrás.
     *
     * <p>Se exige que la secuencia sea <b>toda</b> la contraseña: un trozo
     * ascendente dentro de una frase larga ({@code mi casa de 1234 metros}) no
     * la vuelve débil, y rechazarlo sería la clase de regla que hace que la
     * gente apunte la contraseña en un papel.
     */
    static boolean esSecuenciaORepeticion(String texto) {
        if (texto.isEmpty()) {
            // Quedó vacía al normalizar: era solo dígitos y signos.
            return true;
        }
        if (texto.chars().distinct().count() == 1) {
            return true;
        }
        return esTiradaDe(texto, "0123456789", true)
                || esTiradaDe(texto, "abcdefghijklmnopqrstuvwxyz", false)
                || esTiradaDe(texto, "qwertyuiop", false)
                || esTiradaDe(texto, "asdfghjkl", false)
                || esTiradaDe(texto, "zxcvbnm", false);
    }

    /**
     * ¿Es {@code texto} un recorrido seguido de {@code fila}, hacia delante o
     * hacia atrás?
     *
     * <p>Se comprueba <b>paso a paso</b> y no buscando el texto dentro de la
     * fila repetida. La diferencia importa: {@code 098765432109} son doce
     * caracteres sobre un alfabeto de diez, así que ninguna ventana de la fila
     * duplicada lo contiene y la búsqueda por subcadena lo daba por bueno. Mirar
     * que cada carácter esté una posición antes o después del anterior funciona
     * sea cual sea el largo.
     *
     * @param ciclica si la fila da la vuelta. Los dígitos sí ({@code …9012…});
     *                las filas del teclado no, porque de la {@code p} no se
     *                pasa a la {@code q} en ningún teclado.
     */
    private static boolean esTiradaDe(String texto, String fila, boolean ciclica) {
        if (texto.length() < 3) {
            return false;
        }
        int anterior = fila.indexOf(texto.charAt(0));
        if (anterior < 0) {
            return false;
        }
        Integer paso = null;
        for (int i = 1; i < texto.length(); i++) {
            int actual = fila.indexOf(texto.charAt(i));
            if (actual < 0) {
                return false;
            }
            int diferencia = actual - anterior;
            if (ciclica) {
                // -9 es «del 9 al 0», es decir +1 dando la vuelta.
                if (diferencia == fila.length() - 1) {
                    diferencia = -1;
                } else if (diferencia == -(fila.length() - 1)) {
                    diferencia = 1;
                }
            }
            if (diferencia != 1 && diferencia != -1) {
                return false;
            }
            if (paso == null) {
                paso = diferencia;
            } else if (paso != diferencia) {
                // Cambia de sentido a mitad («12321»): no es una tirada.
                return false;
            }
            anterior = actual;
        }
        return true;
    }

    private static boolean contieneElCorreo(String contrasena, String correo) {
        if (correo == null || correo.isBlank()) {
            return false;
        }
        String enMinusculas = contrasena.toLowerCase();
        String limpio = correo.trim().toLowerCase();
        if (enMinusculas.contains(limpio)) {
            return true;
        }
        String antesDelArroba = limpio.split("@")[0];
        // Tres caracteres de mínimo: con menos, un «ana@…» prohibiría cualquier
        // contraseña que lleve esas tres letras seguidas, lo que es absurdo.
        return antesDelArroba.length() >= 4 && enMinusculas.contains(antesDelArroba);
    }

    private static Set<String> cargarLista() {
        ClassPathResource recurso = new ClassPathResource(RUTA_LISTA);
        try (BufferedReader lector = new BufferedReader(
                new InputStreamReader(recurso.getInputStream(), StandardCharsets.UTF_8))) {
            return lector.lines()
                    .map(String::trim)
                    .filter(linea -> !linea.isEmpty() && !linea.startsWith("#"))
                    .map(PoliticaDeContrasena::normalizar)
                    .filter(linea -> !linea.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            // Sin la lista, la política quedaría silenciosamente a medias: el
            // largo se seguiría exigiendo y «Password123456» pasaría. Un
            // control que se degrada sin avisar es peor que no tenerlo, así que
            // el arranque se detiene — el mismo criterio que JwtService aplica
            // a la falta de JWT_SECRET.
            throw new UncheckedIOException(
                    "No se pudo leer " + RUTA_LISTA + ", la lista de contraseñas prohibidas. "
                            + "Sin ella la política de contraseñas quedaría incompleta y nadie se enteraría.", e);
        }
    }
}
