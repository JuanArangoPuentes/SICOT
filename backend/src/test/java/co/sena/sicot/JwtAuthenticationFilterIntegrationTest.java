package co.sena.sicot;

import co.sena.sicot.dto.auth.AuthResponse;
import co.sena.sicot.entity.Usuario;
import co.sena.sicot.entity.enums.Rol;
import co.sena.sicot.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El filtro que decide, en cada petición, si quien llama es quien dice ser.
 *
 * <p>{@code JwtServiceTest} comprueba que un token malo no se puede leer. Aquí
 * se comprueba lo que de verdad importa: que ese token malo <b>no abre la
 * puerta</b>. Son cosas distintas — un filtro que atrapara la excepción y
 * siguiera adelante dejaría pasar la petición igualmente, y la prueba unitaria
 * seguiría en verde.
 *
 * <p>Se usa {@code GET /api/contratos} como puerta de entrada porque no lleva
 * {@code @PreAuthorize}: la atiende cualquier usuario autenticado. Eso permite
 * distinguir un <b>401</b> (el token no vale) de un <b>403</b> (el token vale
 * pero el rol no alcanza), que es justo la diferencia que estas pruebas miden.
 */
class JwtAuthenticationFilterIntegrationTest extends PruebaDeIntegracion {

    private static final String SUPERVISOR = "supervisor@soy.sena.edu.co";
    private static final String CLAVE_SUPERVISOR = "Supervisor123*";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * El mismo secreto que usa el backend. Hace falta para poder fabricar un
     * token <b>legítimamente firmado pero caducado</b>: si se firmara con otro
     * secreto, la prueba pasaría por el motivo equivocado (firma inválida) y no
     * probaría la expiración en absoluto.
     */
    @Value("${sicot.security.jwt-secret}")
    private String secreto;

    @Test
    void unTokenCaducadoNoAbreLaPuerta() throws Exception {
        JwtService emisorDeTokensVencidos = new JwtService(secreto, -1_000L);
        String tokenVencido = emisorDeTokensVencidos.generateToken(
                usuarioSemilla(SUPERVISOR, Rol.SUPERVISOR));

        mockMvc.perform(get("/api/contratos")
                        .header("Authorization", "Bearer " + tokenVencido))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenConElCuerpoManipuladoNoAbreLaPuerta() throws Exception {
        String token = login(SUPERVISOR, CLAVE_SUPERVISOR);

        // El token viaja entero en la cabecera y cualquiera puede leer su
        // contenido: lo que impide reescribirlo es la firma, no el secreto.
        String[] partes = token.split("\\.");
        char primero = partes[1].charAt(0);
        partes[1] = (primero == 'A' ? 'B' : 'A') + partes[1].substring(1);

        mockMvc.perform(get("/api/contratos")
                        .header("Authorization", "Bearer " + String.join(".", partes)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elTokenDeUnUsuarioDesactivadoDejaDeServirDeInmediato() throws Exception {
        String tokenAdmin = login("administrador@soy.sena.edu.co", "Admin123*");

        String creado = mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Supervisor A Desactivar","email":"por.desactivar@soy.sena.edu.co",
                                 "password":"ClaveTest123","telefono":"3000000000","rol":"SUPERVISOR"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(creado).get("id").asLong();

        String tokenDelSupervisor = login("por.desactivar@soy.sena.edu.co", "ClaveTest123");

        // Mientras la cuenta está activa, el token abre la puerta.
        mockMvc.perform(get("/api/contratos")
                        .header("Authorization", "Bearer " + tokenDelSupervisor))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/usuarios/{id}/estado", id)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"activo":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        // Y este es el punto de la prueba: el token no ha caducado ni se ha
        // tocado, sigue siendo criptográficamente válido. Si el filtro se
        // fiara solo de él, desactivar a una persona no la sacaría del sistema
        // hasta que su sesión venciera sola — hasta ocho horas después.
        mockMvc.perform(get("/api/contratos")
                        .header("Authorization", "Bearer " + tokenDelSupervisor))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unaCabeceraQueNoEsBearerSeIgnoraYLaPeticionQuedaSinAutenticar() throws Exception {
        String token = login(SUPERVISOR, CLAVE_SUPERVISOR);

        mockMvc.perform(get("/api/contratos").header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unaCadenaCualquieraEnLugarDeUnTokenNoProvocaUnErrorDelServidor() throws Exception {
        // La diferencia entre 401 y 500 no es cosmética: un 500 aquí significa que
        // el filtro dejó escapar una excepción que no esperaba, y basta con una
        // cabecera mal formada para provocarlo desde fuera, sin credenciales.
        mockMvc.perform(get("/api/contratos")
                        .header("Authorization", "Bearer esto-no-es-un-jwt"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Un {@link Usuario} en memoria que representa a una cuenta ya sembrada.
     * Solo se usa para firmar un token: el filtro no confía en lo que venga
     * dentro, vuelve a buscar la cuenta en la base por su correo.
     */
    private Usuario usuarioSemilla(String email, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setNombre("Cuenta sembrada");
        usuario.setEmail(email);
        usuario.setRol(rol);
        return usuario;
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
