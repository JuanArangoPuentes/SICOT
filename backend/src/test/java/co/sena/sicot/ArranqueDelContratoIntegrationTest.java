package co.sena.sicot;

import co.sena.sicot.dto.secop.VerificacionSecopResponse;
import co.sena.sicot.service.DatosAbiertosSecop;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lo que el supervisor registra al arrancar el contrato, según la lista de
 * chequeo de Alex Zapata: las fechas de entrega acordadas con el contratista
 * (paso 5 de la hoja), los profesionales de SST y ambiental (paso 3) y el
 * teléfono del contratista (paso 5: «correo de contacto + número de
 * teléfono»).
 *
 * <p>El contrato de las pruebas tiene el plazo del Lote 4: del 14-10-2026 al
 * 15-12-2026. datos.gov.co se sustituye por un doble: ninguna prueba depende de
 * que haya red.
 */
class ArranqueDelContratoIntegrationTest extends PruebaDeIntegracion {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DatosAbiertosSecop datosAbiertos;

    // ─────────────────────────── Acuerdo de entregas ───────────────────────────

    @Test
    void lasEntregasSeGuardanNumeradasPorFechaYConElPlazoDelContrato() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.910100", supervisorId(), null, null);
        String supervisor = login("supervisor@soy.sena.edu.co", "Supervisor123*");

        // Sin acuerdo todavía: lista vacía y el plazo, para que la pantalla no
        // ofrezca días fuera de él.
        mockMvc.perform(get("/api/contratos/{id}/entregas-acordadas", contratoId)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entregas").isEmpty())
                .andExpect(jsonPath("$.inicioDelContrato").value("2026-10-14"))
                .andExpect(jsonPath("$.finDelContrato").value("2026-12-15"));

        // Llegan en otro orden: «la entrega 1» tiene que ser la primera en el tiempo.
        mockMvc.perform(put("/api/contratos/{id}/entregas-acordadas", contratoId)
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entregas":[
                                  {"fecha":"2026-11-20","descripcion":"Ítems 21 a 35"},
                                  {"fecha":"2026-10-30","descripcion":"  Ítems 1 a 20  "}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entregas.length()").value(2))
                .andExpect(jsonPath("$.entregas[0].numero").value(1))
                .andExpect(jsonPath("$.entregas[0].fecha").value("2026-10-30"))
                .andExpect(jsonPath("$.entregas[0].descripcion").value("Ítems 1 a 20"))
                .andExpect(jsonPath("$.entregas[1].numero").value(2))
                .andExpect(jsonPath("$.entregas[1].fecha").value("2026-11-20"));

        // Y queda constancia en el expediente.
        mockMvc.perform(get("/api/contratos/{id}/registros", contratoId)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.accion == 'ENTREGAS_ACORDADAS')].descripcion")
                        .value(org.hamcrest.Matchers.hasItem(containsString("1) 30/10/2026, 2) 20/11/2026"))));
    }

    @Test
    void unaFechaFueraDelPlazoSeRechazaDiciendoCualYElPlazo() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.910200", supervisorId(), null, null);
        String supervisor = login("supervisor@soy.sena.edu.co", "Supervisor123*");

        mockMvc.perform(put("/api/contratos/{id}/entregas-acordadas", contratoId)
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entregas":[{"fecha":"2026-12-20","descripcion":"Entrega total"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("20/12/2026")))
                .andExpect(jsonPath("$.message", containsString("del 14/10/2026 al 15/12/2026")))
                .andExpect(jsonPath("$.message", containsString("prórroga")));

        mockMvc.perform(put("/api/contratos/{id}/entregas-acordadas", contratoId)
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entregas":[{"fecha":"2026-10-01","descripcion":"Entrega total"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("01/10/2026")));

        // Sin descripción tampoco: el contratista tiene que saber qué entrega ese día.
        mockMvc.perform(put("/api/contratos/{id}/entregas-acordadas", contratoId)
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entregas":[{"fecha":"2026-11-01","descripcion":"  "}]}
                                """))
                .andExpect(status().isBadRequest());

        // Nada de lo rechazado quedó guardado.
        mockMvc.perform(get("/api/contratos/{id}/entregas-acordadas", contratoId)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(jsonPath("$.entregas").isEmpty());
    }

    @Test
    void elPrimerYUltimoDiaDelPlazoSonValidosYLaListaVaciaBorraElAcuerdo() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.910300", supervisorId(), null, null);
        String supervisor = login("supervisor@soy.sena.edu.co", "Supervisor123*");

        mockMvc.perform(put("/api/contratos/{id}/entregas-acordadas", contratoId)
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entregas":[{"fecha":"2026-10-14","descripcion":"Primera"},
                                             {"fecha":"2026-12-15","descripcion":"Última"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entregas.length()").value(2));

        mockMvc.perform(put("/api/contratos/{id}/entregas-acordadas", contratoId)
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entregas\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entregas").isEmpty());
    }

    @Test
    void otroSupervisorNoVeNiCambiaElAcuerdoDeUnContratoAjeno() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.910400", supervisorId(), null, null);
        String ajeno = supervisorAjeno();

        mockMvc.perform(get("/api/contratos/{id}/entregas-acordadas", contratoId)
                        .header("Authorization", "Bearer " + ajeno))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/contratos/{id}/entregas-acordadas", contratoId)
                        .header("Authorization", "Bearer " + ajeno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entregas\":[{\"fecha\":\"2026-11-01\",\"descripcion\":\"x\"}]}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/contratos/{id}/contactos-sst-ambiental", contratoId)
                        .header("Authorization", "Bearer " + ajeno))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/contratos/{id}/contacto-contratista/desde-secop", contratoId)
                        .header("Authorization", "Bearer " + ajeno))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────── Profesionales de SST y ambiental ───────────────────────

    @Test
    void losContactosSeGuardanSinRepetirYPrimeroSst() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.910500", supervisorId(), null, null);
        String supervisor = login("supervisor@soy.sena.edu.co", "Supervisor123*");

        mockMvc.perform(put("/api/contratos/{id}/contactos-sst-ambiental", contratoId)
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contactos":[
                                  {"rol":"AMBIENTAL","nombre":"Profesional Ambiental","correo":"Ambiental.Centro@sena.edu.co"},
                                  {"rol":"SST","nombre":"  ","correo":"sst.centro@sena.edu.co"},
                                  {"rol":"SST","nombre":"Repetido","correo":"SST.CENTRO@sena.edu.co"}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactos.length()").value(2))
                .andExpect(jsonPath("$.contactos[0].rol").value("SST"))
                .andExpect(jsonPath("$.contactos[0].correo").value("sst.centro@sena.edu.co"))
                // Un nombre en blanco es «no lo sé», no un nombre.
                .andExpect(jsonPath("$.contactos[0].nombre").doesNotExist())
                .andExpect(jsonPath("$.contactos[1].rol").value("AMBIENTAL"))
                .andExpect(jsonPath("$.contactos[1].correo").value("ambiental.centro@sena.edu.co"));

        mockMvc.perform(put("/api/contratos/{id}/contactos-sst-ambiental", contratoId)
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactos\":[{\"rol\":\"SST\",\"correo\":\"no-es-un-correo\"}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delEstudioPrevioSeProponenLosCorreosConElRolQueSugiereElTexto() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.910600", supervisorId(), null, null);
        String supervisor = login("supervisor@soy.sena.edu.co", "Supervisor123*");

        byte[] estudioPrevio = ArchivoDePrueba.pdfConRenglones(
                "ESTUDIO PREVIO - SUMINISTRO DE MATERIALES DE FORMACIÓN",
                "Responsables del acompañamiento del contrato:",
                "Profesional de Seguridad y Salud en el Trabajo (SST): Ana María Gómez",
                "Correo: amgomez@sena.edu.co",
                "Profesional de Gestión Ambiental: Carlos Pérez",
                "Correo: cperez@sena.edu.co",
                "Para notificaciones del proceso escriba a contratacion.centro@sena.edu.co.");

        mockMvc.perform(multipart("/api/contratos/{id}/contactos-sst-ambiental/lectura", contratoId)
                        .file(new MockMultipartFile("archivo", "6. ESTUDIO PREVIO.pdf", "application/pdf", estudioPrevio))
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aviso").doesNotExist())
                .andExpect(jsonPath("$.encontrados.length()").value(3))
                .andExpect(jsonPath("$.encontrados[?(@.correo == 'amgomez@sena.edu.co')].rolSugerido").value("SST"))
                .andExpect(jsonPath("$.encontrados[?(@.correo == 'cperez@sena.edu.co')].rolSugerido").value("AMBIENTAL"))
                // El punto final de la oración no es parte del correo.
                .andExpect(jsonPath("$.encontrados[2].correo").value("contratacion.centro@sena.edu.co"))
                .andExpect(jsonPath("$.encontrados[2].rolSugerido").doesNotExist())
                // El fragmento deja leer el nombre, con sus tildes.
                .andExpect(jsonPath("$.encontrados[0].contexto", containsString("Ana María Gómez")));

        // Leer no guarda nada: los contactos los elige el supervisor.
        mockMvc.perform(get("/api/contratos/{id}/contactos-sst-ambiental", contratoId)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(jsonPath("$.contactos").isEmpty());
    }

    @Test
    void unEstudioPrevioEscaneadoSeExplicaYUnaImagenSeRechaza() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.910700", supervisorId(), null, null);
        String supervisor = login("supervisor@soy.sena.edu.co", "Supervisor123*");

        mockMvc.perform(multipart("/api/contratos/{id}/contactos-sst-ambiental/lectura", contratoId)
                        .file(new MockMultipartFile("archivo", "escaneado.pdf", "application/pdf",
                                ArchivoDePrueba.pdfSinTexto()))
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encontrados").isEmpty())
                .andExpect(jsonPath("$.aviso", containsString("escaneada")));

        mockMvc.perform(multipart("/api/contratos/{id}/contactos-sst-ambiental/lectura", contratoId)
                        .file(new MockMultipartFile("archivo", "foto.png", "image/png", ArchivoDePrueba.imagen("png")))
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────── Teléfono del contratista ───────────────────────────

    @Test
    void elTelefonoSeCompletaDesdeElRegistroDeProveedoresSinPisarElCorreoDelActa() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.910800", supervisorId(),
                "maybesuministros@gmail.com", null);
        given(datosAbiertos.proveedor("800154801-6")).willReturn(Optional.of(maybe("otro@correo.co")));
        String supervisor = login("supervisor@soy.sena.edu.co", "Supervisor123*");

        mockMvc.perform(post("/api/contratos/{id}/contacto-contratista/desde-secop", contratoId)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefonoContratista").value("4489948"))
                // El del acta manda: el registro no lo reemplaza.
                .andExpect(jsonPath("$.correoContratista").value("maybesuministros@gmail.com"));

        mockMvc.perform(get("/api/contratos/{id}", contratoId).header("Authorization", "Bearer " + supervisor))
                .andExpect(jsonPath("$.telefonoContratista").value("4489948"));
        mockMvc.perform(get("/api/contratos/{id}/registros", contratoId)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(jsonPath("$[?(@.accion == 'CONTACTO_CONTRATISTA_COMPLETADO')].descripcion")
                        .value(org.hamcrest.Matchers.hasItem(containsString("4489948"))));
    }

    @Test
    void sinCorreoEnElActaTambienSeTomaElDelRegistro() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.910900", supervisorId(), null, null);
        given(datosAbiertos.proveedor(anyString())).willReturn(Optional.of(maybe("maybesuministros@gmail.com")));
        String supervisor = login("supervisor@soy.sena.edu.co", "Supervisor123*");

        mockMvc.perform(post("/api/contratos/{id}/contacto-contratista/desde-secop", contratoId)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefonoContratista").value("4489948"))
                .andExpect(jsonPath("$.correoContratista").value("maybesuministros@gmail.com"));
    }

    @Test
    void unNitQueNoEstaRegistradoOSinConexionSeDiceSinTocarElContrato() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.911000", supervisorId(), null, null);
        String supervisor = login("supervisor@soy.sena.edu.co", "Supervisor123*");

        given(datosAbiertos.proveedor(anyString())).willReturn(Optional.empty());
        mockMvc.perform(post("/api/contratos/{id}/contacto-contratista/desde-secop", contratoId)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("No se encontró en el registro de proveedores")));

        given(datosAbiertos.proveedor(anyString()))
                .willThrow(new DatosAbiertosSecop.NoDisponible("sin red", new RuntimeException()));
        mockMvc.perform(post("/api/contratos/{id}/contacto-contratista/desde-secop", contratoId)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("No hay conexión con datos.gov.co")));

        mockMvc.perform(get("/api/contratos/{id}", contratoId).header("Authorization", "Bearer " + supervisor))
                .andExpect(jsonPath("$.telefonoContratista").doesNotExist());
    }

    @Test
    void gestionRegistraElTelefonoYUnaEdicionSinElCampoNoLoBorra() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.911100", supervisorId(), null, "604 448 9948");

        mockMvc.perform(get("/api/contratos/{id}", contratoId).header("Authorization", "Bearer " + gestion))
                .andExpect(jsonPath("$.telefonoContratista").value("604 448 9948"));

        // Una instalación de escritorio anterior no envía el campo: editar el
        // contrato desde ella no puede borrar el teléfono.
        mockMvc.perform(put("/api/contratos/{id}", contratoId)
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"CO1.PCCNTR.911100","objeto":"Objeto corregido",
                                 "valor":45900300,"fechaInicio":"2026-10-14","fechaFin":"2026-12-15"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefonoContratista").value("604 448 9948"));

        // Un teléfono con letras no es un teléfono.
        mockMvc.perform(put("/api/contratos/{id}", contratoId)
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"CO1.PCCNTR.911100","objeto":"Objeto corregido",
                                 "valor":45900300,"telefonoContratista":"llamar a Luz"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unElementoVacioEnLaListaEsUn400YNoUn500() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.911200", supervisorId(), null, null);
        String supervisor = login("supervisor@soy.sena.edu.co", "Supervisor123*");

        mockMvc.perform(put("/api/contratos/{id}/entregas-acordadas", contratoId)
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entregas\":[null]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/contratos/{id}/contactos-sst-ambiental", contratoId)
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactos\":[null]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sinUnTelefonoUtilizableEnElRegistroSeDiceYNoSeGuardaNada() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.911300", supervisorId(),
                "maybesuministros@gmail.com", null);
        // Lo que devuelve proveedor() cuando el registro publica «0» o «xxxxxx»:
        // ya normalizado a null (DatosAbiertosSecop.telefonoUtilizable).
        given(datosAbiertos.proveedor(anyString())).willReturn(Optional.of(
                new VerificacionSecopResponse.Proveedor("SUMINISTROS MAYBE SAS", "800154801", null, null)));
        String supervisor = login("supervisor@soy.sena.edu.co", "Supervisor123*");

        mockMvc.perform(post("/api/contratos/{id}/contacto-contratista/desde-secop", contratoId)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("no publica un teléfono utilizable")));
    }

    /**
     * Gestión tiene la lista abierta desde antes de que el supervisor trajera el
     * teléfono y el correo, y corrige otro dato: la pantalla no los manda
     * porque no los tocó, y no se pueden borrar por eso.
     */
    @Test
    void unaCorreccionDeGestionSinLosCamposNoBorraLoQueTrajoElSupervisor() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.911400", supervisorId(), null, null);
        given(datosAbiertos.proveedor(anyString())).willReturn(Optional.of(maybe("maybesuministros@gmail.com")));
        String supervisor = login("supervisor@soy.sena.edu.co", "Supervisor123*");
        mockMvc.perform(post("/api/contratos/{id}/contacto-contratista/desde-secop", contratoId)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/contratos/{id}", contratoId)
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"CO1.PCCNTR.911400","objeto":"Objeto corregido por Gestión",
                                 "valor":45900300,"fechaInicio":"2026-10-14","fechaFin":"2026-12-15"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correoContratista").value("maybesuministros@gmail.com"))
                .andExpect(jsonPath("$.telefonoContratista").value("4489948"));

        // Y vacío sí lo borra: es Gestión dejándolo en blanco a propósito.
        mockMvc.perform(put("/api/contratos/{id}", contratoId)
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"CO1.PCCNTR.911400","objeto":"Objeto corregido por Gestión",
                                 "valor":45900300,"correoContratista":"","telefonoContratista":""}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correoContratista").doesNotExist())
                .andExpect(jsonPath("$.telefonoContratista").doesNotExist());
    }

    // ─────────────────────────────── Apoyo ───────────────────────────────

    private static VerificacionSecopResponse.Proveedor maybe(String correo) {
        return new VerificacionSecopResponse.Proveedor("SUMINISTROS MAYBE SAS", "800154801", "4489948", correo);
    }

    private long crearContrato(String token, String numero, long supervisorId, String correo, String telefono)
            throws Exception {
        String creado = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"%s","objeto":"Suministro de materiales para la formación",
                                 "valor":45900300,"fechaInicio":"2026-10-14","fechaFin":"2026-12-15",
                                 "supervisorId":%d,"contratista":"SUMINISTROS MAYBE S.A.S",
                                 "contratistaNit":"800154801-6","correoContratista":%s,"telefonoContratista":%s}
                                """.formatted(numero, supervisorId, json(correo), json(telefono))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(creado).get("id").asLong();
    }

    private static String json(String valor) {
        return valor == null ? "null" : "\"" + valor + "\"";
    }

    private String supervisorAjeno() throws Exception {
        String admin = login("administrador@soy.sena.edu.co", "Admin123*");
        mockMvc.perform(post("/api/usuarios")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"Supervisor Ajeno Arranque","email":"ajeno.arranque@soy.sena.edu.co",
                         "password":"Ajeno123*","telefono":"3000000000","rol":"SUPERVISOR"}
                        """));
        return login("ajeno.arranque@soy.sena.edu.co", "Ajeno123*");
    }

    private long supervisorId() throws Exception {
        JsonNode cuerpo = objectMapper.readTree(loginBody("supervisor@soy.sena.edu.co", "Supervisor123*"));
        return cuerpo.get("usuarioId").asLong();
    }

    private String login(String email, String password) throws Exception {
        return objectMapper.readTree(loginBody(email, password)).get("token").asText();
    }

    private String loginBody(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
