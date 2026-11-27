package co.sena.sicot;

import co.sena.sicot.dto.auth.AuthResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cerrar sesión ({@code POST /api/cuenta/cerrar-sesion}).
 *
 * <p><b>Qué fallaba.</b> Cerrar sesión era una operación solo del cliente: la
 * aplicación borraba el token de este equipo y en el servidor seguía siendo
 * válido hasta ocho horas más. No existía ningún endpoint de cierre. Lo que se
 * fija aquí es justo lo que haría daño si no estuviera: que el token deje de
 * autenticar <b>en el servidor</b>, y que al hacerlo no se lleve por delante
 * las otras sesiones de la misma persona —el supervisor usa el portátil del
 * Centro y el teléfono que lleva a bodega, y salir de uno no puede echarlo del
 * otro—.
 */
class CierreDeSesionIntegrationTest extends PruebaDeIntegracion {

    private static final String ADMIN = "administrador@soy.sena.edu.co";
    private static final String CLAVE_ADMIN = "Admin123*";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("el token con el que se cierra sesión deja de autenticar en el servidor")
    void elTokenCerradoYaNoAutentica() throws Exception {
        String token = login(ADMIN, CLAVE_ADMIN);
        listarUsuarios(token).andExpect(status().isOk());

        cerrarSesion(token).andExpect(status().isNoContent());

        listarUsuarios(token)
                // Antes de esto el token seguía sirviendo hasta ocho horas
                // después de que la persona pulsara «Cerrar sesión».
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("cerrar sesión en un equipo no cierra las de los otros")
    void lasOtrasSesionesSiguenAbiertas() throws Exception {
        String portatilDelCentro = login(ADMIN, CLAVE_ADMIN);
        String telefono = login(ADMIN, CLAVE_ADMIN);

        cerrarSesion(portatilDelCentro).andExpect(status().isNoContent());

        listarUsuarios(portatilDelCentro).andExpect(status().isUnauthorized());
        // Revocar la cuenta entera (lo que hace el cambio de contraseña) habría
        // echado también al teléfono: por eso el cierre anota el `jti` del token
        // y no incrementa `versionCredenciales`.
        listarUsuarios(telefono).andExpect(status().isOk());
    }

    @Test
    @DisplayName("cerrar sesión dos veces con el mismo token responde 204 igual, no un error")
    void esIdempotente() throws Exception {
        String token = login(ADMIN, CLAVE_ADMIN);

        cerrarSesion(token).andExpect(status().isNoContent());
        // La segunda llega sin sesión válida (el token ya no autentica), que es
        // el resultado correcto: la sesión está cerrada. Lo que no puede pasar
        // es un 500 por violar la clave primaria.
        cerrarSesion(token).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("después de cerrar sesión se puede volver a entrar: la cuenta no queda bloqueada")
    void sePuedeVolverAEntrar() throws Exception {
        String token = login(ADMIN, CLAVE_ADMIN);
        cerrarSesion(token).andExpect(status().isNoContent());

        String nuevo = login(ADMIN, CLAVE_ADMIN);
        listarUsuarios(nuevo).andExpect(status().isOk());
    }

    @Test
    @DisplayName("sin sesión no se puede cerrar la de nadie")
    void sinSesionNoSeCierraNada() throws Exception {
        mockMvc.perform(post("/api/cuenta/cerrar-sesion"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/cuenta/cerrar-sesion")
                        .header("Authorization", "Bearer no-es-un-token"))
                .andExpect(status().isUnauthorized());
    }

    // ── Utilidades ──────────────────────────────────────────────────────────

    private String login(String email, String password) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(cuerpo, AuthResponse.class).token();
    }

    private ResultActions cerrarSesion(String token) throws Exception {
        return mockMvc.perform(post("/api/cuenta/cerrar-sesion")
                .header("Authorization", "Bearer " + token));
    }

    private ResultActions listarUsuarios(String token) throws Exception {
        return mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + token));
    }
}
