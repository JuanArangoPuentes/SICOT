package co.sena.sicot;

import co.sena.sicot.automatizacion.FotoDelContrato;
import co.sena.sicot.automatizacion.LectorDeContratos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El camino completo que convierte un contrato recién registrado en uno que el
 * motor de automatizaciones vigila, recorrido <b>solo por la API pública</b>.
 *
 * <h2>Por qué no basta con las pruebas que ya existen</h2>
 * {@code ReglasDeCalendarioTest} y {@code TopeDelListadoDeContratosIntegrationTest}
 * fijan {@code EstadoContrato.ACTIVO} escribiendo directamente en el
 * repositorio. Con eso, las cuatro reglas de calendario quedan probadas pero el
 * eslabón que las alimenta no: si ningún cliente puede llevar un contrato de
 * BORRADOR a ACTIVO, {@code LectorDeContratos.vigentes()} devuelve siempre la
 * lista vacía en producción y el log del motor repite cada día «no hay
 * contratos ACTIVO» mientras todas las pruebas siguen en verde (auditoría del
 * 06-10-2026).
 *
 * <p>Esta prueba cierra ese hueco: crea el contrato con {@code POST}, lo activa
 * con {@code PATCH} y comprueba contra el mismo lector que usa el motor. Si
 * alguien retira el endpoint de estado o le estrecha la transición, aquí se ve.
 */
class ActivacionDelContratoIntegrationTest extends PruebaDeIntegracion {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LectorDeContratos lectorDeContratos;

    @Test
    void unContratoCreadoYActivadoPorLaApiEntraEnLoQueVigilaElMotor() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");

        long contratoId = crearContrato(gestion, "CO1.PCCNTR.ACTIVACION01");

        // En BORRADOR el motor no lo mira: es la situación de partida.
        assertThat(lectorDeContratos.vigentes()).isEmpty();

        mockMvc.perform(patch("/api/contratos/{id}/estado", contratoId)
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ACTIVO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVO"));

        List<FotoDelContrato> vigentes = lectorDeContratos.vigentes();
        assertThat(vigentes).hasSize(1);
        assertThat(vigentes.getFirst().contratoId()).isEqualTo(contratoId);
        assertThat(vigentes.getFirst().numeroContrato()).isEqualTo("CO1.PCCNTR.ACTIVACION01");
        // Las seis etapas del GCCON-P-010 que sembró la creación: sin totales,
        // la regla de cronograma atrasado no puede afirmar nada del avance.
        assertThat(vigentes.getFirst().subetapasTotales()).isPositive();
    }

    @Test
    void laCorreccionDeLosDatosGeneralesSePersisteYSeAudita() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.ACTIVACION02");

        // El caso real: un error de tecleo en el valor y en la vigencia, que son
        // justo los datos que alimentan el cronograma y los documentos.
        mockMvc.perform(put("/api/contratos/{id}", contratoId)
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"CO1.PCCNTR.ACTIVACION02","objeto":"Objeto corregido",
                                 "valor":2500000,"fechaInicio":"2026-02-01","fechaFin":"2026-11-30",
                                 "contratista":"Maderas del Sur S.A.S."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objeto").value("Objeto corregido"))
                .andExpect(jsonPath("$.valor").value(2500000))
                .andExpect(jsonPath("$.fechaFin").value("2026-11-30"))
                .andExpect(jsonPath("$.contratista").value("Maderas del Sur S.A.S."));

        mockMvc.perform(get("/api/contratos/{id}/registros", contratoId)
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.accion == 'CONTRATO_ACTUALIZADO')]").exists());
    }

    private long crearContrato(String token, String numero) throws Exception {
        String creado = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"%s","objeto":"Suministro de mobiliario para el CTMA",
                                 "valor":1000000,"fechaInicio":"2026-01-01","fechaFin":"2026-12-31"}
                                """.formatted(numero)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(creado).get("id").asLong();
    }

    private String login(String email, String password) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo).get("token").asText();
    }
}
