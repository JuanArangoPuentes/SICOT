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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Un usuario tiene como máximo una firma electrónica activa".
 *
 * <p>Es una regla que la base ya imponía —el índice parcial
 * {@code uq_firma_activa_por_usuario}, de V1— pero que el servicio ignoraba.
 * Sobre PostgreSQL, asignarle una firma nueva a alguien que ya tenía una
 * chocaba contra el índice y devolvía un 409 de "posible duplicado": una
 * operación legítima de administrador que era imposible completar por la API.
 * La suite no lo detectaba porque corre sobre H2, donde ese índice parcial no
 * existe.
 *
 * <p>Estas pruebas verifican el comportamiento del servicio, que es lo que
 * arregla el problema en ambos motores. La existencia del índice en el esquema
 * real la comprueba {@code EsquemaPostgreSqlIntegrationTest}; entre las dos
 * queda cubierta la regla completa: la base la impone y el código la respeta.
 *
 * <p>Importa más allá de la ergonomía: {@code DocumentoService.firmar} resuelve
 * la firma con {@code findFirstByUsuarioIdAndActivaTrue}. Con dos activas,
 * "primera" es el orden arbitrario que devuelva el motor, y un documento
 * quedaría firmado con una firma impredecible — precisamente el dato que un
 * documento firmado debe dejar fuera de duda.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UnaSolaFirmaActivaPorUsuarioIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void asignarUnaFirmaNuevaRevocaLaAnteriorEnVezDeDejarDosActivas() throws Exception {
        String adminToken = login("administrador@soy.sena.edu.co", "Admin123*");
        long supervisorId = idDe("supervisor@soy.sena.edu.co", "Supervisor123*");

        long primera = asignarFirma(adminToken, supervisorId);
        long segunda = asignarFirma(adminToken, supervisorId);

        // La anterior queda revocada, no borrada: los documentos ya firmados con
        // ella siguen siendo rastreables hasta su titular.
        mockMvc.perform(get("/api/firmas").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + primera + " && @.activa == false)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.id == " + segunda + " && @.activa == true)]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.usuarioId == " + supervisorId + " && @.activa == true)]", hasSize(1)));
    }

    @Test
    void despuesDeUnaReasignacionLaFirmaDelUsuarioEsLaNuevaYNoLaAnterior() throws Exception {
        String adminToken = login("administrador@soy.sena.edu.co", "Admin123*");
        String supervisorToken = login("supervisor@soy.sena.edu.co", "Supervisor123*");
        long supervisorId = idDe("supervisor@soy.sena.edu.co", "Supervisor123*");

        String codigoAnterior = codigoDeFirmaAsignada(adminToken, supervisorId);
        String codigoNuevo = codigoDeFirmaAsignada(adminToken, supervisorId);

        // La consulta que usa DocumentoService.firmar para resolver con qué
        // firma se sella un documento. Debe devolver la nueva sin ambigüedad.
        mockMvc.perform(get("/api/firmas/mia").header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tieneFirmaActiva").value(true))
                .andExpect(jsonPath("$.firmaId").value(codigoNuevo))
                .andExpect(jsonPath("$.firmaId").value(org.hamcrest.Matchers.not(codigoAnterior)));
    }

    @Test
    void restaurarUnaFirmaRevocadaSeRechazaSiElUsuarioYaTieneOtraVigente() throws Exception {
        String adminToken = login("administrador@soy.sena.edu.co", "Admin123*");
        long supervisorId = idDe("supervisor@soy.sena.edu.co", "Supervisor123*");

        long primera = asignarFirma(adminToken, supervisorId);
        asignarFirma(adminToken, supervisorId);

        // Restaurar la vieja dejaría dos activas. Se rechaza con un mensaje que
        // dice qué hacer, en vez de rotar por cuenta propia: cuál debe quedar
        // vigente es una decisión del administrador.
        mockMvc.perform(patch("/api/firmas/{id}/estado", primera)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    /** Asigna una firma y devuelve el id de la fila creada. */
    private long asignarFirma(String adminToken, long usuarioId) throws Exception {
        return objectMapper.readTree(respuestaDeAsignar(adminToken, usuarioId)).get("id").asLong();
    }

    /** Asigna una firma y devuelve su código público (FIRMA-XXXXXXXX). */
    private String codigoDeFirmaAsignada(String adminToken, long usuarioId) throws Exception {
        return objectMapper.readTree(respuestaDeAsignar(adminToken, usuarioId)).get("firmaId").asText();
    }

    private String respuestaDeAsignar(String adminToken, long usuarioId) throws Exception {
        return mockMvc.perform(post("/api/firmas")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + usuarioId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activa").value(true))
                .andReturn().getResponse().getContentAsString();
    }

    private long idDe(String email, String password) throws Exception {
        return objectMapper.readTree(loginBody(email, password)).get("usuarioId").asLong();
    }

    private String login(String email, String password) throws Exception {
        return objectMapper.readValue(loginBody(email, password), AuthResponse.class).token();
    }

    private String loginBody(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
