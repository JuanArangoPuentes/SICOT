package co.sena.sicot;

import co.sena.sicot.dto.auth.AuthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Cubre el reemplazo del mock de "Firmas Electrónicas" (antes: setTimeout + useState en el
 * frontend, se perdía al refrescar) por persistencia real en /api/firmas.
 */
class FirmaElectronicaIntegrationTest extends PruebaDeIntegracion {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void asignarRevocarYRestaurarFirmaPersisteDeVerdad() throws Exception {
        String adminToken = login("administrador@soy.sena.edu.co", "Admin123*");
        String supervisorBody = loginBody("supervisor@soy.sena.edu.co", "Supervisor123*");
        long supervisorId = objectMapper.readTree(supervisorBody).get("usuarioId").asLong();

        // 1. Asignar firma real — no un mock: queda en la base.
        String crear = mockMvc.perform(post("/api/firmas")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + supervisorId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$.firmaId", startsWith("FIRMA-")))
                .andExpect(jsonPath("$.usuarioId").value(supervisorId))
                .andReturn().getResponse().getContentAsString();
        long firmaId = objectMapper.readTree(crear).get("id").asLong();

        // 2. Sigue ahí en una consulta nueva (la prueba real de que no es estado de React).
        mockMvc.perform(get("/api/firmas").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + firmaId + ")]", hasSize(1)));

        // 3. Revocar
        mockMvc.perform(patch("/api/firmas/{id}/estado", firmaId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false));

        // 4. Restaurar
        mockMvc.perform(patch("/api/firmas/{id}/estado", firmaId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(true));

        // 5. Usuario inexistente -> 404, no crea nada
        mockMvc.perform(post("/api/firmas")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":999999}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void soloAdministradorPuedeAsignarFirmas() throws Exception {
        String gestionToken = login("gestion@soy.sena.edu.co", "Gestion123*");

        mockMvc.perform(post("/api/firmas")
                        .header("Authorization", "Bearer " + gestionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":1}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/firmas").header("Authorization", "Bearer " + gestionToken))
                .andExpect(status().isForbidden());
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
