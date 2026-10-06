package co.sena.sicot.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El tope se fija contra el comportamiento real de la biblioteca, no contra un
 * número copiado: si una versión futura de spring-security cambia el límite,
 * estas pruebas lo hacen visible antes de que vuelva a salir un 500.
 */
class LimiteDeBcryptTest {

    /** Coste mínimo: aquí solo importa si BCrypt acepta o rechaza la entrada. */
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(4);

    @Test
    void setentaYDosBytesCabenYBcryptLosAcepta() {
        String justa = "a".repeat(72);

        assertThat(LimiteDeBcrypt.cabe(justa)).isTrue();
        assertThatCode(() -> bcrypt.encode(justa)).doesNotThrowAnyException();
    }

    @Test
    void unByteDeMasNoCabeYBcryptLoRechaza() {
        String larga = "a".repeat(73);

        assertThat(LimiteDeBcrypt.cabe(larga)).isFalse();
        assertThatThrownBy(() -> bcrypt.encode(larga)).isInstanceOf(IllegalArgumentException.class);
    }

    /** El caso que la validación por caracteres dejaba pasar: 40 caracteres, 80 bytes. */
    @Test
    void lasTildesYEñesCuentanDoble() {
        String conTildes = "ñ".repeat(40);

        assertThat(conTildes).hasSizeLessThan(72);
        assertThat(LimiteDeBcrypt.cabe(conTildes)).isFalse();
        assertThatThrownBy(() -> bcrypt.encode(conTildes)).isInstanceOf(IllegalArgumentException.class);
    }
}
