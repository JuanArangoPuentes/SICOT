package co.sena.sicot;

import co.sena.sicot.dto.auth.AuthResponse;
import co.sena.sicot.entity.Alerta;
import co.sena.sicot.entity.Documento;
import co.sena.sicot.entity.enums.EstadoDocumento;
import co.sena.sicot.entity.enums.PrioridadAlerta;
import co.sena.sicot.entity.enums.TipoAlerta;
import co.sena.sicot.entity.enums.TipoDocumento;
import co.sena.sicot.repository.AlertaRepository;
import co.sena.sicot.repository.ContratoRepository;
import co.sena.sicot.repository.DocumentoRepository;
import co.sena.sicot.repository.SubetapaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El seguimiento de supervisores del Administrador: que diga dónde va cada uno
 * y que solo lo pueda ver el Administrador.
 *
 * <p>Escenario: un supervisor con firma y un contrato activo que ya avanzó dos
 * subetapas y tiene un documento firmado y una alerta sin leer; un segundo
 * supervisor sin firma ni contratos; y un contrato activo sin supervisor.
 */
class SeguimientoDeSupervisoresIntegrationTest extends PruebaDeIntegracion {

    private static final String PASSWORD = "Seguimiento123";
    private static final String EMAIL_CON = "sup.con.seguimiento@soy.sena.edu.co";
    private static final String EMAIL_SIN = "sup.sin.seguimiento@soy.sena.edu.co";

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

    private String tokenAdmin;
    private String tokenGestion;
    private long idConFirma;
    private long idSinFirma;
    private long contratoId;
    private long contratoSinSupervisorId;

    @BeforeEach
    void montarEscenario() throws Exception {
        tokenAdmin = login("administrador@soy.sena.edu.co", "Admin123*");
        tokenGestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        idConFirma = crearSupervisor("Ana Con Firma", EMAIL_CON);
        idSinFirma = crearSupervisor("Beto Sin Firma", EMAIL_SIN);
        mockMvc.perform(post("/api/firmas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + idConFirma + "}"))
                .andExpect(status().isCreated());

        contratoId = crearContrato("CO1.PCCNTR.SEGUIM-1");
        asignarSupervisor(contratoId, idConFirma);
        activar(contratoId);
        contratoSinSupervisorId = crearContrato("CO1.PCCNTR.SEGUIM-2");
        activar(contratoSinSupervisorId);

        // Avance real por la API, como lo haría el supervisor: 1.1 queda
        // completada y 1.2 en curso.
        String tokenSupervisor = login(EMAIL_CON, PASSWORD);
        JsonNode subs = objectMapper.readTree(mockMvc.perform(get("/api/contratos/{id}/etapas", contratoId)
                        .header("Authorization", "Bearer " + tokenSupervisor))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get(0).get("subEtapas");
        cambiarSubetapa(tokenSupervisor, subs.get(0).get("id").asLong(), "COMPLETADA");
        cambiarSubetapa(tokenSupervisor, subs.get(1).get("id").asLong(), "EN_CURSO");

        Documento doc = new Documento();
        doc.setContrato(contratoRepository.findById(contratoId).orElseThrow());
        doc.setSubetapa(subetapaRepository.findById(subs.get(0).get("id").asLong()).orElseThrow());
        doc.setNombre("Acta de Inicio — CO1.PCCNTR.SEGUIM-1");
        doc.setTipo(TipoDocumento.PDF);
        doc.setContentType("application/pdf");
        doc.setContenido("%PDF-1.4 seguimiento".getBytes(StandardCharsets.UTF_8));
        doc.setTamanioBytes(20L);
        doc.setEstado(EstadoDocumento.APROBADO);
        doc.setFirmaId("FIRMA-SEGUIM");
        doc.setGeneradoPorIa(true);
        documentoRepository.save(doc);

        Alerta alerta = new Alerta();
        alerta.setContrato(contratoRepository.findById(contratoId).orElseThrow());
        alerta.setTipo(TipoAlerta.RECORDATORIO);
        alerta.setPrioridad(PrioridadAlerta.MEDIA);
        alerta.setMensaje("Alerta del escenario de seguimiento");
        alertaRepository.save(alerta);
    }

    @Test
    void elAdministradorVeDondeVaCadaSupervisor() throws Exception {
        JsonNode r = seguimiento(tokenAdmin);

        JsonNode ana = supervisor(r, idConFirma);
        assertThat(ana.get("firmaVigente").asBoolean()).isTrue();
        assertThat(ana.get("contratos")).hasSize(1);
        JsonNode c = ana.get("contratos").get(0);
        assertThat(c.get("numeroContrato").asText()).isEqualTo("CO1.PCCNTR.SEGUIM-1");
        assertThat(c.get("estado").asText()).isEqualTo("ACTIVO");
        assertThat(c.get("subetapasCompletadas").asLong()).isEqualTo(1);
        assertThat(c.get("subetapasTotales").asLong()).isEqualTo(27);
        assertThat(c.get("etapaActual").asInt()).isEqualTo(1);
        assertThat(c.get("subetapaEnCurso").get("codigo").asText()).isEqualTo("1.2");
        assertThat(c.get("etapas")).hasSize(6);
        assertThat(c.get("cronograma").get("semaforo").asText()).isNotBlank();
        assertThat(c.get("alertasSinLeer").asLong()).isEqualTo(1);
        JsonNode doc = c.get("documentos").get(0);
        assertThat(doc.get("subetapaCodigo").asText()).isEqualTo("1.1");
        assertThat(doc.get("firmado").asBoolean()).isTrue();
        assertThat(doc.get("generadoPorIa").asBoolean()).isTrue();
        // El avance de la subetapa dejó registro, y es lo último que pasó.
        assertThat(c.get("ultimaActividad").isNull()).isFalse();

        JsonNode beto = supervisor(r, idSinFirma);
        assertThat(beto.get("firmaVigente").asBoolean()).isFalse();
        assertThat(beto.get("contratos")).isEmpty();
    }

    @Test
    void unContratoAbiertoSinSupervisorNoSeEsconde() throws Exception {
        JsonNode sin = seguimiento(tokenAdmin).get("contratosSinSupervisor");
        assertThat(sin).anySatisfy(c -> assertThat(c.get("id").asLong()).isEqualTo(contratoSinSupervisorId));
    }

    @Test
    void soloElAdministradorPuedeVerlo() throws Exception {
        mockMvc.perform(get("/api/seguimiento/supervisores")
                        .header("Authorization", "Bearer " + login(EMAIL_CON, PASSWORD)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/seguimiento/supervisores")
                        .header("Authorization", "Bearer " + tokenGestion))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/seguimiento/supervisores"))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode seguimiento(String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/seguimiento/supervisores")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode supervisor(JsonNode r, long id) {
        for (JsonNode s : r.get("supervisores")) {
            if (s.get("id").asLong() == id) {
                return s;
            }
        }
        throw new AssertionError("El supervisor " + id + " no aparece en el seguimiento");
    }

    private void cambiarSubetapa(String token, long id, String estado) throws Exception {
        mockMvc.perform(patch("/api/subetapas/{id}/estado", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"" + estado + "\"}"))
                .andExpect(status().isOk());
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

    private long crearContrato(String numero) throws Exception {
        String respuesta = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + tokenGestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"%s","objeto":"Contrato del escenario de seguimiento","valor":1000000,
                                 "fechaInicio":"2026-01-01","fechaFin":"2026-12-31"}
                                """.formatted(numero)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).get("id").asLong();
    }

    private void asignarSupervisor(long id, long supervisorId) throws Exception {
        mockMvc.perform(patch("/api/contratos/{id}/supervisor", id)
                        .header("Authorization", "Bearer " + tokenGestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supervisorId\":" + supervisorId + "}"))
                .andExpect(status().isOk());
    }

    private void activar(long id) throws Exception {
        mockMvc.perform(patch("/api/contratos/{id}/estado", id)
                        .header("Authorization", "Bearer " + tokenGestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ACTIVO\"}"))
                .andExpect(status().isOk());
    }

    private String login(String email, String password) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(cuerpo, AuthResponse.class).token();
    }

    /**
     * Revisión del 24-09-2026: si a la cuenta asignada se le cambia el rol, su
     * contrato abierto no aparecía en ninguna lista del seguimiento.
     */
    @Test
    void unContratoCuyoSupervisorDejoDeSerloAparaceSinSupervisor() throws Exception {
        mockMvc.perform(put("/api/usuarios/{id}", idConFirma)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ana Con Firma","email":"%s","telefono":"3000000000","rol":"GESTION"}
                                """.formatted(EMAIL_CON)))
                .andExpect(status().isOk());

        JsonNode sin = seguimiento(tokenAdmin).get("contratosSinSupervisor");
        assertThat(sin).anySatisfy(c -> assertThat(c.get("id").asLong()).isEqualTo(contratoId));
    }

    /**
     * La subetapa en curso es la primera sin cerrar de la etapa actual, aunque
     * nadie la haya puesto EN_CURSO: el panel del supervisor cierra las
     * subetapas de PENDIENTE a COMPLETADA.
     */
    @Test
    void laSubetapaEnCursoEsLaPrimeraSinCerrarAunqueEsteEnPendiente() throws Exception {
        String tokenSupervisor = login(EMAIL_CON, PASSWORD);
        JsonNode subs = objectMapper.readTree(mockMvc.perform(get("/api/contratos/{id}/etapas", contratoId)
                        .header("Authorization", "Bearer " + tokenSupervisor))
                .andReturn().getResponse().getContentAsString()).get(0).get("subEtapas");
        // 1.2 estaba EN_CURSO; se cierra, y 1.3 queda PENDIENTE sin que nadie la inicie.
        cambiarSubetapa(tokenSupervisor, subs.get(1).get("id").asLong(), "COMPLETADA");

        JsonNode c = supervisor(seguimiento(tokenAdmin), idConFirma).get("contratos").get(0);
        assertThat(c.get("subetapaEnCurso").get("codigo").asText()).isEqualTo("1.3");
        assertThat(c.get("subetapaEnCurso").get("estado").asText()).isEqualTo("PENDIENTE");
    }
}
