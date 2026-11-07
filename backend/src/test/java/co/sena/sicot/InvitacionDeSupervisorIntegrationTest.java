package co.sena.sicot;

import co.sena.sicot.dto.auth.AuthResponse;
import co.sena.sicot.entity.Usuario;
import co.sena.sicot.entity.enums.Rol;
import co.sena.sicot.repository.UsuarioRepository;
import co.sena.sicot.service.EmailService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gestión pone un supervisor en un contrato escribiendo su correo, y a ese
 * correo le llegan el usuario y la clave con los que entrará después de
 * instalar SICOT.
 *
 * <h2>Qué había antes</h2>
 * Tres pasos repartidos entre dos personas: el Administrador creaba la cuenta,
 * el Administrador mandaba la clave, y Gestión asignaba el contrato eligiendo a
 * alguien de una lista de usuarios que ya existían. Un supervisor nuevo no
 * recibía nada y nadie se enteraba de que faltaba un paso (26-10-2026).
 */
class InvitacionDeSupervisorIntegrationTest extends PruebaDeIntegracion {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    /**
     * El servidor de correo. En las pruebas de integración no hay ninguno
     * configurado, así que sin esto {@code enviarCredenciales} siempre fallaría
     * y nunca se recorrería el camino bueno.
     */
    @MockitoBean
    private EmailService emailService;

    @Test
    void gestionPoneUnSupervisorNuevoYLeLleganSusDatosDeAcceso() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.INVITA-1");

        JsonNode respuesta = invitar(gestion, contratoId, """
                {"nombre":"Alex Fernando Zapata","email":"AFZapatar@sena.edu.co","telefono":"3001234567"}
                """, status().isOk());

        assertThat(respuesta.get("cuentaCreada").asBoolean()).isTrue();
        assertThat(respuesta.get("credencialesEnviadas").asBoolean()).isTrue();
        // Si salió por correo, la clave no se devuelve: ya está donde tiene que estar.
        assertThat(respuesta.get("claveParaEntregar").isNull()).isTrue();
        assertThat(respuesta.get("contrato").get("supervisorNombre").asString()).isEqualTo("Alex Fernando Zapata");

        // La cuenta existe, es de SUPERVISOR y el correo quedó en minúsculas.
        Usuario creado = usuarioRepository.findByEmail("afzapatar@sena.edu.co").orElseThrow();
        assertThat(creado.getRol()).isEqualTo(Rol.SUPERVISOR);
        assertThat(creado.isActivo()).isTrue();
        assertThat(creado.getTelefono()).isEqualTo("3001234567");

        verify(emailService).enviarCredenciales(eqIgnorandoMayusculas("afzapatar@sena.edu.co"),
                anyString(), anyString());
    }

    /** La clave sirve de verdad: con ella el supervisor entra. */
    @Test
    void conLaClaveQueSeLeEntregaElSupervisorEntra() throws Exception {
        doThrow(new IllegalStateException("correo sin configurar"))
                .when(emailService).enviarCredenciales(anyString(), anyString(), anyString());
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.INVITA-2");

        JsonNode respuesta = invitar(gestion, contratoId, """
                {"nombre":"Supervisora Nueva","email":"nueva@sena.edu.co","telefono":"3009998877"}
                """, status().isOk());

        String clave = respuesta.get("claveParaEntregar").asString();
        assertThat(clave).isNotBlank();
        assertThat(login("nueva@sena.edu.co", clave)).isNotBlank();
    }

    /**
     * Sin servidor de correo la cuenta se crea igual y Gestión se entera, con
     * la clave para entregarla en mano. Que el correo falle no puede impedir
     * asignar un contrato.
     */
    @Test
    void sinServidorDeCorreoLaCuentaSeCreaYSeDiceQueNoSalio() throws Exception {
        doThrow(new IllegalStateException("El correo no está configurado (MAIL_USERNAME vacío)."))
                .when(emailService).enviarCredenciales(anyString(), anyString(), anyString());
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.INVITA-3");

        JsonNode respuesta = invitar(gestion, contratoId, """
                {"nombre":"Sin Correo","email":"sincorreo@sena.edu.co","telefono":"3001112233"}
                """, status().isOk());

        assertThat(respuesta.get("cuentaCreada").asBoolean()).isTrue();
        assertThat(respuesta.get("credencialesEnviadas").asBoolean()).isFalse();
        assertThat(respuesta.get("claveParaEntregar").asString()).isNotBlank();
        assertThat(respuesta.get("detalle").asString()).contains("Entréguele usted");
        assertThat(usuarioRepository.findByEmail("sincorreo@sena.edu.co")).isPresent();
    }

    /**
     * Un supervisor que ya trabaja en SICOT conserva su clave. Mandarle una
     * nueva le tumbaría la que usa, y dejaría que cualquiera con acceso a
     * Gestión restableciera la clave de un supervisor escribiendo su correo.
     */
    @Test
    void aUnSupervisorQueYaExisteSoloSeLeAsignaElContrato() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.INVITA-4");

        JsonNode respuesta = invitar(gestion, contratoId, """
                {"nombre":"Da igual lo que se escriba","email":"supervisor@soy.sena.edu.co","telefono":"3000000000"}
                """, status().isOk());

        assertThat(respuesta.get("cuentaCreada").asBoolean()).isFalse();
        assertThat(respuesta.get("claveParaEntregar").isNull()).isTrue();
        assertThat(respuesta.get("detalle").asString()).contains("ya tenía cuenta");
        // Su clave de siempre sigue sirviendo.
        assertThat(login("supervisor@soy.sena.edu.co", "Supervisor123*")).isNotBlank();
    }

    /** Un correo mal escrito no puede convertir al Administrador en supervisor de un contrato. */
    @Test
    void unCorreoQueNoEsDeSupervisorSeRechaza() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.INVITA-5");

        mockMvc.perform(post("/api/contratos/{id}/supervisor/invitacion", contratoId)
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Quien sea","email":"administrador@soy.sena.edu.co","telefono":"3000000000"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("no de un supervisor")));
    }

    /** Esto no le abre a Gestión la administración de usuarios: un supervisor no invita a nadie. */
    @Test
    void unSupervisorNoPuedeInvitarSupervisores() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.INVITA-6");
        String supervisor = login("supervisor@soy.sena.edu.co", "Supervisor123*");

        mockMvc.perform(post("/api/contratos/{id}/supervisor/invitacion", contratoId)
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Colado","email":"colado@sena.edu.co","telefono":"3000000000"}
                                """))
                .andExpect(status().isForbidden());

        assertThat(usuarioRepository.findByEmail("colado@sena.edu.co")).isEmpty();
    }

    /** Un contrato que no existe no puede dejar una cuenta de supervisor huérfana. */
    @Test
    void conUnContratoQueNoExisteNoSeCreaLaCuenta() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");

        mockMvc.perform(post("/api/contratos/{id}/supervisor/invitacion", 999_999)
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Huérfano","email":"huerfano@sena.edu.co","telefono":"3000000000"}
                                """))
                .andExpect(status().isNotFound());

        assertThat(usuarioRepository.findByEmail("huerfano@sena.edu.co")).isEmpty();
    }

    @Test
    void sinCorreoNoSeInvitaANadie() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.INVITA-7");

        mockMvc.perform(post("/api/contratos/{id}/supervisor/invitacion", contratoId)
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Sin correo","email":"","telefono":"3000000000"}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── Utilidades ──────────────────────────────────────────────────────────

    private static String eqIgnorandoMayusculas(String esperado) {
        return org.mockito.ArgumentMatchers.argThat(valor -> esperado.equalsIgnoreCase(valor));
    }

    private JsonNode invitar(String token, long contratoId, String cuerpo,
                             org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        String json = mockMvc.perform(post("/api/contratos/{id}/supervisor/invitacion", contratoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(esperado)
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private long crearContrato(String token, String numero) throws Exception {
        String json = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"%s","objeto":"Suministro para la prueba de invitación",
                                 "valor":5000000,"fechaInicio":"2026-01-01","fechaFin":"2026-06-30"}
                                """.formatted(numero)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
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
