package co.sena.sicot.service;

import co.sena.sicot.dto.secop.VerificacionSecopResponse.Documento;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La lectura de las filas de datos.gov.co. Las filas de ejemplo son recortes
 * de lo que el dataset publicó el 15-10-2026 para el contrato del Lote 4; no
 * hay red en estas pruebas.
 */
class DatosAbiertosSecopTest {

    private final ObjectMapper json = new ObjectMapper();

    private JsonNode fila(String texto) throws Exception {
        return json.readTree(texto);
    }

    @Test
    @DisplayName("«No definido» y «Sin Descripcion» se leen como dato ausente, no como texto")
    void losMarcadoresDeSinDatoSonNulos() throws Exception {
        JsonNode f = fila("""
                {"nombre_banco":"No definido","identificaci_n_representante_legal":"Sin Descripcion",
                 "tipo_de_cuenta":"No Definido","proveedor_adjudicado":"SUMINISTROS MAYBE SAS"}""");

        assertThat(DatosAbiertosSecop.texto(f, "nombre_banco")).isNull();
        assertThat(DatosAbiertosSecop.texto(f, "identificaci_n_representante_legal")).isNull();
        assertThat(DatosAbiertosSecop.texto(f, "tipo_de_cuenta")).isNull();
        assertThat(DatosAbiertosSecop.texto(f, "proveedor_adjudicado")).isEqualTo("SUMINISTROS MAYBE SAS");
    }

    @Test
    @DisplayName("fechas, cifras, sí/no y enlaces envueltos se leen con su tipo")
    void losTiposSeLeenBien() throws Exception {
        JsonNode f = fila("""
                {"fecha_de_fin_del_contrato":"2026-12-15T00:00:00.000","valor_del_contrato":"45900300",
                 "obligaci_n_ambiental":"Si","es_pyme":"No",
                 "urlproceso":{"url":"https://community.secop.gov.co/Public/Tendering/OpportunityDetail/Index?noticeUID=CO1.NTC.10815297"}}""");

        assertThat(DatosAbiertosSecop.fecha(f, "fecha_de_fin_del_contrato")).isEqualTo(LocalDate.of(2026, 12, 15));
        assertThat(DatosAbiertosSecop.numero(f, "valor_del_contrato")).isEqualByComparingTo(new BigDecimal("45900300"));
        assertThat(DatosAbiertosSecop.siNo(f, "obligaci_n_ambiental")).isTrue();
        assertThat(DatosAbiertosSecop.siNo(f, "es_pyme")).isFalse();
        assertThat(DatosAbiertosSecop.url(f, "urlproceso")).endsWith("noticeUID=CO1.NTC.10815297");
    }

    @Test
    @DisplayName("un enlace que no es del SECOP no llega al panel, aunque venga de la fuente oficial")
    void soloSeAceptanEnlacesDelSecop() throws Exception {
        JsonNode f = fila("""
                {"bueno":{"url":"https://community.secop.gov.co/Public/Archive/RetrieveFile/Index?DocumentId=1"},
                 "otro":{"url":"https://sitio-ajeno.example/descarga"},
                 "script":{"url":"javascript:alert(1)"}}""");

        assertThat(DatosAbiertosSecop.url(f, "bueno")).startsWith("https://community.secop.gov.co/");
        assertThat(DatosAbiertosSecop.url(f, "otro")).isNull();
        assertThat(DatosAbiertosSecop.url(f, "script")).isNull();
    }

    /*
     * Si datos.gov.co cambia la forma de un enlace (un arreglo, un objeto dentro
     * de «url»), el enlace se pierde y la verificación sigue. En Jackson 3
     * asString() lanza sobre un contenedor, donde el asText() de Jackson 2 daba
     * cadena vacía: sin cuidarlo, un cambio de esquema ajeno sería un 500.
     */
    @Test
    @DisplayName("un enlace con una forma inesperada se descarta sin romper la consulta")
    void unEnlaceConFormaInesperadaSeDescarta() throws Exception {
        JsonNode f = fila("""
                {"arreglo":["https://community.secop.gov.co/a","https://community.secop.gov.co/b"],
                 "anidado":{"url":{"href":"https://community.secop.gov.co/a"}},
                 "numero":{"url":42}}""");

        assertThat(DatosAbiertosSecop.url(f, "arreglo")).isNull();
        assertThat(DatosAbiertosSecop.url(f, "anidado")).isNull();
        assertThat(DatosAbiertosSecop.url(f, "numero")).isNull();
    }

    @Test
    @DisplayName("solo se consulta con un número de contrato de SECOP II bien formado")
    void unNumeroMalFormadoNoLlegaALaConsulta() {
        DatosAbiertosSecop cliente = new DatosAbiertosSecop("http://127.0.0.1:9", 1);

        assertThat(DatosAbiertosSecop.esNumeroDeContratoValido("CO1.PCCNTR.10057820")).isTrue();
        assertThat(DatosAbiertosSecop.esNumeroDeContratoValido("CO1.PCCNTR.1005")).isTrue();
        assertThat(DatosAbiertosSecop.esNumeroDeContratoValido("CO1.PCCNTR.10057820' OR '1'='1")).isFalse();
        assertThat(DatosAbiertosSecop.esNumeroDeContratoValido(null)).isFalse();
        assertThatThrownBy(() -> cliente.contrato("CO1.PCCNTR.1&$where=1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("el proceso de compra se valida como el contrato: es otro identificador, no se deduce")
    void elProcesoDeCompraTieneSuPropiaForma() {
        assertThat(DatosAbiertosSecop.esNumeroDeProcesoValido("CO1.BDOS.10711126")).isTrue();
        // El del contrato no vale como proceso: son familias distintas.
        assertThat(DatosAbiertosSecop.esNumeroDeProcesoValido("CO1.PCCNTR.10057820")).isFalse();
        assertThat(DatosAbiertosSecop.esNumeroDeProcesoValido("CO1.BDOS.1&$where=1")).isFalse();
        assertThat(DatosAbiertosSecop.esNumeroDeProcesoValido(null)).isFalse();
    }

    @Test
    @DisplayName("sin conexión responde NoDisponible, no una excepción cualquiera")
    void sinConexionEsNoDisponible() {
        // Puerto 9 (discard) cerrado en 127.0.0.1: la conexión se rechaza al instante.
        DatosAbiertosSecop cliente = new DatosAbiertosSecop("http://127.0.0.1:9", 1);

        assertThatThrownBy(() -> cliente.contrato("CO1.PCCNTR.10057820"))
                .isInstanceOf(DatosAbiertosSecop.NoDisponible.class);
    }

    /**
     * El expediente está archivado bajo el proceso de compra
     * (CO1.BDOS.10711126), no bajo el número del contrato
     * (CO1.PCCNTR.10057820): buscar solo por contrato deja fuera los archivos
     * del conjunto de datos, que solo trae ese campo poblado en parte de sus
     * filas. Se busca por los dos y se junta.
     */
    @Nested
    @DisplayName("El índice del expediente")
    class IndiceDelExpediente {

        private static final String ESTUDIO_PREVIO = """
                {"id_documento":"145358384","proceso":"CO1.BDOS.10711126",
                 "n_mero_de_contrato":"CO1.PCCNTR.10057820","nombre_archivo":"6. ESTUDIO PREVIO.pdf",
                 "tamanno_archivo":"5383716","fecha_carga":"2026-10-08T19:00:00.000",
                 "url_descarga_documento":{"url":"https://community.secop.gov.co/Public/Archive/RetrieveFile/Index?DocumentId=145358384"}}""";

        private static final String OFERTA = """
                {"id_documento":"145358385","proceso":"CO1.BDOS.10711126",
                 "nombre_archivo":"8. ECONOMICA LOTE 4 AJUSTADA.pdf","tamanno_archivo":"210334",
                 "fecha_carga":"2026-10-09T19:00:00.000",
                 "url_descarga_documento":{"url":"https://community.secop.gov.co/Public/Archive/RetrieveFile/Index?DocumentId=145358385"}}""";

        private final List<String> consultas = new ArrayList<>();
        private HttpServer servidor;
        private DatosAbiertosSecop cliente;

        @BeforeEach
        void levantarUnDatosGovDeJuguete() throws IOException {
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/" + DatosAbiertosSecop.DOCUMENTOS + ".json", intercambio -> {
                String consulta = intercambio.getRequestURI().getQuery();
                consultas.add(consulta);
                // Por contrato sale solo el estudio previo; por proceso salen los
                // dos, y el estudio previo repetido: es el mismo archivo.
                String cuerpo = consulta != null && consulta.contains("proceso=")
                        ? "[" + ESTUDIO_PREVIO + "," + OFERTA + "]"
                        : "[" + ESTUDIO_PREVIO + "]";
                byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
                intercambio.getResponseHeaders().add("Content-Type", "application/json");
                intercambio.sendResponseHeaders(200, bytes.length);
                intercambio.getResponseBody().write(bytes);
                intercambio.close();
            });
            servidor.start();
            cliente = new DatosAbiertosSecop("http://127.0.0.1:" + servidor.getAddress().getPort(), 5);
        }

        @AfterEach
        void apagarlo() {
            servidor.stop(0);
        }

        @Test
        @DisplayName("se busca por contrato y por proceso, y el archivo repetido sale una vez")
        void seBuscaPorLosDosIdentificadores() {
            List<Documento> documentos = cliente.documentos("CO1.PCCNTR.10057820", "CO1.BDOS.10711126");

            assertThat(consultas).hasSize(2);
            assertThat(consultas).anySatisfy(c -> assertThat(c).contains("n_mero_de_contrato=CO1.PCCNTR.10057820"));
            assertThat(consultas).anySatisfy(c -> assertThat(c).contains("proceso=CO1.BDOS.10711126"));
            assertThat(documentos).extracting(Documento::nombre)
                    .containsExactly("6. ESTUDIO PREVIO.pdf", "8. ECONOMICA LOTE 4 AJUSTADA.pdf");
            assertThat(documentos.getFirst().tamanoBytes()).isEqualTo(5383716L);
        }

        @Test
        @DisplayName("un proceso mal formado no se consulta: no se pega a la URL para que falle allá")
        void unProcesoMalFormadoNoLlegaALaConsulta() {
            List<Documento> documentos = cliente.documentos("CO1.PCCNTR.10057820", "CO1.BDOS.1&$where=1");

            assertThat(consultas).hasSize(1);
            assertThat(consultas).noneSatisfy(c -> assertThat(c).contains("where"));
            assertThat(documentos).extracting(Documento::nombre).containsExactly("6. ESTUDIO PREVIO.pdf");
        }

        @Test
        @DisplayName("sin proceso de compra se busca solo por contrato")
        void sinProcesoSeBuscaSoloPorContrato() {
            assertThat(cliente.documentos("CO1.PCCNTR.10057820", null)).hasSize(1);
            assertThat(consultas).hasSize(1);
        }
    }

    @Test
    @DisplayName("el NIT se consulta sin dígito de verificación ni puntos, y uno sin forma de NIT no se consulta")
    void elNitSeLimpiaAntesDeConsultar() {
        assertThat(DatosAbiertosSecop.nitSinVerificacion("800154801-6")).isEqualTo("800154801");
        assertThat(DatosAbiertosSecop.nitSinVerificacion("800.154.801")).isEqualTo("800154801");
        assertThat(DatosAbiertosSecop.nitSinVerificacion("800154801")).isEqualTo("800154801");
        assertThat(DatosAbiertosSecop.nitSinVerificacion("NIT")).isNull();
        assertThat(DatosAbiertosSecop.nitSinVerificacion("1&$where=1")).isNull();
        assertThat(DatosAbiertosSecop.nitSinVerificacion(null)).isNull();
    }

    /**
     * El registro es texto libre. Valores reales del 23-10-2026: lo que se puede
     * usar se normaliza a la forma que exige el formulario de Gestión; lo que no
     * es un teléfono no se guarda como teléfono.
     */
    @Test
    @DisplayName("el teléfono del registro se normaliza o se descarta, nunca se guarda «0»")
    void elTelefonoDelRegistroSeNormaliza() {
        assertThat(DatosAbiertosSecop.telefonoUtilizable("4489948")).isEqualTo("4489948");
        assertThat(DatosAbiertosSecop.telefonoUtilizable("604.440.9977")).isEqualTo("604 440 9977");
        assertThat(DatosAbiertosSecop.telefonoUtilizable("2120777 ext 1")).isEqualTo("2120777");
        assertThat(DatosAbiertosSecop.telefonoUtilizable("3330333536 Ext. 1000")).isEqualTo("3330333536");
        assertThat(DatosAbiertosSecop.telefonoUtilizable("3747265/6")).isEqualTo("3747265");
        assertThat(DatosAbiertosSecop.telefonoUtilizable("8353513 / 3183309330")).isEqualTo("8353513");
        assertThat(DatosAbiertosSecop.telefonoUtilizable("3102286173.")).isEqualTo("3102286173");
        assertThat(DatosAbiertosSecop.telefonoUtilizable("0")).isNull();
        assertThat(DatosAbiertosSecop.telefonoUtilizable("0000000")).isNull();
        assertThat(DatosAbiertosSecop.telefonoUtilizable("xxxxxx")).isNull();
        assertThat(DatosAbiertosSecop.telefonoUtilizable(null)).isNull();
    }

    @Test
    @DisplayName("un correo sin forma de correo no llega al contrato")
    void elCorreoDelRegistroSeValida() {
        assertThat(DatosAbiertosSecop.correoUtilizable("MAYBESUMINISTROS@GMAIL.COM")).isEqualTo("maybesuministros@gmail.com");
        assertThat(DatosAbiertosSecop.correoUtilizable("jeffergomez2018gmail.com")).isNull();
        assertThat(DatosAbiertosSecop.correoUtilizable("dos palabras@x.co")).isNull();
    }

    /**
     * El registro de proveedores, que es de donde sale el teléfono que el acta
     * no trae (paso 5 de la hoja de Alex). La fila es la que devolvió
     * datos.gov.co el 22-10-2026 para el NIT de Suministros Maybe, recortada.
     */
    @Nested
    @DisplayName("El registro de proveedores")
    class RegistroDeProveedores {

        private static final String MAYBE = """
                {"codigo":"701940041","nombre":"SUMINISTROS MAYBE SAS","nit":"800154801","esta_activa":"Si",
                 "telefono":"4489948","fax":"No Provisto","correo":"MAYBESUMINISTROS@GMAIL.COM",
                 "direccion":"CALLE 78 52D-51, CENTRO COMERCIAL LA TORRE","departamento":"ANTIOQUIA",
                 "municipio":"ITAGUI","sitio_web":"No Provisto","telefono_representante_legal":"No Definido",
                 "correo_representante_legal":"personal@ejemplo.co"}""";

        private final List<String> consultas = new ArrayList<>();
        private HttpServer servidor;
        private DatosAbiertosSecop cliente;

        @BeforeEach
        void levantarUnDatosGovDeJuguete() throws IOException {
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/" + DatosAbiertosSecop.PROVEEDORES + ".json", intercambio -> {
                String consulta = intercambio.getRequestURI().getQuery();
                consultas.add(consulta);
                String cuerpo = consulta != null && consulta.contains("'800154801'") ? "[" + MAYBE + "]" : "[]";
                byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
                intercambio.getResponseHeaders().add("Content-Type", "application/json");
                intercambio.sendResponseHeaders(200, bytes.length);
                intercambio.getResponseBody().write(bytes);
                intercambio.close();
            });
            servidor.start();
            cliente = new DatosAbiertosSecop("http://127.0.0.1:" + servidor.getAddress().getPort(), 5);
        }

        @AfterEach
        void apagarlo() {
            servidor.stop(0);
        }

        @Test
        @DisplayName("trae el teléfono y el correo en minúsculas, y nada de la dirección ni del representante")
        void traeElContactoDeLaEmpresa() {
            var proveedor = cliente.proveedor("800154801-6").orElseThrow();

            // Con el NIT a secas y con el dígito pegado: unas 7.500 filas lo guardan así.
            assertThat(consultas).singleElement().satisfies(c -> assertThat(c)
                    .contains("nit='800154801' OR starts_with(nit,'800154801-')"));
            assertThat(proveedor.telefono()).isEqualTo("4489948");
            assertThat(proveedor.correo()).isEqualTo("maybesuministros@gmail.com");
        }

        @Test
        @DisplayName("un NIT que no está registrado da vacío, y uno mal formado ni se consulta")
        void sinRegistroEsVacio() {
            assertThat(cliente.proveedor("900000001")).isEmpty();
            assertThat(cliente.proveedor("no es un nit")).isEmpty();
            assertThat(consultas).hasSize(1);
        }
    }
}
