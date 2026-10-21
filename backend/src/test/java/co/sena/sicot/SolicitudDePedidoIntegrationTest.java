package co.sena.sicot;

import co.sena.sicot.dto.auth.AuthResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La solicitud de pedido al contratista: el Excel del paso 2.4.
 *
 * <p>Comprueba el recorrido que pidió Alex Zapata el 20-10-2026 en el paso 5 de
 * su lista de chequeo («generar y adjuntar el documento excel con la lista de
 * materiales solicitado»): el supervisor carga los bienes, SICOT calcula el
 * presupuesto comprometido y lo que queda del contrato, y entrega un XLSX que
 * se abre de verdad y trae las cifras.
 *
 * <p>Los datos son los del pedido real del Lote 4 («Solicitud_Maybe.xlsx»): el
 * mouse a $29.462,51 las 83 unidades y el SSD a $183.122,52 las 20.
 */
class SolicitudDePedidoIntegrationTest extends PruebaDeIntegracion {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String DOS_ITEMS_DEL_LOTE_4 = """
            {"items":[
              {"numero":1,"descripcion":"Mouse Óptico Alámbrico, sensor óptico 1000 DPI, USB 2.0",
               "cantidad":83,"valorUnitario":29462.51},
              {"numero":2,"descripcion":"Unidad de Estado Sólido 500 GB, 2.5 pulgadas, SATA III",
               "cantidad":20,"valorUnitario":183122.52}
            ]}
            """;

    @Test
    void elSupervisorCargaLosBienesYSicotCalculaLoQueQuedaDelContrato() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.10057821", 45_900_300);

        // Antes de cargar nada: la solicitud existe vacía y el presupuesto es
        // el valor del contrato. No tener pedido todavía no es un error.
        mockMvc.perform(get("/api/contratos/{c}/solicitud-pedido", contratoId)
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.presupuestoAsignado").value(45900300))
                .andExpect(jsonPath("$.comprometido").value(0))
                .andExpect(jsonPath("$.queda").value(45900300))
                .andExpect(jsonPath("$.excedeElContrato").value(false));

        // 83 × 29.462,51 = 2.445.388,33 y 20 × 183.122,52 = 3.662.450,40,
        // exactamente los totales del archivo real del Lote 4.
        mockMvc.perform(put("/api/contratos/{c}/solicitud-pedido", contratoId)
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DOS_ITEMS_DEL_LOTE_4))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].valorTotal").value(2445388.33))
                .andExpect(jsonPath("$.items[1].valorTotal").value(3662450.40))
                .andExpect(jsonPath("$.comprometido").value(6107838.73))
                .andExpect(jsonPath("$.queda").value(39792461.27))
                .andExpect(jsonPath("$.excedeElContrato").value(false));

        // Y queda guardada: es lo que hace que el supervisor no tenga que
        // volver a pegar la tabla en cada correo.
        mockMvc.perform(get("/api/contratos/{c}/solicitud-pedido", contratoId)
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[1].cantidad").value(20))
                .andExpect(jsonPath("$.comprometido").value(6107838.73));
    }

    @Test
    void elExcelQueSeAdjuntaAlCorreoSeAbreYTraeLasCifras() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.10057822", 45_900_300);
        mockMvc.perform(put("/api/contratos/{c}/solicitud-pedido", contratoId)
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DOS_ITEMS_DEL_LOTE_4))
                .andExpect(status().isOk());

        byte[] xlsx = mockMvc.perform(get("/api/contratos/{c}/solicitud-pedido/archivo", contratoId)
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Type",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();

        // Que se abra con el mismo lector que usará quien lo reciba, y no solo
        // que empiece como un XLSX: es la misma exigencia que ArchivoValidator
        // le hace a lo que se carga.
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet hoja = libro.getSheetAt(0);
            List<String> textos = new ArrayList<>();
            List<Double> numeros = new ArrayList<>();
            for (Row fila : hoja) {
                for (Cell celda : fila) {
                    switch (celda.getCellType()) {
                        case STRING -> textos.add(celda.getStringCellValue());
                        case NUMERIC -> numeros.add(celda.getNumericCellValue());
                        default -> { }
                    }
                }
            }
            assertThat(textos).contains("SOLICITUD DE PEDIDO", "ITEMS", "DESCRIPCIÓN", "CANTIDAD",
                    "VALOR UNITARIO CON IVA", "VALOR TOTAL CON IVA", "SUBTOTAL",
                    "ASIGNADO", "COMPROMETIDO", "QUEDA", "SUPERVISOR");
            assertThat(textos).anyMatch(t -> t.contains("Mouse Óptico Alámbrico"));
            assertThat(textos).anyMatch(t -> t.contains("CENTRO TECNOLÓGICO DEL MOBILIARIO"));
            // El total de cada fila, el comprometido y lo que queda: las cifras
            // por las que el contratista y el supervisor discuten.
            assertThat(numeros).contains(2445388.33, 3662450.40, 6107838.73, 39792461.27, 45900300.0);
            // Sin el aviso rojo: este pedido cabe en el contrato.
            assertThat(textos).noneMatch(t -> t.contains("excede el valor del contrato"));
        }
    }

    @Test
    void avisaEnLaRespuestaYEnElArchivoCuandoElPedidoSePasaDelValorDelContrato() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.10057823", 1_000_000);

        mockMvc.perform(put("/api/contratos/{c}/solicitud-pedido", contratoId)
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DOS_ITEMS_DEL_LOTE_4))
                .andExpect(status().isOk())
                // Se guarda igual: el supervisor puede estar a mitad de pegar la
                // tabla, y es él quien sabe si lo que falla es una cantidad o el
                // valor registrado del contrato.
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.queda").value(-5107838.73))
                .andExpect(jsonPath("$.excedeElContrato").value(true));

        byte[] xlsx = mockMvc.perform(get("/api/contratos/{c}/solicitud-pedido/archivo", contratoId)
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            List<String> textos = new ArrayList<>();
            for (Row fila : libro.getSheetAt(0)) {
                for (Cell celda : fila) {
                    if (celda.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                        textos.add(celda.getStringCellValue());
                    }
                }
            }
            assertThat(textos).anyMatch(t -> t.contains("excede el valor del contrato"));
        }
    }

    @Test
    void noGeneraUnArchivoVacioYDiceQueCargarPrimero() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.10057824", 1_000_000);

        mockMvc.perform(get("/api/contratos/{c}/solicitud-pedido/archivo", contratoId)
                        .header("Authorization", "Bearer " + gestion))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("oferta económica")));
    }

    @Test
    void rechazaDosItemsConElMismoNumeroDiciendoCual() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.10057825", 1_000_000);

        mockMvc.perform(put("/api/contratos/{c}/solicitud-pedido", contratoId)
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[
                                  {"numero":7,"descripcion":"Extensión eléctrica industrial 3 m",
                                   "cantidad":20,"valorUnitario":49602.46},
                                  {"numero":7,"descripcion":"Micrómetro digital 0-25 mm",
                                   "cantidad":10,"valorUnitario":205463.32}
                                ]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("ítem número 7")));
    }

    @Test
    void rechazaCantidadNegativaYDescripcionVacia() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.10057826", 1_000_000);

        mockMvc.perform(put("/api/contratos/{c}/solicitud-pedido", contratoId)
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"numero":1,"descripcion":"  ","cantidad":-5,"valorUnitario":100}]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void laListaVaciaBorraLaSolicitud() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.10057827", 45_900_300);
        mockMvc.perform(put("/api/contratos/{c}/solicitud-pedido", contratoId)
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DOS_ITEMS_DEL_LOTE_4))
                .andExpect(status().isOk());

        // Así se deshace un pegado equivocado, sin editar 35 filas.
        mockMvc.perform(put("/api/contratos/{c}/solicitud-pedido", contratoId)
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.comprometido").value(0));
    }

    @Test
    void volverAGuardarReemplazaLaSolicitudEnVezDeSumarla() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.10057828", 45_900_300);
        mockMvc.perform(put("/api/contratos/{c}/solicitud-pedido", contratoId)
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DOS_ITEMS_DEL_LOTE_4))
                .andExpect(status().isOk());

        // Con los mismos números de ítem: si no se borrara antes de insertar,
        // esto chocaría con la restricción de número único de la base.
        mockMvc.perform(put("/api/contratos/{c}/solicitud-pedido", contratoId)
                        .header("Authorization", "Bearer " + gestion)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[
                                  {"numero":1,"descripcion":"Mouse Óptico Alámbrico (cantidad corregida)",
                                   "cantidad":80,"valorUnitario":29462.51},
                                  {"numero":2,"descripcion":"Unidad de Estado Sólido 500 GB","cantidad":0,
                                   "valorUnitario":183122.52}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                // 80 × 29.462,51; el ítem 2 queda en 0, que en el formato real
                // significa «estaba en la oferta y no se pide».
                .andExpect(jsonPath("$.comprometido").value(2357000.80))
                .andExpect(jsonPath("$.items[1].valorTotal").value(0));
    }

    @Test
    void unSupervisorNoVeNiCambiaLaSolicitudDeOtroContrato() throws Exception {
        String gestion = login("gestion@soy.sena.edu.co", "Gestion123*");
        long contratoId = crearContrato(gestion, "CO1.PCCNTR.10057829", 1_000_000);
        // El contrato no tiene supervisor asignado, así que para el supervisor
        // de la semilla es ajeno: mismo 404 que da el resto del expediente.
        String supervisor = login("supervisor@soy.sena.edu.co", "Supervisor123*");

        mockMvc.perform(get("/api/contratos/{c}/solicitud-pedido", contratoId)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/contratos/{c}/solicitud-pedido", contratoId)
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DOS_ITEMS_DEL_LOTE_4))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/contratos/{c}/solicitud-pedido/archivo", contratoId)
                        .header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isNotFound());
    }

    private long crearContrato(String gestionToken, String numero, long valor) throws Exception {
        String creado = mockMvc.perform(post("/api/contratos")
                        .header("Authorization", "Bearer " + gestionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroContrato":"%s",
                                 "objeto":"Contratar el suministro de materiales para la formación para los \
                                diferentes programas de formación del Centro Tecnológico del Mobiliario",
                                 "valor":%d,
                                 "contratista":"SUMINISTROS MAYBE S.A.S",
                                 "contratistaNit":"800154801-6"}
                                """.formatted(numero, valor)))
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
