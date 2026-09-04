package co.sena.sicot;

import co.sena.sicot.dto.auth.AuthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ListaChequeoIntegrationTest extends PruebaDeIntegracion {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void cualquierRolAutenticadoPuedeConsultarElCatalogo() throws Exception {
        String supervisorToken = login("supervisor@soy.sena.edu.co", "Supervisor123*");

        mockMvc.perform(get("/api/listas-chequeo").header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[0].codigo").value("GCCON-F-026"))
                .andExpect(jsonPath("$[?(@.codigo=='GRF-F-088')].tipo").value("TRAMITE_PAGO"));
    }

    @Test
    void elDetalleTraeLasEtapasYLosItemsDelFormatoOficial() throws Exception {
        String gestionToken = login("gestion@soy.sena.edu.co", "Gestion123*");

        mockMvc.perform(get("/api/listas-chequeo/{codigo}", "GCCON-F-053")
                        .header("Authorization", "Bearer " + gestionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("05"))
                .andExpect(jsonPath("$.tipo").value("MODALIDAD_SELECCION"))
                .andExpect(jsonPath("$.etapas", hasSize(4)))
                .andExpect(jsonPath("$.etapas[0].nombre").value("ETAPA PRECONTRACTUAL"))
                .andExpect(jsonPath("$.etapas[0].items[2].documento")
                        .value("Estudios previos con sus Anexos"))
                .andExpect(jsonPath("$.etapas[0].items[2].formatos[0]").value("GCCON-F-046"));
    }

    @Test
    void filtraElCatalogoPorTipoDeTramite() throws Exception {
        String gestionToken = login("gestion@soy.sena.edu.co", "Gestion123*");

        mockMvc.perform(get("/api/listas-chequeo").param("tipo", "TRAMITE_PAGO")
                        .header("Authorization", "Bearer " + gestionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].codigo").value("GRF-F-088"));
    }

    @Test
    void unCodigoDesconocidoDevuelve404() throws Exception {
        String gestionToken = login("gestion@soy.sena.edu.co", "Gestion123*");

        mockMvc.perform(get("/api/listas-chequeo/{codigo}", "GCCON-F-999")
                        .header("Authorization", "Bearer " + gestionToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("GCCON-F-999")));
    }

    @Test
    void elCatalogoExigeAutenticacion() throws Exception {
        mockMvc.perform(get("/api/listas-chequeo"))
                .andExpect(status().isUnauthorized());
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
