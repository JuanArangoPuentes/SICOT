package co.sena.sicot.ia;

import co.sena.sicot.entity.Usuario;
import co.sena.sicot.entity.enums.Rol;
import co.sena.sicot.exception.DemasiadasSolicitudesException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Única puerta de salida del backend hacia Ollama, y por tanto el sitio donde se
 * decide qué ve un usuario cuando la IA falla.
 *
 * <p>Las pruebas levantan un Ollama falso —un servidor HTTP del propio JDK en un
 * puerto libre— en vez de sustituir el cliente por un doble. La razón es que lo
 * que hay que fijar aquí es justo lo que un doble se saltaría: qué se manda por
 * el cable, qué ocurre cuando el otro extremo responde mal, y qué mensaje llega
 * al usuario final. Ninguna de estas pruebas necesita un Ollama instalado.
 */
class OllamaClientTest {

    private static final String MODELO = "qwen2.5:7b";

    private HttpServer servidor;
    private final List<String> peticionesRecibidas = new CopyOnWriteArrayList<>();
    private final ObjectMapper json = new ObjectMapper();

    private volatile int codigoRespuesta = 200;
    private volatile String cuerpoRespuesta = "{\"response\":\"texto redactado por el modelo\"}";

    @BeforeEach
    void levantarUnOllamaFalso() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/api/generate", this::responder);
        servidor.start();
    }

    @AfterEach
    void apagarlo() {
        servidor.stop(0);
        SecurityContextHolder.clearContext();
    }

    private void responder(HttpExchange intercambio) throws IOException {
        peticionesRecibidas.add(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] cuerpo = cuerpoRespuesta.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(codigoRespuesta, cuerpo.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(cuerpo);
        }
    }

    private String urlDelFalso() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    private OllamaClient cliente() {
        return new OllamaClient(urlDelFalso(), MODELO, 5, new LimitadorDeUsoIa(2, 100));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Camino normal y contrato de la petición
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void devuelveElTextoQueGeneraElModelo() {
        assertThat(cliente().generar("redacta un acta", false))
                .isEqualTo("texto redactado por el modelo");
    }

    @Test
    void leExigeJsonAlModeloSoloCuandoQuienLlamaLoPide() throws Exception {
        OllamaClient cliente = cliente();

        cliente.generar("extrae los campos", true);
        cliente.generar("redacta el acta", false);

        JsonNode conJson = json.readTree(peticionesRecibidas.get(0));
        JsonNode sinJson = json.readTree(peticionesRecibidas.get(1));

        assertThat(conJson.get("format").asText()).isEqualTo("json");
        assertThat(sinJson.get("format").isNull()).isTrue();
    }

    @Test
    void mandaElModeloConfiguradoYNuncaPideRespuestaEnStreaming() throws Exception {
        cliente().generar("hola", false);

        JsonNode peticion = json.readTree(peticionesRecibidas.get(0));
        assertThat(peticion.get("model").asText()).isEqualTo(MODELO);
        assertThat(peticion.get("stream").asBoolean()).isFalse();
        assertThat(peticion.get("prompt").asText()).isEqualTo("hola");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fallo honesto: nunca se fabrica una respuesta para disimular
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void unaRespuestaVaciaDeOllamaEsUnFalloYNoUnaCadenaVacia() {
        cuerpoRespuesta = "{}";

        assertThatThrownBy(() -> cliente().generar("hola", false))
                .isInstanceOf(IaNoDisponibleException.class)
                .hasMessageContaining("vacío");
    }

    @Test
    void unErrorDelServidorDeIaLlegaAlUsuarioComoIaNoDisponible() {
        codigoRespuesta = 500;
        cuerpoRespuesta = "{\"error\":\"model runner crashed\"}";

        assertThatThrownBy(() -> cliente().generar("hola", false))
                .isInstanceOf(IaNoDisponibleException.class)
                .hasMessageContaining("no está disponible");
    }

    @Test
    void siOllamaNoEstaEscuchandoElFalloSigueSiendoDeNegocioYNoUnaExcepcionCruda() throws IOException {
        int puertoQueQuedaLibre = servidor.getAddress().getPort();
        servidor.stop(0);

        OllamaClient sinServidor = new OllamaClient(
                "http://127.0.0.1:" + puertoQueQuedaLibre, MODELO, 2, new LimitadorDeUsoIa(2, 100));

        assertThatThrownBy(() -> sinServidor.generar("hola", false))
                .isInstanceOf(IaNoDisponibleException.class);

        // Se vuelve a levantar para que el @AfterEach tenga algo que parar.
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.start();
    }

    /**
     * El comentario de {@code OllamaClient.llamar} dice que la URL interna y el
     * modelo van SOLO al log del servidor, porque el mensaje de esta excepción
     * lo propaga {@code GlobalExceptionHandler} tal cual hasta el navegador del
     * usuario. Es topología interna: no le corresponde verla a un funcionario, y
     * a un atacante le ahorra trabajo de reconocimiento.
     */
    @Test
    void elMensajeQueVeElUsuarioNoRevelaLaUrlInternaNiElModelo() {
        codigoRespuesta = 503;

        assertThatThrownBy(() -> cliente().generar("hola", false))
                .isInstanceOf(IaNoDisponibleException.class)
                .satisfies(e -> assertThat(e.getMessage())
                        .doesNotContain("127.0.0.1")
                        .doesNotContain(String.valueOf(servidor.getAddress().getPort()))
                        .doesNotContain(MODELO)
                        .doesNotContain("/api/generate"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // El límite de uso se aplica AQUÍ, que es el único punto de paso
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void elLimiteDeFrecuenciaCortaAntesDeGastarUnaLlamadaAOllama() {
        autenticarComo(7L);
        OllamaClient cliente = new OllamaClient(urlDelFalso(), MODELO, 5, new LimitadorDeUsoIa(2, 1));

        cliente.generar("primera", false);

        assertThatThrownBy(() -> cliente.generar("segunda", false))
                .isInstanceOf(DemasiadasSolicitudesException.class);

        // Lo que importa: la petición rechazada nunca llegó al modelo. Si el
        // límite se aplicara después de llamar, no protegería de nada.
        assertThat(peticionesRecibidas).hasSize(1);
    }

    @Test
    void cadaLlamadaReponeSuCupoDeConcurrencia() {
        OllamaClient cliente = new OllamaClient(urlDelFalso(), MODELO, 1, new LimitadorDeUsoIa(1, 100));

        List<String> respuestas = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            respuestas.add(cliente.generar("pregunta " + i, false));
        }

        // Con un solo permiso, si alguna llamada no lo liberara, la siguiente se
        // quedaría sin cupo y el sistema rechazaría toda petición de IA hasta el
        // reinicio, sin ningún motivo visible.
        assertThat(respuestas).hasSize(5)
                .allMatch(r -> r.equals("texto redactado por el modelo"));
    }

    private void autenticarComo(long id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombre("Supervisor " + id);
        usuario.setEmail("supervisor" + id + "@soy.sena.edu.co");
        usuario.setRol(Rol.SUPERVISOR);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario, null, List.of()));
    }
}
