package co.sena.sicot.security;

import co.sena.sicot.PruebaDeIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El límite de intentos de acceso «por origen» tiene que distinguir a los
 * clientes aunque todos lleguen a través del mismo proxy.
 *
 * <h2>El fallo que cubre</h2>
 * Desde ADR-009, en producción todo entra por Caddy. Sin procesar las cabeceras
 * reenviadas, {@code getRemoteAddr()} devolvía siempre la IP del proxy, y
 * {@link LoginAttemptService} contaba los fallos de todo el Centro con una sola
 * clave: veinte contraseñas erradas desde un equipo cualquiera dejaban sin
 * acceso a todos los demás durante quince minutos.
 *
 * <h2>Por qué Tomcat de verdad y no MockMvc</h2>
 * El arreglo es la {@code RemoteIpValve} de Tomcat, que se activa con
 * {@code server.forward-headers-strategy=native}. MockMvc no pasa por Tomcat,
 * así que con él esta prueba saldría en verde tanto con el arreglo como sin él.
 * Aquí se arranca el servidor en un puerto real y se le habla por HTTP. Las
 * peticiones salen de 127.0.0.1, que la válvula trata como proxy interno: es la
 * misma situación que la de Caddy dentro de la red de Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "server.forward-headers-strategy=native")
class OrigenDetrasDelProxyIntegrationTest extends PruebaDeIntegracion {

    @LocalServerPort
    private int puerto;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void cadaClienteDetrasDelProxyTieneSuPropioContadorDeIntentos() throws Exception {
        String atacante = "203.0.113.10";
        // Un correo distinto en cada intento: así solo se acumula el contador
        // por origen, que es el que esta prueba quiere ver, y no el de correo.
        for (int i = 0; i < 20; i++) {
            assertThat(intentarEntrar("nadie" + i + "@soy.sena.edu.co", "NoEsLaClave123*", atacante))
                    .as("intento fallido %d desde el atacante", i + 1)
                    .isEqualTo(401);
        }
        assertThat(intentarEntrar("otro@soy.sena.edu.co", "NoEsLaClave123*", atacante))
                .as("el atacante queda bloqueado por origen")
                .isEqualTo(429);

        // El mismo proxy, otro equipo del Centro. Antes del arreglo esto era
        // 429: el bloqueo del atacante caía sobre la IP del proxy, que era la
        // de todos.
        assertThat(intentarEntrar("supervisor@soy.sena.edu.co", "Supervisor123*", "203.0.113.20"))
                .as("otro cliente detrás del mismo proxy sigue entrando")
                .isEqualTo(200);
    }

    /**
     * La propiedad tiene que estar en el perfil de producción y en ningún otro.
     * En desarrollo el backend publica su puerto y lo que entra desde la
     * máquina anfitriona llega con la IP de la pasarela de Docker, que la
     * válvula considera interna: activarla ahí permitiría a cualquiera elegir
     * su propio origen escribiendo la cabecera.
     */
    @Test
    void soloElPerfilDeProduccionLeeLasCabecerasDelProxy() throws IOException {
        Properties produccion = PropertiesLoaderUtils.loadProperties(
                new ClassPathResource("application-prod.properties"));
        Properties comun = PropertiesLoaderUtils.loadProperties(
                new ClassPathResource("application.properties"));

        assertThat(produccion.getProperty("server.forward-headers-strategy")).isEqualTo("native");
        assertThat(comun.getProperty("server.forward-headers-strategy")).isNull();
    }

    private int intentarEntrar(String correo, String clave, String ipDelCliente) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", ipDelCliente)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(correo, clave)))
                .build();
        return http.send(peticion, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
