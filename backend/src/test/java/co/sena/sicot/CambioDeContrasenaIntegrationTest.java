package co.sena.sicot;

import co.sena.sicot.dto.auth.AuthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.hamcrest.Matchers.blankString;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cambiar la contraseña propia ({@code PUT /api/cuenta/contrasena}).
 *
 * <p>Hasta el 05-11-2026 no se podía: al montar el servidor del Centro, la
 * guía pedía «cambie la contraseña del administrador» y no había dónde. Lo que
 * se fija aquí es lo que haría daño si fallara: que la anterior deje de servir,
 * que las demás sesiones se cierren, que una sesión abierta no baste para
 * quedarse con la cuenta y que este camino no sirva para adivinar contraseñas
 * sin el límite de intentos.
 */
class CambioDeContrasenaIntegrationTest extends PruebaDeIntegracion {

    private static final String ADMIN = "administrador@soy.sena.edu.co";
    private static final String CLAVE_ADMIN = "Admin123*";
    private static final String NUEVA = "OtraClave456*";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void conLaActualCorrectaCambiaYLaAnteriorDejaDeServir() throws Exception {
        String token = login(ADMIN, CLAVE_ADMIN);

        cambiar(token, CLAVE_ADMIN, NUEVA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(blankString())))
                .andExpect(jsonPath("$.email").value(ADMIN))
                .andExpect(jsonPath("$.rol").value("ADMINISTRADOR"));

        intentarLogin(ADMIN, CLAVE_ADMIN).andExpect(status().isUnauthorized());
        intentarLogin(ADMIN, NUEVA).andExpect(status().isOk());
    }

    /**
     * Las sesiones de otros equipos se cierran, la de este también, y la
     * respuesta trae la que permite seguir trabajando sin volver a entrar.
     */
    @Test
    void cierraTodasLasSesionesYDevuelveUnaQueSirve() throws Exception {
        String otroEquipo = login(ADMIN, CLAVE_ADMIN);
        String esteEquipo = login(ADMIN, CLAVE_ADMIN);

        String respuesta = cambiar(esteEquipo, CLAVE_ADMIN, NUEVA)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String nuevo = objectMapper.readValue(respuesta, AuthResponse.class).token();

        listarUsuarios(otroEquipo).andExpect(status().isUnauthorized());
        listarUsuarios(esteEquipo).andExpect(status().isUnauthorized());
        listarUsuarios(nuevo).andExpect(status().isOk());
    }

    /**
     * 400 y no 401: un 401 haría que la aplicación cerrara la sesión de quien
     * solo se equivocó al escribir.
     */
    @Test
    void conLaActualEquivocadaNoCambiaNadaNiCierraLaSesion() throws Exception {
        String token = login(ADMIN, CLAVE_ADMIN);

        cambiar(token, "NoEsLaMia1*", NUEVA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La contraseña actual no es correcta."));

        listarUsuarios(token).andExpect(status().isOk());
        intentarLogin(ADMIN, CLAVE_ADMIN).andExpect(status().isOk());
        intentarLogin(ADMIN, NUEVA).andExpect(status().isUnauthorized());
    }

    @Test
    void laNuevaCumpleLasMismasReglasQueAlCrearUnUsuario() throws Exception {
        String token = login(ADMIN, CLAVE_ADMIN);

        cambiar(token, CLAVE_ADMIN, "corta1*")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.contrasenaNueva", containsString("entre 8 y 72")));
        cambiar(token, CLAVE_ADMIN, "ClaveTest123 ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.contrasenaNueva",
                        containsString("no puede empezar ni terminar con espacios")));
        // 36 eñes son 36 caracteres pero 72 bytes... más uno: no cabe en BCrypt.
        cambiar(token, CLAVE_ADMIN, "ñ".repeat(36) + "a")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("72 bytes")));

        intentarLogin(ADMIN, CLAVE_ADMIN).andExpect(status().isOk());
    }

    @Test
    void laNuevaTieneQueSerDistintaDeLaActual() throws Exception {
        String token = login(ADMIN, CLAVE_ADMIN);

        cambiar(token, CLAVE_ADMIN, CLAVE_ADMIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La contraseña nueva tiene que ser distinta de la actual."));
        listarUsuarios(token).andExpect(status().isOk());
    }

    @Test
    void sinSesionNoSePuede() throws Exception {
        mockMvc.perform(put("/api/cuenta/contrasena")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(CLAVE_ADMIN, NUEVA)))
                .andExpect(status().isUnauthorized());
        intentarLogin(ADMIN, CLAVE_ADMIN).andExpect(status().isOk());
    }

    /** Es para todos los roles: el supervisor era justo quien no tenía cómo dejar la temporal. */
    @Test
    void unSupervisorCambiaLaSuya() throws Exception {
        String token = login("supervisor@soy.sena.edu.co", "Supervisor123*");

        cambiar(token, "Supervisor123*", NUEVA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("SUPERVISOR"));
        intentarLogin("supervisor@soy.sena.edu.co", NUEVA).andExpect(status().isOk());
        // La del administrador no se tocó.
        intentarLogin(ADMIN, CLAVE_ADMIN).andExpect(status().isOk());
    }

    /**
     * Con una sesión robada, este endpoint permitiría probar contraseñas para
     * la cuenta sin límite. Los fallos cuentan en el mismo contador que el
     * inicio de sesión: al quinto, ni este camino ni el login aceptan más.
     */
    @Test
    void losFallosConLaActualCuentanComoLosDelInicioDeSesion() throws Exception {
        String token = login(ADMIN, CLAVE_ADMIN);

        for (int i = 0; i < 5; i++) {
            cambiar(token, "Equivocada" + i + "*", NUEVA).andExpect(status().isBadRequest());
        }
        cambiar(token, CLAVE_ADMIN, NUEVA).andExpect(status().isTooManyRequests());
        intentarLogin(ADMIN, CLAVE_ADMIN).andExpect(status().isTooManyRequests());
    }

    private ResultActions cambiar(String token, String actual, String nueva) throws Exception {
        return mockMvc.perform(put("/api/cuenta/contrasena")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(actual, nueva)));
    }

    private String cuerpo(String actual, String nueva) throws Exception {
        return objectMapper.writeValueAsString(Map.of("contrasenaActual", actual, "contrasenaNueva", nueva));
    }

    private ResultActions listarUsuarios(String token) throws Exception {
        return mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + token));
    }

    private ResultActions intentarLogin(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private String login(String email, String password) throws Exception {
        String body = intentarLogin(email, password)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AuthResponse.class).token();
    }
}
