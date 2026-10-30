package co.sena.sicot;

import co.sena.sicot.config.HechoSinConexionInterceptor;
import co.sena.sicot.dto.auth.AuthResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lo que el servidor tiene que garantizar para que la cola de cambios de
 * ADR-015 funcione: que lo hecho sin red llegue con su hora declarada aparte de
 * la del servidor, que reenviarlo no duplique nada, y que una cabecera rota no
 * toque el expediente.
 *
 * <p>La cola vive en el cliente y reenvía contra los mismos endpoints de
 * siempre. Por eso estas pruebas no necesitan nada nuevo del servidor salvo la
 * cabecera: lo que comprueban es que esos endpoints ya se comportan como la
 * cola necesita.
 */
class TrabajoSinConexionIntegrationTest extends PruebaDeIntegracion {

    private static final String HECHO_EN = "2026-10-30T14:05:00Z";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String supervisorToken;
    private long contratoId;
    private long subpaso22;

    @BeforeEach
    void unContratoConSupervisor() throws Exception {
        String gestionToken = login("gestion@soy.sena.edu.co", "Gestion123*");
        supervisorToken = login("supervisor@soy.sena.edu.co", "Supervisor123*");
        long supervisorId = objectMapper.readTree(
                loginBody("supervisor@soy.sena.edu.co", "Supervisor123*")).get("usuarioId").asLong();

        String crear = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + gestionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"CO1.PCCNTR.SINRED","objeto":"Trabajo sin conexión",
                                 "valor":1000000,"fechaInicio":"2026-10-01","fechaFin":"2026-12-31"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        contratoId = objectMapper.readTree(crear).get("id").asLong();
        mockMvc.perform(patch("/api/contratos/{id}/supervisor", contratoId)
                        .header("Authorization", "Bearer " + gestionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supervisorId\":" + supervisorId + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/contratos/{id}/estado", contratoId)
                        .header("Authorization", "Bearer " + gestionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ACTIVO\"}"))
                .andExpect(status().isOk());

        // El 2.2 (obligaciones de SST y ambientales) no exige documentos: se
        // puede cerrar directamente, que es lo que hace falta para mirar solo la
        // cabecera y no los requisitos del paso.
        JsonNode sub = etapas().get(1).get("subEtapas").get(1);
        assertThat(sub.get("codigo").asString()).isEqualTo("2.2");
        subpaso22 = sub.get("id").asLong();
    }

    @Test
    void loHechoSinRedQuedaConLaHoraDelEquipoYAparteLaDelServidor() throws Exception {
        cerrar22(HECHO_EN).andExpect(status().isOk());

        JsonNode registro = registroDelCierre();
        assertThat(registro.get("hechoSinConexionEn").asString()).isEqualTo(HECHO_EN);
        // La hora del servidor sigue ahí y es otra: la de ahora, no la declarada.
        assertThat(registro.get("fecha").asString()).isNotEqualTo(HECHO_EN);
    }

    @Test
    void loHechoConConexionNoTraeHoraDeclarada() throws Exception {
        cerrar22(null).andExpect(status().isOk());

        assertThat(registroDelCierre().get("hechoSinConexionEn").isNull()).isTrue();
    }

    /**
     * La red puede cortarse después de que el servidor aplicó el cambio y antes
     * de que la respuesta llegara. La cola no sabe cuál de las dos pasó y
     * reenvía: el segundo envío tiene que dar el mismo resultado y no dejar una
     * segunda constancia en el expediente.
     */
    @Test
    void reenviarElMismoCierreNoLoDuplica() throws Exception {
        cerrar22(HECHO_EN).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("COMPLETADA"));
        cerrar22(HECHO_EN).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("COMPLETADA"));

        assertThat(registrosDel22()).hasSize(1);
    }

    @Test
    void reenviarLasMismasFechasDeEntregaDejaElMismoAcuerdo() throws Exception {
        String cuerpo = """
                {"entregas":[{"fecha":"2026-11-20","descripcion":"Primera entrega del mobiliario"}]}
                """;
        String primera = guardarEntregas(cuerpo);
        String segunda = guardarEntregas(cuerpo);

        assertThat(objectMapper.readTree(segunda).get("entregas"))
                .isEqualTo(objectMapper.readTree(primera).get("entregas"));
        assertThat(objectMapper.readTree(segunda).get("entregas")).hasSize(1);
    }

    @Test
    void unaHoraMalEscritaSeRechazaSinTocarElPaso() throws Exception {
        cerrar22("30/10/2026 9:05")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString(HechoSinConexionInterceptor.CABECERA)));

        JsonNode sub = etapas().get(1).get("subEtapas").get(1);
        assertThat(sub.get("estado").asString()).isNotEqualTo("COMPLETADA");
        assertThat(registrosDel22()).isEmpty();
    }

    /**
     * La app de escritorio y el APK llaman entre orígenes: si la comprobación
     * previa de CORS no permite la cabecera, el navegador no envía el cambio y
     * la cola se queda llena para siempre sin un solo error del servidor.
     */
    @Test
    void laComprobacionPreviaDeCorsPermiteLaCabecera() throws Exception {
        mockMvc.perform(options("/api/subetapas/{id}/estado", subpaso22)
                        .header("Origin", "http://tauri.localhost")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "authorization,content-type,x-sicot-hecho-sin-conexion"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsStringIgnoringCase(HechoSinConexionInterceptor.CABECERA)));
    }

    // ── ayudantes ──────────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.ResultActions cerrar22(String hechoEn) throws Exception {
        var peticion = patch("/api/subetapas/{id}/estado", subpaso22)
                .header("Authorization", "Bearer " + supervisorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"COMPLETADA\"}");
        if (hechoEn != null) {
            peticion.header(HechoSinConexionInterceptor.CABECERA, hechoEn);
        }
        return mockMvc.perform(peticion);
    }

    private String guardarEntregas(String cuerpo) throws Exception {
        return mockMvc.perform(put("/api/contratos/{id}/entregas-acordadas", contratoId)
                        .header("Authorization", "Bearer " + supervisorToken)
                        .header(HechoSinConexionInterceptor.CABECERA, HECHO_EN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private JsonNode etapas() throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/contratos/{id}/etapas", contratoId)
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private List<JsonNode> registrosDel22() throws Exception {
        JsonNode todos = objectMapper.readTree(mockMvc.perform(get("/api/contratos/{id}/registros", contratoId)
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        List<JsonNode> del22 = new ArrayList<>();
        for (JsonNode r : todos) {
            if (r.get("accion").asString().startsWith("SUBETAPA_")
                    && r.get("descripcion").asString().contains("Subetapa 2.2 ")) {
                del22.add(r);
            }
        }
        return del22;
    }

    private JsonNode registroDelCierre() throws Exception {
        List<JsonNode> del22 = registrosDel22();
        assertThat(del22).hasSize(1);
        return del22.get(0);
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
