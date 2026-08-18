package co.sena.sicot;

import co.sena.sicot.dto.auth.AuthResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void loginConCredencialesValidasDevuelveToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"administrador@soy.sena.edu.co","password":"Admin123*"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(blankString())))
                .andExpect(jsonPath("$.rol").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.email").value("administrador@soy.sena.edu.co"));
    }

    @Test
    void loginConContrasenaIncorrectaDevuelveError() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"gestion@soy.sena.edu.co","password":"incorrecta"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Credenciales inválidas."));
    }

    @Test
    void loginDeUsuarioInactivoEsRechazado() throws Exception {
        String adminToken = login("administrador@soy.sena.edu.co", "Admin123*");

        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Inactivo Test","email":"inactivo@soy.sena.edu.co",
                                 "password":"ClaveTest123","rol":"SUPERVISOR"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()));

        String tokenResp = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"inactivo@soy.sena.edu.co","password":"ClaveTest123"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long usuarioId = objectMapper.readTree(tokenResp).get("usuarioId").asLong();

        mockMvc.perform(patch("/api/usuarios/{id}/estado", usuarioId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"activo":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"inactivo@soy.sena.edu.co","password":"ClaveTest123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El usuario está inactivo. Contacte al administrador."));
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AuthResponse.class).token();
    }

    @Test
    void accesoSinTokenEsRechazado() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"No Auth","email":"noauth@soy.sena.edu.co",
                                 "password":"ClaveTest123","rol":"SUPERVISOR"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void soloAdministradorPuedeGestionarUsuarios() throws Exception {
        String gestionToken = login("gestion@soy.sena.edu.co", "Gestion123*");
        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + gestionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"No Permitido","email":"nopermitido@soy.sena.edu.co",
                                 "password":"ClaveTest123","rol":"SUPERVISOR"}
                                """))
                .andExpect(status().isForbidden());

        // GESTION sí puede listar (solo lectura) para poder asignar supervisores a un contrato.
        mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + gestionToken))
                .andExpect(status().isOk());
    }

    @Test
    void jsonDeErrorEsConsistente() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"no-valido","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        org.assertj.core.api.Assertions.assertThat(json.has("timestamp")).isTrue();
        org.assertj.core.api.Assertions.assertThat(json.has("status")).isTrue();
        org.assertj.core.api.Assertions.assertThat(json.has("error")).isTrue();
        org.assertj.core.api.Assertions.assertThat(json.has("message")).isTrue();
        org.assertj.core.api.Assertions.assertThat(json.has("path")).isTrue();
        org.assertj.core.api.Assertions.assertThat(json.has("fieldErrors")).isTrue();
    }
}
