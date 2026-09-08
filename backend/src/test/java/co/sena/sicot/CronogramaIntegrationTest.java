package co.sena.sicot;

import co.sena.sicot.automatizacion.EjecutorDeTareas;
import co.sena.sicot.automatizacion.MotorDeAutomatizacion;
import co.sena.sicot.dto.auth.AuthResponse;
import co.sena.sicot.repository.AlertaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/contratos/{id}/cronograma} de punta a punta.
 *
 * <h2>Qué protege esta clase</h2>
 * Que no vuelva a haber dos cronogramas.
 *
 * <p>Antes, el panel del supervisor calculaba el semáforo en el navegador con un
 * criterio, y la regla de automatización decidía si alertar con otro. SICOT
 * podía decir «va a tiempo» en la pantalla y «atrasado 39 puntos» en la bandeja
 * del mismo contrato el mismo día.
 *
 * <p>La prueba clave de aquí abajo es
 * {@link #laPantallaYLaAlertaDicenLoMismoSobreElMismoContrato()}: compara lo que
 * responde la API con lo que quedó escrito en {@code alertas} tras evaluar las
 * reglas. Si alguien vuelve a duplicar el cálculo, esa comparación se rompe.
 */
class CronogramaIntegrationTest extends PruebaDeIntegracion {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MotorDeAutomatizacion motor;

    @Autowired
    private EjecutorDeTareas ejecutor;

    @Autowired
    private AlertaRepository alertaRepository;

    @Test
    void unContratoMuyAtrasadoSeReportaEnRojoConSuBrecha() throws Exception {
        long contratoId = contratoActivo(LocalDate.now().minusDays(80), LocalDate.now().plusDays(10));

        mockMvc.perform(get("/api/contratos/{id}/cronograma", contratoId)
                        .header("Authorization", "Bearer " + login("gestion@soy.sena.edu.co", "Gestion123*")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semaforo").value("ROJO"))
                .andExpect(jsonPath("$.etapaActual").value(1))
                .andExpect(jsonPath("$.brecha").isNumber())
                // La estimación se declara como tal en todos los casos: no hay un
                // plazo por etapa aprobado por el SENA y el sistema no debe
                // aparentar que sí.
                .andExpect(jsonPath("$.mensaje").value(
                        org.hamcrest.Matchers.containsString("no hay un plazo oficial por etapa")));
    }

    /**
     * La pantalla y la alerta persistida tienen que decir lo mismo. Es la prueba
     * que existe para que el problema no vuelva.
     */
    @Test
    void laPantallaYLaAlertaDicenLoMismoSobreElMismoContrato() throws Exception {
        long contratoId = contratoActivo(LocalDate.now().minusDays(80), LocalDate.now().plusDays(10));
        String token = login("gestion@soy.sena.edu.co", "Gestion123*");

        String respuesta = mockMvc.perform(get("/api/contratos/{id}/cronograma", contratoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String mensajeDeLaPantalla = objectMapper.readTree(respuesta).get("mensaje").asText();

        motor.evaluarCalendario(LocalDate.now());
        ejecutor.procesarPendientes();

        String mensajeDeLaAlerta = alertaRepository
                .findByContratoIdOrderByFechaCreacionDesc(contratoId, PageRequest.of(0, 20)).stream()
                .filter(a -> a.getTipo() == co.sena.sicot.entity.enums.TipoAlerta.CRONOGRAMA)
                .findFirst()
                .orElseThrow(() -> new AssertionError("La regla de cronograma no produjo alerta"))
                .getMensaje();

        assertThat(mensajeDeLaAlerta)
                .as("la alerta es el mismo texto del panel, precedido del número de contrato")
                .contains(mensajeDeLaPantalla);
    }

    /**
     * Un contrato sin fechas completas no se pinta en verde: se declara sin
     * datos. Pintar verde sería afirmar que va bien sobre información que no se
     * tiene.
     */
    @Test
    void unContratoSinFechasSeReportaSinDatosYNoEnVerde() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        String creado = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"CO1.PCCNTR.SINFECHAS","objeto":"Contrato sin plazo definido",
                                 "valor":1000000}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long contratoId = objectMapper.readTree(creado).get("id").asLong();

        mockMvc.perform(get("/api/contratos/{id}/cronograma", contratoId)
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semaforo").value("SIN_DATOS"))
                .andExpect(jsonPath("$.mensaje").value(
                        org.hamcrest.Matchers.containsString("no se puede estimar")));
    }

    /**
     * El cronograma cuelga de un contrato, así que hereda su control de acceso:
     * un supervisor que no lo tiene asignado recibe 404, no 403 — para no poder
     * distinguir «no existe» de «no es suyo».
     */
    @Test
    void unSupervisorSinElContratoAsignadoNoVeSuCronograma() throws Exception {
        long contratoId = contratoActivo(LocalDate.now().minusDays(10), LocalDate.now().plusDays(80));

        mockMvc.perform(get("/api/contratos/{id}/cronograma", contratoId)
                        .header("Authorization", "Bearer " + login("supervisor@soy.sena.edu.co", "Supervisor123*")))
                .andExpect(status().isNotFound());
    }

    @Test
    void sinAutenticarNoSeAlcanzaElCronograma() throws Exception {
        mockMvc.perform(get("/api/contratos/{id}/cronograma", 1))
                .andExpect(status().isUnauthorized());
    }

    // ── Utilidades ─────────────────────────────────────────────────────────

    private long contratoActivo(LocalDate inicio, LocalDate fin) throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        String creado = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"CO1.PCCNTR.CRONO","objeto":"Suministro de mobiliario",
                                 "valor":25000000,"fechaInicio":"%s","fechaFin":"%s"}
                                """.formatted(DateTimeFormatter.ISO_LOCAL_DATE.format(inicio),
                                DateTimeFormatter.ISO_LOCAL_DATE.format(fin))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long contratoId = objectMapper.readTree(creado).get("id").asLong();

        mockMvc.perform(patch("/api/contratos/{id}/estado", contratoId)
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ACTIVO\"}"))
                .andExpect(status().isOk());
        return contratoId;
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
