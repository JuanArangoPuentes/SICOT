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
     * Cuenta un intento de inicio de sesión <b>antes</b> de comprobar la
     * contraseña, o lo rechaza con 429 si ya se llegó al tope.
     *
     * <h2>Por qué se cuenta antes y no después</h2>
     * Antes se miraba el contador al entrar y el fallo se sumaba al salir,
     * después de BCrypt. Entre una cosa y otra pasan unos 100 ms, y todas las
     * peticiones que llegaban en esa ventana veían el contador por debajo del
     * tope: doscientos POST simultáneos contra un correo eran doscientos
     * intentos por ventana, no cinco. Comprobar y sumar en el mismo
     * {@code compute} hace que los intentos en vuelo también cuenten.
     *
     * <p>Por eso un intento reservado cuenta como fallo hasta que se demuestre
     * lo contrario: {@link #registrarExito} lo devuelve si la contraseña era
     * correcta, y {@link #liberarIntento} si no se llegó a decidir (la base no
     * respondió, por ejemplo). Una contraseña equivocada no necesita nada más:
     * la reserva ya es el fallo.
     *
     * @param email  correo con el que se intenta entrar
     * @param origen dirección IP de la petición; puede ser {@code null} si no
     *               se pudo determinar, en cuyo caso solo se aplica el límite
     *               por correo
     */
    public void reservarIntento(String email, String origen) {
        String claveCorreo = clavePorCorreo(email);
        reservar(claveCorreo, MAX_INTENTOS_POR_CORREO,
                "Demasiados intentos fallidos con este correo. Intente de nuevo en unos minutos.");
        if (origen != null && !origen.isBlank()) {
            try {
                reservar(clavePorOrigen(origen), MAX_INTENTOS_POR_ORIGEN,
                        "Demasiados intentos fallidos desde esta red. Intente de nuevo en unos minutos.");
            } catch (DemasiadasSolicitudesException e) {
                // El intento no llega a hacerse, así que tampoco puede quedar
                // contado contra el correo.
                devolver(claveCorreo, MAX_INTENTOS_POR_CORREO);
                throw e;
            }
        }
    }

    /**
     * Un inicio de sesión correcto limpia el contador del correo, pero del
     * origen solo devuelve su propia reserva: <b>no</b> lo limpia. Si lo
     * limpiara, quien está probando cuentas ajenas podría reiniciar su propio
     * contador de red simplemente entrando una vez con una cuenta que sí
     * controla, y el límite por IP dejaría de servir. Y si no devolviera la
     * reserva, los inicios de sesión correctos de un Centro que sale a internet
     * por una sola IP acabarían bloqueándolo entero.
     */
    public void registrarExito(String email, String origen) {
        intentos.remove(clavePorCorreo(email));
        if (origen != null && !origen.isBlank()) {
            devolver(clavePorOrigen(origen), MAX_INTENTOS_POR_ORIGEN);
        }
    }

    /**
     * Deshace una reserva cuyo intento no llegó a resolverse por algo ajeno a
     * las credenciales. Una caída de la base no puede dejar a nadie bloqueado.
     */
    public void liberarIntento(String email, String origen) {
        devolver(clavePorCorreo(email), MAX_INTENTOS_POR_CORREO);
        if (origen != null && !origen.isBlank()) {
            devolver(clavePorOrigen(origen), MAX_INTENTOS_POR_ORIGEN);
        }
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

    private void reservar(String clave, int maximo, String mensaje) {
        if (intentos.size() >= MAX_ENTRADAS) {
            purgarEntradasCaducadas();
        }
        intentos.compute(clave, (k, actual) -> {
            Instant ahora = Instant.now();
            if (actual != null && actual.bloqueadoEn(ahora)) {
                // Lanzar dentro de compute deja la entrada como estaba: un
                // intento rechazado no suma ni alarga el bloqueo.
                long espera = Duration.between(ahora, actual.bloqueadoHasta()).toSeconds();
                throw new DemasiadasSolicitudesException(mensaje, espera);
            }
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

    /**
     * Resta una reserva. Si con ella el contador baja del tope, el bloqueo lo
     * había puesto justo esa reserva —mientras hay bloqueo no se aceptan
     * otras—, así que deja de tener motivo y se levanta.
     */
    private void devolver(String clave, int maximo) {
        intentos.computeIfPresent(clave, (k, actual) -> {
            int cuenta = actual.intentos() - 1;
            if (cuenta <= 0) {
                return null;
            }
            Instant bloqueadoHasta = cuenta >= maximo ? actual.bloqueadoHasta() : null;
            return new Estado(cuenta, bloqueadoHasta, actual.ultimoIntento());
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

        private boolean bloqueadoEn(Instant ahora) {
            return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
        }

        /**
         * Una entrada caduca cuando ya no está bloqueada y su último intento
         * quedó fuera de la ventana: en ese punto no aporta nada y puede
         * olvidarse o reiniciarse.
         */
        private boolean caducado(Instant ahora) {
            if (bloqueadoEn(ahora)) {
                return false;
            }
            return ultimoIntento.plus(VENTANA_INTENTOS).isBefore(ahora);
        }
    }
}
