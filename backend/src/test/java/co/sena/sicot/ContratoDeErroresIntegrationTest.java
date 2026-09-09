package co.sena.sicot;

import co.sena.sicot.dto.auth.AuthResponse;
import co.sena.sicot.ia.IaNoDisponibleException;
import co.sena.sicot.ia.OllamaClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica que el backend responda con el código HTTP correcto en vez de
 * convertir todo en 500.
 *
 * Antes existía un único {@code @ExceptionHandler(Exception.class)} que
 * capturaba también las excepciones del propio framework, así que una ruta
 * inexistente, un Content-Type equivocado o una caída de Ollama devolvían todos
 * el mismo "Ocurrió un error interno del servidor" — y además escribían un
 * stack trace completo en el log por cada petición.
 */
class ContratoDeErroresIntegrationTest extends PruebaDeIntegracion {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Se sustituye el cliente real de Ollama para poder provocar la caída del
     * servicio de IA de forma determinista. Sin esto la prueba dependería de si
     * hay o no un Ollama corriendo en la máquina, que es justo lo que no se
     * quiere en una suite automatizada.
     */
    @MockitoBean
    private OllamaClient ollamaClient;

    @Test
    void rutaInexistenteDevuelve404NoError500() throws Exception {
        mockMvc.perform(get("/api/ruta-que-no-existe")
                        .header("Authorization", "Bearer " + login("administrador@soy.sena.edu.co", "Admin123*")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", not(containsString("interno"))));
    }

    @Test
    void contentTypeNoSoportadoDevuelve415() throws Exception {
        mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + login("gestion@soy.sena.edu.co", "Gestion123*"))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("esto no es json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    /**
     * El caso que motivó toda esta corrección: {@code OllamaClient} falla de
     * forma explícita y con un mensaje accionable cuando Ollama no responde,
     * en vez de inventar un resultado. Ese mensaje debe llegar al usuario.
     */
    @Test
    void ollamaCaidoDevuelve503ConMensajeHonesto() throws Exception {
        // Mismo mensaje que produce OllamaClient en el fallo real: explica qué
        // pasó y qué hacer, sin exponer URL interna ni nombre del modelo.
        given(ollamaClient.generar(anyString(), anyBoolean()))
                .willThrow(new IaNoDisponibleException(
                        "El servicio de IA no está disponible en este momento. "
                                + "Intente de nuevo en unos minutos; si el problema persiste, avise al área de sistemas."));

        MockMultipartFile pdf = new MockMultipartFile(
                "archivos", "acta.pdf", MediaType.APPLICATION_PDF_VALUE, pdfConTextoExtraible());

        mockMvc.perform(multipart("/api/ia/extraer-contrato").file(pdf)
                        .header("Authorization", "Bearer " + login("gestion@soy.sena.edu.co", "Gestion123*")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                // El mensaje real del backend debe propagarse, no el generico.
                .andExpect(jsonPath("$.message", containsString("servicio de IA no está disponible")))
                // ...pero sin filtrar topologia interna al cliente.
                .andExpect(jsonPath("$.message", not(containsString("localhost"))))
                .andExpect(jsonPath("$.message", not(containsString("11434"))));
    }

    @Test
    void todaRespuestaDeErrorConservaLaMismaForma() throws Exception {
        mockMvc.perform(get("/api/ruta-que-no-existe")
                        .header("Authorization", "Bearer " + login("administrador@soy.sena.edu.co", "Admin123*")))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    /**
     * PDF de una página <b>con texto extraíble</b>, generado con PDFBox.
     *
     * Tiene que llevar texto de verdad: {@code ExtraccionContratoService} se
     * detiene antes de llamar a Ollama si el PDF no tiene texto legible (caso
     * de un escaneo sin OCR) y devuelve una respuesta vacía. Con un PDF sin
     * texto esta prueba pasaría por el camino equivocado y nunca ejercitaría
     * el manejo del fallo de la IA.
     */
    private byte[] pdfConTextoExtraible() throws Exception {
        try (PDDocument documento = new PDDocument();
             java.io.ByteArrayOutputStream salida = new java.io.ByteArrayOutputStream()) {
            PDPage pagina = new PDPage();
            documento.addPage(pagina);
            try (PDPageContentStream contenido = new PDPageContentStream(documento, pagina)) {
                contenido.beginText();
                contenido.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contenido.newLineAtOffset(72, 700);
                contenido.showText("ACTA DE INICIO - Contrato CO1.PCCNTR.PRUEBA");
                contenido.newLineAtOffset(0, -20);
                contenido.showText("Objeto: prueba automatizada del contrato de errores.");
                contenido.endText();
            }
            documento.save(salida);
            return salida.toByteArray();
        }
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AuthResponse.class).token();
    }
}
