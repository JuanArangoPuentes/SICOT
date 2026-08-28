package co.sena.sicot;

import co.sena.sicot.dto.auth.AuthResponse;
import co.sena.sicot.entity.Alerta;
import co.sena.sicot.entity.Documento;
import co.sena.sicot.entity.enums.EstadoDocumento;
import co.sena.sicot.entity.enums.EstadoSubetapa;
import co.sena.sicot.entity.enums.PrioridadAlerta;
import co.sena.sicot.entity.enums.TipoAlerta;
import co.sena.sicot.entity.enums.TipoDocumento;
import co.sena.sicot.ia.OllamaClient;
import co.sena.sicot.repository.AlertaRepository;
import co.sena.sicot.repository.ContratoRepository;
import co.sena.sicot.repository.DocumentoRepository;
import co.sena.sicot.repository.SubetapaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobertura de IDOR y aislamiento entre supervisores.
 *
 * <p>La regla que se prueba es una sola: <b>un SUPERVISOR solo alcanza el
 * contrato que tiene asignado</b>. Hoy vive en un único método
 * ({@code SecurityUtils.verificarAccesoAlContrato}) aplicado en ~15 sitios de 7
 * servicios, y nadie había comprobado que cubriera todas las puertas de entrada
 * de la API. Esta suite recorre cada endpoint que resuelve un recurso de un
 * contrato y verifica qué pasa cuando el <b>supervisor B</b> lo llama con un
 * identificador del contrato del <b>supervisor A</b> — incluido el acceso
 * directo por id de recurso hijo (documento, alerta, subetapa), que un
 * {@code @PreAuthorize} por rol nunca detecta.
 *
 * <p>Escenario base (montado una vez y reutilizado): dos supervisores
 * (A y B) con un contrato ACTIVO cada uno, más un tercer supervisor C sin
 * contrato asignado. Corre con H2 y {@code spring-security-test} — sin Docker,
 * sin PostgreSQL.
 *
 * <p>Dos de estas pruebas nacieron {@code @Disabled} porque documentaban brechas
 * reales que esta suite no podía arreglar (solo toca {@code src/test}): el
 * oráculo de enumeración entre contrato ajeno y contrato inexistente, y el orden
 * de comprobaciones en {@code DocumentoService.firmar}. Las dos las cerró la rama
 * de consistencia de API, así que ya están habilitadas y en verde.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AislamientoEntreSupervisoresIntegrationTest {

    private static final String PASSWORD = "Aislamiento123";
    private static final String EMAIL_SUP_A = "sup.a.aislamiento@soy.sena.edu.co";
    private static final String EMAIL_SUP_B = "sup.b.aislamiento@soy.sena.edu.co";
    private static final String EMAIL_SUP_C = "sup.c.aislamiento@soy.sena.edu.co";
    private static final String NUM_A = "CO1.PCCNTR.AISLAM-A";
    private static final String NUM_B = "CO1.PCCNTR.AISLAM-B";
    private static final String OBJETO_A = "Contrato A dotacion - escenario de aislamiento IDOR";
    private static final String OBJETO_B = "Contrato B mantenimiento - escenario de aislamiento IDOR";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ContratoRepository contratoRepository;

    @Autowired
    private DocumentoRepository documentoRepository;

    @Autowired
    private AlertaRepository alertaRepository;

    @Autowired
    private SubetapaRepository subetapaRepository;

    /**
     * Se sustituye el cliente real de Ollama: las pruebas del Copiloto y de la
     * generación de documentos deben poder correr sin un Ollama levantado, y
     * además así se puede verificar que la comprobación de acceso corta el flujo
     * <b>antes</b> de llegar a la IA ({@code verifyNoInteractions}).
     */
    @MockitoBean
    private OllamaClient ollamaClient;

    private boolean listo = false;

    private String tokenAdmin;
    private String tokenGestion;
    private String tokenA;
    private String tokenB;
    private String tokenC;
    private long idSupA;
    private long idSupB;
    private long contratoAId;
    private long contratoBId;
    private long etapaAId;
    private long subAId;
    private long subBId;
    private long alertaAId;
    private long alertaBId;
    private long alertaSinContratoId;
    private long docAPendienteId;
    private long docAFirmadoId;

    @BeforeEach
    void montarEscenario() throws Exception {
        if (listo) {
            return;
        }
        tokenAdmin = login("administrador@soy.sena.edu.co", "Admin123*");
        tokenGestion = login("gestion@soy.sena.edu.co", "Gestion123*");

        idSupA = crearSupervisor("Supervisor A Aislamiento", EMAIL_SUP_A);
        idSupB = crearSupervisor("Supervisor B Aislamiento", EMAIL_SUP_B);
        crearSupervisor("Supervisor C Aislamiento", EMAIL_SUP_C);
        tokenA = login(EMAIL_SUP_A, PASSWORD);
        tokenB = login(EMAIL_SUP_B, PASSWORD);
        tokenC = login(EMAIL_SUP_C, PASSWORD);

        contratoAId = crearContrato(NUM_A, OBJETO_A);
        asignarSupervisor(contratoAId, idSupA);
        activar(contratoAId);
        contratoBId = crearContrato(NUM_B, OBJETO_B);
        asignarSupervisor(contratoBId, idSupB);
        activar(contratoBId);

        long[] etapaSubA = primeraEtapaYSubetapa(contratoAId);
        etapaAId = etapaSubA[0];
        subAId = etapaSubA[1];
        subBId = primeraEtapaYSubetapa(contratoBId)[1];

        alertaAId = seedAlerta(contratoAId);
        alertaBId = seedAlerta(contratoBId);
        alertaSinContratoId = seedAlerta(null);

        docAPendienteId = seedDocumento(contratoAId, null, EstadoDocumento.PENDIENTE);
        docAFirmadoId = seedDocumento(contratoAId, "FIRMA-AISLAM-TEST", EstadoDocumento.APROBADO);

        listo = true;
    }

    // ----------------------------------------------------------------------
    // 1. Lectura cruzada entre supervisores — cada puerta de entrada.
    // ----------------------------------------------------------------------

    @Test
    void supervisorNoPuedeObtenerElContratoDeOtroSupervisor() throws Exception {
        String cuerpo = mockMvc.perform(get("/api/contratos/{id}", contratoAId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().is4xxClientError())
                .andReturn().getResponse().getContentAsString();
        sinFugaDe(cuerpo, NUM_A, OBJETO_A);
    }

    @Test
    void supervisorNoVeContratosAjenosAlListar() throws Exception {
        mockMvc.perform(get("/api/contratos")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.numeroContrato == '" + NUM_A + "')]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.numeroContrato == '" + NUM_B + "')]", hasSize(1)));
    }

    @Test
    void supervisorNoPuedeForzarElListadoDeOtroSupervisorConElParametroSupervisorId() throws Exception {
        // Aunque pida explícitamente los contratos del supervisor A, el backend
        // ignora el parámetro y solo devuelve los suyos.
        mockMvc.perform(get("/api/contratos")
                        .param("supervisorId", String.valueOf(idSupA))
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.numeroContrato == '" + NUM_A + "')]", hasSize(0)));
    }

    @Test
    void supervisorNoPuedeListarLasEtapasDeOtroContrato() throws Exception {
        mockMvc.perform(get("/api/contratos/{id}/etapas", contratoAId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void supervisorNoPuedeObtenerUnaEtapaDeOtroContratoNiSiquieraDesdeLaRutaDeSuPropioContrato() throws Exception {
        // Ruta con el id del contrato propio de B pero el id de una etapa de A.
        mockMvc.perform(get("/api/contratos/{contratoId}/etapas/{etapaId}", contratoBId, etapaAId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void supervisorNoPuedeListarLasSubetapasDeUnaEtapaDeOtroContrato() throws Exception {
        // Acceso directo por id de recurso hijo: /api/etapas/{etapaId}/subetapas
        // no menciona el contrato en ninguna parte.
        mockMvc.perform(get("/api/etapas/{etapaId}/subetapas", etapaAId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void supervisorNoPuedeListarLosDocumentosDeOtroContrato() throws Exception {
        String cuerpo = mockMvc.perform(get("/api/contratos/{id}/documentos", contratoAId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().is4xxClientError())
                .andReturn().getResponse().getContentAsString();
        sinFugaDe(cuerpo, "pendiente", "firmado");
    }

    @Test
    void supervisorNoPuedeDescargarUnDocumentoDeOtroContrato() throws Exception {
        // El endpoint ignora el {contratoId} de la ruta y resuelve el documento
        // solo por su id — se prueba con el id del contrato propio de B.
        mockMvc.perform(get("/api/contratos/{contratoId}/documentos/{id}/archivo", contratoBId, docAPendienteId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void supervisorNoPuedeListarLasAlertasDeOtroContrato() throws Exception {
        mockMvc.perform(get("/api/contratos/{id}/alertas", contratoAId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void supervisorNoPuedeListarLosRegistrosDeAuditoriaDeOtroContrato() throws Exception {
        String cuerpo = mockMvc.perform(get("/api/contratos/{id}/registros", contratoAId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().is4xxClientError())
                .andReturn().getResponse().getContentAsString();
        sinFugaDe(cuerpo, NUM_A);
    }

    @Test
    void supervisorNoPuedeChatearConElCopilotoSobreUnContratoAjeno() throws Exception {
        mockMvc.perform(post("/api/contratos/{id}/copiloto/chat", contratoAId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pregunta\":\"¿Cuál es el objeto de este contrato?\"}"))
                .andExpect(status().is4xxClientError());
        // La comprobación de acceso corta el flujo antes de llamar a la IA.
        verifyNoInteractions(ollamaClient);
    }

    // ----------------------------------------------------------------------
    // 2. Mutación cruzada — rechazada y sin efecto lateral.
    // ----------------------------------------------------------------------

    @Test
    void supervisorNoPuedeMarcarLeidaUnaAlertaDeOtroContrato() throws Exception {
        mockMvc.perform(patch("/api/alertas/{id}/leida", alertaAId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().is4xxClientError());
        assertThat(alertaRepository.findById(alertaAId).orElseThrow().isLeida())
                .as("la alerta del contrato ajeno no debe quedar marcada como leída")
                .isFalse();
    }

    @Test
    void supervisorNoPuedeCambiarElEstadoDeUnaSubetapaDeOtroContrato() throws Exception {
        mockMvc.perform(patch("/api/subetapas/{id}/estado", subAId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"COMPLETADA\"}"))
                .andExpect(status().is4xxClientError());
        assertThat(subetapaRepository.findById(subAId).orElseThrow().getEstado())
                .as("la subetapa del contrato ajeno no debe cambiar de estado")
                .isNotEqualTo(EstadoSubetapa.COMPLETADA);
    }

    @Test
    void supervisorNoPuedeFirmarUnDocumentoDeOtroContrato() throws Exception {
        mockMvc.perform(post("/api/contratos/{contratoId}/documentos/{id}/firmar", contratoAId, docAPendienteId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().is4xxClientError());
        assertThat(documentoRepository.findById(docAPendienteId).orElseThrow().getFirmaId())
                .as("el documento del contrato ajeno no debe quedar firmado")
                .isNull();
    }

    @Test
    void supervisorNoPuedeGenerarUnDocumentoEnUnContratoAjeno() throws Exception {
        long documentosAntes = documentoRepository.count();
        mockMvc.perform(post("/api/contratos/{id}/documentos/generar", contratoAId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"ACTA_INICIO\"}"))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(ollamaClient);
        assertThat(documentoRepository.count())
                .as("no debe crearse ningún documento en el contrato ajeno")
                .isEqualTo(documentosAntes);
    }

    @Test
    void supervisorNoPuedeMutarLosDatosGeneralesDeUnContrato() throws Exception {
        // PUT y los PATCH administrativos son de GESTION/ADMINISTRADOR: un
        // SUPERVISOR recibe 403 tanto en su contrato como en uno ajeno. Se
        // prueba contra el contrato ajeno.
        mockMvc.perform(put("/api/contratos/{id}", contratoAId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"%s","objeto":"Modificado sin permiso","valor":1,
                                 "fechaInicio":"2026-01-01","fechaFin":"2026-12-31"}
                                """.formatted(NUM_A)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/contratos/{id}/supervisor", contratoAId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supervisorId\":" + idSupB + "}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/contratos/{id}/estado", contratoAId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"SUSPENDIDO\"}"))
                .andExpect(status().isForbidden());
    }

    // ----------------------------------------------------------------------
    // 3. Firmas electrónicas — nadie ve la firma de otro.
    // ----------------------------------------------------------------------

    @Test
    void supervisorNoPuedeListarLasFirmasElectronicasDeTodos() throws Exception {
        mockMvc.perform(get("/api/firmas")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    void miFirmaSoloDevuelveLaFirmaDeLaCuentaActual() throws Exception {
        // Se asigna una firma al supervisor A.
        String firmaA = mockMvc.perform(post("/api/firmas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + idSupA + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String firmaIdDeA = objectMapper.readTree(firmaA).get("firmaId").asText();

        // El supervisor B consulta la suya: no tiene, y jamás ve la de A.
        String miFirmaB = mockMvc.perform(get("/api/firmas/mia")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tieneFirmaActiva").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(miFirmaB).doesNotContain(firmaIdDeA);

        // El supervisor A sí ve la suya.
        mockMvc.perform(get("/api/firmas/mia")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tieneFirmaActiva").value(true))
                .andExpect(jsonPath("$.firmaId").value(firmaIdDeA));
    }

    // ----------------------------------------------------------------------
    // 4. Los roles legítimos NO quedan estrangulados.
    // ----------------------------------------------------------------------

    @Test
    void gestionSiPuedeObtenerCualquierContrato() throws Exception {
        mockMvc.perform(get("/api/contratos/{id}", contratoAId)
                        .header("Authorization", "Bearer " + tokenGestion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroContrato").value(NUM_A));
    }

    @Test
    void administradorSiPuedeObtenerCualquierContrato() throws Exception {
        mockMvc.perform(get("/api/contratos/{id}", contratoAId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroContrato").value(NUM_A));
    }

    @Test
    void gestionSiPuedeListarLosDocumentosDeCualquierContrato() throws Exception {
        mockMvc.perform(get("/api/contratos/{id}/documentos", contratoAId)
                        .header("Authorization", "Bearer " + tokenGestion))
                .andExpect(status().isOk());
    }

    @Test
    void elSupervisorSiAlcanzaSuPropioContrato() throws Exception {
        mockMvc.perform(get("/api/contratos/{id}", contratoBId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroContrato").value(NUM_B));
        mockMvc.perform(get("/api/contratos/{id}/etapas", contratoBId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/contratos/{id}/registros", contratoBId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk());
    }

    @Test
    void elSupervisorSiPuedeMarcarLeidaUnaAlertaDeSuPropioContrato() throws Exception {
        mockMvc.perform(patch("/api/alertas/{id}/leida", alertaBId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leida").value(true));
    }

    @Test
    void elSupervisorSiPuedeCambiarElEstadoDeUnaSubetapaDeSuPropioContrato() throws Exception {
        mockMvc.perform(patch("/api/subetapas/{id}/estado", subBId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"COMPLETADA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETADA"));
    }

    // ----------------------------------------------------------------------
    // 5. Supervisor sin contrato asignado — vacío honesto, no 500 ni lista total.
    // ----------------------------------------------------------------------

    @Test
    void supervisorSinContratoRecibeUnaListaVaciaNoUn500NiLaListaCompleta() throws Exception {
        mockMvc.perform(get("/api/contratos")
                        .header("Authorization", "Bearer " + tokenC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void supervisorSinContratoNoAlcanzaUnContratoAjenoYNoRecibe500() throws Exception {
        mockMvc.perform(get("/api/contratos/{id}", contratoAId)
                        .header("Authorization", "Bearer " + tokenC))
                .andExpect(status().is4xxClientError());
    }

    // ----------------------------------------------------------------------
    // 6. Comportamiento documentado (no es brecha): alerta sin contrato.
    // ----------------------------------------------------------------------

    @Test
    void marcarLeidaUnaAlertaSinContratoEstaPermitidoPorDiseno() throws Exception {
        // SecurityUtils.verificarAccesoAlContrato(null) no restringe nada: una
        // alerta sin contrato es un dato sin dueño (ver su Javadoc). Se fija
        // aquí para que el día que exista un creador de alertas huérfanas con
        // información sensible, esta decisión se revise a conciencia.
        mockMvc.perform(patch("/api/alertas/{id}/leida", alertaSinContratoId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk());
    }

    // ----------------------------------------------------------------------
    // 7. Brechas reales encontradas — YA ARREGLADAS en esta rama.
    // ----------------------------------------------------------------------

    @Test
    void contratoAjenoYContratoInexistenteDebenResponderElMismoCodigo() throws Exception {
        int codigoAjeno = mockMvc.perform(get("/api/contratos/{id}", contratoAId)
                        .header("Authorization", "Bearer " + tokenB))
                .andReturn().getResponse().getStatus();
        int codigoInexistente = mockMvc.perform(get("/api/contratos/{id}", 999_999_999L)
                        .header("Authorization", "Bearer " + tokenB))
                .andReturn().getResponse().getStatus();
        assertThat(codigoAjeno)
                .as("un contrato ajeno y uno inexistente deben ser indistinguibles para el supervisor B")
                .isEqualTo(codigoInexistente);
    }

    @Test
    void firmarUnDocumentoAjenoNoDebeRevelarSiYaEstaFirmado() throws Exception {
        String mensajePendiente = mensajeDeError(mockMvc.perform(
                        post("/api/contratos/{contratoId}/documentos/{id}/firmar", contratoAId, docAPendienteId)
                                .header("Authorization", "Bearer " + tokenB))
                .andReturn().getResponse().getContentAsString());
        String mensajeFirmado = mensajeDeError(mockMvc.perform(
                        post("/api/contratos/{contratoId}/documentos/{id}/firmar", contratoAId, docAFirmadoId)
                                .header("Authorization", "Bearer " + tokenB))
                .andReturn().getResponse().getContentAsString());
        assertThat(mensajeFirmado)
                .as("la respuesta para un documento ajeno no debe depender de si ya está firmado")
                .doesNotContain("firmado")
                .isEqualTo(mensajePendiente);
    }

    // ----------------------------------------------------------------------
    // Utilidades del escenario.
    // ----------------------------------------------------------------------

    private String mensajeDeError(String cuerpoJson) throws Exception {
        JsonNode nodo = objectMapper.readTree(cuerpoJson).get("message");
        return nodo == null ? "" : nodo.asText();
    }

    private void sinFugaDe(String cuerpo, String... prohibidos) {
        for (String prohibido : prohibidos) {
            assertThat(cuerpo)
                    .as("la respuesta de acceso denegado no debe filtrar datos del contrato ajeno")
                    .doesNotContain(prohibido);
        }
    }

    private long crearSupervisor(String nombre, String email) throws Exception {
        String respuesta = mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"%s","email":"%s","password":"%s","telefono":"3000000000","rol":"SUPERVISOR"}
                                """.formatted(nombre, email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).get("id").asLong();
    }

    private long crearContrato(String numero, String objeto) throws Exception {
        String respuesta = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + tokenGestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"%s","objeto":"%s","valor":1000000,
                                 "fechaInicio":"2026-01-01","fechaFin":"2026-12-31"}
                                """.formatted(numero, objeto)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).get("id").asLong();
    }

    private void asignarSupervisor(long contratoId, long supervisorId) throws Exception {
        mockMvc.perform(patch("/api/contratos/{id}/supervisor", contratoId)
                        .header("Authorization", "Bearer " + tokenGestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supervisorId\":" + supervisorId + "}"))
                .andExpect(status().isOk());
    }

    private void activar(long contratoId) throws Exception {
        mockMvc.perform(patch("/api/contratos/{id}/estado", contratoId)
                        .header("Authorization", "Bearer " + tokenGestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ACTIVO\"}"))
                .andExpect(status().isOk());
    }

    private long[] primeraEtapaYSubetapa(long contratoId) throws Exception {
        String respuesta = mockMvc.perform(get("/api/contratos/{id}/etapas", contratoId)
                        .header("Authorization", "Bearer " + tokenGestion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode etapas = objectMapper.readTree(respuesta);
        JsonNode primera = etapas.get(0);
        return new long[]{
                primera.get("id").asLong(),
                primera.get("subEtapas").get(0).get("id").asLong()
        };
    }

    private long seedAlerta(Long contratoId) {
        Alerta alerta = new Alerta();
        if (contratoId != null) {
            alerta.setContrato(contratoRepository.findById(contratoId).orElseThrow());
        }
        alerta.setTipo(TipoAlerta.RECORDATORIO);
        alerta.setPrioridad(PrioridadAlerta.MEDIA);
        alerta.setMensaje("Alerta del escenario de aislamiento");
        alerta.setLeida(false);
        return alertaRepository.save(alerta).getId();
    }

    private long seedDocumento(long contratoId, String firmaId, EstadoDocumento estado) {
        Documento documento = new Documento();
        documento.setContrato(contratoRepository.findById(contratoId).orElseThrow());
        documento.setNombre("Documento de prueba " + (firmaId == null ? "pendiente" : "firmado"));
        documento.setTipo(TipoDocumento.PDF);
        documento.setContentType("application/pdf");
        documento.setContenido("%PDF-1.4 contenido de prueba".getBytes(StandardCharsets.UTF_8));
        documento.setTamanioBytes(10L);
        documento.setEstado(estado);
        documento.setFirmaId(firmaId);
        return documentoRepository.save(documento).getId();
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
