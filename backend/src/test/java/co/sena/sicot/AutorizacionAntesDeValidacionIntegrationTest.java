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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Un usuario sin permiso para una ruta debe recibir <b>403</b>, no un 400 con
 * el detalle de qué campos le faltaban.
 *
 * <p>Spring MVC resuelve y valida los argumentos del método
 * ({@code @Valid @RequestBody}) antes de invocarlo, y por tanto antes de que
 * actúe {@code @PreAuthorize}. El resultado era que un SUPERVISOR que llamara a
 * {@code POST /api/contratos} —ruta reservada a GESTION y ADMINISTRADOR—
 * recibía el mapa completo de campos obligatorios de un endpoint que tiene
 * prohibido, y además un código de estado que mentía sobre el motivo del
 * rechazo. La corrección son las reglas por ruta de {@code SecurityConfig},
 * que corren en la cadena de filtros, mucho antes de que exista un cuerpo que
 * validar.
 *
 * <p>Todas las peticiones de aquí llevan cuerpos <b>deliberadamente inválidos</b>:
 * es la única forma de comprobar que el 403 gana la carrera a la validación.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AutorizacionAntesDeValidacionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void supervisorNoRecibeElEsquemaDeCrearContrato() throws Exception {
        mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + login("supervisor@soy.sena.edu.co", "Supervisor123*"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void gestionNoRecibeElEsquemaDeCrearUsuario() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + login("gestion@soy.sena.edu.co", "Gestion123*"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void supervisorNoRecibeElEsquemaDeAsignarFirma() throws Exception {
        mockMvc.perform(post("/api/firmas")
                        .header("Authorization", "Bearer " + login("supervisor@soy.sena.edu.co", "Supervisor123*"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    /**
     * La contraparte imprescindible: el pre-filtro no debe haber cerrado nada
     * que antes estuviera permitido. Con el rol correcto y un cuerpo inválido,
     * la respuesta tiene que seguir siendo 400 con el detalle de los campos.
     */
    @Test
    void conElRolCorrectoLaValidacionSigueExplicandoQueFalta() throws Exception {
        mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + login("gestion@soy.sena.edu.co", "Gestion123*"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.numeroContrato").exists())
                .andExpect(jsonPath("$.fieldErrors.objeto").exists())
                .andExpect(jsonPath("$.fieldErrors.valor").exists());
    }

    @Test
    void conElRolCorrectoCrearUsuarioSigueValidando() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + login("administrador@soy.sena.edu.co", "Admin123*"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
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
