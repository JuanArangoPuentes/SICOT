package co.sena.sicot;

import co.sena.sicot.dto.auth.AuthResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de extremo a extremo del flujo del supervisor: crear un contrato,
 * asignarlo, y recorrer sus 6 etapas / 21 subetapas marcando cada
 * subetapa como COMPLETADA hasta que las 6 etapas quedan al 100 %.
 *
 * Existe para blindar la tarea de transiciones de estado: la validación
 * introducida en {@code TransicionesDeEstado} <b>no</b> debe romper el único
 * camino que un supervisor recorre de verdad. {@code PENDIENTE → COMPLETADA}
 * (saltando {@code EN_CURSO}) es parte de ese camino y tiene que seguir pasando.
 */
class FlujoSupervisorEtapasIntegrationTest extends PruebaDeIntegracion {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void elSupervisorRecorreLas6EtapasY21SubetapasDePrincipioAFin() throws Exception {
        String gestionToken = login("gestion@soy.sena.edu.co", "Gestion123*");
        String supervisorToken = login("supervisor@soy.sena.edu.co", "Supervisor123*");
        long supervisorId = objectMapper.readTree(
                loginBody("supervisor@soy.sena.edu.co", "Supervisor123*")).get("usuarioId").asLong();

        // 1. Crear contrato y asignarle el supervisor.
        String crear = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + gestionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"CO1.PCCNTR.FLUJO21","objeto":"Flujo completo del supervisor",
                                 "valor":1000000,"fechaInicio":"2026-01-01","fechaFin":"2026-12-31"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long contratoId = objectMapper.readTree(crear).get("id").asLong();

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

        // 2. Estructura inicial: 6 etapas, 21 subetapas. Asignar el supervisor es
        //    la designación: la etapa 1 ya llega completa y el proceso arranca en
        //    el paso del acta, como en el proceso real.
        JsonNode etapas = leerEtapas(contratoId, supervisorToken);
        assertThat(etapas).hasSize(6);
        assertThat(contarSubetapas(etapas)).isEqualTo(21);
        assertThat(etapas.get(0).get("estado").asString()).isEqualTo("COMPLETADA");
        JsonNode pasoDelActa = etapas.get(1).get("subEtapas").get(0);
        assertThat(pasoDelActa.get("codigo").asString()).isEqualTo("2.1");
        assertThat(pasoDelActa.get("estado").asString()).isEqualTo("EN_CURSO");
        assertThat(pasoDelActa.get("documentosRequeridos").asString()).isEqualTo("ACTA_FIRMADA_Y_ENVIO");

        // 3. El paso del acta queda esperando: sin el acta firmada y la evidencia
        //    del envío no se cierra.
        long pasoDelActaId = pasoDelActa.get("id").asLong();
        mockMvc.perform(patch("/api/subetapas/{id}/estado", pasoDelActaId)
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"COMPLETADA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Acta de Inicio firmada")));
        // Dos piezas de la evidencia del envío, aunque una sea PDF, no son el
        // acta: hasta el 18-10-2026 con eso el paso se cerraba sin ella.
        cargarConNombre(contratoId, pasoDelActaId, supervisorToken, "correo.pdf", "application/pdf",
                ArchivoDePrueba.pdfDeUnaPagina("correo enviado"), "Evidencia del envío del acta");
        cargarConNombre(contratoId, pasoDelActaId, supervisorToken, "respuesta.pdf", "application/pdf",
                ArchivoDePrueba.pdfDeUnaPagina("respuesta de contratacion"), "Evidencia del envío del acta (2)");
        mockMvc.perform(patch("/api/subetapas/{id}/estado", pasoDelActaId)
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"COMPLETADA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("falta el acta firmada")));
        cargar(contratoId, pasoDelActaId, supervisorToken, "acta-firmada.pdf", "application/pdf",
                ArchivoDePrueba.pdfDeUnaPagina("acta firmada"));
        cargar(contratoId, pasoDelActaId, supervisorToken, "correo-enviado.png", "image/png",
                ArchivoDePrueba.imagen("png"));

        // 3b. El paso del contacto con el contratista es también el del acuerdo
        //     de entregas (paso 5 de la hoja de Alex): sin una fecha acordada no
        //     se cierra. Con una, sí.
        JsonNode pasoDeEntregas = etapas.get(1).get("subEtapas").get(3);
        assertThat(pasoDeEntregas.get("codigo").asString()).isEqualTo("2.4");
        assertThat(pasoDeEntregas.get("documentosRequeridos").asString()).isEqualTo("ENTREGAS_ACORDADAS");
        mockMvc.perform(patch("/api/subetapas/{id}/estado", pasoDeEntregas.get("id").asLong())
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"COMPLETADA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("fechas de entrega")));
        mockMvc.perform(put("/api/contratos/{id}/entregas-acordadas", contratoId)
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entregas\":[{\"fecha\":\"2026-06-30\",\"descripcion\":\"Entrega total\"}]}"))
                .andExpect(status().isOk());

        // 4. Recorrer cada subetapa de cada etapa, en orden, marcándola COMPLETADA.
        //    Igual que el panel del supervisor: la mayoría van PENDIENTE -> COMPLETADA
        //    directo (nunca pasan por EN_CURSO). Las de la etapa 1 ya estaban
        //    completas: volver a marcarlas no cambia nada ni deja traza.
        for (JsonNode etapa : etapas) {
            for (JsonNode subetapa : etapa.get("subEtapas")) {
                long subetapaId = subetapa.get("id").asLong();
                mockMvc.perform(patch("/api/subetapas/{id}/estado", subetapaId)
                                .header("Authorization", "Bearer " + supervisorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"estado\":\"COMPLETADA\"}"))
                        .andExpect(status().isOk());
            }
        }

        // 5. Las 6 etapas quedan COMPLETADA al 100 %.
        JsonNode etapasFinales = leerEtapas(contratoId, supervisorToken);
        for (JsonNode etapa : etapasFinales) {
            assertThat(etapa.get("estado").asString())
                    .as("etapa %s", etapa.get("numero").asInt())
                    .isEqualTo("COMPLETADA");
            assertThat(etapa.get("porcentaje").asInt()).isEqualTo(100);
        }

        // 6. Cada subetapa del supervisor dejó su traza de avance; ninguna de
        //    retroceso. Las 3 de la designación quedaron registradas de una vez.
        String registros = mockMvc.perform(get("/api/contratos/{id}/registros", contratoId)
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long avances = contar(registros, "SUBETAPA_AVANZADA");
        long retrocesos = contar(registros, "SUBETAPA_REVERTIDA") + contar(registros, "ETAPA_RETROCEDIDA");
        assertThat(avances).isEqualTo(18);
        assertThat(contar(registros, "DESIGNACION_REGISTRADA")).isEqualTo(1);
        assertThat(retrocesos).isZero();

        // 7. El contrato NO se cierra solo al completar las 6 etapas: sigue ACTIVO.
        mockMvc.perform(get("/api/contratos/{id}", contratoId)
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }

    private void cargar(long contratoId, long subetapaId, String token, String nombre, String tipo, byte[] bytes)
            throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/contratos/{id}/documentos", contratoId)
                        .file(new org.springframework.mock.web.MockMultipartFile("archivo", nombre, tipo, bytes))
                        .param("subetapaId", String.valueOf(subetapaId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    private void cargarConNombre(long contratoId, long subetapaId, String token, String archivo, String tipo,
                                 byte[] bytes, String nombre) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/contratos/{id}/documentos", contratoId)
                        .file(new org.springframework.mock.web.MockMultipartFile("archivo", archivo, tipo, bytes))
                        .param("subetapaId", String.valueOf(subetapaId))
                        .param("nombre", nombre)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    private JsonNode leerEtapas(long contratoId, String token) throws Exception {
        String json = mockMvc.perform(get("/api/contratos/{id}/etapas", contratoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private int contarSubetapas(JsonNode etapas) {
        int total = 0;
        for (JsonNode etapa : etapas) {
            total += etapa.get("subEtapas").size();
        }
        return total;
    }

    private long contar(String texto, String aguja) {
        long total = 0;
        int i = texto.indexOf(aguja);
        while (i != -1) {
            total++;
            i = texto.indexOf(aguja, i + aguja.length());
        }
        return total;
    }

    private String login(String email, String password) throws Exception {
        return objectMapper.readValue(loginBody(email, password), AuthResponse.class).token();
    }

    private String loginBody(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
