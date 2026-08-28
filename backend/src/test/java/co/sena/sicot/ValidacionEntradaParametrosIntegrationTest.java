package co.sena.sicot;

import co.sena.sicot.dto.auth.AuthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los formularios multipart pasan {@code codigo} / {@code nombre} como
 * {@code @RequestParam} crudos. Sin {@code @Validated} en la clase, las
 * restricciones sobre esos parámetros no se ejecutan (hallazgo 6 de la tarea):
 * un valor demasiado largo se colaba hasta la columna y volvía como 409. Estas
 * pruebas fijan que ahora se rechaza con 400 <b>antes</b> de tocar la base.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ValidacionEntradaParametrosIntegrationTest {

    private static final byte[] PDF_FALSO = "%PDF-1.4 contenido de prueba".getBytes();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void formatoConCodigoDemasiadoLargoEs400NoConflicto() throws Exception {
        String adminToken = login("administrador@soy.sena.edu.co", "Admin123*");
        MockMultipartFile archivo = new MockMultipartFile("archivo", "GCCON.pdf", "application/pdf", PDF_FALSO);

        mockMvc.perform(multipart("/api/formatos")
                        .file(archivo)
                        .param("codigo", "X".repeat(51))
                        .param("nombre", "Formato de prueba")
                        .header("Authorization", "Bearer " + adminToken))
                // La columna codigo es VARCHAR(50): sin la restricción declarada
                // esto sería un 409 al chocar con la base.
                .andExpect(status().isBadRequest());
    }

    @Test
    void formatoConNombreDemasiadoLargoEs400() throws Exception {
        String adminToken = login("administrador@soy.sena.edu.co", "Admin123*");
        MockMultipartFile archivo = new MockMultipartFile("archivo", "GCCON.pdf", "application/pdf", PDF_FALSO);

        mockMvc.perform(multipart("/api/formatos")
                        .file(archivo)
                        .param("codigo", "GCCON-F-VAL-1")
                        .param("nombre", "N".repeat(256))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void formatoConCodigoEnBlancoEs400() throws Exception {
        String adminToken = login("administrador@soy.sena.edu.co", "Admin123*");
        MockMultipartFile archivo = new MockMultipartFile("archivo", "GCCON.pdf", "application/pdf", PDF_FALSO);

        mockMvc.perform(multipart("/api/formatos")
                        .file(archivo)
                        .param("codigo", "   ")
                        .param("nombre", "Formato de prueba")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void formatoValidoSeSigueCargando() throws Exception {
        String adminToken = login("administrador@soy.sena.edu.co", "Admin123*");
        MockMultipartFile archivo = new MockMultipartFile("archivo", "GCCON-F-VAL.pdf", "application/pdf", PDF_FALSO);

        mockMvc.perform(multipart("/api/formatos")
                        .file(archivo)
                        .param("codigo", "GCCON-F-VAL-OK")
                        .param("nombre", "Formato válido de regresión")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("GCCON-F-VAL-OK"));
    }

    @Test
    void documentoConNombreDemasiadoLargoEs400NoConflicto() throws Exception {
        String gestionToken = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestionToken, "CO1.PCCNTR.VAL-DOC");

        MockMultipartFile archivo = new MockMultipartFile("archivo", "evidencia.pdf", "application/pdf", PDF_FALSO);

        mockMvc.perform(multipart("/api/contratos/{contratoId}/documentos", contratoId)
                        .file(archivo)
                        .param("nombre", "D".repeat(256))
                        .header("Authorization", "Bearer " + gestionToken))
                // La columna nombre es VARCHAR(255): 400 declarado, no 409.
                .andExpect(status().isBadRequest());
    }

    @Test
    void documentoConSubetapaIdNoPositivoEs400() throws Exception {
        String gestionToken = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestionToken, "CO1.PCCNTR.VAL-DOC2");

        MockMultipartFile archivo = new MockMultipartFile("archivo", "evidencia.pdf", "application/pdf", PDF_FALSO);

        mockMvc.perform(multipart("/api/contratos/{contratoId}/documentos", contratoId)
                        .file(archivo)
                        .param("nombre", "Evidencia")
                        .param("subetapaId", "-3")
                        .header("Authorization", "Bearer " + gestionToken))
                .andExpect(status().isBadRequest());
    }

    private long crearContrato(String token, String numero) throws Exception {
        String creado = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroContrato\":\"" + numero + "\",\"objeto\":\"Prueba\",\"valor\":1000000}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(creado).get("id").asLong();
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AuthResponse.class).token();
    }
}
