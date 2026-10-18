package co.sena.sicot;

import co.sena.sicot.dto.auth.AuthResponse;
import co.sena.sicot.ia.IaNoDisponibleException;
import co.sena.sicot.ia.OllamaClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La respuesta en vivo del Copiloto ({@code POST /copiloto/chat/en-vivo}):
 * el texto del modelo llega por trozos y termina con la respuesta completa,
 * con las mismas reglas de acceso y los mismos errores que {@code POST /chat}.
 *
 * <p>Existe porque en el portátil del Centro el modelo escribe a unos 3 tokens
 * por segundo: sin esto el supervisor miraba «Pensando…» medio minuto o más
 * por cada respuesta (ADR-006, revisión del 18-10-2026).
 */
class CopilotoEnVivoIntegrationTest extends PruebaDeIntegracion {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OllamaClient ollamaClient;

    @Test
    void elTextoDelModeloLlegaPorTrozosYTerminaConLaRespuestaCompleta() throws Exception {
        given(ollamaClient.generarEnVivo(anyString(), any())).willAnswer(inv -> {
            Consumer<String> alEscribir = inv.getArgument(1);
            alEscribir.accept("La póliza ");
            alEscribir.accept("se verifica en SECOP II.");
            return "La póliza se verifica en SECOP II.";
        });
        long contratoId = contratoDelSupervisor("CO1.PCCNTR.EN-VIVO-1");

        List<JsonNode> eventos = eventos(preguntar(contratoId, "¿qué riesgos ve en la póliza de este contrato?",
                login("supervisor@soy.sena.edu.co", "Supervisor123*")));

        assertThat(eventos).extracting(e -> e.get("tipo").asText())
                .containsExactly("inicio", "texto", "texto", "fin");
        assertThat(eventos.get(1).get("t").asText()).isEqualTo("La póliza ");
        JsonNode fin = eventos.get(3).get("respuesta");
        assertThat(fin.get("respuesta").asText()).isEqualTo("La póliza se verifica en SECOP II.");
        assertThat(fin.get("fuente").asText()).isEqualTo("MODELO");
    }

    /** Lo que contesta un atajo no pasa por el modelo: llega entero en el evento final. */
    @Test
    void unAtajoLlegaEnteroEnElEventoFinalSinLlamarAlModelo() throws Exception {
        long contratoId = contratoDelSupervisor("CO1.PCCNTR.EN-VIVO-2");

        List<JsonNode> eventos = eventos(preguntar(contratoId, "¿En qué paso voy?",
                login("supervisor@soy.sena.edu.co", "Supervisor123*")));

        assertThat(eventos).extracting(e -> e.get("tipo").asText()).containsExactly("inicio", "fin");
        assertThat(eventos.get(1).get("respuesta").get("fuente").asText()).isEqualTo("SISTEMA");
        assertThat(eventos.get(1).get("respuesta").get("respuesta").asText()).contains("Está en el paso 2");
        verifyNoInteractions(ollamaClient);
    }

    /** Un fallo de Ollama llega como evento con el mismo código y el mismo texto que POST /chat. */
    @Test
    void siOllamaNoEstaElErrorLlegaComoEventoConSuCodigo() throws Exception {
        given(ollamaClient.generarEnVivo(anyString(), any()))
                .willThrow(new IaNoDisponibleException("El servicio de IA no está disponible en este momento."));
        long contratoId = contratoDelSupervisor("CO1.PCCNTR.EN-VIVO-3");

        List<JsonNode> eventos = eventos(preguntar(contratoId, "¿qué riesgos ve en este contrato?",
                login("supervisor@soy.sena.edu.co", "Supervisor123*")));

        JsonNode ultimo = eventos.get(eventos.size() - 1);
        assertThat(ultimo.get("tipo").asText()).isEqualTo("error");
        assertThat(ultimo.get("status").asInt()).isEqualTo(503);
        assertThat(ultimo.get("mensaje").asText()).contains("no está disponible");
    }

    /**
     * El acceso se comprueba antes de abrir la respuesta: un contrato ajeno sale
     * con su 404 de siempre, no como un error dentro de un 200.
     */
    @Test
    void unContratoAjenoEsUn404AntesDeAbrirLaRespuesta() throws Exception {
        String admin = login("administrador@soy.sena.edu.co", "Admin123*");
        mockMvc.perform(post("/api/usuarios")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"Supervisor Ajeno En Vivo","email":"ajeno.envivo@soy.sena.edu.co",
                         "password":"AjenoVivo123*","telefono":"3000000000","rol":"SUPERVISOR"}
                        """));
        long contratoId = contratoDelSupervisor("CO1.PCCNTR.EN-VIVO-4");

        mockMvc.perform(post("/api/contratos/{id}/copiloto/chat/en-vivo", contratoId)
                        .header("Authorization", "Bearer " + login("ajeno.envivo@soy.sena.edu.co", "AjenoVivo123*"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pregunta\":\"hola\"}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(ollamaClient);
    }

    private MvcResult preguntar(long contratoId, String pregunta, String token) throws Exception {
        MvcResult abierta = mockMvc.perform(post("/api/contratos/{id}/copiloto/chat/en-vivo", contratoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("pregunta", pregunta))))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(abierta))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/x-ndjson"))
                .andReturn();
    }

    private List<JsonNode> eventos(MvcResult r) throws Exception {
        List<JsonNode> eventos = new ArrayList<>();
        for (String linea : r.getResponse().getContentAsString(StandardCharsets.UTF_8).split("\n")) {
            if (!linea.isBlank()) {
                eventos.add(objectMapper.readTree(linea));
            }
        }
        return eventos;
    }

    private long contratoDelSupervisor(String numero) throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long supervisorId = objectMapper.readTree(loginBody("supervisor@soy.sena.edu.co", "Supervisor123*"))
                .get("usuarioId").asLong();
        String creado = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"%s","objeto":"Prueba de la respuesta en vivo del Copiloto",
                                 "valor":1000000,"fechaInicio":"2026-01-01","fechaFin":"2026-06-30",
                                 "supervisorId":%d}
                                """.formatted(numero, supervisorId)))
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
