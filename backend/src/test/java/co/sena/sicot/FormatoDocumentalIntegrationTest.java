package co.sena.sicot;

import co.sena.sicot.dto.auth.AuthResponse;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FormatoDocumentalIntegrationTest extends PruebaDeIntegracion {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    private static final byte[] PDF = ArchivoDePrueba.pdfDeUnaPagina("formato GCCON-F-031 de prueba");

    @Test
    void administradorPuedeCargarListarYDescargarUnFormato() throws Exception {
        String adminToken = login("administrador@soy.sena.edu.co", "Admin123*");

        MockMultipartFile archivo = new MockMultipartFile("archivo", "GCCON-F-031.pdf", "application/pdf", PDF);

        String creado = mockMvc.perform(multipart("/api/formatos")
                        .file(archivo)
                        .param("codigo", "GCCON-F-031-TEST")
                        .param("nombre", "Informe de supervisión (prueba)")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("GCCON-F-031-TEST"))
                .andExpect(jsonPath("$.version").value("v1"))
                .andExpect(jsonPath("$.estado").value("VIGENTE"))
                .andExpect(jsonPath("$.subidoPorNombre").value("Administrador SICOT"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(creado).get("id").asLong();

        mockMvc.perform(get("/api/formatos").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo=='GCCON-F-031-TEST')]", hasSize(1)));

        mockMvc.perform(get("/api/formatos/{id}/archivo", id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PDF));

        // Recargar el mismo código sube la versión en vez de duplicar la fila.
        MockMultipartFile archivoV2 = new MockMultipartFile("archivo", "GCCON-F-031-v2.pdf", "application/pdf", PDF);
        mockMvc.perform(multipart("/api/formatos")
                        .file(archivoV2)
                        .param("codigo", "GCCON-F-031-TEST")
                        .param("nombre", "Informe de supervisión (prueba) v2")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.version").value("v2"));
    }

    @Test
    void rechazaArchivosConExtensionNoPermitida() throws Exception {
        String adminToken = login("administrador@soy.sena.edu.co", "Admin123*");
        MockMultipartFile ejecutable = new MockMultipartFile("archivo", "malicioso.exe", "application/octet-stream", PDF);

        mockMvc.perform(multipart("/api/formatos")
                        .file(ejecutable)
                        .param("codigo", "TEST-BAD")
                        .param("nombre", "Archivo no permitido")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("no permitido")));
    }

    @Test
    void gestionPuedeListarPeroNoCargarFormatos() throws Exception {
        String gestionToken = login("gestion@soy.sena.edu.co", "Gestion123*");

        mockMvc.perform(get("/api/formatos").header("Authorization", "Bearer " + gestionToken))
                .andExpect(status().isOk());

        MockMultipartFile archivo = new MockMultipartFile("archivo", "GCCON-F-031.pdf", "application/pdf", PDF);
        mockMvc.perform(multipart("/api/formatos")
                        .file(archivo)
                        .param("codigo", "NO-PERMITIDO")
                        .param("nombre", "No permitido")
                        .header("Authorization", "Bearer " + gestionToken))
                .andExpect(status().isForbidden());
    }

    /**
     * El catálogo se llenó antes de que SICOT comprobara que un archivo se
     * abre, y su descarga no tenía ninguna de las dos redes que el expediente
     * sí tenía: ni la de contenido vacío ni la de contenido ilegible. El
     * Administrador bajaba el formato, su lector lo daba por dañado y nada le
     * decía por qué (auditoría del 26-10-2026).
     */
    @Test
    void unFormatoGuardadoQueNingunLectorAbreNoSeEntrega() throws Exception {
        String adminToken = login("administrador@soy.sena.edu.co", "Admin123*");
        long id = cargarFormato(adminToken, "GCCON-F-031-ROTO", PDF);

        // Lo que hay en las filas antiguas: texto cuya primera línea parece un
        // PDF. No se puede cargar por la API —hoy se rechaza—, así que se
        // sustituye en la base, que es como llegó allí.
        actualizarContenido(id, "%PDF-1.4\n% formato de prueba\n%%EOF\n".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/formatos/{id}/archivo", id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("no se puede abrir")));
    }

    /** Una fila del catálogo registrada sin contenido: tampoco se entrega como un archivo de cero bytes. */
    @Test
    void unFormatoSinArchivoGuardadoNoSeEntrega() throws Exception {
        String adminToken = login("administrador@soy.sena.edu.co", "Admin123*");
        long id = cargarFormato(adminToken, "GCCON-F-031-VACIO", PDF);

        actualizarContenido(id, new byte[0]);

        mockMvc.perform(get("/api/formatos/{id}/archivo", id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("no tiene archivo guardado")));
    }

    private long cargarFormato(String adminToken, String codigo, byte[] contenido) throws Exception {
        String creado = mockMvc.perform(multipart("/api/formatos")
                        .file(new MockMultipartFile("archivo", codigo + ".pdf", "application/pdf", contenido))
                        .param("codigo", codigo)
                        .param("nombre", "Formato de prueba " + codigo)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(creado).get("id").asLong();
    }

    private void actualizarContenido(long id, byte[] valor) {
        transactionTemplate.executeWithoutResult(status ->
                entityManager.createQuery("UPDATE FormatoDocumental f SET f.contenido = :valor WHERE f.id = :id")
                        .setParameter("valor", valor)
                        .setParameter("id", id)
                        .executeUpdate());
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AuthResponse.class).token();
    }
}
