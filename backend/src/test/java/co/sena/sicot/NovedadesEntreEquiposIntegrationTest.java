package co.sena.sicot;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lo que el servidor tiene que garantizar para que dos equipos que miran el
 * mismo contrato se vean casi al instante.
 *
 * <p>El escenario es el real: el equipo A tiene el expediente abierto y el
 * equipo B —otro computador, o el teléfono del mismo supervisor— cierra un
 * sub-paso. Nadie recarga nada. Lo que hace que A se entere es preguntar cada
 * pocos segundos por {@code /novedades} con la marca que le devolvió el
 * servidor la vez anterior.
 *
 * <p>Las dos propiedades que se comprueban aquí son las que, si fallan, dejan
 * la propagación rota de una forma que no se ve: que la marca sea exacta —ni
 * repite lo ya avisado ni se salta nada, sin que el reloj de ninguno de los dos
 * equipos intervenga— y que sondear no sea una rendija para mirar contratos
 * ajenos.
 */
class NovedadesEntreEquiposIntegrationTest extends PruebaDeIntegracion {

    private static final String PASSWORD_OTRO = "OtroSupervisor123";
    private static final String EMAIL_OTRO = "otro.supervisor.novedades@soy.sena.edu.co";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String supervisorToken;
    private String adminToken;
    private long contratoId;
    private long subpaso22;

    @BeforeEach
    void unContratoActivoConSupervisor() throws Exception {
        String gestionToken = login("gestion@soy.sena.edu.co", "Gestion123*");
        adminToken = login("administrador@soy.sena.edu.co", "Admin123*");
        supervisorToken = login("supervisor@soy.sena.edu.co", "Supervisor123*");
        long supervisorId = objectMapper.readTree(
                loginBody("supervisor@soy.sena.edu.co", "Supervisor123*")).get("usuarioId").asLong();

        String crear = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + gestionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"CO1.PCCNTR.NOVEDADES","objeto":"Dos equipos sobre el mismo contrato",
                                 "valor":1000000,"fechaInicio":"2026-10-01","fechaFin":"2026-12-31"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        contratoId = objectMapper.readTree(crear).get("id").asLong();
        mockMvc.perform(patch("/api/contratos/{id}/supervisor", contratoId)
                        .header("Authorization", "Bearer " + gestionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supervisorId\":" + supervisorId + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/contratos/{id}/estado", contratoId)
                        .header("Authorization", "Bearer " + gestionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"ACTIVO\"}"))
                .andExpect(status().isOk());

        // El 2.2 no exige documentos: se cierra directamente, que es lo que
        // hace falta para producir una novedad sin montar un expediente entero.
        JsonNode sub = etapas().get(1).get("subEtapas").get(1);
        assertThat(sub.get("codigo").asString()).isEqualTo("2.2");
        subpaso22 = sub.get("id").asLong();
    }

    @Test
    void laPrimeraPreguntaSoloTraeLaMarcaConLaQueSeguirPreguntando() throws Exception {
        JsonNode primera = novedades(supervisorToken, null);

        // Sin novedades contra nada: la pantalla acaba de cargar el estado
        // entero, así que no hay nada que haya cambiado «desde».
        assertThat(primera.get("novedades")).isEmpty();
        // Y la marca ya está al día: el montaje de este contrato dejó varias
        // entradas de auditoría, y ninguna de ellas es una novedad.
        assertThat(primera.get("ultimoId").asLong()).isPositive();
    }

    @Test
    void loQueHaceElOtroEquipoAparecePreguntandoDesdeLaMarcaAnterior() throws Exception {
        long marca = novedades(supervisorToken, null).get("ultimoId").asLong();

        // El equipo B cierra el sub-paso. El equipo A no ha tocado nada.
        cerrarElSubpaso22();

        JsonNode despues = novedades(supervisorToken, marca);
        assertThat(despues.get("novedades")).isNotEmpty();
        assertThat(despues.get("novedades").get(0).get("contratoId").asLong()).isEqualTo(contratoId);
        assertThat(despues.get("novedades").get(0).get("accion").asString()).isNotEmpty();
        assertThat(despues.get("ultimoId").asLong()).isGreaterThan(marca);
    }

    @Test
    void sinCambiosLaRespuestaSigueVaciaYLaMarcaNoRetrocede() throws Exception {
        long primera = novedades(supervisorToken, null).get("ultimoId").asLong();

        JsonNode segunda = novedades(supervisorToken, primera);
        assertThat(segunda.get("novedades")).isEmpty();
        // Nunca hacia atrás: si retrocediera, el equipo volvería a recibir lo
        // que ya aplicó cada vez que pregunta, para siempre.
        assertThat(segunda.get("ultimoId").asLong()).isEqualTo(primera);
    }

    @Test
    void loYaAvisadoNoVuelveAAparecerEnLaSiguientePregunta() throws Exception {
        long marca = novedades(supervisorToken, null).get("ultimoId").asLong();
        cerrarElSubpaso22();

        JsonNode conLaNovedad = novedades(supervisorToken, marca);
        assertThat(conLaNovedad.get("novedades")).isNotEmpty();

        // Con la marca que vino en esa misma respuesta, el cierre ya no está:
        // es lo que evita que la pantalla se recargue sola una y otra vez.
        JsonNode siguiente = novedades(supervisorToken, conLaNovedad.get("ultimoId").asLong());
        assertThat(siguiente.get("novedades")).isEmpty();
    }

    @Test
    void unSupervisorAjenoNoPuedeSondearElContrato() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Otro Supervisor","email":"%s","password":"%s",
                                 "telefono":"3000000000","rol":"SUPERVISOR"}
                                """.formatted(EMAIL_OTRO, PASSWORD_OTRO)))
                .andExpect(status().isCreated());
        String ajeno = login(EMAIL_OTRO, PASSWORD_OTRO);

        // Tiene que cerrarse por la misma puerta que el expediente: un «sí,
        // algo cambió» sobre un contrato ajeno ya es filtrar actividad, aunque
        // no enseñe ni una línea. Y 404, no 403, como en el resto de la API:
        // un 403 diría «existe, pero no es suyo», que es la mitad de la
        // respuesta que no se quiere dar.
        mockMvc.perform(get("/api/contratos/{id}/novedades", contratoId)
                        .header("Authorization", "Bearer " + ajeno))
                .andExpect(status().isNotFound());
    }

    @Test
    void sinSesionNoSeSondea() throws Exception {
        mockMvc.perform(get("/api/contratos/{id}/novedades", contratoId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unaMarcaQueNoEsUnNumeroSeRechazaSinTocarElExpediente() throws Exception {
        mockMvc.perform(get("/api/contratos/{id}/novedades", contratoId)
                        .param("desdeId", "ayer por la tarde")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elSondeoNoSeGuardaEnNingunIntermediario() throws Exception {
        mockMvc.perform(get("/api/contratos/{id}/novedades", contratoId)
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ultimoId").exists())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Cache-Control"))
                        .contains("no-store"));
    }

    // ── ayudas ───────────────────────────────────────────────────────────────

    private JsonNode novedades(String token, Long desdeId) throws Exception {
        var peticion = get("/api/contratos/{id}/novedades", contratoId)
                .header("Authorization", "Bearer " + token);
        if (desdeId != null) {
            peticion = peticion.param("desdeId", String.valueOf(desdeId));
        }
        return objectMapper.readTree(mockMvc.perform(peticion)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private void cerrarElSubpaso22() throws Exception {
        mockMvc.perform(patch("/api/subetapas/{id}/estado", subpaso22)
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"COMPLETADA\"}"))
                .andExpect(status().isOk());
    }

    private JsonNode etapas() throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/contratos/{id}/etapas", contratoId)
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private String login(String email, String password) throws Exception {
        return objectMapper.readTree(loginBody(email, password)).get("token").asString();
    }

    private String loginBody(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
