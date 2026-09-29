package co.sena.sicot;

import co.sena.sicot.dto.auth.AuthResponse;
import co.sena.sicot.entity.Documento;
import co.sena.sicot.entity.enums.EstadoDocumento;
import co.sena.sicot.entity.enums.TipoDocumento;
import co.sena.sicot.ia.PdfTextExtractor;
import co.sena.sicot.repository.ContratoRepository;
import co.sena.sicot.repository.DocumentoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Un documento formal de principio a fin, por la API, como lo recorre el
 * supervisor: generarlo con los datos que el contrato no tiene, firmarlo,
 * descargarlo y verificar que es el mismo que se firmó.
 *
 * <p>Fija las correcciones del 28-09-2026: el PDF sigue el formato oficial
 * (código y versión al pie, fuentes incrustadas), lleva la firma visible, su
 * huella se calcula después de estampar la firma (así la verificación da
 * íntegro), baja con extensión .pdf, y un documento sin archivo responde 404
 * en vez de un 200 vacío. Sin notas, no se llama al modelo: no necesita Ollama.
 */
class DocumentoFormalDeExtremoAExtremoIntegrationTest extends PruebaDeIntegracion {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentoRepository documentoRepository;

    @Autowired
    private ContratoRepository contratoRepository;

    @Autowired
    private co.sena.sicot.repository.SubetapaRepository subetapaRepository;

    @Autowired
    private co.sena.sicot.repository.UsuarioRepository usuarioRepository;

    private String supervisor;
    private String admin;
    private String gestion;
    private long contratoId;

    @BeforeEach
    void preparar() throws Exception {
        gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        supervisor = login("supervisor@soy.sena.edu.co", "Supervisor123*");
        admin = login("administrador@soy.sena.edu.co", "Admin123*");
        long idSupervisor = objectMapper.readTree(loginBody("supervisor@soy.sena.edu.co", "Supervisor123*"))
                .get("usuarioId").asLong();
        String creado = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"CO1.PCCNTR.8151685-E2E","objeto":"CONTRATAR EL SUMINISTRO DE MATERIALES",
                                 "valor":99067527,"fechaInicio":"2025-08-01","fechaFin":"2025-12-15",
                                 "contratista":"EQUISUMINISTROS Y CONSTRUCCIONES SAS","contratistaNit":"900352345",
                                 "supervisorId":%d}""".formatted(idSupervisor)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        contratoId = objectMapper.readTree(creado).get("id").asLong();
        String miFirma = mockMvc.perform(get("/api/firmas/mia").header("Authorization", "Bearer " + supervisor))
                .andReturn().getResponse().getContentAsString();
        if (!objectMapper.readTree(miFirma).get("tieneFirmaActiva").asBoolean()) {
            mockMvc.perform(post("/api/firmas")
                            .header("Authorization", "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"usuarioId\":" + idSupervisor + "}"))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void elCertificadoSeGeneraConLosDatosDelSupervisorSeFirmaYBajaIntegro() throws Exception {
        String generado = mockMvc.perform(post("/api/contratos/{c}/documentos/generar", contratoId)
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"CERTIFICACION_CUMPLIMIENTO","datos":{"numeroFactura":"FE 547",
                                 "fechaFactura":"03/11/2025","valorFactura":"$39.400.634,00","banco":"Bancolombia"}}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(generado).get("id").asLong();

        mockMvc.perform(post("/api/contratos/{c}/documentos/{id}/firmar", contratoId, id)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk());

        MockHttpServletResponse descarga = mockMvc.perform(get("/api/contratos/{c}/documentos/{id}/archivo", contratoId, id)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        byte[] pdf = descarga.getContentAsByteArray();

        assertThat(descarga.getHeader("Content-Disposition"))
                .contains("filename=\"Certificacion de cumplimiento - CO1.PCCNTR.8151685-E2E.pdf\"")
                .contains("filename*=UTF-8''");
        assertThat(descarga.getHeader("X-SICOT-Integridad")).isEqualTo("INTEGRO");
        String texto = new PdfTextExtractor().extraerTexto(pdf);
        assertThat(texto)
                .contains("CERTIFICA:")
                .contains("FE 547")
                .contains("BANCOLOMBIA")
                .contains("Firmado electrónicamente en SICOT");
        // Lo que se descarga es exactamente lo que se firmó.
        String huella = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf));
        assertThat(documentoRepository.findById(id).orElseThrow().getFirmaHashSha256()).isEqualTo(huella);
        try (PDDocument d = Loader.loadPDF(pdf)) {
            d.getPages().forEach(p -> p.getResources().getFontNames().forEach(n -> {
                try {
                    assertThat(p.getResources().getFont(n).isEmbedded()).isTrue();
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }));
        }
        mockMvc.perform(get("/api/contratos/{c}/documentos/{id}/verificacion", contratoId, id)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INTEGRO"));
    }

    @Test
    void elCatalogoDiceQueDatosPideCadaDocumento() throws Exception {
        JsonNode plantillas = objectMapper.readTree(mockMvc.perform(get("/api/ia/plantillas")
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(plantillas).hasSize(5);
        JsonNode certificado = null;
        for (JsonNode p : plantillas) {
            if (p.get("tipo").asText().equals("CERTIFICACION_CUMPLIMIENTO")) {
                certificado = p;
            }
        }
        assertThat(certificado).isNotNull();
        assertThat(certificado.get("llevaObservaciones").asBoolean()).isFalse();
        assertThat(certificado.get("campos").toString()).contains("numeroFactura").contains("numeroCuenta");

        // El formulario usa estos dos datos para no recordar la factura del mes
        // pasado en el informe siguiente y para exigir el valor actualizado
        // solo cuando hubo adición.
        java.util.Map<String, JsonNode> camposDelInforme = new java.util.HashMap<>();
        for (JsonNode p : plantillas) {
            if (p.get("tipo").asText().equals("INFORME_SUPERVISION")) {
                p.get("campos").forEach(c -> camposDelInforme.put(c.get("clave").asText(), c));
            }
        }
        assertThat(camposDelInforme.get("numeroFactura").get("porDocumento").asBoolean()).isTrue();
        assertThat(camposDelInforme.get("valorActual").get("dependeDe").asText()).isEqualTo("adicion");
        assertThat(camposDelInforme.get("valorActual").get("opcional").asBoolean()).isTrue();
        assertThat(camposDelInforme.get("aseguradora").get("porDocumento").asBoolean()).isFalse();
        assertThat(camposDelInforme.get("aseguradora").get("dependeDe").isNull()).isTrue();
    }

    /** Así bajaban los documentos de demostración sin archivo: 200 con cero bytes, un «.pdf» dañado. */
    @Test
    void unDocumentoSinArchivoRespondeNoEncontradoConElMotivo() throws Exception {
        Documento vacio = new Documento();
        vacio.setContrato(contratoRepository.findById(contratoId).orElseThrow());
        vacio.setNombre("Informe de supervision mensual - periodo 2.pdf");
        vacio.setTipo(TipoDocumento.PDF);
        vacio.setContentType("application/pdf");
        vacio.setTamanioBytes(271360L);
        vacio.setEstado(EstadoDocumento.PENDIENTE);
        long id = documentoRepository.save(vacio).getId();

        mockMvc.perform(get("/api/contratos/{c}/documentos/{id}/archivo", contratoId, id)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("no tiene archivo guardado")));
    }

    // ── Hallazgos de la revisión adversarial del 28-09-2026 ─────────────────

    private long subetapa27() throws Exception {
        JsonNode etapas = objectMapper.readTree(mockMvc.perform(get("/api/contratos/{id}/etapas", contratoId)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return etapas.get(1).get("subEtapas").get(6).get("id").asLong();
    }

    private long generarActa(long subetapa) throws Exception {
        String r = mockMvc.perform(post("/api/contratos/{c}/documentos/generar", contratoId)
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"ACTA_INICIO\",\"subetapaId\":" + subetapa + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(r).get("id").asLong();
    }

    /**
     * Un PDF cargado a mano en la subetapa, con un nombre que empieza como el
     * formato, se tomaba por borrador: la generación lo sobrescribía y
     * respondía 500.
     */
    @Test
    void generarNoSobrescribeUnDocumentoCargadoAManoConUnNombreParecido() throws Exception {
        long subetapa = subetapa27();
        byte[] escaneado = "%PDF-1.4 acta escaneada firmada en papel".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Documento cargado = new Documento();
        cargado.setContrato(contratoRepository.findById(contratoId).orElseThrow());
        cargado.setSubetapa(subetapaRepository.findById(subetapa).orElseThrow());
        cargado.setNombre("Acta de Inicio firmada por el contratista");
        cargado.setTipo(TipoDocumento.PDF);
        cargado.setContentType("application/pdf");
        cargado.setContenido(escaneado);
        cargado.setTamanioBytes((long) escaneado.length);
        cargado.setEstado(EstadoDocumento.PENDIENTE);
        long idCargado = documentoRepository.save(cargado).getId();

        long generado = generarActa(subetapa);

        assertThat(generado).isNotEqualTo(idCargado);
        assertThat(documentoRepository.findById(idCargado).orElseThrow().getContenido()).isEqualTo(escaneado);
    }

    /** El nombre estampado en el hueco del supervisor tiene que ser el del supervisor. */
    @Test
    void unDocumentoGeneradoSoloLoFirmaElSupervisorDelContrato() throws Exception {
        long id = generarActa(subetapa27());
        String miFirma = mockMvc.perform(get("/api/firmas/mia").header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getContentAsString();
        if (!objectMapper.readTree(miFirma).get("tieneFirmaActiva").asBoolean()) {
            long idAdmin = objectMapper.readTree(loginBody("administrador@soy.sena.edu.co", "Admin123*"))
                    .get("usuarioId").asLong();
            mockMvc.perform(post("/api/firmas")
                            .header("Authorization", "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"usuarioId\":" + idAdmin + "}"))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/contratos/{c}/documentos/{id}/firmar", contratoId, id)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("lo firma el supervisor del contrato")));
        assertThat(documentoRepository.findById(id).orElseThrow().getFirmaId()).isNull();
    }

    /** Si Gestión corrige el número del contrato, el borrador regenerado lleva el nombre nuevo. */
    @Test
    void alRegenerarElBorradorTomaElNumeroDeContratoActual() throws Exception {
        long subetapa = subetapa27();
        long primero = generarActa(subetapa);
        var contrato = contratoRepository.findById(contratoId).orElseThrow();
        contrato.setNumeroContrato("CO1.PCCNTR.8151685-CORREGIDO");
        contratoRepository.save(contrato);

        long segundo = generarActa(subetapa);

        assertThat(segundo).isEqualTo(primero);
        assertThat(documentoRepository.findById(segundo).orElseThrow().getNombre())
                .isEqualTo("Acta de Inicio — CO1.PCCNTR.8151685-CORREGIDO");
    }

    // ── Hallazgos de la revisión del 29-09-2026 ─────────────────────────────

    private void asegurarFirmaDelAdministrador() throws Exception {
        String miFirma = mockMvc.perform(get("/api/firmas/mia").header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getContentAsString();
        if (!objectMapper.readTree(miFirma).get("tieneFirmaActiva").asBoolean()) {
            long idAdmin = objectMapper.readTree(loginBody("administrador@soy.sena.edu.co", "Admin123*"))
                    .get("usuarioId").asLong();
            mockMvc.perform(post("/api/firmas")
                            .header("Authorization", "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"usuarioId\":" + idAdmin + "}"))
                    .andExpect(status().isCreated());
        }
    }

    /**
     * Gestión reasigna el contrato con un borrador ya generado: el supervisor
     * nuevo no puede firmar un documento que lleva impreso el nombre del
     * anterior. Regenerarlo lo pone a su nombre, y entonces sí.
     */
    @Test
    void trasReasignarElContratoElBorradorDelSupervisorAnteriorNoSeFirmaSinRegenerarlo() throws Exception {
        asegurarFirmaDelAdministrador();
        long subetapa = subetapa27();
        long id = generarActa(subetapa);
        var contrato = contratoRepository.findById(contratoId).orElseThrow();
        long idAdmin = objectMapper.readTree(loginBody("administrador@soy.sena.edu.co", "Admin123*"))
                .get("usuarioId").asLong();
        contrato.setSupervisor(usuarioRepository.findById(idAdmin).orElseThrow());
        contratoRepository.save(contrato);

        mockMvc.perform(post("/api/contratos/{c}/documentos/{id}/firmar", contratoId, id)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("era otra persona")));

        String regenerado = mockMvc.perform(post("/api/contratos/{c}/documentos/generar", contratoId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"ACTA_INICIO\",\"subetapaId\":" + subetapa + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(regenerado).get("id").asLong()).isEqualTo(id);
        mockMvc.perform(post("/api/contratos/{c}/documentos/{id}/firmar", contratoId, id)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    /** Sin supervisor, el administrador firmaba sobre «[dato pendiente: supervisor sin asignar]». */
    @Test
    void unDocumentoGeneradoDeUnContratoSinSupervisorNoSeFirma() throws Exception {
        asegurarFirmaDelAdministrador();
        var contrato = contratoRepository.findById(contratoId).orElseThrow();
        contrato.setSupervisor(null);
        contratoRepository.save(contrato);
        String generado = mockMvc.perform(post("/api/contratos/{c}/documentos/generar", contratoId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"ACTA_INICIO\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(generado).get("id").asLong();

        mockMvc.perform(post("/api/contratos/{c}/documentos/{id}/firmar", contratoId, id)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("no tiene supervisor asignado")));
    }

    /**
     * Un PDF cargado a mano y firmado en la subetapa, con nombre parecido, no
     * es el acta generada del paso: no bloquea generarla. Antes el servidor lo
     * contaba como «ya firmado» y el panel no, y el sub-paso no se cerraba.
     */
    @Test
    void unDocumentoCargadoAManoYFirmadoNoImpideGenerarElDelPaso() throws Exception {
        long subetapa = subetapa27();
        Documento cargado = new Documento();
        cargado.setContrato(contratoRepository.findById(contratoId).orElseThrow());
        cargado.setSubetapa(subetapaRepository.findById(subetapa).orElseThrow());
        cargado.setNombre("Acta de Inicio firmada por el contratista");
        cargado.setTipo(TipoDocumento.PDF);
        cargado.setContentType("application/pdf");
        byte[] escaneado = "%PDF-1.4 escaneado".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        cargado.setContenido(escaneado);
        cargado.setTamanioBytes((long) escaneado.length);
        cargado.setEstado(EstadoDocumento.APROBADO);
        cargado.setFirmaId("FIRMA-EN-PAPEL");
        documentoRepository.save(cargado);

        assertThat(generarActa(subetapa)).isPositive();
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
