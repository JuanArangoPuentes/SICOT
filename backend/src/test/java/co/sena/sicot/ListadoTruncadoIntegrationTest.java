package co.sena.sicot;

import co.sena.sicot.controller.ListadoTruncado;
import co.sena.sicot.dto.auth.AuthResponse;
import co.sena.sicot.service.AlertaService;
import co.sena.sicot.service.ContratoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Un listado que llegó a su techo tiene que decirlo.
 *
 * <p><b>Qué fallaba.</b> Varios listados tienen un tope duro —500 contratos,
 * 500 alertas, 500 registros, 200 tareas— y el razonamiento que lo justifica es
 * correcto: sin él, una pantalla cargaría la tabla entera. Lo que faltaba es el
 * otro medio paso: la respuesta <b>no decía que había truncado</b>. El día que
 * un Centro pase de 500 contratos, la pantalla mostraría 500 y parecería
 * completa. En un registro de expedientes, una lista que miente por omisión es
 * peor que un error, porque quien la consulta decide creyendo que lo vio todo.
 *
 * <p>No se crean 500 contratos para probarlo: eso tardaría minutos y mediría la
 * velocidad de H2, no el comportamiento. Lo que se comprueba es que la cabecera
 * del tope viaje siempre —es el contrato con el cliente— y que la de truncado
 * NO aparezca cuando no falta nada, que es el caso que se da todos los días y
 * donde un falso aviso haría que la gente aprendiera a ignorarlo.
 */
class ListadoTruncadoIntegrationTest extends PruebaDeIntegracion {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("el tope viaja en cada listado, para que el cliente sepa con qué está trabajando")
    void elTopeSiempreViaja() throws Exception {
        String token = login();

        mockMvc.perform(get("/api/contratos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(ListadoTruncado.CABECERA_TOPE,
                        String.valueOf(ContratoService.MAX_CONTRATOS_LISTADO)));

        mockMvc.perform(get("/api/alertas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(ListadoTruncado.CABECERA_TOPE,
                        String.valueOf(AlertaService.MAX_ALERTAS_LISTADO)));
    }

    @Test
    @DisplayName("con pocos elementos NO se avisa de truncado: un aviso que sale siempre se aprende a ignorar")
    void sinTruncarNoSeAvisa() throws Exception {
        String token = login();

        mockMvc.perform(get("/api/contratos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(ListadoTruncado.CABECERA_TRUNCADO));

        mockMvc.perform(get("/api/registros").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(ListadoTruncado.CABECERA_TRUNCADO));
    }

    @Test
    @DisplayName("el aviso aparece justo al alcanzar el tope")
    void alAlcanzarElTopeSeAvisa() {
        // La lógica, sin montar 500 filas: el tope se alcanza por tamaño.
        var sinLlegar = ListadoTruncado.conAviso(java.util.List.of(1, 2), 3);
        var alLlegar = ListadoTruncado.conAviso(java.util.List.of(1, 2, 3), 3);

        org.assertj.core.api.Assertions.assertThat(
                        sinLlegar.getHeaders().getFirst(ListadoTruncado.CABECERA_TRUNCADO))
                .isNull();
        org.assertj.core.api.Assertions.assertThat(
                        alLlegar.getHeaders().getFirst(ListadoTruncado.CABECERA_TRUNCADO))
                .isEqualTo("true");
        org.assertj.core.api.Assertions.assertThat(
                        alLlegar.getHeaders().getFirst(ListadoTruncado.CABECERA_TOPE))
                .isEqualTo("3");
    }

    private String login() throws Exception {
        String cuerpo = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "administrador@soy.sena.edu.co", "password", "Admin123*"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(cuerpo, AuthResponse.class).token();
    }
}
