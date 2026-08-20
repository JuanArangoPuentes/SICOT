package co.sena.sicot;

import co.sena.sicot.dto.auth.AuthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContratoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void flujoCompletoDeContrato() throws Exception {
        String gestionToken = login("gestion@soy.sena.edu.co", "Gestion123*");
        String supervisorToken = login("supervisor@soy.sena.edu.co", "Supervisor123*");

        // 1. Crear contrato (rol GESTION)
        String crear = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + gestionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"CO1.PCCNTR.TEST001","objeto":"Prueba de integración",
                                 "valor":1000000,"fechaInicio":"2026-01-01","fechaFin":"2026-06-30"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.numeroContrato").value("CO1.PCCNTR.TEST001"))
                .andReturn().getResponse().getContentAsString();
        long contratoId = objectMapper.readTree(crear).get("id").asLong();

        // 2. Asignar supervisor y cambiar estado a ACTIVO
        String supervisorBody = loginBody("supervisor@soy.sena.edu.co", "Supervisor123*");
        long supervisorId = objectMapper.readTree(supervisorBody).get("usuarioId").asLong();
        mockMvc.perform(patch("/api/contratos/{id}/supervisor", contratoId)
                        .header("Authorization", "Bearer " + gestionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supervisorId\":" + supervisorId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supervisorId").value(supervisorId));

        mockMvc.perform(patch("/api/contratos/{id}/estado", contratoId)
                        .header("Authorization", "Bearer " + gestionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"estado":"ACTIVO"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVO"));

        // 3. El supervisor puede consultar, pero NO modificar datos generales
        mockMvc.perform(get("/api/contratos/{id}", contratoId)
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supervisorEmail").value("supervisor@soy.sena.edu.co"));

        mockMvc.perform(put("/api/contratos/{id}", contratoId)
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"CO1.PCCNTR.TEST001","objeto":"Sin permiso",
                                 "valor":1000000,"fechaInicio":"2026-01-01","fechaFin":"2026-06-30"}
                                """))
                .andExpect(status().isForbidden());

        // 4. Subetapa inexistente en contrato sin etapas -> 404
        mockMvc.perform(patch("/api/subetapas/999999/estado")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"estado":"COMPLETADA"}
                                """))
                .andExpect(status().isNotFound());

        // 5. Documentos y registros del contrato
        mockMvc.perform(get("/api/contratos/{id}/documentos", contratoId)
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/contratos/{id}/registros", contratoId)
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", notNullValue()))
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(3)));

        // 6. Crear contrato con número duplicado -> 400
        mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + gestionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"CO1.PCCNTR.TEST001","objeto":"Duplicado",
                                 "valor":500000}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearContratoConCamposDeIdentificacionLosPersiste() throws Exception {
        String gestionToken = login("gestion@soy.sena.edu.co", "Gestion123*");

        mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + gestionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"CO1.PCCNTR.TEST-ID","objeto":"Prueba de campos reales",
                                 "valor":5000000,"tipoContrato":"Suministro de Bienes",
                                 "contratista":"Proveedor de Prueba S.A.S.","contratistaNit":"900123456-7",
                                 "representanteLegal":"Alguien Responsable","lugarEjecucion":"Itagüí, Antioquia",
                                 "numeroRegistroPresupuestal":"11125","fechaRegistroPresupuestal":"2026-02-07",
                                 "centroCosto":"920510 — CTMA Formación"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoContrato").value("Suministro de Bienes"))
                .andExpect(jsonPath("$.contratista").value("Proveedor de Prueba S.A.S."))
                .andExpect(jsonPath("$.contratistaNit").value("900123456-7"))
                .andExpect(jsonPath("$.representanteLegal").value("Alguien Responsable"))
                .andExpect(jsonPath("$.lugarEjecucion").value("Itagüí, Antioquia"))
                .andExpect(jsonPath("$.numeroRegistroPresupuestal").value("11125"))
                .andExpect(jsonPath("$.centroCosto").value("920510 — CTMA Formación"));
    }

    private String login(String email, String password) throws Exception {
        String body = loginBody(email, password);
        return objectMapper.readValue(body, AuthResponse.class).token();
    }

    private String loginBody(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
