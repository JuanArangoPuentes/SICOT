package co.sena.sicot.salud;

import co.sena.sicot.PruebaDeIntegracion;
import co.sena.sicot.dto.auth.AuthResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/salud} es lo que la interfaz consulta para decirle a la
 * persona «esto no le va a funcionar, y no es culpa suya». Estaba al 23,1 % de
 * cobertura.
 *
 * <h2>Lo que se fija aquí</h2>
 * Las dos propiedades de las que depende que este endpoint no haga daño:
 * <ul>
 *   <li><b>Con todo en orden, lista vacía.</b> Es lo que permite a la interfaz
 *       no interpretar nada —si hay algo, se pinta—. Si devolviera el estado de
 *       todos los servicios, cada pantalla tendría que filtrar y el primer
 *       despiste pintaría un aviso con el sistema sano.</li>
 *   <li><b>Exige sesión.</b> La respuesta nombra las piezas de la
 *       infraestructura del Centro (correo, respaldo, almacenamiento, IA) y
 *       cuánto llevan caídas. Abierta, sería un inventario de qué está roto en
 *       el servidor para cualquiera que pase por la dirección.</li>
 * </ul>
 */
class SaludControllerIntegrationTest extends PruebaDeIntegracion {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SaludDelSistema salud;

    @Test
    @DisplayName("con todo en orden la lista llega vacía: la interfaz no tiene nada que pintar")
    void conTodoEnOrdenNoHayAvisos() throws Exception {
        salud.exito(Servicio.IA);

        mockMvc.perform(get("/api/salud").header("Authorization", "Bearer " + login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("un servicio caído sale con su mensaje ya redactado y cuánto lleva así")
    void unServicioCaidoSaleConSuMensaje() throws Exception {
        // Tantos fallos como haga falta para que la incidencia se dé por
        // abierta: el umbral es del propio SaludDelSistema y no se replica aquí.
        for (int i = 0; i < 10; i++) {
            salud.fallo(Servicio.IA, "Ollama no responde");
        }

        mockMvc.perform(get("/api/salud").header("Authorization", "Bearer " + login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].servicio").value("ia"))
                // El mensaje viene listo para mostrarse tal cual: la pantalla no
                // traduce códigos, que es como se acaba enseñando «error 503».
                .andExpect(jsonPath("$[0].mensaje").isString())
                .andExpect(jsonPath("$[0].minutos").isNumber());

        salud.exito(Servicio.IA);
    }

    @Test
    @DisplayName("sin sesión no se puede preguntar qué está roto en el servidor")
    void sinSesionNoSeVeNada() throws Exception {
        mockMvc.perform(get("/api/salud"))
                .andExpect(status().isUnauthorized());
    }

    private String login() throws Exception {
        String cuerpo = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "supervisor@soy.sena.edu.co", "password", "Supervisor123*"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(cuerpo, AuthResponse.class).token();
    }
}
