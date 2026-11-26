package co.sena.sicot;

import co.sena.sicot.config.AgenteQueActuaInterceptor;
import co.sena.sicot.dto.auth.AuthResponse;
import co.sena.sicot.entity.enums.OrigenRegistro;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Una acción hecha por una herramienta automática con la cuenta de una persona
 * no puede quedar en el expediente como si la hubiera hecho ella.
 *
 * <p><b>Qué fallaba.</b> El servidor MCP de {@code mcp/} inicia sesión con las
 * credenciales de una cuenta real y opera como ella. Todo lo que hacía quedaba
 * en {@code registros} con {@code origen = USUARIO} y el {@code usuario_id} del
 * funcionario: indistinguible de lo que esa persona escribió en la pantalla. En
 * un expediente de contratación pública «lo decidió una persona» frente a «lo
 * propuso un modelo con sus credenciales» es justo la distinción que hay que
 * conservar, y el proyecto ya tiene la regla —la IA es asesora, nunca
 * autoridad—, solo que la auditoría no la sostenía.
 */
class AuditoriaDeAgenteIntegrationTest extends PruebaDeIntegracion {

    /**
     * Se usa la cuenta de ADMINISTRADOR y no la de GESTION porque esta prueba
     * necesita las dos mitades: crear un contrato (GESTION o ADMINISTRADOR) y
     * leer /api/registros, que es solo del ADMINISTRADOR —la bitácora global
     * expone acciones de otras personas—. El rol no cambia nada de lo que se
     * está comprobando.
     */
    private static final String ADMIN = "administrador@soy.sena.edu.co";
    private static final String CLAVE_ADMIN = "Admin123*";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("sin la cabecera, el registro sigue siendo de la persona: nada cambia para la aplicación")
    void sinCabeceraElOrigenEsUsuario() throws Exception {
        String token = login();
        crearContrato(token, null, "SIN-AGENTE-001");

        registroDeCreacion(token)
                .andExpect(jsonPath("$[0].origen").value(OrigenRegistro.USUARIO.name()))
                .andExpect(jsonPath("$[0].agente").value(nullValue()))
                .andExpect(jsonPath("$[0].usuarioNombre").value(notNullValue()));
    }

    @Test
    @DisplayName("con la cabecera, el origen es AGENTE y queda cuál fue, sin perder la cuenta responsable")
    void conCabeceraElOrigenEsAgente() throws Exception {
        String token = login();
        crearContrato(token, "servidor MCP de SICOT", "CON-AGENTE-001");

        registroDeCreacion(token)
                .andExpect(jsonPath("$[0].origen").value(OrigenRegistro.AGENTE.name()))
                .andExpect(jsonPath("$[0].agente").value("servidor MCP de SICOT"))
                // La cuenta responsable NO desaparece: sigue siendo la de quien
                // prestó sus credenciales, y eso es parte de lo que el
                // expediente debe decir.
                .andExpect(jsonPath("$[0].usuarioNombre").value(notNullValue()));
    }

    @Test
    @DisplayName("un nombre de agente enorme se recorta, no rechaza la operación")
    void elNombreDelAgenteSeRecorta() throws Exception {
        // Un nombre demasiado largo rompería la tabla de auditoría al mostrarla,
        // y sigue identificando al agente por sus primeros caracteres: no hay
        // motivo para tumbar la operación entera por eso.
        String token = login();
        crearContrato(token, "agente con un nombre larguísimo " + "x".repeat(200), "AGENTE-LARGO-001");

        String agente = objectMapper.readTree(
                        registroDeCreacion(token).andReturn().getResponse().getContentAsString())
                .get(0).get("agente").asString();
        assertThat(agente)
                .hasSizeLessThanOrEqualTo(AgenteQueActuaInterceptor.MAXIMO)
                .startsWith("agente con un nombre larguísimo");
    }

    @Test
    @DisplayName("una cabecera con un salto de línea es un 400, no un «error interno del servidor»")
    void unaCabeceraConSaltoDeLineaEsPeticionMalFormada() throws Exception {
        // La rechaza el cortafuegos de Spring Security ANTES de llegar a
        // cualquier código de SICOT, y es lo correcto: un salto de línea en una
        // cabecera es la base del contrabando de peticiones. Lo que estaba mal
        // era el código: salía como 500 y el servidor se echaba la culpa de una
        // petición mal formada (ver GlobalExceptionHandler).
        mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + login())
                        .header(AgenteQueActuaInterceptor.CABECERA, "agente\ncon salto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("declararse agente no sirve para esquivar las automatizaciones")
    void elAgenteTambienDisparaLasAutomatizaciones() throws Exception {
        // El evento de negocio se publicaba solo para origen USUARIO. Si AGENTE
        // no lo publicara, bastaría con mandar la cabecera para que asignar un
        // supervisor dejara de avisarle: la honestidad en la auditoría se
        // habría convertido en una puerta para saltarse las reglas. La fila con
        // origen AGENTE prueba que no se tomó el atajo de SISTEMA, que es el
        // único camino que no publica.
        String token = login();
        crearContrato(token, "servidor MCP de SICOT", "AGENTE-EVENTO-001");

        registroDeCreacion(token)
                .andExpect(jsonPath("$[0].accion").value("CONTRATO_CREADO"))
                .andExpect(jsonPath("$[0].origen").value(OrigenRegistro.AGENTE.name()));
    }

    // ── Utilidades ──────────────────────────────────────────────────────────

    /**
     * Los registros vistos por la API, que es como los lee el expediente. Se
     * consulta así y no por el repositorio porque `Registro.usuario` es
     * perezoso: leerlo fuera de una transacción revienta, y además lo que
     * importa comprobar es lo que el cliente recibe.
     */
    private org.springframework.test.web.servlet.ResultActions registroDeCreacion(String token) throws Exception {
        return mockMvc.perform(get("/api/registros").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String login() throws Exception {
        String cuerpo = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", ADMIN, "password", CLAVE_ADMIN))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(cuerpo, AuthResponse.class).token();
    }

    private void crearContrato(String token, String agente, String numero) throws Exception {
        var peticion = post("/api/contratos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"numeroContrato":"%s","objeto":"Dotación de mobiliario para el taller de ebanistería",
                         "valor":15000000,"fechaInicio":"2026-01-15","fechaFin":"2026-12-15"}
                        """.formatted(numero));
        if (agente != null) {
            peticion = peticion.header(AgenteQueActuaInterceptor.CABECERA, agente);
        }
        mockMvc.perform(peticion).andExpect(status().isCreated());
    }
}
