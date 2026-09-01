package co.sena.sicot;

import co.sena.sicot.dto.auth.AuthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cubre dos correcciones que comparten origen: el {@code Content-Type} dejó de
 * venir del cliente, y la firma pasó a registrar la huella del contenido.
 *
 * <p>Las dos nacieron del mismo defecto de fondo — el backend se creía lo que le
 * contaba el cliente sobre un archivo, y no comprobaba nada sobre los bytes que
 * de verdad guardaba — así que conviene que se rompan juntas si alguien las
 * revierte.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IntegridadYContentTypeIntegrationTest {

    private static final byte[] PDF = "%PDF-1.4 acta de inicio del contrato".getBytes(StandardCharsets.UTF_8);

    /** Cada prueba usa su propio número de contrato: la base es compartida dentro de la clase. */
    private static final AtomicInteger SECUENCIA = new AtomicInteger(1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    private long contratoId;
    private String gestion;
    private String supervisor;

    @BeforeEach
    void prepararContratoConSupervisor() throws Exception {
        gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        supervisor = login("supervisor@soy.sena.edu.co", "Supervisor123*");
        contratoId = crearContratoAsignadoAlSupervisor();
    }

    /**
     * El fallo original: el {@code Content-Type} lo elegía quien subía el
     * archivo, se guardaba tal cual y se devolvía en la descarga. Con un valor
     * sin barra ("no-es-un-mime"), {@code MediaType.parseMediaType} lanzaba al
     * descargar y ese documento quedaba <b>permanentemente indescargable</b> con
     * un 500, sin ningún endpoint para borrarlo ni corregirlo.
     */
    @Test
    void unContentTypeInventadoPorElClienteNoRompeLaDescarga() throws Exception {
        MockMultipartFile archivo = new MockMultipartFile("archivo", "acta.pdf", "no-es-un-mime", PDF);

        long id = subir(archivo, "Acta con content-type falseado");

        mockMvc.perform(get("/api/contratos/{c}/documentos/{id}/archivo", contratoId, id)
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/pdf"))
                .andExpect(content().bytes(PDF));
    }

    /**
     * Mismo ataque, otra forma: un tipo válido como cabecera HTTP pero que
     * convertiría un PDF en algo que el navegador podría interpretar como página.
     */
    @Test
    void unContentTypeDeTextoHtmlNoSobreviveALaSubida() throws Exception {
        MockMultipartFile archivo = new MockMultipartFile("archivo", "informe.pdf", "text/html", PDF);

        long id = subir(archivo, "Informe con text/html");

        mockMvc.perform(get("/api/contratos/{c}/documentos/{id}/archivo", contratoId, id)
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/pdf"))
                // La descarga sigue forzando la bajada, nunca la interpretación.
                .andExpect(header().string("Content-Disposition", containsString("attachment")));
    }

    @Test
    void unDocumentoSinFirmarSeReportaComoSinFirma() throws Exception {
        long id = subir(pdf(), "Documento sin firmar");

        mockMvc.perform(get("/api/contratos/{c}/documentos/{id}/verificacion", contratoId, id)
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SIN_FIRMA"))
                .andExpect(jsonPath("$.hashRegistrado").doesNotExist());
    }

    @Test
    void firmarRegistraLaHuellaYLaVerificacionLaConfirma() throws Exception {
        long id = subir(pdf(), "Acta para firmar");
        asegurarFirmaDelSupervisor();

        mockMvc.perform(post("/api/contratos/{c}/documentos/{id}/firmar", contratoId, id)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firmaId", notNullValue()))
                .andExpect(jsonPath("$.firmaHashSha256", notNullValue()))
                .andExpect(jsonPath("$.firmadoPorNombre").value("Alex Fernando Zapata"));

        mockMvc.perform(get("/api/contratos/{c}/documentos/{id}/verificacion", contratoId, id)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INTEGRO"));

        mockMvc.perform(get("/api/contratos/{c}/documentos/{id}/archivo", contratoId, id)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andExpect(header().string("X-SICOT-Integridad", "INTEGRO"));
    }

    /**
     * La razón de existir de toda la columna: si alguien cambia los bytes
     * después de la firma, el sistema tiene que <b>decirlo</b>. Antes de esta
     * corrección el documento seguía apareciendo firmado y válido, sin rastro.
     */
    @Test
    void alterarElContenidoDespuesDeFirmarSeDetecta() throws Exception {
        long id = subir(pdf(), "Acta que será alterada");
        asegurarFirmaDelSupervisor();
        mockMvc.perform(post("/api/contratos/{c}/documentos/{id}/firmar", contratoId, id)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk());

        // Exactamente el escenario que preocupa: alguien con acceso a la base
        // cambia el contenido sin pasar por la aplicación.
        actualizar("UPDATE Documento d SET d.contenido = :valor WHERE d.id = :id",
                "%PDF-1.4 CONTENIDO SUSTITUIDO".getBytes(StandardCharsets.UTF_8), id);

        mockMvc.perform(get("/api/contratos/{c}/documentos/{id}/verificacion", contratoId, id)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ALTERADO"))
                .andExpect(jsonPath("$.mensaje", containsString("cambió")));

        mockMvc.perform(get("/api/contratos/{c}/documentos/{id}/archivo", contratoId, id)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andExpect(header().string("X-SICOT-Integridad", "ALTERADO"));
    }

    /**
     * Los documentos firmados antes de que existiera la huella no se pueden
     * verificar, y el sistema debe decir eso — {@code NO_VERIFICABLE} — en vez
     * de asumir que están bien.
     */
    @Test
    void unaFirmaSinHuellaSeReportaComoNoVerificable() throws Exception {
        long id = subir(pdf(), "Acta firmada antes de la huella");
        asegurarFirmaDelSupervisor();
        mockMvc.perform(post("/api/contratos/{c}/documentos/{id}/firmar", contratoId, id)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk());

        actualizar("UPDATE Documento d SET d.firmaHashSha256 = :valor WHERE d.id = :id", null, id);

        mockMvc.perform(get("/api/contratos/{c}/documentos/{id}/verificacion", contratoId, id)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("NO_VERIFICABLE"));
    }

    /**
     * El listado usa una proyección JPQL con LEFT JOIN. Si alguien la
     * convirtiera en unión implícita, los documentos sin subetapa —que son la
     * mayoría— desaparecerían de la pantalla sin ningún error.
     */
    @Test
    void elListadoIncluyeDocumentosSinSubetapaYSinFirmante() throws Exception {
        subir(pdf(), "Documento suelto sin subetapa");

        mockMvc.perform(get("/api/contratos/{c}/documentos", contratoId)
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Documento suelto sin subetapa"))
                .andExpect(jsonPath("$[0].subetapaId").doesNotExist())
                .andExpect(jsonPath("$[0].firmadoPorNombre").doesNotExist())
                .andExpect(jsonPath("$[0].subidoPorNombre").value("Unidad de Gestión Contractual"));
    }

    // ── Utilidades ──────────────────────────────────────────────────────────

    private void actualizar(String jpql, Object valor, long id) {
        transactionTemplate.executeWithoutResult(status ->
                entityManager.createQuery(jpql)
                        .setParameter("valor", valor)
                        .setParameter("id", id)
                        .executeUpdate());
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("archivo", "doc.pdf", "application/pdf", PDF);
    }

    private long subir(MockMultipartFile archivo, String nombre) throws Exception {
        String creado = mockMvc.perform(multipart("/api/contratos/{c}/documentos", contratoId)
                        .file(archivo)
                        .param("nombre", nombre)
                        .header("Authorization", "Bearer " + gestion))
                // 201 Created: la subida crea un recurso nuevo.
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(creado).get("id").asLong();
    }

    private long crearContratoAsignadoAlSupervisor() throws Exception {
        String numero = "CO1.PCCNTR.INTEG" + SECUENCIA.getAndIncrement();
        String creado = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"%s","objeto":"Contrato para pruebas de integridad",
                                 "valor":1000000}
                                """.formatted(numero)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(creado).get("id").asLong();

        mockMvc.perform(patch("/api/contratos/{id}/supervisor", id)
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supervisorId\":" + idDe("supervisor@soy.sena.edu.co", "Supervisor123*") + "}"))
                .andExpect(status().isOk());
        return id;
    }

    /** El supervisor necesita una firma activa para poder firmar; la asigna el administrador. */
    private void asegurarFirmaDelSupervisor() throws Exception {
        String miFirma = mockMvc.perform(get("/api/firmas/mia")
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        if (objectMapper.readTree(miFirma).get("tieneFirmaActiva").asBoolean()) {
            return;
        }
        String admin = login("administrador@soy.sena.edu.co", "Admin123*");
        mockMvc.perform(post("/api/firmas")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":"
                                + idDe("supervisor@soy.sena.edu.co", "Supervisor123*") + "}"))
                .andExpect(status().isCreated());
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
