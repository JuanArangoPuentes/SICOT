package co.sena.sicot;

import co.sena.sicot.dto.auth.AuthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los documentos que el SENA envía al asignar un contrato a un supervisor, y el
 * problema de saber cuál es cuál una vez cargados.
 *
 * <h2>Qué faltaba</h2>
 * SICOT ya sabía recibir el paquete —el endpoint de extracción acepta varios
 * archivos a la vez precisamente porque el correo real trae más de uno— y ya
 * sabía guardarlos contra el contrato. Lo que no había era forma de decir que
 * <i>este</i> PDF es el GCCON-F-031 y <i>aquel</i> el Acta de Inicio: la columna
 * {@code tipo} de un documento guarda el formato del ARCHIVO (PDF, DOCX…), no el
 * papel que cumple en el proceso. Cargados, los cuatro eran indistinguibles
 * salvo por el nombre de archivo que trajera cada uno, que lo escribe quien sube
 * y no se puede consultar de forma fiable.
 *
 * <h2>Qué NO se prueba aquí, a propósito</h2>
 * El catálogo de formatos no se siembra con los códigos institucionales reales.
 * Los cuatro documentos de asignación se conocen, pero el catálogo completo del
 * proceso lo entrega la entidad, y sembrar códigos que nadie puede verificar
 * contra el documento original sería inventar el proceso. Aquí los formatos los
 * carga el administrador, que es como funciona de verdad.
 */
class DocumentosDeAsignacionIntegrationTest extends PruebaDeIntegracion {

    private static final byte[] PDF = "%PDF-1.4 documento de asignación".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * El caso completo: los cuatro documentos del correo de asignación quedan
     * cargados contra el contrato y cada uno identificado por su formato.
     */
    @Test
    void losCuatroDocumentosDeAsignacionQuedanIdentificadosPorSuFormato() throws Exception {
        String admin = login("administrador@soy.sena.edu.co", "Admin123*");
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");

        long carta = registrarFormato(admin, "CARTA-NOTIFICACION", "Carta de notificación al supervisor");
        long manual = registrarFormato(admin, "GCCON-M-002", "Manual de Supervisión e Interventoría");
        long informe = registrarFormato(admin, "GCCON-F-031", "Informe de Supervisión");
        long acta = registrarFormato(admin, "GCCON-F-018", "Acta de Inicio");

        long contratoId = crearContrato(gestion);

        subirDocumento(gestion, contratoId, carta, "Notificación supervisor.pdf");
        subirDocumento(gestion, contratoId, manual, "GCCON-M-002 V07.pdf");
        subirDocumento(gestion, contratoId, informe, "GCCON-F-031.pdf");
        subirDocumento(gestion, contratoId, acta, "Acta de inicio.pdf");

        mockMvc.perform(get("/api/contratos/{id}/documentos", contratoId)
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                // El listado va ordenado por fecha de subida descendente, así que
                // el último cargado es el primero de la lista.
                .andExpect(jsonPath("$[0].formatoCodigo").value("GCCON-F-018"))
                .andExpect(jsonPath("$[0].formatoNombre").value("Acta de Inicio"))
                .andExpect(jsonPath("$[3].formatoCodigo").value("CARTA-NOTIFICACION"));
    }

    @Test
    void elFormatoLlegaYaEnLaRespuestaDeLaCargaYNoSoloEnElListado() throws Exception {
        String admin = login("administrador@soy.sena.edu.co", "Admin123*");
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long formatoId = registrarFormato(admin, "GCCON-F-018", "Acta de Inicio");
        long contratoId = crearContrato(gestion);

        mockMvc.perform(multipart("/api/contratos/{c}/documentos", contratoId)
                        .file(new MockMultipartFile("archivo", "acta.pdf", "application/pdf", PDF))
                        .param("formatoId", String.valueOf(formatoId))
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.formatoId").value(formatoId))
                .andExpect(jsonPath("$.formatoCodigo").value("GCCON-F-018"))
                .andExpect(jsonPath("$.formatoNombre").value("Acta de Inicio"));
    }

    /**
     * No todo documento de un contrato es la instancia de un formato oficial: un
     * soporte cualquiera que adjunta el supervisor no lo es. Si el formato fuera
     * obligatorio, quien sube acabaría eligiendo cualquier entrada del catálogo
     * con tal de poder guardar, y la etiqueta dejaría de significar nada.
     */
    @Test
    void unDocumentoSinFormatoSigueSiendoValidoYNoDesapareceDelListado() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion);

        mockMvc.perform(multipart("/api/contratos/{c}/documentos", contratoId)
                        .file(new MockMultipartFile("archivo", "soporte.pdf", "application/pdf", PDF))
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.formatoId").doesNotExist())
                .andExpect(jsonPath("$.formatoCodigo").doesNotExist());

        // Sin el LEFT JOIN en la proyección del listado, este documento —que es
        // el caso mayoritario— desaparecería de la pantalla sin más.
        mockMvc.perform(get("/api/contratos/{id}/documentos", contratoId)
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("soporte.pdf"))
                .andExpect(jsonPath("$[0].formatoCodigo").doesNotExist());
    }

    @Test
    void unDocumentoConFormatoYOtroSinElConvivenEnElMismoListado() throws Exception {
        String admin = login("administrador@soy.sena.edu.co", "Admin123*");
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long formatoId = registrarFormato(admin, "GCCON-F-018", "Acta de Inicio");
        long contratoId = crearContrato(gestion);

        subirDocumento(gestion, contratoId, null, "soporte suelto.pdf");
        subirDocumento(gestion, contratoId, formatoId, "acta.pdf");

        mockMvc.perform(get("/api/contratos/{id}/documentos", contratoId)
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].formatoCodigo").value("GCCON-F-018"))
                .andExpect(jsonPath("$[1].formatoCodigo").doesNotExist());
    }

    @Test
    void unFormatoQueNoEstaEnElCatalogoSeRechazaConUnErrorClaro() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion);

        mockMvc.perform(multipart("/api/contratos/{c}/documentos", contratoId)
                        .file(new MockMultipartFile("archivo", "acta.pdf", "application/pdf", PDF))
                        .param("formatoId", "9999")
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("no existe en el catálogo")));
    }

    @Test
    void unIdentificadorDeFormatoNegativoSeRechazaEnElBordeYNoEnElServicio() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion);

        mockMvc.perform(multipart("/api/contratos/{c}/documentos", contratoId)
                        .file(new MockMultipartFile("archivo", "acta.pdf", "application/pdf", PDF))
                        .param("formatoId", "-1")
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────────────────

    private long registrarFormato(String adminToken, String codigo, String nombre) throws Exception {
        String creado = mockMvc.perform(multipart("/api/formatos")
                        .file(new MockMultipartFile("archivo", codigo + ".pdf", "application/pdf", PDF))
                        .param("codigo", codigo)
                        .param("nombre", nombre)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(creado).get("id").asLong();
    }

    private long crearContrato(String gestionToken) throws Exception {
        String creado = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + gestionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"CO1.PCCNTR.7986334",
                                 "objeto":"Suministro de materiales para el lote 8",
                                 "valor":10000000}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(creado).get("id").asLong();
    }

    private void subirDocumento(String token, long contratoId, Long formatoId, String nombreArchivo) throws Exception {
        var peticion = multipart("/api/contratos/{c}/documentos", contratoId)
                .file(new MockMultipartFile("archivo", nombreArchivo, "application/pdf", PDF))
                .header("Authorization", "Bearer " + token);
        if (formatoId != null) {
            peticion = peticion.param("formatoId", String.valueOf(formatoId));
        }
        mockMvc.perform(peticion).andExpect(status().isCreated());
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
