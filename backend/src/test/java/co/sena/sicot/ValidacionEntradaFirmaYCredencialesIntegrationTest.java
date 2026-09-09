package co.sena.sicot;

import co.sena.sicot.dto.auth.AuthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guardas de entrada para dos request pequeños: un identificador de usuario debe
 * ser positivo antes de buscarlo en la base, y la contraseña que se enviará por
 * correo no puede ser un texto de longitud arbitraria.
 */
class ValidacionEntradaFirmaYCredencialesIntegrationTest extends PruebaDeIntegracion {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void crearFirmaConUsuarioIdNoPositivoEs400ConFieldError() throws Exception {
        String adminToken = login("administrador@soy.sena.edu.co", "Admin123*");

        mockMvc.perform(post("/api/firmas")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.usuarioId").exists());
    }

    @Test
    void enviarCredencialesConContrasenaDemasiadoLargaEs400ConFieldError() throws Exception {
        String adminToken = login("administrador@soy.sena.edu.co", "Admin123*");

        mockMvc.perform(post("/api/usuarios/{id}/enviar-credenciales", 1)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + "x".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
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
