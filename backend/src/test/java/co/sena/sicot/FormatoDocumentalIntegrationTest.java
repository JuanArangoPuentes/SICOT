package co.sena.sicot;

import co.sena.sicot.dto.auth.AuthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FormatoDocumentalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final byte[] PDF_FALSO = "%PDF-1.4 contenido de prueba".getBytes();

    @Test
    void administradorPuedeCargarListarYDescargarUnFormato() throws Exception {
        String adminToken = login("administrador@soy.sena.edu.co", "Admin123*");

        MockMultipartFile archivo = new MockMultipartFile("archivo", "GCCON-F-031.pdf", "application/pdf", PDF_FALSO);

        String creado = mockMvc.perform(multipart("/api/formatos")
                        .file(archivo)
                        .param("codigo", "GCCON-F-031-TEST")
                        .param("nombre", "Informe de supervisión (prueba)")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
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
                .andExpect(content().bytes(PDF_FALSO));

        // Recargar el mismo código sube la versión en vez de duplicar la fila.
        MockMultipartFile archivoV2 = new MockMultipartFile("archivo", "GCCON-F-031-v2.pdf", "application/pdf", PDF_FALSO);
        mockMvc.perform(multipart("/api/formatos")
                        .file(archivoV2)
                        .param("codigo", "GCCON-F-031-TEST")
                        .param("nombre", "Informe de supervisión (prueba) v2")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.version").value("v2"));
    }

    @Test
    void rechazaArchivosConExtensionNoPermitida() throws Exception {
        String adminToken = login("administrador@soy.sena.edu.co", "Admin123*");
        MockMultipartFile ejecutable = new MockMultipartFile("archivo", "malicioso.exe", "application/octet-stream", PDF_FALSO);

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

        MockMultipartFile archivo = new MockMultipartFile("archivo", "GCCON-F-031.pdf", "application/pdf", PDF_FALSO);
        mockMvc.perform(multipart("/api/formatos")
                        .file(archivo)
                        .param("codigo", "NO-PERMITIDO")
                        .param("nombre", "No permitido")
                        .header("Authorization", "Bearer " + gestionToken))
                .andExpect(status().isForbidden());
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
