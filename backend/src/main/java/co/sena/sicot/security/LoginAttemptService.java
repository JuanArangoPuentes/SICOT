package co.sena.sicot.security;

import co.sena.sicot.exception.DemasiadasSolicitudesException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mitigación de fuerza bruta sobre {@code /api/auth/login}. Bloquea tras varios
 * intentos fallidos seguidos, contando por <b>dos claves independientes</b>: el
 * correo y la dirección de origen.
 *
 * <h2>Por qué hacen falta las dos</h2>
 * Contar solo por correo —como se hacía antes— deja dos huecos que no son
 * teóricos:
 * <ul>
 *   <li><b>Rociado de contraseñas.</b> Probar <i>una</i> contraseña común
 *       contra <i>muchas</i> cuentas no acumula fallos en ninguna, así que el
 *       contador por correo nunca se dispara. Es la forma habitual de atacar un
 *       directorio institucional, donde los correos son predecibles
 *       (nombre.apellido&#64;…). El contador por IP sí lo ve, porque todos esos
 *       intentos vienen del mismo origen.</li>
 *   <li><b>Bloqueo malicioso de una persona.</b> Cualquiera que conozca el
 *       correo de un funcionario puede dejarlo fuera del sistema fallando cinco
 *       veces a propósito. Eso no desaparece del todo —es el precio de bloquear
 *       por correo— pero el umbral por IP hace que quien lo intente se bloquee
 *       a sí mismo antes de poder repetirlo con varias cuentas.</li>
 * </ul>
 *
 * <h2>Umbrales distintos a propósito</h2>
 * Cinco fallos por correo y veinte por IP. Una IP puede ser legítimamente
 * compartida —toda la red del centro de formación puede salir por una sola
 * dirección—, así que su umbral tiene que tolerar a varias personas
 * equivocándose el mismo día sin castigarlas a todas. Cinco por correo, en
 * cambio, es una sola persona.
 *
 * <h2>Alcance</h2>
 * En memoria: asume una única instancia del backend, que es el despliegue
 * previsto. Se reinicia si el backend se reinicia, lo cual es aceptable para
 * este riesgo. Si algún día se corre más de una instancia detrás de un
 * balanceador, esto debe pasar a un almacén compartido o cada instancia
 * contará por su cuenta y el umbral real se multiplicará por el número de
 * instancias.
 */
@Component
public class LoginAttemptService {

    private static final int MAX_INTENTOS_POR_CORREO = 5;
    private static final int MAX_INTENTOS_POR_ORIGEN = 20;
    private static final Duration DURACION_BLOQUEO = Duration.ofMinutes(15);

    /**
     * Ventana en la que se acumulan los fallos. Pasado este tiempo desde el
     * último intento fallido, el contador vuelve a empezar: cinco errores de
     * tecleo repartidos a lo largo de un mes no son un ataque de fuerza bruta.
     */
    private static final Duration VENTANA_INTENTOS = Duration.ofMinutes(15);

    /**
     * Tope de claves vigiladas a la vez. La clave la elige quien llama al
     * login, así que sin este tope un atacante que envíe correos aleatorios
     * distintos haría crecer el mapa hasta agotar la memoria. Al superarlo se
     * purgan las entradas que ya caducaron; el número es holgado para el uso
     * real de SICOT (decenas de cuentas) y aun así acota el crecimiento.
     */
    private static final int MAX_ENTRADAS = 10_000;

    private final ConcurrentHashMap<String, Estado> intentos = new ConcurrentHashMap<>();

    /**
     * @param email  correo con el que se intenta entrar
     * @param origen dirección IP de la petición; puede ser {@code null} si no
     *               se pudo determinar, en cuyo caso solo se aplica el límite
     *               por correo
     */
    public void verificarNoBloqueado(String email, String origen) {
        comprobar(clavePorCorreo(email),
                "Demasiados intentos fallidos con este correo. Intente de nuevo en unos minutos.");
        if (origen != null && !origen.isBlank()) {
            comprobar(clavePorOrigen(origen),
                    "Demasiados intentos fallidos desde esta red. Intente de nuevo en unos minutos.");
        }
    }

    public void registrarFallo(String email, String origen) {
        registrar(clavePorCorreo(email), MAX_INTENTOS_POR_CORREO);
        if (origen != null && !origen.isBlank()) {
            registrar(clavePorOrigen(origen), MAX_INTENTOS_POR_ORIGEN);
        }
    }

    /**
     * Un inicio de sesión correcto limpia el contador del correo, pero
     * <b>no</b> el del origen. Si así fuera, quien está probando cuentas ajenas
     * podría reiniciar su propio contador de red simplemente entrando una vez
     * con una cuenta que sí controla, y el límite por IP dejaría de servir.
     */
    public void registrarExito(String email) {
        intentos.remove(clavePorCorreo(email));
    }

    /**
     * Olvida todos los intentos acumulados.
     *
     * <p>Existe para las pruebas de integración, que comparten una única
     * instancia de este componente: los contadores no viven en la base de
     * datos, así que vaciar las tablas entre pruebas no los alcanza y un
     * bloqueo provocado a propósito por una prueba de fuerza bruta seguía
     * vigente para la siguiente, que fallaba con 429 sin motivo aparente.
     *
     * <p>No se expone por ninguna ruta HTTP y no debe llamarse desde la
     * aplicación: un endpoint capaz de limpiar estos contadores anularía la
     * mitigación de fuerza bruta entera.
     */
    public void reiniciar() {
        intentos.clear();
    }

    private void comprobar(String clave, String mensaje) {
        Estado estado = intentos.get(clave);
        if (estado != null && estado.sigueBloqueado()) {
            long espera = Duration.between(Instant.now(), estado.bloqueadoHasta()).toSeconds();
            throw new DemasiadasSolicitudesException(mensaje, espera);
        }
    }

    private void registrar(String clave, int maximo) {
        if (intentos.size() >= MAX_ENTRADAS) {
            purgarEntradasCaducadas();
        }
        intentos.compute(clave, (k, actual) -> {
            Instant ahora = Instant.now();
            // El contador se reinicia si la entrada anterior ya caducó. Sin esto,
            // una cuenta que alguna vez llegó al máximo quedaba atrapada: cada
            // error posterior, por aislado que fuera, la volvía a bloquear otros
            // 15 minutos, indefinidamente, y solo un login exitoso lo limpiaba
            // — imposible si justamente lo que pasa es que no recuerda la clave.
            int cuenta = (actual == null || actual.caducado(ahora)) ? 1 : actual.intentos() + 1;
            Instant bloqueadoHasta = cuenta >= maximo ? ahora.plus(DURACION_BLOQUEO) : null;
            return new Estado(cuenta, bloqueadoHasta, ahora);
        });
    }

    private void purgarEntradasCaducadas() {
        Instant ahora = Instant.now();
        intentos.values().removeIf(estado -> estado.caducado(ahora));
    }

    // Prefijos distintos para que un correo no pueda colisionar nunca con una
    // dirección IP dentro del mismo mapa.
    private String clavePorCorreo(String email) {
        return "correo:" + (email == null ? "" : email.trim().toLowerCase());
    }

    private String clavePorOrigen(String origen) {
        return "origen:" + origen.trim();
    }

    private record Estado(int intentos, Instant bloqueadoHasta, Instant ultimoIntento) {

        private boolean sigueBloqueado() {
            return bloqueadoHasta != null && bloqueadoHasta.isAfter(Instant.now());
        }

        /**
         * Una entrada caduca cuando ya no está bloqueada y su último intento
         * quedó fuera de la ventana: en ese punto no aporta nada y puede
         * olvidarse o reiniciarse.
         */
        private boolean caducado(Instant ahora) {
            if (bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora)) {
                return false;
            }
            return ultimoIntento.plus(VENTANA_INTENTOS).isBefore(ahora);
        }
    }
}
