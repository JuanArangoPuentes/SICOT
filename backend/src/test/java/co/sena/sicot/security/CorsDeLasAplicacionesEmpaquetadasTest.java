package co.sena.sicot.security;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El APK y la aplicación de escritorio llaman al servidor desde un origen fijo
 * del webview, sea cual sea el despliegue.
 *
 * <p>Antes esos orígenes sólo estaban en los valores por defecto de
 * CORS_ALLOWED_ORIGINS. El .env de producción, que pide «la dirección real»,
 * los pisaba: el navegador funcionaba (allí la SPA y la API comparten origen) y
 * el APK recibía un rechazo en el preflight que la pantalla de acceso sólo
 * podía explicar como un certificado sin instalar.
 */
class CorsDeLasAplicacionesEmpaquetadasTest {

    private static CorsConfiguration configuracion(String origenesDelEntorno) {
        UrlBasedCorsConfigurationSource fuente = (UrlBasedCorsConfigurationSource)
                new SecurityConfig(null).corsConfigurationSource(origenesDelEntorno);
        return fuente.getCorsConfigurations().get("/**");
    }

    @Test
    void conLaDireccionDeProduccionSiguenEntrandoElApkYElEscritorio() {
        CorsConfiguration cors = configuracion("https://sicot.centro.sena.edu.co");

        assertThat(cors.checkOrigin("https://sicot.centro.sena.edu.co")).isNotNull();
        // Android y Windows sirven la app desde http://tauri.localhost; macOS y
        // Linux, desde tauri://localhost.
        assertThat(cors.checkOrigin("http://tauri.localhost")).isNotNull();
        assertThat(cors.checkOrigin("tauri://localhost")).isNotNull();
    }

    @Test
    void unOrigenCualquieraSigueRechazado() {
        CorsConfiguration cors = configuracion("https://sicot.centro.sena.edu.co");

        assertThat(cors.checkOrigin("https://otro-sitio.example")).isNull();
        assertThat(cors.checkOrigin("http://localhost:8443")).isNull();
    }

    @Test
    void unaListaQueYaLosTraeNoLosRepite() {
        CorsConfiguration cors = configuracion("http://localhost:8443, http://tauri.localhost,tauri://localhost");

        assertThat(cors.getAllowedOrigins())
                .containsExactly("http://localhost:8443", "http://tauri.localhost", "tauri://localhost");
    }

    @Test
    void sinNingunOrigenDelEntornoSigueNegandoseAArrancar() {
        // Los orígenes de las aplicaciones no cuentan como configuración: el
        // frontend web también necesita el suyo, y una lista vacía sigue siendo
        // un error de despliegue que conviene ver al arrancar.
        assertThatThrownBy(() -> configuracion(" , "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ALLOWED_ORIGINS");
    }
}
