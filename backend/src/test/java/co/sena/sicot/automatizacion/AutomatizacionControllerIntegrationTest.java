package co.sena.sicot.automatizacion;

import co.sena.sicot.PruebaDeIntegracion;
import co.sena.sicot.dto.auth.AuthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La puerta HTTP del motor de automatizaciones.
 *
 * <p>{@code MotorDeAutomatizacionIntegrationTest} cubre el motor por dentro —qué
 * reglas disparan, qué tareas se encolan, cómo se reintenta—. Lo que no cubría
 * nadie es el controlador: las tres rutas por las que se opera el motor desde
 * fuera existían sin una sola prueba, y son las de mayor privilegio de la API.
 * {@code /evaluar} en particular no consulta nada: <b>dispara</b> la evaluación
 * del calendario y escribe en la cola.
 *
 * <p>Lo que se comprueba aquí es sobre todo <b>quién puede llamarlas</b>. El
 * control vive en un solo {@code @PreAuthorize} a nivel de clase, que es
 * precisamente la forma de protección que desaparece sin ruido: alguien mueve
 * un método a otro controlador, o añade uno nuevo en una clase sin la
 * anotación, y no se rompe nada visible — sólo queda abierto. Por eso cada ruta
 * se prueba con los tres roles y sin autenticar, y no sólo con el que debe
 * pasar.
 *
 * <p>El perfil de pruebas apaga {@code sicot.automatizacion.habilitado}, así que
 * {@code /estado} debe reportar {@code habilitado: false}. Esa aserción vale
 * doble: confirma que el endpoint lee la configuración real del despliegue y no
 * un valor fijo, que es justo lo que haría inútil un panel de estado.
 */
class AutomatizacionControllerIntegrationTest extends PruebaDeIntegracion {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ─── Lo que el ADMINISTRADOR sí puede ───────────────────────────────────

    @Test
    void elEstadoReportaLaConfiguracionRealDelDespliegueYLasReglasCargadas() throws Exception {
        String admin = login("administrador@soy.sena.edu.co", "Admin123*");

        mockMvc.perform(get("/api/automatizaciones/estado").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                // Falso porque el perfil "test" apaga el motor. Si esto diera
                // true, el endpoint estaría inventando su respuesta.
                .andExpect(jsonPath("$.habilitado").value(false))
                // Un contador por cada estado del enum: la cola nunca debe
                // aparecer vacía "porque no hay tareas de ese tipo todavía".
                .andExpect(jsonPath("$.porEstado", hasKey("PENDIENTE")))
                .andExpect(jsonPath("$.porEstado", hasKey("EN_PROCESO")))
                .andExpect(jsonPath("$.porEstado", hasKey("COMPLETADA")))
                .andExpect(jsonPath("$.porEstado", hasKey("FALLIDA")))
                .andExpect(jsonPath("$.porEstado", hasKey("DESCARTADA")))
                // Las reglas registradas. Es lo que permite ver de un vistazo
                // que una recién desplegada quedó cargada de verdad.
                .andExpect(jsonPath("$.reglas.length()", greaterThan(0)));
    }

    @Test
    void laColaSePuedeListarYFiltrarPorEstado() throws Exception {
        String admin = login("administrador@soy.sena.edu.co", "Admin123*");

        mockMvc.perform(get("/api/automatizaciones/tareas").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mockMvc.perform(get("/api/automatizaciones/tareas")
                        .param("estado", "PENDIENTE")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void unEstadoQueNoExisteEs400YNoUn500() throws Exception {
        String admin = login("administrador@soy.sena.edu.co", "Admin123*");

        // El parámetro se convierte a un enum. Sin manejo, un valor inventado
        // sale como error 500 —un fallo del servidor— cuando lo que ocurrió es
        // que la petición estaba mal escrita.
        mockMvc.perform(get("/api/automatizaciones/tareas")
                        .param("estado", "NO_ES_UN_ESTADO")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void evaluarEsIdempotenteYDevuelveCuantasTareasEncolo() throws Exception {
        String admin = login("administrador@soy.sena.edu.co", "Admin123*");

        mockMvc.perform(post("/api/automatizaciones/evaluar").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tareasEncoladas").exists());

        // Dos veces seguidas. La idempotencia la garantiza una clave UNIQUE en
        // la cola, no el controlador, pero llamar dos veces es exactamente lo
        // que hará quien opere el sistema si la primera parece no responder.
        mockMvc.perform(post("/api/automatizaciones/evaluar").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tareasEncoladas").value(0));
    }

    // ─── Lo que nadie más puede ─────────────────────────────────────────────

    @Test
    void ningunaDeLasTresRutasAceptaAOtroRol() throws Exception {
        String supervisor = login("supervisor@soy.sena.edu.co", "Supervisor123*");
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");

        for (String token : new String[]{supervisor, gestion}) {
            mockMvc.perform(get("/api/automatizaciones/estado").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/automatizaciones/tareas").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/automatizaciones/evaluar").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void sinTokenNingunaDeLasTresRutasResponde() throws Exception {
        mockMvc.perform(get("/api/automatizaciones/estado")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/automatizaciones/tareas")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/automatizaciones/evaluar")).andExpect(status().isUnauthorized());
    }

    private String login(String email, String password) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(cuerpo, AuthResponse.class).token();
    }
}
