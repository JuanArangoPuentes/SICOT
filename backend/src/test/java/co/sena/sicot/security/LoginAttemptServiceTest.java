package co.sena.sicot.security;

import co.sena.sicot.exception.DemasiadasSolicitudesException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cada {@code reservarIntento} es un intento de inicio de sesión: cuenta desde
 * que se hace, y solo un éxito ({@code registrarExito}) o un intento que no
 * llegó a decidirse ({@code liberarIntento}) lo devuelven. Por eso aquí «un
 * fallo» es una reserva que nadie devuelve, igual que en {@code AuthService}.
 */
class LoginAttemptServiceTest {

    private static final String EMAIL = "usuario@soy.sena.edu.co";
    private static final String IP = "10.0.0.7";
    private static final String OTRA_IP = "10.0.0.8";

    @Test
    void bloqueaTrasCincoIntentosFallidosDelMismoCorreo() {
        LoginAttemptService servicio = new LoginAttemptService();

        fallar(servicio, EMAIL, IP, 4);
        // Con cuatro fallos todavía debe dejar intentar el quinto.
        assertThatCode(() -> servicio.reservarIntento(EMAIL, IP)).doesNotThrowAnyException();

        assertThatThrownBy(() -> servicio.reservarIntento(EMAIL, IP))
                .isInstanceOf(DemasiadasSolicitudesException.class)
                .hasMessageContaining("Demasiados intentos");
    }

    /**
     * El bloqueo tiene que decir cuánto esperar: es lo que alimenta la cabecera
     * {@code Retry-After} del 429 y lo que permite a un cliente reintentar sin
     * adivinar.
     */
    @Test
    void elBloqueoIndicaCuantoEsperar() {
        LoginAttemptService servicio = new LoginAttemptService();
        fallar(servicio, EMAIL, IP, 5);

        DemasiadasSolicitudesException ex = org.junit.jupiter.api.Assertions.assertThrows(
                DemasiadasSolicitudesException.class,
                () -> servicio.reservarIntento(EMAIL, IP));

        assertThat(ex.getSegundosDeEspera()).isPositive();
    }

    /**
     * El hueco que motivó la reserva: doscientos POST simultáneos contra un
     * correo pasaban todos la comprobación antes de que se registrara el primer
     * fallo, porque BCrypt tarda ~100 ms entre una cosa y otra. Con la reserva
     * atómica, de cincuenta intentos a la vez pasan exactamente cinco.
     */
    @Test
    void losIntentosSimultaneosContraUnCorreoNoSuperanElTope() throws Exception {
        LoginAttemptService servicio = new LoginAttemptService();

        int aceptados = contarAceptadosEnParalelo(50, i -> servicio.reservarIntento(EMAIL, null));

        assertThat(aceptados).isEqualTo(5);
    }

    @Test
    void losIntentosSimultaneosDesdeUnaRedNoSuperanElTope() throws Exception {
        LoginAttemptService servicio = new LoginAttemptService();

        int aceptados = contarAceptadosEnParalelo(100,
                i -> servicio.reservarIntento("victima" + i + "@soy.sena.edu.co", IP));

        assertThat(aceptados).isEqualTo(20);
    }

    /**
     * Un login correcto tiene que limpiar el contador. Sin esto, la cuenta
     * arrastraba los fallos anteriores y un solo error posterior la volvía a
     * bloquear de inmediato.
     */
    @Test
    void unLoginExitosoLimpiaElContadorDelCorreo() {
        LoginAttemptService servicio = new LoginAttemptService();

        fallar(servicio, EMAIL, IP, 4);
        servicio.reservarIntento(EMAIL, IP);
        servicio.registrarExito(EMAIL, IP);

        // Tras el éxito el contador arranca de cero: cuatro fallos más no bloquean.
        fallar(servicio, EMAIL, IP, 4);
        assertThatCode(() -> servicio.reservarIntento(EMAIL, IP)).doesNotThrowAnyException();
    }

    /**
     * Si la base no responde, el intento no llegó a decidirse y no puede contar
     * como fallo: una caída no debe dejar a nadie bloqueado. Liberar el quinto
     * intento, el que puso el bloqueo, también lo levanta.
     */
    @Test
    void unIntentoLiberadoNoCuentaComoFallo() {
        LoginAttemptService servicio = new LoginAttemptService();

        fallar(servicio, EMAIL, IP, 4);
        servicio.reservarIntento(EMAIL, IP);
        servicio.liberarIntento(EMAIL, IP);

        assertThatCode(() -> servicio.reservarIntento(EMAIL, IP)).doesNotThrowAnyException();
        assertThatThrownBy(() -> servicio.reservarIntento(EMAIL, IP))
                .isInstanceOf(DemasiadasSolicitudesException.class);
    }

    @Test
    void elBloqueoDistingueEntreCorreosDistintos() {
        LoginAttemptService servicio = new LoginAttemptService();

        fallar(servicio, EMAIL, IP, 5);

        assertThatThrownBy(() -> servicio.reservarIntento(EMAIL, IP))
                .isInstanceOf(DemasiadasSolicitudesException.class);
        // Desde OTRA_IP, para aislar el efecto del contador de correo del de red.
        assertThatCode(() -> servicio.reservarIntento("otro@soy.sena.edu.co", OTRA_IP))
                .doesNotThrowAnyException();
    }

    @Test
    void elCorreoSeNormalizaAntesDeContar() {
        LoginAttemptService servicio = new LoginAttemptService();

        servicio.reservarIntento("  Usuario@Soy.Sena.Edu.Co ", IP);
        servicio.reservarIntento("usuario@soy.sena.edu.co", IP);
        servicio.reservarIntento("USUARIO@SOY.SENA.EDU.CO", IP);
        servicio.reservarIntento(EMAIL, IP);
        servicio.reservarIntento(EMAIL, IP);

        // Las cinco variantes son la misma cuenta: deben sumar al mismo contador,
        // o bastaría con cambiar mayúsculas para saltarse el bloqueo.
        assertThatThrownBy(() -> servicio.reservarIntento(EMAIL, IP))
                .isInstanceOf(DemasiadasSolicitudesException.class);
    }

    /**
     * Rociado de contraseñas: una contraseña común contra muchas cuentas. Es el
     * hueco que dejaba contar solo por correo — ningún correo llega a cinco
     * fallos, así que ninguno se bloqueaba y el atacante podía seguir
     * indefinidamente. El contador por red es lo único que lo ve.
     */
    @Test
    void bloqueaElRociadoDeContrasenasDesdeUnMismoOrigen() {
        LoginAttemptService servicio = new LoginAttemptService();

        // Veinte cuentas distintas, un solo fallo en cada una: ninguna se acerca
        // a su propio umbral de cinco.
        for (int i = 0; i < 20; i++) {
            servicio.reservarIntento("victima" + i + "@soy.sena.edu.co", IP);
        }

        assertThatThrownBy(() -> servicio.reservarIntento("victima99@soy.sena.edu.co", IP))
                .isInstanceOf(DemasiadasSolicitudesException.class)
                .hasMessageContaining("desde esta red");
    }

    @Test
    void elBloqueoDeRedNoAfectaAOtrasRedes() {
        LoginAttemptService servicio = new LoginAttemptService();

        for (int i = 0; i < 20; i++) {
            servicio.reservarIntento("victima" + i + "@soy.sena.edu.co", IP);
        }

        assertThatCode(() -> servicio.reservarIntento("alguien@soy.sena.edu.co", OTRA_IP))
                .doesNotThrowAnyException();
    }

    /**
     * Un intento que la red rechaza no llega a hacerse, así que tampoco puede
     * gastar los intentos del correo: si no, quien bloquea una red dejaría de
     * paso sin intentos a cada cuenta que se probara desde ella.
     */
    @Test
    void unIntentoRechazadoPorLaRedNoGastaLosDelCorreo() {
        LoginAttemptService servicio = new LoginAttemptService();
        for (int i = 0; i < 20; i++) {
            servicio.reservarIntento("victima" + i + "@soy.sena.edu.co", IP);
        }
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> servicio.reservarIntento(EMAIL, IP))
                    .isInstanceOf(DemasiadasSolicitudesException.class);
        }

        // Desde otra red, el correo conserva sus cinco intentos.
        fallar(servicio, EMAIL, OTRA_IP, 5);
        assertThatThrownBy(() -> servicio.reservarIntento(EMAIL, OTRA_IP))
                .hasMessageContaining("este correo");
    }

    /**
     * Entrar con una cuenta propia no debe limpiar el contador de la red: si lo
     * hiciera, quien está probando cuentas ajenas reiniciaría su propio límite a
     * voluntad y el freno por origen sería decorativo. Solo devuelve su propia
     * reserva.
     */
    @Test
    void unLoginExitosoNoLimpiaElContadorDeRed() {
        LoginAttemptService servicio = new LoginAttemptService();

        for (int i = 0; i < 19; i++) {
            servicio.reservarIntento("victima" + i + "@soy.sena.edu.co", IP);
        }
        servicio.reservarIntento("propia@soy.sena.edu.co", IP);
        servicio.registrarExito("propia@soy.sena.edu.co", IP);

        // Quedan los diecinueve fallos: cabe uno más y el siguiente se bloquea.
        assertThatCode(() -> servicio.reservarIntento("otra@soy.sena.edu.co", IP)).doesNotThrowAnyException();
        assertThatThrownBy(() -> servicio.reservarIntento("otra2@soy.sena.edu.co", IP))
                .isInstanceOf(DemasiadasSolicitudesException.class);
    }

    /**
     * La otra cara de contar antes de comprobar: los inicios de sesión correctos
     * de un Centro que sale a internet por una sola IP no pueden ir sumando
     * hasta bloquearlo entero.
     */
    @Test
    void losLoginsCorrectosDesdeUnaRedCompartidaNoLaBloquean() {
        LoginAttemptService servicio = new LoginAttemptService();

        for (int i = 0; i < 50; i++) {
            String correo = "persona" + i + "@soy.sena.edu.co";
            servicio.reservarIntento(correo, IP);
            servicio.registrarExito(correo, IP);
        }

        assertThatCode(() -> servicio.reservarIntento(EMAIL, IP)).doesNotThrowAnyException();
    }

    /**
     * Sin dirección de origen (no se pudo determinar) el servicio debe seguir
     * funcionando con el límite por correo, no reventar.
     */
    @Test
    void funcionaSinDireccionDeOrigen() {
        LoginAttemptService servicio = new LoginAttemptService();

        fallar(servicio, EMAIL, null, 5);

        assertThatThrownBy(() -> servicio.reservarIntento(EMAIL, null))
                .isInstanceOf(DemasiadasSolicitudesException.class)
                .hasMessageContaining("este correo");
    }

    /** Intentos con contraseña equivocada: reservas que nadie devuelve. */
    private static void fallar(LoginAttemptService servicio, String email, String origen, int veces) {
        for (int i = 0; i < veces; i++) {
            servicio.reservarIntento(email, origen);
        }
    }

    private interface Intento {
        void hacer(int numero);
    }

    /** Lanza todos los intentos a la vez y cuenta cuántos no recibieron 429. */
    private static int contarAceptadosEnParalelo(int cuantos, Intento intento) throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(16);
        try {
            CountDownLatch salida = new CountDownLatch(1);
            List<Future<Boolean>> resultados = new ArrayList<>();
            for (int i = 0; i < cuantos; i++) {
                int numero = i;
                Callable<Boolean> tarea = () -> {
                    salida.await();
                    try {
                        intento.hacer(numero);
                        return true;
                    } catch (DemasiadasSolicitudesException e) {
                        return false;
                    }
                };
                resultados.add(hilos.submit(tarea));
            }
            salida.countDown();
            int aceptados = 0;
            for (Future<Boolean> resultado : resultados) {
                if (resultado.get()) {
                    aceptados++;
                }
            }
            return aceptados;
        } finally {
            hilos.shutdownNow();
        }
    }
}
