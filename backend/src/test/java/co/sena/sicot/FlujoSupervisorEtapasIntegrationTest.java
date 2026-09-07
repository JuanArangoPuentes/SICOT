package co.sena.sicot;

import co.sena.sicot.dto.auth.AuthResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de extremo a extremo del flujo del supervisor: crear un contrato,
 * asignarlo, y recorrer sus 6 etapas / 27 subetapas GCCON-P-010 marcando cada
 * subetapa como COMPLETADA hasta que las 6 etapas quedan al 100 %.
 *
 * Existe para blindar la tarea de transiciones de estado: la validación
 * introducida en {@code TransicionesDeEstado} <b>no</b> debe romper el único
 * camino que un supervisor recorre de verdad. {@code PENDIENTE → COMPLETADA}
 * (saltando {@code EN_CURSO}) es parte de ese camino y tiene que seguir pasando.
 */
class FlujoSupervisorEtapasIntegrationTest extends PruebaDeIntegracion {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void elSupervisorRecorreLas6EtapasY27SubetapasDePrincipioAFin() throws Exception {
        String gestionToken = login("gestion@soy.sena.edu.co", "Gestion123*");
        String supervisorToken = login("supervisor@soy.sena.edu.co", "Supervisor123*");
        long supervisorId = objectMapper.readTree(
                loginBody("supervisor@soy.sena.edu.co", "Supervisor123*")).get("usuarioId").asLong();

        // 1. Crear contrato y asignarle el supervisor.
        String crear = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + gestionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"CO1.PCCNTR.FLUJO27","objeto":"Flujo completo del supervisor",
                                 "valor":1000000,"fechaInicio":"2026-01-01","fechaFin":"2026-12-31"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long contratoId = objectMapper.readTree(crear).get("id").asLong();

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

        // 2. Estructura inicial: 6 etapas, 27 subetapas.
        JsonNode etapas = leerEtapas(contratoId, supervisorToken);
        assertThat(etapas).hasSize(6);
        assertThat(contarSubetapas(etapas)).isEqualTo(27);

        // 3. Recorrer cada subetapa de cada etapa, en orden, marcándola COMPLETADA.
        //    Igual que el panel del supervisor: la mayoría van PENDIENTE -> COMPLETADA
        //    directo (nunca pasan por EN_CURSO).
        for (JsonNode etapa : etapas) {
            for (JsonNode subetapa : etapa.get("subEtapas")) {
                long subetapaId = subetapa.get("id").asLong();
                mockMvc.perform(patch("/api/subetapas/{id}/estado", subetapaId)
                                .header("Authorization", "Bearer " + supervisorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"estado\":\"COMPLETADA\"}"))
                        .andExpect(status().isOk());
            }
        }

        // 4. Las 6 etapas quedan COMPLETADA al 100 %.
        JsonNode etapasFinales = leerEtapas(contratoId, supervisorToken);
        for (JsonNode etapa : etapasFinales) {
            assertThat(etapa.get("estado").asText())
                    .as("etapa %s", etapa.get("numero").asInt())
                    .isEqualTo("COMPLETADA");
            assertThat(etapa.get("porcentaje").asInt()).isEqualTo(100);
        }

        // 5. Cada subetapa dejó su traza de avance; ninguna de retroceso.
        String registros = mockMvc.perform(get("/api/contratos/{id}/registros", contratoId)
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long avances = contar(registros, "SUBETAPA_AVANZADA");
        long retrocesos = contar(registros, "SUBETAPA_REVERTIDA") + contar(registros, "ETAPA_RETROCEDIDA");
        assertThat(avances).isEqualTo(27);
        assertThat(retrocesos).isZero();

        // 6. El contrato NO se cierra solo al completar las 6 etapas: sigue ACTIVO.
        mockMvc.perform(get("/api/contratos/{id}", contratoId)
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }

    private JsonNode leerEtapas(long contratoId, String token) throws Exception {
        String json = mockMvc.perform(get("/api/contratos/{id}/etapas", contratoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private int contarSubetapas(JsonNode etapas) {
        int total = 0;
        for (JsonNode etapa : etapas) {
            total += etapa.get("subEtapas").size();
        }
        return total;
    }

    private long contar(String texto, String aguja) {
        long total = 0;
        int i = texto.indexOf(aguja);
        while (i != -1) {
            total++;
            i = texto.indexOf(aguja, i + aguja.length());
        }
        return total;
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
