package co.sena.sicot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

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
    @DisplayName("sin conexión responde NoDisponible, no una excepción cualquiera")
    void sinConexionEsNoDisponible() {
        // Puerto 9 (discard) cerrado en 127.0.0.1: la conexión se rechaza al instante.
        DatosAbiertosSecop cliente = new DatosAbiertosSecop("http://127.0.0.1:9", 1);

        assertThatThrownBy(() -> cliente.contrato("CO1.PCCNTR.10057820"))
                .isInstanceOf(DatosAbiertosSecop.NoDisponible.class);
    }
}
