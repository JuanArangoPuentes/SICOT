package co.sena.sicot;

import co.sena.sicot.dto.auth.AuthResponse;
import co.sena.sicot.entity.Documento;
import co.sena.sicot.entity.enums.EstadoDocumento;
import co.sena.sicot.entity.enums.TipoDocumento;
import co.sena.sicot.repository.ContratoRepository;
import co.sena.sicot.repository.DocumentoRepository;
import co.sena.sicot.repository.SubetapaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Un paso del proceso tiene un solo documento formal firmado.
 *
 * <p>La revisión adversarial del 24-09-2026 mostró dos caminos a un duplicado:
 * un reintento tras un corte de conexión creaba un segundo borrador (que la
 * bandeja mostraba para siempre como «documento sin firmar»), y firmar después
 * ese borrador dejaba dos actas firmadas del mismo paso. Sin notas, la
 * generación no llama al modelo, así que esta prueba no necesita Ollama.
 */
class UnSoloDocumentoFirmadoPorPasoIntegrationTest extends PruebaDeIntegracion {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentoRepository documentoRepository;

    @Autowired
    private ContratoRepository contratoRepository;

    @Autowired
    private SubetapaRepository subetapaRepository;

    private String supervisor;
    private long contratoId;
    private long subetapaId;

    @BeforeEach
    void preparar() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        supervisor = login("supervisor@soy.sena.edu.co", "Supervisor123*");
        String admin = login("administrador@soy.sena.edu.co", "Admin123*");
        long idSupervisor = objectMapper.readTree(loginBody("supervisor@soy.sena.edu.co", "Supervisor123*"))
                .get("usuarioId").asLong();

        String creado = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"CO1.PCCNTR.UN-SOLO-DOC","objeto":"Contrato de la prueba de duplicados",
                                 "valor":1000000,"fechaInicio":"2026-01-01","fechaFin":"2026-12-31",
                                 "supervisorId":%d}""".formatted(idSupervisor)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        contratoId = objectMapper.readTree(creado).get("id").asLong();

        JsonNode etapas = objectMapper.readTree(mockMvc.perform(get("/api/contratos/{id}/etapas", contratoId)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        subetapaId = etapas.get(1).get("subEtapas").get(6).get("id").asLong(); // 2.7

        String miFirma = mockMvc.perform(get("/api/firmas/mia").header("Authorization", "Bearer " + supervisor))
                .andReturn().getResponse().getContentAsString();
        if (!objectMapper.readTree(miFirma).get("tieneFirmaActiva").asBoolean()) {
            mockMvc.perform(post("/api/firmas")
                            .header("Authorization", "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"usuarioId\":" + idSupervisor + "}"))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void unReintentoDevuelveElMismoBorradorYUnaVezFirmadoNoSeGeneraOtro() throws Exception {
        long primero = generar();
        long reintento = generar();
        assertThat(reintento).isEqualTo(primero);

        mockMvc.perform(post("/api/contratos/{c}/documentos/{id}/firmar", contratoId, primero)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/contratos/{c}/documentos/generar", contratoId)
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"ACTA_INICIO\",\"subetapaId\":" + subetapaId + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Ya hay un «Acta de Inicio» firmado")));
    }

    @Test
    void unBorradorDuplicadoHeredadoNoSePuedeFirmarSiYaHayUnoFirmado() throws Exception {
        long primero = generar();
        mockMvc.perform(post("/api/contratos/{c}/documentos/{id}/firmar", contratoId, primero)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk());

        // Un borrador duplicado como los que dejaba la versión anterior.
        Documento duplicado = new Documento();
        duplicado.setContrato(contratoRepository.findById(contratoId).orElseThrow());
        duplicado.setSubetapa(subetapaRepository.findById(subetapaId).orElseThrow());
        duplicado.setNombre(documentoRepository.findById(primero).orElseThrow().getNombre());
        duplicado.setTipo(TipoDocumento.PDF);
        duplicado.setContentType("application/pdf");
        duplicado.setContenido("%PDF-1.4 duplicado".getBytes(StandardCharsets.UTF_8));
        duplicado.setTamanioBytes(18L);
        duplicado.setEstado(EstadoDocumento.PENDIENTE);
        duplicado.setGeneradoPorIa(true);
        long idDuplicado = documentoRepository.save(duplicado).getId();

        mockMvc.perform(post("/api/contratos/{c}/documentos/{id}/firmar", contratoId, idDuplicado)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Ya hay un")));
    }

    private long generar() throws Exception {
        String r = mockMvc.perform(post("/api/contratos/{c}/documentos/generar", contratoId)
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"ACTA_INICIO\",\"subetapaId\":" + subetapaId + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(r).get("id").asLong();
    }

    private String login(String email, String password) throws Exception {
        return objectMapper.readValue(loginBody(email, password), AuthResponse.class).token();
    }

    private String loginBody(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
