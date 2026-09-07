package co.sena.sicot.ia;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * La guarda que avisa al arrancar si el modelo configurado no está descargado.
 *
 * <p>Su javadoc explica que existe porque el fallo ya ocurrió una vez: se cambió
 * {@code OLLAMA_MODEL} y nadie descargó el modelo nuevo, así que Ollama no falló
 * al arrancar sino en la primera petición real de un usuario, con un 503 y días
 * después del despliegue. Una guarda escrita contra una regresión conocida y sin
 * prueba propia es una guarda de la que nadie sabe si sigue funcionando — que es
 * exactamente la situación en la que estaba.
 *
 * <p>Lo que se fija aquí son las dos mitades de su contrato: que <b>avisa</b>
 * cuando el modelo falta, y que <b>no impide arrancar</b> cuando Ollama no
 * responde. La segunda importa tanto como la primera: la IA es opcional en
 * SICOT, y tumbar el sistema entero por un modelo ausente cambiaría un fallo
 * parcial por uno total.
 */
class VerificacionDelModeloIaTest {

    private HttpServer servidor;
    private ListAppender<ILoggingEvent> registro;
    private ch.qos.logback.classic.Logger logger;

    private volatile String catalogoDeModelos = "{\"models\":[]}";
    private volatile int codigoRespuesta = 200;

    @BeforeEach
    void levantarUnOllamaFalsoYEscucharElLog() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/api/tags", this::responder);
        servidor.start();

        registro = new ListAppender<>();
        registro.start();
        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(VerificacionDelModeloIa.class);
        logger.addAppender(registro);
    }

    @AfterEach
    void apagarlo() {
        logger.detachAppender(registro);
        servidor.stop(0);
    }

    private void responder(HttpExchange intercambio) throws IOException {
        byte[] cuerpo = catalogoDeModelos.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(codigoRespuesta, cuerpo.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(cuerpo);
        }
    }

    private VerificacionDelModeloIa verificacionPara(String modelo) {
        return new VerificacionDelModeloIa("http://127.0.0.1:" + servidor.getAddress().getPort(), modelo);
    }

    private String mensajesDeNivel(Level nivel) {
        return registro.list.stream()
                .filter(e -> e.getLevel() == nivel)
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Avisa cuando el modelo falta, y dice exactamente qué ejecutar
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void avisaConElComandoExactoCuandoElModeloConfiguradoNoEstaDescargado() {
        catalogoDeModelos = "{\"models\":[{\"name\":\"llama3.2:1b\"},{\"name\":\"nomic-embed-text:latest\"}]}";

        verificacionPara("qwen2.5:7b").verificarModeloConfigurado();

        assertThat(mensajesDeNivel(Level.WARN))
                .contains("NO está descargado")
                .contains("ollama pull qwen2.5:7b")
                .contains("llama3.2:1b");
    }

    @Test
    void unCatalogoVacioSeReportaComoNingunoYNoComoUnaListaEnBlanco() {
        catalogoDeModelos = "{\"models\":[]}";

        verificacionPara("qwen2.5:7b").verificarModeloConfigurado();

        assertThat(mensajesDeNivel(Level.WARN)).contains("(ninguno)");
    }

    @Test
    void confirmaEnSilencioCuandoElModeloSiEstaDisponible() {
        catalogoDeModelos = "{\"models\":[{\"name\":\"qwen2.5:7b\"}]}";

        verificacionPara("qwen2.5:7b").verificarModeloConfigurado();

        assertThat(mensajesDeNivel(Level.INFO)).contains("disponible en Ollama");
        assertThat(mensajesDeNivel(Level.WARN)).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // La comparación de nombres, que es donde está el falso negativo fácil
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ollama lista los modelos con la etiqueta explícita pero acepta peticiones
     * sin ella, resolviéndola a {@code :latest}. Sin esta equivalencia, una
     * configuración perfectamente válida produciría un aviso alarmante cada
     * arranque, y un aviso que cría lobos deja de leerse.
     */
    @Test
    void unModeloSinEtiquetaSeReconoceEnSuFormaLatest() {
        catalogoDeModelos = "{\"models\":[{\"name\":\"qwen2.5:latest\"}]}";

        verificacionPara("qwen2.5").verificarModeloConfigurado();

        assertThat(mensajesDeNivel(Level.INFO)).contains("disponible en Ollama");
        assertThat(mensajesDeNivel(Level.WARN)).isEmpty();
    }

    /**
     * La equivalencia anterior no puede irse de las manos: pedir {@code :7b} y
     * tener {@code :latest} descargado son dos modelos distintos, y darlo por
     * bueno devolvería el fallo original —descubrirlo en la primera petición
     * real— con la guarda puesta y en verde.
     */
    @Test
    void unaEtiquetaDistintaNoSeDaPorBuena() {
        catalogoDeModelos = "{\"models\":[{\"name\":\"qwen2.5:latest\"}]}";

        verificacionPara("qwen2.5:7b").verificarModeloConfigurado();

        assertThat(mensajesDeNivel(Level.WARN)).contains("ollama pull qwen2.5:7b");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Nunca impide arrancar
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void conOllamaApagadoNoLanzaNadaYLoRegistraComoEscenarioNormal() throws IOException {
        servidor.stop(0);

        assertThatCode(() -> verificacionPara("qwen2.5:7b").verificarModeloConfigurado())
                .doesNotThrowAnyException();

        assertThat(mensajesDeNivel(Level.INFO)).contains("No se pudo consultar el catálogo");
        assertThat(mensajesDeNivel(Level.WARN)).isEmpty();

        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.start();
    }

    @Test
    void unaRespuestaMalformadaDeOllamaTampocoTumbaElArranque() {
        catalogoDeModelos = "esto no es json";

        assertThatCode(() -> verificacionPara("qwen2.5:7b").verificarModeloConfigurado())
                .doesNotThrowAnyException();
    }

    @Test
    void unCatalogoSinLaClaveModelsSeTrataComoCatalogoVacio() {
        catalogoDeModelos = "{}";

        assertThatCode(() -> verificacionPara("qwen2.5:7b").verificarModeloConfigurado())
                .doesNotThrowAnyException();

        assertThat(mensajesDeNivel(Level.WARN)).contains("(ninguno)");
    }
}
