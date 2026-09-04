package co.sena.sicot;

import co.sena.sicot.dto.auth.AuthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cada campo de escritura de un contrato debe rechazarse por una restricción
 * declarada —400 con {@code fieldErrors} que señala el campo— antes de llegar a
 * la base. Antes, un texto largo se colaba hasta la columna y volvía como un 409
 * genérico, y una fecha de fin anterior al inicio solo la frenaba el servicio con
 * un mensaje sin {@code fieldErrors}.
 */
class ValidacionEntradaContratoIntegrationTest extends PruebaDeIntegracion {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void objetoDemasiadoLargoEs400ConFieldError() throws Exception {
        String token = login("gestion@soy.sena.edu.co", "Gestion123*");
        String objeto = "x".repeat(4001);

        mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroContrato\":\"CO1.PCCNTR.VAL-OBJ\",\"objeto\":\"" + objeto
                                + "\",\"valor\":1000000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.objeto").exists());
    }

    @Test
    void campoOpcionalDemasiadoLargoEs400NoConflicto() throws Exception {
        String token = login("gestion@soy.sena.edu.co", "Gestion123*");
        String contratista = "x".repeat(256);

        mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroContrato\":\"CO1.PCCNTR.VAL-CONTR\",\"objeto\":\"Prueba\","
                                + "\"valor\":1000000,\"contratista\":\"" + contratista + "\"}"))
                // 400 (restricción declarada), no 409 (choque con la columna).
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.contratista").exists());
    }

    @Test
    void fechaFinAnteriorAlInicioEs400ConFieldError() throws Exception {
        String token = login("gestion@soy.sena.edu.co", "Gestion123*");

        mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"CO1.PCCNTR.VAL-FECHA","objeto":"Prueba",
                                 "valor":1000000,"fechaInicio":"2026-06-30","fechaFin":"2026-01-01"}
                                """))
                .andExpect(status().isBadRequest())
                // La restricción declarada (@AssertTrue) sí llena fieldErrors;
                // el chequeo del servicio devolvía un 400 sin este detalle.
                .andExpect(jsonPath("$.fieldErrors.fechasCoherentes").exists());
    }

    @Test
    void valorConDemasiadosDecimalesEs400ConFieldError() throws Exception {
        String token = login("gestion@soy.sena.edu.co", "Gestion123*");

        mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"CO1.PCCNTR.VAL-NUM","objeto":"Prueba",
                                 "valor":1000000.123}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.valor").exists());
    }

    @Test
    void actualizarConTipoContratoDemasiadoLargoEs400() throws Exception {
        String token = login("gestion@soy.sena.edu.co", "Gestion123*");

        String creado = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"CO1.PCCNTR.VAL-UPD","objeto":"Prueba","valor":1000000}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(creado).get("id").asLong();

        mockMvc.perform(put("/api/contratos/{id}", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroContrato\":\"CO1.PCCNTR.VAL-UPD\",\"objeto\":\"Prueba\","
                                + "\"valor\":1000000,\"tipoContrato\":\"" + "x".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.tipoContrato").exists());
    }

    @Test
    void asignarSupervisorConIdNoPositivoEs400ConFieldError() throws Exception {
        String token = login("gestion@soy.sena.edu.co", "Gestion123*");

        String creado = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"CO1.PCCNTR.VAL-SUP","objeto":"Prueba","valor":1000000}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(creado).get("id").asLong();

        mockMvc.perform(patch("/api/contratos/{id}/supervisor", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supervisorId\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.supervisorId").exists());
    }

    @Test
    void contratoValidoConCamposOpcionalesSeSigueCreando() throws Exception {
        String token = login("gestion@soy.sena.edu.co", "Gestion123*");

        mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"CO1.PCCNTR.VAL-OK","objeto":"Contrato válido de regresión",
                                 "valor":1234567.89,"fechaInicio":"2026-01-01","fechaFin":"2026-12-31",
                                 "tipoContrato":"Suministro de Bienes","contratista":"Proveedor S.A.S.",
                                 "contratistaNit":"900123456-7","numeroRegistroPresupuestal":"11125"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroContrato").value("CO1.PCCNTR.VAL-OK"));
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AuthResponse.class).token();
    }
}
