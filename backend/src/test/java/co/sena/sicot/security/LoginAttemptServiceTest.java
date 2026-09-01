package co.sena.sicot.security;

import co.sena.sicot.exception.DemasiadasSolicitudesException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

    private static final String EMAIL = "usuario@soy.sena.edu.co";
    private static final String IP = "10.0.0.7";
    private static final String OTRA_IP = "10.0.0.8";

    @Test
    void bloqueaTrasCincoIntentosFallidosDelMismoCorreo() {
        LoginAttemptService servicio = new LoginAttemptService();

        for (int i = 0; i < 4; i++) {
            servicio.registrarFallo(EMAIL, IP);
        }
        // Con cuatro fallos todavía debe dejar intentar.
        assertThatCode(() -> servicio.verificarNoBloqueado(EMAIL, IP)).doesNotThrowAnyException();

        servicio.registrarFallo(EMAIL, IP);

        assertThatThrownBy(() -> servicio.verificarNoBloqueado(EMAIL, IP))
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
        for (int i = 0; i < 5; i++) {
            servicio.registrarFallo(EMAIL, IP);
        }

        DemasiadasSolicitudesException ex = org.junit.jupiter.api.Assertions.assertThrows(
                DemasiadasSolicitudesException.class,
                () -> servicio.verificarNoBloqueado(EMAIL, IP));

        assertThat(ex.getSegundosDeEspera()).isPositive();
    }

    /**
     * Un login correcto tiene que limpiar el contador. Sin esto, la cuenta
     * arrastraba los fallos anteriores y un solo error posterior la volvía a
     * bloquear de inmediato.
     */
    @Test
    void unLoginExitosoLimpiaElContadorDelCorreo() {
        LoginAttemptService servicio = new LoginAttemptService();

        for (int i = 0; i < 4; i++) {
            servicio.registrarFallo(EMAIL, IP);
        }
        servicio.registrarExito(EMAIL);

        // Tras el éxito el contador arranca de cero: cuatro fallos más no bloquean.
        for (int i = 0; i < 4; i++) {
            servicio.registrarFallo(EMAIL, IP);
        }
        assertThatCode(() -> servicio.verificarNoBloqueado(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    void elBloqueoDistingueEntreCorreosDistintos() {
        LoginAttemptService servicio = new LoginAttemptService();

        for (int i = 0; i < 5; i++) {
            servicio.registrarFallo(EMAIL, IP);
        }

        assertThatThrownBy(() -> servicio.verificarNoBloqueado(EMAIL, IP))
                .isInstanceOf(DemasiadasSolicitudesException.class);
        // Desde OTRA_IP, para aislar el efecto del contador de correo del de red.
        assertThatCode(() -> servicio.verificarNoBloqueado("otro@soy.sena.edu.co", OTRA_IP))
                .doesNotThrowAnyException();
    }

    @Test
    void elCorreoSeNormalizaAntesDeContar() {
        LoginAttemptService servicio = new LoginAttemptService();

        servicio.registrarFallo("  Usuario@Soy.Sena.Edu.Co ", IP);
        servicio.registrarFallo("usuario@soy.sena.edu.co", IP);
        servicio.registrarFallo("USUARIO@SOY.SENA.EDU.CO", IP);
        servicio.registrarFallo(EMAIL, IP);
        servicio.registrarFallo(EMAIL, IP);

        // Las cinco variantes son la misma cuenta: deben sumar al mismo contador,
        // o bastaría con cambiar mayúsculas para saltarse el bloqueo.
        assertThatThrownBy(() -> servicio.verificarNoBloqueado(EMAIL, IP))
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
            servicio.registrarFallo("victima" + i + "@soy.sena.edu.co", IP);
        }

        assertThatThrownBy(() -> servicio.verificarNoBloqueado("victima99@soy.sena.edu.co", IP))
                .isInstanceOf(DemasiadasSolicitudesException.class)
                .hasMessageContaining("desde esta red");
    }

    @Test
    void elBloqueoDeRedNoAfectaAOtrasRedes() {
        LoginAttemptService servicio = new LoginAttemptService();

        for (int i = 0; i < 20; i++) {
            servicio.registrarFallo("victima" + i + "@soy.sena.edu.co", IP);
        }

        assertThatCode(() -> servicio.verificarNoBloqueado("alguien@soy.sena.edu.co", OTRA_IP))
                .doesNotThrowAnyException();
    }

    /**
     * Entrar con una cuenta propia no debe limpiar el contador de la red: si lo
     * hiciera, quien está probando cuentas ajenas reiniciaría su propio límite a
     * voluntad y el freno por origen sería decorativo.
     */
    @Test
    void unLoginExitosoNoLimpiaElContadorDeRed() {
        LoginAttemptService servicio = new LoginAttemptService();

        for (int i = 0; i < 20; i++) {
            servicio.registrarFallo("victima" + i + "@soy.sena.edu.co", IP);
        }
        servicio.registrarExito("propia@soy.sena.edu.co");

        assertThatThrownBy(() -> servicio.verificarNoBloqueado("otra@soy.sena.edu.co", IP))
                .isInstanceOf(DemasiadasSolicitudesException.class);
    }

    /**
     * Sin dirección de origen (no se pudo determinar) el servicio debe seguir
     * funcionando con el límite por correo, no reventar.
     */
    @Test
    void funcionaSinDireccionDeOrigen() {
        LoginAttemptService servicio = new LoginAttemptService();

        for (int i = 0; i < 5; i++) {
            servicio.registrarFallo(EMAIL, null);
        }

        assertThatThrownBy(() -> servicio.verificarNoBloqueado(EMAIL, null))
                .isInstanceOf(DemasiadasSolicitudesException.class)
                .hasMessageContaining("este correo");
    }
}
