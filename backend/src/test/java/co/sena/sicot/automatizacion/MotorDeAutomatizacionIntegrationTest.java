package co.sena.sicot.automatizacion;

import co.sena.sicot.PruebaDeIntegracion;
import co.sena.sicot.dto.auth.AuthResponse;
import co.sena.sicot.entity.enums.EstadoTareaAutomatizada;
import co.sena.sicot.entity.enums.PrioridadAlerta;
import co.sena.sicot.entity.enums.TipoAlerta;
import co.sena.sicot.entity.enums.TipoTareaAutomatizada;
import co.sena.sicot.repository.AlertaRepository;
import co.sena.sicot.repository.TareaAutomatizadaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El motor de automatizaciones de punta a punta: desde que ocurre el hecho hasta
 * que la alerta existe en la base.
 *
 * <h2>Por qué no se espera a ningún temporizador</h2>
 * El perfil de pruebas apaga {@code sicot.automatizacion.habilitado}, así que el
 * {@link PlanificadorDeAutomatizaciones} ni siquiera se crea. Los beans de
 * trabajo sí existen, y esta clase los invoca directamente. Una prueba que
 * esperase a que saltara un {@code @Scheduled} pasaría o fallaría según lo
 * cargada que estuviera la máquina, y una prueba intermitente acaba desactivada
 * — llevándose por delante la cobertura real del módulo.
 */
class MotorDeAutomatizacionIntegrationTest extends PruebaDeIntegracion {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MotorDeAutomatizacion motor;

    @Autowired
    private EjecutorDeTareas ejecutor;

    @Autowired
    private AlmacenDeTareas almacen;

    @Autowired
    private TareaAutomatizadaRepository tareaRepository;

    @Autowired
    private AlertaRepository alertaRepository;

    @Autowired
    private JdbcTemplate jdbc;

    /** Los listados por contrato llevan tope desde la auditoría; aquí se pide todo. */
    private static final Pageable TODAS = PageRequest.of(0, 100);

    /**
     * El recorrido completo: un contrato a punto de vencer produce tareas al
     * evaluar el calendario, y esas tareas se convierten en alertas reales al
     * consumir la cola.
     *
     * <p>Es la prueba que demuestra que el hueco de ADR-008 quedó cerrado: antes
     * de este módulo, {@code alertas} no recibía una sola fila en toda la vida
     * del sistema.
     */
    @Test
    void unContratoPorVencerTerminaProduciendoAlertasRealesEnLaBase() throws Exception {
        long contratoId = contratoActivoQueVenceEn(10);

        // Tres reglas de calendario se pronuncian sobre este contrato: los
        // umbrales de 30 y 15 días (el de 7 aún no se cruzó) y el atraso de
        // cronograma — lleva 80 de 90 días de plazo con cero subetapas cerradas.
        int encoladas = motor.evaluarCalendario(LocalDate.now());
        assertThat(encoladas).isEqualTo(3);

        assertThat(alertaRepository.findByContratoIdOrderByFechaCreacionDesc(contratoId, TODAS))
                .as("las reglas no crean alertas: solo encolan tareas")
                .isEmpty();

        ejecutor.procesarPendientes();

        assertThat(alertaRepository.findByContratoIdOrderByFechaCreacionDesc(contratoId, TODAS))
                .hasSize(3)
                .extracting(a -> a.getTipo())
                .containsExactlyInAnyOrder(TipoAlerta.VENCIMIENTO, TipoAlerta.VENCIMIENTO, TipoAlerta.CRONOGRAMA);
        assertThat(tareaRepository.findAll())
                .allSatisfy(t -> assertThat(t.getEstado()).isEqualTo(EstadoTareaAutomatizada.COMPLETADA));
    }

    /**
     * La propiedad que hace que un sistema de alertas siga siendo usable: las
     * reglas de calendario se evalúan todos los días y no pueden generar la
     * misma alerta cada mañana.
     */
    @Test
    void evaluarElCalendarioVariasVecesNoDuplicaNingunaAlerta() throws Exception {
        long contratoId = contratoActivoQueVenceEn(10);

        motor.evaluarCalendario(LocalDate.now());
        int segundaPasada = motor.evaluarCalendario(LocalDate.now());
        int terceraPasada = motor.evaluarCalendario(LocalDate.now());

        assertThat(segundaPasada).isZero();
        assertThat(terceraPasada).isZero();

        ejecutor.procesarPendientes();
        assertThat(alertaRepository.findByContratoIdOrderByFechaCreacionDesc(contratoId, TODAS)).hasSize(3);
    }

    /**
     * Asignar un supervisor dispara la regla de evento por la vía real: el
     * cambio pasa por la API, {@code RegistroService} publica el evento al
     * confirmar la transacción y el motor reacciona.
     */
    @Test
    void asignarSupervisorEncolaElAvisoAlSupervisor() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long supervisorId = idDe("supervisor@soy.sena.edu.co", "Supervisor123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.EVENTO", LocalDate.now().plusMonths(6));

        mockMvc.perform(patch("/api/contratos/{id}/supervisor", contratoId)
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supervisorId\":" + supervisorId + "}"))
                .andExpect(status().isOk());

        assertThat(tareaRepository.findAll())
                .as("una alerta en el panel y un correo, como tareas independientes")
                .hasSize(2)
                .extracting(t -> t.getTipo())
                .containsExactlyInAnyOrder(TipoTareaAutomatizada.CREAR_ALERTA, TipoTareaAutomatizada.ENVIAR_CORREO);

        ejecutor.procesarPendientes();

        assertThat(alertaRepository.findByContratoIdOrderByFechaCreacionDesc(contratoId, TODAS)).hasSize(1);
        // Sin SMTP configurado en la suite, el correo se DESCARTA con un motivo
        // claro. No es un fallo, y no debe contarse como tal: ver EnvioDeCorreo.
        assertThat(tareaRepository.findAll())
                .filteredOn(t -> t.getTipo() == TipoTareaAutomatizada.ENVIAR_CORREO)
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.getEstado()).isEqualTo(EstadoTareaAutomatizada.DESCARTADA);
                    assertThat(t.getUltimoError()).contains("no está configurado");
                });
    }

    /**
     * Una tarea que quedó EN_PROCESO porque el proceso murió a mitad tiene que
     * volver a la cola. Es la fuga clásica de toda cola basada en estado, y solo
     * se manifiesta tras el primer reinicio brusco en producción.
     */
    @Test
    void unaTareaAbandonadaPorUnReinicioVuelveALaCola() {
        almacen.encolar(TareaSolicitada.ahora("prueba", "prueba:abandonada", null,
                new PayloadDeTarea.CrearAlerta(TipoAlerta.RECORDATORIO, PrioridadAlerta.BAJA, "Sin contrato")));
        Long id = tareaRepository.findAll().getFirst().getId();

        // Se simula el escenario real: la tarea quedó reservada y el proceso
        // murió. Hay que escribirlo por JDBC porque `fecha_actualizacion` la
        // gestiona @UpdateTimestamp — guardar la entidad la pondría en «ahora»,
        // que es justo lo contrario de lo que esta prueba necesita.
        jdbc.update("UPDATE tareas_automatizadas SET estado = 'EN_PROCESO', "
                + "fecha_actualizacion = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofHours(2))), id);

        // procesarPendientes rescata antes de trabajar: la tarea vuelve a
        // PENDIENTE y se ejecuta en la misma ronda.
        ejecutor.procesarPendientes();

        assertThat(tareaRepository.findById(id))
                .get()
                .satisfies(t -> assertThat(t.getEstado()).isEqualTo(EstadoTareaAutomatizada.COMPLETADA));
        assertThat(alertaRepository.findByContratoIsNullOrderByFechaCreacionDesc()).hasSize(1);
    }

    /** La pantalla de operación existe y solo la ve ADMINISTRADOR. */
    @Test
    void laPantallaDeOperacionEstaRestringidaAAdministrador() throws Exception {
        String admin = login("administrador@soy.sena.edu.co", "Admin123*");
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");

        mockMvc.perform(get("/api/automatizaciones/estado")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habilitado").value(false))
                .andExpect(jsonPath("$.reglas").isArray())
                .andExpect(jsonPath("$.porEstado.PENDIENTE").exists());

        mockMvc.perform(get("/api/automatizaciones/estado")
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/automatizaciones/tareas")
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isForbidden());
    }

    /**
     * La regla de IA está apagada por defecto, y apagada significa que el bean
     * no existe — no que exista y se salte con un {@code if}.
     */
    @Test
    void laReglaDeIaNoEstaRegistradaCuandoElCarrilEstaApagado() throws Exception {
        String admin = login("administrador@soy.sena.edu.co", "Admin123*");

        mockMvc.perform(get("/api/automatizaciones/estado")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reglas", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("resumen-semanal-ia"))));
    }

    // ── Utilidades ─────────────────────────────────────────────────────────

    private long contratoActivoQueVenceEn(int dias) throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.VENCE", LocalDate.now().plusDays(dias));
        mockMvc.perform(patch("/api/contratos/{id}/estado", contratoId)
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ACTIVO\"}"))
                .andExpect(status().isOk());
        return contratoId;
    }

    private long crearContrato(String token, String numero, LocalDate fin) throws Exception {
        String cuerpo = """
                {"numeroContrato":"%s","objeto":"Suministro de mobiliario para el CTMA",
                 "valor":25000000,"fechaInicio":"%s","fechaFin":"%s"}
                """.formatted(numero,
                DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDate.now().minusDays(80)),
                DateTimeFormatter.ISO_LOCAL_DATE.format(fin));

        String respuesta = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).get("id").asLong();
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
