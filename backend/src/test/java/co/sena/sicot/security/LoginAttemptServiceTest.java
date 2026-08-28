package co.sena.sicot.security;

import co.sena.sicot.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

    private static final String EMAIL = "usuario@soy.sena.edu.co";

    @Test
    void bloqueaTrasCincoIntentosFallidos() {
        LoginAttemptService servicio = new LoginAttemptService();

        for (int i = 0; i < 4; i++) {
            servicio.registrarFallo(EMAIL);
        }
        // Con cuatro fallos todavía debe dejar intentar.
        assertThatCode(() -> servicio.verificarNoBloqueado(EMAIL)).doesNotThrowAnyException();

        servicio.registrarFallo(EMAIL);

        assertThatThrownBy(() -> servicio.verificarNoBloqueado(EMAIL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Demasiados intentos");
    }

    /**
     * Un login correcto tiene que limpiar el contador. Sin esto, la cuenta
     * arrastraba los fallos anteriores y un solo error posterior la volvía a
     * bloquear de inmediato.
     */
    @Test
    void unLoginExitosoLimpiaElContador() {
        LoginAttemptService servicio = new LoginAttemptService();

        for (int i = 0; i < 4; i++) {
            servicio.registrarFallo(EMAIL);
        }
        servicio.registrarExito(EMAIL);

        // Tras el éxito el contador arranca de cero: cuatro fallos más no bloquean.
        for (int i = 0; i < 4; i++) {
            servicio.registrarFallo(EMAIL);
        }
        assertThatCode(() -> servicio.verificarNoBloqueado(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void elBloqueoDistingueEntreCorreosDistintos() {
        LoginAttemptService servicio = new LoginAttemptService();

        for (int i = 0; i < 5; i++) {
            servicio.registrarFallo(EMAIL);
        }

        assertThatThrownBy(() -> servicio.verificarNoBloqueado(EMAIL))
                .isInstanceOf(BusinessException.class);
        assertThatCode(() -> servicio.verificarNoBloqueado("otro@soy.sena.edu.co"))
                .doesNotThrowAnyException();
    }

    @Test
    void elCorreoSeNormalizaAntesDeContar() {
        LoginAttemptService servicio = new LoginAttemptService();

        servicio.registrarFallo("  Usuario@Soy.Sena.Edu.Co ");
        servicio.registrarFallo("usuario@soy.sena.edu.co");
        servicio.registrarFallo("USUARIO@SOY.SENA.EDU.CO");
        servicio.registrarFallo(EMAIL);
        servicio.registrarFallo(EMAIL);

        // Las cinco variantes son la misma cuenta: deben sumar al mismo contador,
        // o bastaría con cambiar mayúsculas para saltarse el bloqueo.
        assertThatThrownBy(() -> servicio.verificarNoBloqueado(EMAIL))
                .isInstanceOf(BusinessException.class);
    }
}
