package co.sena.sicot;

import co.sena.sicot.dto.auth.AuthResponse;
import co.sena.sicot.ia.OllamaClient;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Que la respuesta en vivo del Copiloto salga de verdad en vivo, por la red.
 *
 * <h2>El fallo que vigila</h2>
 * Al pasar a Spring Boot 4 (ADR-014) la respuesta en vivo siguió funcionando
 * en todas las pruebas, pero en un servidor real llegaba entera al final: el
 * flujo que Spring 7 le pasa al cuerpo ignora {@code flush()}, y Tomcat
 * retenía los eventos. Se notó en el ensayo del 28-10-2026 midiendo la llegada
 * de cada evento: con Boot 3.5, 111 llegadas distintas y la primera palabra a
 * los 20 s; con Boot 4, 116 eventos en un solo instante, a los 57 s.
 *
 * <h2>Por qué Tomcat de verdad y no MockMvc</h2>
 * MockMvc junta la respuesta en memoria y la entrega cuando termina, así que
 * no distingue una respuesta en vivo de una retenida: con él, esta prueba
 * pasaría con el fallo y sin él. Aquí el «modelo» escribe un trozo y se queda
 * esperando a que el cliente lo haya recibido antes de terminar. Si el
 * servidor retiene la respuesta, el cliente no ve el trozo hasta que el
 * modelo se cansa de esperar, y la prueba falla.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CopilotoEnVivoPorLaRedIntegrationTest extends PruebaDeIntegracion {

    @LocalServerPort
    private int puerto;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OllamaClient ollamaClient;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void elPrimerTrozoLlegaAlClienteAntesDeQueElModeloTermine() throws Exception {
        CountDownLatch clienteLoRecibio = new CountDownLatch(1);
        AtomicBoolean elModeloSupoQueLlego = new AtomicBoolean();
        given(ollamaClient.generarEnVivo(anyString(), any())).willAnswer(inv -> {
            Consumer<String> alEscribir = inv.getArgument(1);
            alEscribir.accept("La póliza ");
            elModeloSupoQueLlego.set(clienteLoRecibio.await(5, TimeUnit.SECONDS));
            alEscribir.accept("se verifica en SECOP II.");
            return "La póliza se verifica en SECOP II.";
        });
        long contratoId = contratoDelSupervisor();
        String token = login("supervisor@soy.sena.edu.co", "Supervisor123*");

        HttpRequest pregunta = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + puerto + "/api/contratos/" + contratoId + "/copiloto/chat/en-vivo"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(
                        Map.of("pregunta", "¿qué riesgos ve en la póliza de este contrato?"))))
                .build();
        HttpResponse<Stream<String>> respuesta = http.send(pregunta, HttpResponse.BodyHandlers.ofLines());
        assertThat(respuesta.statusCode()).isEqualTo(200);

        StringBuilder tipos = new StringBuilder();
        try (Stream<String> lineas = respuesta.body()) {
            Iterator<String> it = lineas.filter(l -> !l.isBlank()).iterator();
            while (it.hasNext()) {
                String tipo = objectMapper.readTree(it.next()).get("tipo").asString();
                tipos.append(tipo).append(' ');
                if (tipo.equals("texto")) {
                    clienteLoRecibio.countDown();
                }
            }
        }

        assertThat(tipos.toString().trim()).isEqualTo("inicio texto texto fin");
        assertThat(elModeloSupoQueLlego)
                .as("el primer trozo tiene que llegar al cliente mientras el modelo sigue escribiendo, "
                        + "no retenido hasta el final")
                .isTrue();
    }

    private long contratoDelSupervisor() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long supervisorId = objectMapper.readTree(loginBody("supervisor@soy.sena.edu.co", "Supervisor123*"))
                .get("usuarioId").asLong();
        String creado = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"CO1.PCCNTR.EN-VIVO-RED","objeto":"Prueba de la respuesta en vivo por la red",
                                 "valor":1000000,"fechaInicio":"2026-01-01","fechaFin":"2026-06-30",
                                 "supervisorId":%d}
                                """.formatted(supervisorId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(creado).get("id").asLong();
    }

    private String login(String email, String password) throws Exception {
        return objectMapper.readValue(loginBody(email, password), AuthResponse.class).token();
    }

    private String loginBody(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
