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
                // Los campos del documento siguen al mismo nivel, y se suma cómo
                // quedaron las observaciones (sin notas: ninguna).
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.nombre").exists())
                .andExpect(jsonPath("$.documento").doesNotExist())
                .andExpect(jsonPath("$.observacionesRedactadasConIa").value(false))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(generado).get("id").asLong();

        mockMvc.perform(post("/api/contratos/{c}/documentos/{id}/firmar", contratoId, id)
                        .param("huellaRevisada", objectMapper.readTree(generado).get("huellaDelBorrador").asText())
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

        // Los cuatro que arma SICOT: el Acta de Inicio la elabora Contratación.
        assertThat(plantillas).hasSize(4);
        assertThat(plantillas.toString()).doesNotContain("ACTA_INICIO");
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

    /**
     * Las tablas que se llenan fila por fila (30-09-2026): el catálogo dice
     * cuáles pide cada formato y qué columnas son del contrato, y la
     * generación las recibe en el campo «tablas» del JSON. Antes, el Informe
     * Final salía con una sola fila pendiente donde el real relaciona treinta
     * obligaciones.
     */
    @Test
    void elInformeFinalSeGeneraConLasTablasQueDaElSupervisorPorHttp() throws Exception {
        JsonNode plantillas = objectMapper.readTree(mockMvc.perform(get("/api/ia/plantillas")
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode informeFinal = null;
        for (JsonNode p : plantillas) {
            if (p.get("tipo").asText().equals("INFORME_FINAL")) {
                informeFinal = p;
            }
        }
        assertThat(informeFinal).isNotNull();
        assertThat(informeFinal.get("tablas").toString())
                .contains("obligacionesGenerales").contains("obligacionesEspecificas").contains("ordenesDePago");
        JsonNode generales = informeFinal.get("tablas").get(0);
        assertThat(generales.get("columnas").get(0).get("delContrato").asBoolean()).isTrue();
        assertThat(generales.get("columnas").get(1).get("delContrato").asBoolean()).isFalse();

        String generado = mockMvc.perform(post("/api/contratos/{c}/documentos/generar", contratoId)
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"INFORME_FINAL","datos":{"valorTotalPagado":"$19.989.620,00"},
                                 "tablas":{"obligacionesGenerales":[["Obrar con lealtad y buena fe","si cumplio",
                                   "Sin dilataciones en el proceso"]],
                                  "ordenesDePago":[["70614726","11/03/2026","16798000"],
                                   ["70614826","11/03/2026","$ 3.191.620,00"]]}}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(generado).get("id").asLong();

        String texto = new PdfTextExtractor().extraerTexto(documentoRepository.findById(id).orElseThrow()
                .getContenido());
        assertThat(texto).contains("Obrar con lealtad y buena fe").contains("SI CUMPLIO")
                .contains("70614826").contains("$3.191.620,00");

        // Si las órdenes no suman el total pagado, 400 con el motivo.
        mockMvc.perform(post("/api/contratos/{c}/documentos/generar", contratoId)
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"INFORME_FINAL","datos":{"valorTotalPagado":"$20.000.000,00"},
                                 "tablas":{"ordenesDePago":[["70614726","11/03/2026","16798000"]]}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("suman $16.798.000,00")));
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

    /**
     * El acta que entró al expediente del contrato de prueba el 15-10-2026:
     * 44 bytes de texto que empiezan por «%PDF-1.4». Desde el 20-10-2026 un
     * archivo así ya no entra (ArchivoValidator), pero las filas anteriores
     * siguen ahí y la descarga las servía como un PDF: el supervisor bajaba el
     * archivo, su lector lo daba por dañado y nada le decía por qué.
     */
    @Test
    void unDocumentoGuardadoQueNingunLectorAbreRespondeNoEncontradoConElMotivo() throws Exception {
        Documento roto = new Documento();
        roto.setContrato(contratoRepository.findById(contratoId).orElseThrow());
        roto.setNombre("Acta de Inicio firmada");
        roto.setTipo(TipoDocumento.PDF);
        roto.setContentType("application/pdf");
        byte[] soloLaCabecera = "%PDF-1.4\n% acta firmada de prueba E2E\n%%EOF\n"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        roto.setContenido(soloLaCabecera);
        roto.setTamanioBytes((long) soloLaCabecera.length);
        roto.setEstado(EstadoDocumento.PENDIENTE);
        long id = documentoRepository.save(roto).getId();

        mockMvc.perform(get("/api/contratos/{c}/documentos/{id}/archivo", contratoId, id)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("no se puede abrir")))
                .andExpect(jsonPath("$.message", containsString("Cárguelo de nuevo")));
    }

    /**
     * Una foto de evidencia sí se entrega aunque no se abra, y es deliberado.
     *
     * <p>Descodificar cada foto al descargarla cuesta unos 300 ms para una de
     * 12 Mpx —las que manda cualquier teléfono—, frente a los 7-10 ms de
     * abrir un PDF, que solo lee la tabla de objetos (medido el 26-10-2026).
     * La evidencia fotográfica es lo que más se baja en el flujo del
     * supervisor y se baja desde el teléfono: pagar eso en cada descarga para
     * cazar una foto antigua ilegible —caso raro, y cerrado para las nuevas
     * en la carga— no compensa. Ver {@code ArchivoValidator.porQueNoSeAbreAlEntregarlo}.
     */
    @Test
    void unaFotoGuardadaQueNoSeAbreSeEntregaIgual() throws Exception {
        Documento foto = new Documento();
        foto.setContrato(contratoRepository.findById(contratoId).orElseThrow());
        foto.setNombre("Evidencia del envío del acta");
        foto.setTipo(TipoDocumento.IMAGEN);
        foto.setContentType("image/png");
        // La firma PNG y la cabecera IHDR, sin un solo bloque de píxeles: Tika
        // la sigue viendo como image/png y ningún visor la muestra.
        byte[] aMedias = java.util.Arrays.copyOf(ArchivoDePrueba.imagen("png"), 40);
        foto.setContenido(aMedias);
        foto.setTamanioBytes((long) aMedias.length);
        foto.setEstado(EstadoDocumento.PENDIENTE);
        long id = documentoRepository.save(foto).getId();

        byte[] bajada = mockMvc.perform(get("/api/contratos/{c}/documentos/{id}/archivo", contratoId, id)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(bajada).isEqualTo(aMedias);
    }

    /** Un PDF real sigue bajando entero: la comprobación no estorba a lo que sí se abre. */
    @Test
    void unDocumentoGuardadoQueSiSeAbreBajaIntacto() throws Exception {
        Documento bueno = new Documento();
        bueno.setContrato(contratoRepository.findById(contratoId).orElseThrow());
        bueno.setNombre("Acta de Inicio firmada");
        bueno.setTipo(TipoDocumento.PDF);
        bueno.setContentType("application/pdf");
        byte[] pdf = ArchivoDePrueba.pdfDeUnaPagina("acta de inicio firmada en papel");
        bueno.setContenido(pdf);
        bueno.setTamanioBytes((long) pdf.length);
        bueno.setEstado(EstadoDocumento.PENDIENTE);
        long id = documentoRepository.save(bueno).getId();

        byte[] bajado = mockMvc.perform(get("/api/contratos/{c}/documentos/{id}/archivo", contratoId, id)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(bajado).isEqualTo(pdf);
    }

    // ── Hallazgos de la revisión adversarial del 28-09-2026 ─────────────────

    private long pasoDelInforme() throws Exception {
        JsonNode etapas = objectMapper.readTree(mockMvc.perform(get("/api/contratos/{id}/etapas", contratoId)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        // 3.4, el Informe de Supervisión. Hasta el 18-10-2026 estas pruebas
        // generaban el Acta de Inicio, que SICOT ya no arma: la API la rechaza.
        return etapas.get(2).get("subEtapas").get(3).get("id").asLong();
    }

    private long generarInforme(long subetapa) throws Exception {
        return generarInformeConHuella(subetapa).get("id").asLong();
    }

    /** La respuesta de la generación: el id y la huella del borrador que se firma. */
    private JsonNode generarInformeConHuella(long subetapa) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/contratos/{c}/documentos/generar", contratoId)
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"INFORME_SUPERVISION\",\"subetapaId\":" + subetapa + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /** Un PDF cargado a mano en el contrato, pendiente de firma. */
    private long cargarPdf(String nombre) {
        byte[] contenido = "%PDF-1.4 acta cargada".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Documento cargado = new Documento();
        cargado.setContrato(contratoRepository.findById(contratoId).orElseThrow());
        cargado.setNombre(nombre);
        cargado.setTipo(TipoDocumento.PDF);
        cargado.setContentType("application/pdf");
        cargado.setContenido(contenido);
        cargado.setTamanioBytes((long) contenido.length);
        cargado.setEstado(EstadoDocumento.PENDIENTE);
        return documentoRepository.save(cargado).getId();
    }

    private static final String EMAIL_SUPERVISOR_NUEVO = "supervisor.nuevo.e2e@soy.sena.edu.co";

    /** Otro supervisor, con su firma electrónica, para reasignarle el contrato. */
    private String supervisorNuevoConFirma() throws Exception {
        String creado = mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Supervisor Nuevo","email":"%s","password":"Reasignado123",
                                 "telefono":"3000000000","rol":"SUPERVISOR"}""".formatted(EMAIL_SUPERVISOR_NUEVO)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/firmas")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":" + objectMapper.readTree(creado).get("id").asLong() + "}"))
                .andExpect(status().isCreated());
        return login(EMAIL_SUPERVISOR_NUEVO, "Reasignado123");
    }

    /**
     * Un PDF cargado a mano en la subetapa, con un nombre que empieza como el
     * formato, se tomaba por borrador: la generación lo sobrescribía y
     * respondía 500.
     */
    @Test
    void generarNoSobrescribeUnDocumentoCargadoAManoConUnNombreParecido() throws Exception {
        long subetapa = pasoDelInforme();
        byte[] escaneado = "%PDF-1.4 acta escaneada firmada en papel".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Documento cargado = new Documento();
        cargado.setContrato(contratoRepository.findById(contratoId).orElseThrow());
        cargado.setSubetapa(subetapaRepository.findById(subetapa).orElseThrow());
        cargado.setNombre("Informe de Supervisión firmado en papel");
        cargado.setTipo(TipoDocumento.PDF);
        cargado.setContentType("application/pdf");
        cargado.setContenido(escaneado);
        cargado.setTamanioBytes((long) escaneado.length);
        cargado.setEstado(EstadoDocumento.PENDIENTE);
        long idCargado = documentoRepository.save(cargado).getId();

        long generado = generarInforme(subetapa);

        assertThat(generado).isNotEqualTo(idCargado);
        assertThat(documentoRepository.findById(idCargado).orElseThrow().getContenido()).isEqualTo(escaneado);
    }

    /**
     * El nombre estampado en el hueco del supervisor tiene que ser el del
     * supervisor: un administrador con firma propia no firma, ni siquiera con
     * la huella del borrador.
     */
    @Test
    void unDocumentoGeneradoSoloLoFirmaElSupervisorDelContrato() throws Exception {
        asegurarFirmaDelAdministrador();
        JsonNode generado = generarInformeConHuella(pasoDelInforme());
        long id = generado.get("id").asLong();

        mockMvc.perform(post("/api/contratos/{c}/documentos/{id}/firmar", contratoId, id)
                        .param("huellaRevisada", generado.get("huellaDelBorrador").asText())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden());
        assertThat(documentoRepository.findById(id).orElseThrow().getFirmaId()).isNull();
    }

    /**
     * Auditoría del 02-10-2026: la regla del supervisor asignado valía solo
     * para lo generado. Un acta cargada por Gestión la firmaba cualquier
     * administrador, y quedaba aprobada e inmutable sin que el supervisor del
     * contrato la viera.
     */
    @Test
    void unDocumentoCargadoTampocoLoFirmaUnAdministrador() throws Exception {
        asegurarFirmaDelAdministrador();
        long id = cargarPdf("Acta firmada por el contratista");

        mockMvc.perform(post("/api/contratos/{c}/documentos/{id}/firmar", contratoId, id)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden());
        Documento documento = documentoRepository.findById(id).orElseThrow();
        assertThat(documento.getFirmaId()).isNull();
        assertThat(documento.getEstado()).isEqualTo(EstadoDocumento.PENDIENTE);

        // El supervisor asignado sí, y sin huella: un documento cargado no se
        // regenera, así que no hay otro contenido que pudiera firmar sin verlo.
        mockMvc.perform(post("/api/contratos/{c}/documentos/{id}/firmar", contratoId, id)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk());
    }

    /**
     * Un borrador generado se regenera sobre la misma fila: sin la huella de lo
     * que se revisó, se firmaba lo que hubiera en ella en ese instante, quizá
     * los datos de otra pestaña (auditoría del 02-10-2026).
     */
    @Test
    void unDocumentoGeneradoNoSeFirmaSinLaHuellaDelBorradorRevisado() throws Exception {
        long id = generarInforme(pasoDelInforme());

        mockMvc.perform(post("/api/contratos/{c}/documentos/{id}/firmar", contratoId, id)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Falta indicar qué borrador revisó")));
        mockMvc.perform(post("/api/contratos/{c}/documentos/{id}/firmar", contratoId, id)
                        .param("huellaRevisada", " ")
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Falta indicar qué borrador revisó")));
        assertThat(documentoRepository.findById(id).orElseThrow().getFirmaId()).isNull();
    }

    /**
     * Si la firma visible no se puede estampar, el documento no se firma:
     * antes quedaba firmado e íntegro con el hueco de la firma vacío, y ya no
     * se podía volver a estampar (auditoría del 02-10-2026).
     */
    @Test
    void siNoSePuedeEstamparLaFirmaElDocumentoNoQuedaFirmado() throws Exception {
        long id = generarInforme(pasoDelInforme());
        Documento documento = documentoRepository.findById(id).orElseThrow();
        byte[] danado;
        try (PDDocument pdf = Loader.loadPDF(documento.getContenido())) {
            // El hueco apunta a una página que el documento no tiene.
            pdf.getDocumentInformation().setCustomMetadataValue("SICOT-AnclaFirma", "99;70;100;200;60");
            var salida = new java.io.ByteArrayOutputStream();
            pdf.save(salida);
            danado = salida.toByteArray();
        }
        documento.setContenido(danado);
        documentoRepository.save(documento);

        mockMvc.perform(post("/api/contratos/{c}/documentos/{id}/firmar", contratoId, id)
                        .param("huellaRevisada", HexFormat.of().formatHex(
                                MessageDigest.getInstance("SHA-256").digest(danado)))
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("No se pudo poner la firma visible")));
        Documento sinFirmar = documentoRepository.findById(id).orElseThrow();
        assertThat(sinFirmar.getFirmaId()).isNull();
        assertThat(sinFirmar.getFirmaHashSha256()).isNull();
        assertThat(sinFirmar.getContenido()).isEqualTo(danado);
    }

    /**
     * Desde el 15-10-2026 el acta la elabora Contratación y el supervisor la
     * carga firmada en el paso 2.1. Generada por SICOT, contaba como «acta
     * firmada» para cerrar ese paso sin la real.
     */
    @Test
    void elActaDeInicioYaNoSeGeneraEnSicot() throws Exception {
        mockMvc.perform(post("/api/contratos/{c}/documentos/generar", contratoId)
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"ACTA_INICIO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("SICOT ya no arma el Acta de Inicio")));
    }

    /** Si Gestión corrige el número del contrato, el borrador regenerado lleva el nombre nuevo. */
    @Test
    void alRegenerarElBorradorTomaElNumeroDeContratoActual() throws Exception {
        long subetapa = pasoDelInforme();
        long primero = generarInforme(subetapa);
        var contrato = contratoRepository.findById(contratoId).orElseThrow();
        contrato.setNumeroContrato("CO1.PCCNTR.8151685-CORREGIDO");
        contratoRepository.save(contrato);

        long segundo = generarInforme(subetapa);

        assertThat(segundo).isEqualTo(primero);
        assertThat(documentoRepository.findById(segundo).orElseThrow().getNombre())
                .isEqualTo("Informe de Supervisión — CO1.PCCNTR.8151685-CORREGIDO");
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
        long subetapa = pasoDelInforme();
        JsonNode generado = generarInformeConHuella(subetapa);
        long id = generado.get("id").asLong();
        String nuevo = supervisorNuevoConFirma();
        var contrato = contratoRepository.findById(contratoId).orElseThrow();
        contrato.setSupervisor(usuarioRepository.findByEmail(EMAIL_SUPERVISOR_NUEVO).orElseThrow());
        contratoRepository.save(contrato);

        mockMvc.perform(post("/api/contratos/{c}/documentos/{id}/firmar", contratoId, id)
                        .param("huellaRevisada", generado.get("huellaDelBorrador").asText())
                        .header("Authorization", "Bearer " + nuevo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("era otra persona")));

        JsonNode regenerado = objectMapper.readTree(mockMvc.perform(
                        post("/api/contratos/{c}/documentos/generar", contratoId)
                                .header("Authorization", "Bearer " + nuevo)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"tipo\":\"INFORME_SUPERVISION\",\"subetapaId\":" + subetapa + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(regenerado.get("id").asLong()).isEqualTo(id);
        mockMvc.perform(post("/api/contratos/{c}/documentos/{id}/firmar", contratoId, id)
                        .param("huellaRevisada", regenerado.get("huellaDelBorrador").asText())
                        .header("Authorization", "Bearer " + nuevo))
                .andExpect(status().isOk());
    }

    /**
     * Sin supervisor, el administrador firmaba sobre «[dato pendiente:
     * supervisor sin asignar]». Ahora ningún documento lo firma un
     * administrador, y un supervisor no ve un contrato que no tiene asignado.
     */
    @Test
    void unDocumentoGeneradoDeUnContratoSinSupervisorNoSeFirma() throws Exception {
        asegurarFirmaDelAdministrador();
        var contrato = contratoRepository.findById(contratoId).orElseThrow();
        contrato.setSupervisor(null);
        contratoRepository.save(contrato);
        String generado = mockMvc.perform(post("/api/contratos/{c}/documentos/generar", contratoId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"INFORME_SUPERVISION\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(generado).get("id").asLong();
        String huella = objectMapper.readTree(generado).get("huellaDelBorrador").asText();

        mockMvc.perform(post("/api/contratos/{c}/documentos/{id}/firmar", contratoId, id)
                        .param("huellaRevisada", huella)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/contratos/{c}/documentos/{id}/firmar", contratoId, id)
                        .param("huellaRevisada", huella)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isNotFound());
        assertThat(documentoRepository.findById(id).orElseThrow().getFirmaId()).isNull();
    }

    /**
     * Un PDF cargado a mano y firmado en la subetapa, con nombre parecido, no
     * es el acta generada del paso: no bloquea generarla. Antes el servidor lo
     * contaba como «ya firmado» y el panel no, y el sub-paso no se cerraba.
     */
    @Test
    void unDocumentoCargadoAManoYFirmadoNoImpideGenerarElDelPaso() throws Exception {
        long subetapa = pasoDelInforme();
        Documento cargado = new Documento();
        cargado.setContrato(contratoRepository.findById(contratoId).orElseThrow());
        cargado.setSubetapa(subetapaRepository.findById(subetapa).orElseThrow());
        cargado.setNombre("Informe de Supervisión firmado en papel");
        cargado.setTipo(TipoDocumento.PDF);
        cargado.setContentType("application/pdf");
        byte[] escaneado = "%PDF-1.4 escaneado".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        cargado.setContenido(escaneado);
        cargado.setTamanioBytes((long) escaneado.length);
        cargado.setEstado(EstadoDocumento.APROBADO);
        cargado.setFirmaId("FIRMA-EN-PAPEL");
        documentoRepository.save(cargado);

        assertThat(generarInforme(subetapa)).isPositive();
    }

    /**
     * «Firmar con esta redacción» firma lo que el supervisor leyó: si el
     * borrador se regeneró entre medias (otra pestaña, un reintento), su
     * huella ya no es la revisada y la firma se rechaza.
     */
    @Test
    void soloSeFirmaElBorradorQueElSupervisorRevisó() throws Exception {
        long subetapa = pasoDelInforme();
        String r = mockMvc.perform(post("/api/contratos/{c}/documentos/generar", contratoId)
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"INFORME_SUPERVISION\",\"subetapaId\":" + subetapa + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.huellaDelBorrador").exists())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(r).get("id").asLong();
        String huellaRevisada = objectMapper.readTree(r).get("huellaDelBorrador").asText();
        // Otra pestaña regenera el mismo borrador con otros datos.
        mockMvc.perform(post("/api/contratos/{c}/documentos/generar", contratoId)
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"INFORME_SUPERVISION\",\"subetapaId\":" + subetapa
                                + ",\"datos\":{\"cedulaSupervisor\":\"98.587.121\"}}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/contratos/{c}/documentos/{id}/firmar", contratoId, id)
                        .param("huellaRevisada", huellaRevisada)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cambió desde que lo revisó")));
        assertThat(documentoRepository.findById(id).orElseThrow().getFirmaId()).isNull();

        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(documentoRepository.findById(id).orElseThrow().getContenido()));
        mockMvc.perform(post("/api/contratos/{c}/documentos/{id}/firmar", contratoId, id)
                        .param("huellaRevisada", actual)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk());
    }

    /**
     * Quien integre contra Swagger tiene que ver los campos que dicen si la
     * redacción la hizo el Copiloto (y que hay que mostrarla antes de firmar),
     * junto a los del documento, al mismo nivel que en el JSON real.
     */
    @Test
    void swaggerDocumentaLaRespuestaDeLaGeneracion() throws Exception {
        JsonNode api = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String ref = api.at("/paths/~1api~1contratos~1{contratoId}~1documentos~1generar/post/responses/200/content")
                .elements().next().get("schema").get("$ref").asText();
        assertThat(ref).endsWith("/DocumentoGeneradoResponse");
        JsonNode propiedades = api.at("/components/schemas/DocumentoGeneradoResponse/properties");
        assertThat(propiedades.has("observacionesRedactadasConIa")).isTrue();
        assertThat(propiedades.has("huellaDelBorrador")).isTrue();
        assertThat(propiedades.has("id")).as("los campos del documento, al mismo nivel").isTrue();
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
