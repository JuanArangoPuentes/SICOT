package co.sena.sicot;

import co.sena.sicot.dto.auth.AuthResponse;
import co.sena.sicot.service.FotoConExif;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La foto de evidencia de la entrega dice cuándo y dónde se tomó (MDL-205).
 *
 * <p>La subetapa 3.2 del GCCON-P-010 pide evidencia fotográfica
 * georreferenciada. La foto se carga tal como sale de la cámara; aquí se
 * comprueba el recorrido completo: el EXIF se lee al cargar, queda guardado con
 * el documento (V17), vuelve en la respuesta y en el listado del expediente, y
 * el registro del contrato lo dice con palabras. Cuando la foto no trae el
 * dato, todo eso dice que falta, sin inventarlo.
 */
class FotosDeEvidenciaIntegrationTest extends PruebaDeIntegracion {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void laFechaYElLugarDeLaFotoQuedanEnElExpedienteYEnElRegistro() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion);
        byte[] foto = FotoConExif.nueva()
                .tomadaEl("2026:09:23 14:03:00").conDesfase("-05:00")
                .en(6.17194, -75.61139)
                .jpeg();

        mockMvc.perform(multipart("/api/contratos/{c}/documentos", contratoId)
                        .file(new MockMultipartFile("archivo", "entrega.jpg", "image/jpeg", foto))
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("IMAGEN"))
                .andExpect(jsonPath("$.capturaFecha").value("2026-09-23T19:03:00Z"))
                .andExpect(jsonPath("$.capturaLatitud").value(closeTo(6.17194, 0.00001)))
                .andExpect(jsonPath("$.capturaLongitud").value(closeTo(-75.61139, 0.00001)));

        // El listado sale de otra consulta (una proyección sin el contenido del
        // archivo), así que se comprueba aparte: los campos tienen que estar
        // también ahí, que es de donde los lee el panel.
        mockMvc.perform(get("/api/contratos/{id}/documentos", contratoId)
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].capturaFecha").value("2026-09-23T19:03:00Z"))
                .andExpect(jsonPath("$[0].capturaLatitud").value(closeTo(6.17194, 0.00001)))
                .andExpect(jsonPath("$[0].capturaLongitud").value(closeTo(-75.61139, 0.00001)));

        mockMvc.perform(get("/api/contratos/{id}/registros", contratoId)
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.accion == 'DOCUMENTO_CARGADO')].descripcion")
                        .value(contains("Documento «entrega.jpg» cargado. "
                                + "Foto tomada el 23/09/2026 a las 14:03 en 6.17194, -75.61139.")));
    }

    @Test
    void unaFotoSinExifSeCargaIgualYElRegistroDiceQueNoTraeLosDatos() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion);

        mockMvc.perform(multipart("/api/contratos/{c}/documentos", contratoId)
                        .file(new MockMultipartFile("archivo", "captura.png", "image/png", FotoConExif.png()))
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.capturaFecha").doesNotExist())
                .andExpect(jsonPath("$.capturaLatitud").doesNotExist())
                .andExpect(jsonPath("$.capturaLongitud").doesNotExist());

        mockMvc.perform(get("/api/contratos/{id}/registros", contratoId)
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.accion == 'DOCUMENTO_CARGADO')].descripcion")
                        .value(contains("Documento «captura.png» cargado. "
                                + "La foto no trae fecha de captura ni ubicación.")));
    }

    /** Un PDF no es una foto: no se le busca EXIF y su registro no habla de captura. */
    @Test
    void unDocumentoQueNoEsFotoNoHablaDeCaptura() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion);

        mockMvc.perform(multipart("/api/contratos/{c}/documentos", contratoId)
                        .file(new MockMultipartFile("archivo", "acta.pdf", "application/pdf",
                                "%PDF-1.4 acta".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.capturaFecha").doesNotExist());

        mockMvc.perform(get("/api/contratos/{id}/registros", contratoId)
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.accion == 'DOCUMENTO_CARGADO')].descripcion")
                        .value(contains("Documento «acta.pdf» cargado.")));
    }

    private long crearContrato(String gestionToken) throws Exception {
        String creado = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + gestionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"CO1.PCCNTR.7986335",
                                 "objeto":"Suministro de mobiliario para el ambiente de formación",
                                 "valor":10000000}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(creado).get("id").asLong();
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
