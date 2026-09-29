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

    private String supervisor;
    private long contratoId;

    @BeforeEach
    void preparar() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        supervisor = login("supervisor@soy.sena.edu.co", "Supervisor123*");
        String admin = login("administrador@soy.sena.edu.co", "Admin123*");
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
