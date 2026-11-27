package co.sena.sicot.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que esta política tiene que impedir, y —tan importante— lo que no debe
 * estorbar.
 *
 * <p>Una lista de bloqueo mal hecha falla de dos maneras, y las dos se prueban
 * aquí: deja pasar {@code Password123} porque solo compara literales, o rechaza
 * frases largas y legítimas porque contienen una palabra de la lista. La
 * primera la vuelve inútil; la segunda hace que la gente apunte la contraseña
 * en un papel, que es peor que el problema original.
 */
class PoliticaDeContrasenaTest {

    @Nested
    @DisplayName("Lo que no se acepta")
    class LoQueNoSeAcepta {

        @ParameterizedTest
        @ValueSource(strings = {"corta1*", "Once123456*", "Admin123*", "Gestion123*"})
        @DisplayName("menos de doce caracteres")
        void demasiadoCorta(String contrasena) {
            assertThat(PoliticaDeContrasena.porQueNoSirve(contrasena, null))
                    .get().asString().contains("al menos 12");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                // El caso exacto que el sistema aceptaba hasta el 20-11-2026.
                "Password123!",
                "password1234",
                "PASSWORD1234",
                // Normalización de acentos: la lista trae «contrasena».
                "contraseña2026",
                "Contrasena2026",
                // Contexto del Centro: lo primero que probaría alguien de dentro.
                "SenaColombia1",
                "sicotsena2026",
                "Mobiliario123",
                "Medellin2026*",
                "administrador1",
                "bienvenido2026",
        })
        @DisplayName("una de las más usadas, o el nombre del SENA, aunque lleve números y símbolos detrás")
        void estaEnLaListaDeBloqueo(String contrasena) {
            assertThat(PoliticaDeContrasena.porQueNoSirve(contrasena, null))
                    .as("añadir dígitos al final no debe esquivar la lista: así es justo como se prueba")
                    .get().asString().contains("más usadas del mundo");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "123456789012",
                "098765432109",
                "aaaaaaaaaaaa",
                "..............",
                "abcdefghijkl",
                "qwertyuiop12",
        })
        @DisplayName("una secuencia o un carácter repetido, aunque cumpla el largo")
        void esSecuenciaORepeticion(String contrasena) {
            assertThat(PoliticaDeContrasena.porQueNoSirve(contrasena, null)).isPresent();
        }

        @Test
        @DisplayName("contiene el correo de su propio dueño")
        void llevaElCorreoDentro() {
            assertThat(PoliticaDeContrasena.porQueNoSirve("mariana.lopez-2026-ok", "mariana.lopez@sena.edu.co"))
                    .get().asString().contains("su propio correo");
            assertThat(PoliticaDeContrasena.porQueNoSirve("xx-mariana.lopez@sena.edu.co-xx",
                    "mariana.lopez@sena.edu.co"))
                    .isPresent();
        }

        @Test
        @DisplayName("vacía o nula")
        void vacia() {
            assertThat(PoliticaDeContrasena.porQueNoSirve(null, null)).isPresent();
            assertThat(PoliticaDeContrasena.porQueNoSirve("   ", null)).isPresent();
        }

        @Test
        @DisplayName("más larga de lo que admite BCrypt: el motivo lo dice, no es un error interno")
        void masAllaDeBcrypt() {
            assertThat(PoliticaDeContrasena.porQueNoSirve("á".repeat(40), null))
                    .get().asString().contains("72 bytes");
        }
    }

    @Nested
    @DisplayName("Lo que sí se acepta: la política no puede estorbar a quien elige bien")
    class LoQueSiSeAcepta {

        @ParameterizedTest
        @ValueSource(strings = {
                // Frases largas: lo que OWASP recomienda fomentar, y lo que las
                // reglas de composición castigan.
                "el taller de ebanisteria queda al fondo",
                "tres sillas y una mesa de pino",
                "Caminar hasta la bodega toma 12 minutos",
                // Sin reglas de composición: no se exige mayúscula ni símbolo.
                "manzanaverdelejana",
                // Dígitos al principio o en medio, que NO se normalizan.
                "2026Presupuesto",
                "Casa7Verde8Lejos",
                // Contiene una palabra de la lista, pero no ES esa palabra.
                "mi clave del taller es larga",
                "passwordnoesestaframe",
                // Una secuencia DENTRO de una frase no la vuelve débil.
                "mi casa de 1234 metros",
                // Clave generada por SICOT (ClaveGenerada, 16 al azar).
                "Kp7mQx2TvRn4Jhbz",
        })
        void contrasenasRazonables(String contrasena) {
            assertThat(PoliticaDeContrasena.porQueNoSirve(contrasena, "mariana.lopez@sena.edu.co"))
                    .as("rechazar esto haría que la gente apunte la contraseña en un papel")
                    .isEmpty();
        }

        @Test
        @DisplayName("un correo corto no prohíbe cualquier contraseña que lleve esas letras")
        void correoMuyCortoNoBloqueaTodo() {
            // Con «ana@…», exigir que la contraseña no contenga «ana» dejaría
            // fuera «manzanaverdelejana», que no tiene nada que ver.
            assertThat(PoliticaDeContrasena.aceptable("manzanaverdelejana", "ana@sena.edu.co")).isTrue();
        }

        @Test
        @DisplayName("sin correo conocido, el resto de la política sigue aplicando")
        void sinCorreoSigueValidando() {
            assertThat(PoliticaDeContrasena.aceptable("Password123!", null)).isFalse();
            assertThat(PoliticaDeContrasena.aceptable("el taller de ebanisteria", null)).isTrue();
        }
    }

    @Test
    @DisplayName("la lista se cargó: si el recurso faltara, la política quedaría a medias en silencio")
    void laListaSeCargo() {
        // La prueba de que el archivo está en el classpath y se leyó. Sin él,
        // PoliticaDeContrasena detiene el arranque a propósito (ver cargarLista),
        // y esta comprobación es la que lo detecta en la suite y no en el servidor.
        assertThat(PoliticaDeContrasena.aceptable("password1234", null)).isFalse();
    }

    @Test
    @DisplayName("el mínimo es el mismo que ya exigía el administrador inicial")
    void elMinimoEsDoce() {
        assertThat(PoliticaDeContrasena.MINIMO_CARACTERES).isEqualTo(12);
    }
}
