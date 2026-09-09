package co.sena.sicot.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El perfil que se activa cuando nadie declara ninguno.
 *
 * <p>Es una prueba sobre un archivo de configuración y no sobre código, lo cual
 * es raro, pero el valor que vigila es exactamente el tipo de cosa que alguien
 * cambia «un momento, para probar» y se queda. Antes decía {@code dev}, y con
 * eso un {@code java -jar} sin variables levantaba un backend que firmaba
 * tokens con el secreto publicado en este repositorio
 * ({@code application-dev.properties}) y sembraba las cuentas demo con
 * contraseñas conocidas ({@code DataInitializer}). No fallaba nada: arrancaba
 * bien, que es la forma peligrosa de fallar.
 *
 * <p>Con {@code prod} por defecto, ese arranque descuidado se detiene, porque
 * prod no trae respaldo para {@code JWT_SECRET} y {@link
 * co.sena.sicot.security.JwtService} lo exige. Las rutas legítimas de
 * desarrollo siguen intactas y cada una declara su perfil explícitamente:
 * {@code spring-boot:run} desde el {@code pom.xml}, {@code docker-compose.yml}
 * por variable de entorno, y la suite con {@code @ActiveProfiles}.
 */
class PerfilPorDefectoTest {

    @Test
    void elPerfilPorDefectoEsProdYNoDev() throws IOException {
        Properties propiedades = cargarApplicationProperties();

        String declarado = propiedades.getProperty("spring.profiles.active");

        assertThat(declarado)
                .as("application.properties debe declarar el perfil activo")
                .isNotNull();
        assertThat(declarado)
                .as("""
                        El respaldo del perfil activo debe ser "prod". Con "dev" ahí, un despliegue \
                        que olvide SPRING_PROFILES_ACTIVE arranca con el secreto JWT publicado en \
                        este repositorio y con las cuentas demo sembradas.""")
                .isEqualTo("${SPRING_PROFILES_ACTIVE:prod}");
    }

    @Test
    void elSecretoJwtNoTieneNingunValorDeConvenienciaEnLaConfiguracionBase() throws IOException {
        Properties propiedades = cargarApplicationProperties();

        String secreto = propiedades.getProperty("sicot.security.jwt-secret");

        // Respaldo vacío, no un valor. Lo que importa es que no haya NINGUNA
        // clave utilizable aquí: el respaldo vacío sólo existe para que el
        // arranque falle en JwtService, que explica qué falta y cómo generarlo,
        // en vez de en el resolutor de propiedades de Spring, que no lo explica.
        assertThat(secreto).isEqualTo("${JWT_SECRET:}");
    }

    private static Properties cargarApplicationProperties() throws IOException {
        Properties propiedades = new Properties();
        try (InputStream entrada = PerfilPorDefectoTest.class
                .getResourceAsStream("/application.properties")) {
            assertThat(entrada).as("application.properties debe existir en el classpath").isNotNull();
            propiedades.load(new java.io.InputStreamReader(entrada, StandardCharsets.UTF_8));
        }
        return propiedades;
    }
}
