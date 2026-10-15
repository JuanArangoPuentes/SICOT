package co.sena.sicot;

import co.sena.sicot.dto.secop.VerificacionSecopResponse;
import co.sena.sicot.service.DatosAbiertosSecop;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/contratos/{id}/secop de punta a punta: el supervisor ve lo que
 * publica el SECOP de SU contrato con las diferencias señaladas, otro
 * supervisor no lo ve, y sin conexión la respuesta lo dice en vez de fallar.
 *
 * <p>datos.gov.co se sustituye por un doble: ninguna prueba puede depender de
 * que haya red, ni de que el SECOP siga publicando el contrato igual.
 */
class VerificacionSecopIntegrationTest extends PruebaDeIntegracion {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DatosAbiertosSecop datosAbiertos;

    private static final String ENLACE =
            "https://community.secop.gov.co/Public/Tendering/OpportunityDetail/Index?noticeUID=CO1.NTC.10815297";

    private static VerificacionSecopResponse.Ficha ficha(String numero) {
        return new VerificacionSecopResponse.Ficha(numero, "Suministro de materiales", "Aprobado",
                "SUMINISTROS MAYBE SAS", "800154801", "LUZ MARINA MEJIA PEREZ", new BigDecimal("45900300"),
                "67 Dia(s)", LocalDate.of(2026, 10, 13), LocalDate.of(2026, 12, 15), null, null,
                "Supervisor de Pruebas", null, null, "Suministros", true, null, null, null, ENLACE);
    }

    @Test
    void elSupervisorVeLoQuePublicaElSecopDeSuContratoConLasDiferencias() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long supervisorId = usuarioId("supervisor@soy.sena.edu.co", "Supervisor123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.900100", supervisorId, "44000000");
        given(datosAbiertos.contrato("CO1.PCCNTR.900100")).willReturn(Optional.of(ficha("CO1.PCCNTR.900100")));
        given(datosAbiertos.documentos("CO1.PCCNTR.900100")).willReturn(List.of(new VerificacionSecopResponse.Documento(
                "6. ESTUDIO PREVIO.pdf", 5383716L, LocalDate.of(2026, 10, 8), "https://community.secop.gov.co/x")));
        given(datosAbiertos.garantias("CO1.PCCNTR.900100")).willReturn(List.of());

        String supervisor = login("supervisor@soy.sena.edu.co", "Supervisor123*");
        mockMvc.perform(get("/api/contratos/{id}/secop", contratoId).header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(true))
                // El contrato no tenía enlace guardado: se ofrece el que publica el SECOP.
                .andExpect(jsonPath("$.enlaceProceso").value(ENLACE))
                .andExpect(jsonPath("$.diferencias[?(@.campo == 'Valor')].enSecop").value("$45.900.300"))
                .andExpect(jsonPath("$.diferencias[?(@.campo == 'Valor')].enSicot").value("$44.000.000"))
                .andExpect(jsonPath("$.documentos[0].nombre").value("6. ESTUDIO PREVIO.pdf"));
    }

    @Test
    void otroSupervisorNoVeLaVerificacionDeUnContratoAjeno() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        String admin = login("administrador@soy.sena.edu.co", "Admin123*");
        long supervisorId = usuarioId("supervisor@soy.sena.edu.co", "Supervisor123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.900200", supervisorId, "45900300");
        mockMvc.perform(post("/api/usuarios")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"Supervisor Ajeno Secop","email":"ajeno.secop@soy.sena.edu.co",
                         "password":"Ajeno123*","telefono":"3000000000","rol":"SUPERVISOR"}
                        """));
        String ajeno = login("ajeno.secop@soy.sena.edu.co", "Ajeno123*");

        mockMvc.perform(get("/api/contratos/{id}/secop", contratoId).header("Authorization", "Bearer " + ajeno))
                .andExpect(status().isNotFound());
    }

    @Test
    void sinConexionLaRespuestaLoDiceYConservaElEnlace() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long supervisorId = usuarioId("supervisor@soy.sena.edu.co", "Supervisor123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.900300", supervisorId, "45900300");
        given(datosAbiertos.contrato(anyString()))
                .willThrow(new DatosAbiertosSecop.NoDisponible("sin red", new RuntimeException()));

        String supervisor = login("supervisor@soy.sena.edu.co", "Supervisor123*");
        mockMvc.perform(get("/api/contratos/{id}/secop", contratoId).header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false))
                .andExpect(jsonPath("$.motivo", containsString("No hay conexión con datos.gov.co")))
                .andExpect(jsonPath("$.enlaceProceso").value(ENLACE));
    }

    private long crearContrato(String token, String numero, long supervisorId, String valor) throws Exception {
        String creado = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"%s","objeto":"Suministro de materiales para la formación",
                                 "valor":%s,"fechaInicio":"2026-10-14","fechaFin":"2026-12-15",
                                 "supervisorId":%d,"contratista":"SUMINISTROS MAYBE S.A.S",
                                 "contratistaNit":"800154801-6","representanteLegal":"LUZ MARINA MEJIA PEREZ",
                                 "enlaceSecop":"%s"}
                                """.formatted(numero, valor, supervisorId,
                                numero.endsWith("300") ? ENLACE : "")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(creado).get("id").asLong();
    }

    private long usuarioId(String email, String password) throws Exception {
        return objectMapper.readTree(loginBody(email, password)).get("usuarioId").asLong();
    }

    private String login(String email, String password) throws Exception {
        return objectMapper.readTree(loginBody(email, password)).get("token").asText();
    }

    private String loginBody(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
