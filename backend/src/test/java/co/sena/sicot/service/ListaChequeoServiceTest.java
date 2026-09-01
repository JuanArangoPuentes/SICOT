package co.sena.sicot.service;

import co.sena.sicot.dto.chequeo.EtapaChequeo;
import co.sena.sicot.dto.chequeo.ItemChequeo;
import co.sena.sicot.dto.chequeo.ListaChequeoDetalle;
import co.sena.sicot.dto.chequeo.ListaChequeoResumen;
import co.sena.sicot.dto.chequeo.TipoListaChequeo;
import co.sena.sicot.dto.chequeo.TipoPagoChequeo;
import co.sena.sicot.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica el catálogo real de {@code src/main/resources/listas-chequeo/} — no datos de prueba.
 * Si alguien regenera los JSON desde un .xlsx nuevo y la transcripción se rompe, estas pruebas
 * fallan antes de que el catálogo llegue a un usuario.
 */
class ListaChequeoServiceTest {

    private final ListaChequeoService service = new ListaChequeoService();

    @Test
    void catalogoTraeLasOchoListasOficialesOrdenadasPorCodigo() {
        List<ListaChequeoResumen> listas = service.listar(null);

        assertThat(listas).extracting(ListaChequeoResumen::codigo).containsExactly(
                "GCCON-F-026", "GCCON-F-049", "GCCON-F-051", "GCCON-F-052",
                "GCCON-F-053", "GCCON-F-055", "GCCON-F-056", "GRF-F-088");
    }

    @Test
    void filtraPorTipoDeTramite() {
        assertThat(service.listar(TipoListaChequeo.TRAMITE_PAGO))
                .extracting(ListaChequeoResumen::codigo)
                .containsExactly("GRF-F-088");
        assertThat(service.listar(TipoListaChequeo.TRAMITE_CONTRACTUAL))
                .extracting(ListaChequeoResumen::codigo)
                .containsExactly("GCCON-F-052");
        assertThat(service.listar(TipoListaChequeo.MODALIDAD_SELECCION)).hasSize(6);
    }

    @Test
    void elResumenCuadraConElDetalle() {
        for (ListaChequeoResumen resumen : service.listar(null)) {
            ListaChequeoDetalle detalle = service.obtener(resumen.codigo());
            assertThat(resumen.totalEtapas()).isEqualTo(detalle.etapas().size());
            assertThat(resumen.totalItems()).isEqualTo(detalle.totalItems());
            assertThat(resumen.version()).isEqualTo(detalle.version());
        }
    }

    @Test
    void minimaCuantiaConservaSusCuatroEtapasYElTextoDelFormato() {
        ListaChequeoDetalle f053 = service.obtener("GCCON-F-053");

        assertThat(f053.nombre()).isEqualTo("LISTA DE CHEQUEO MÍNIMA CUANTÍA");
        assertThat(f053.version()).isEqualTo("05");
        assertThat(f053.tipo()).isEqualTo(TipoListaChequeo.MODALIDAD_SELECCION);
        assertThat(f053.etapas()).extracting(EtapaChequeo::nombre).containsExactly(
                "ETAPA PRECONTRACTUAL", "ETAPA CONTRACTUAL",
                "ETAPA DE EJECUCIÓN", "ETAPA POSCONTRACTUAL");

        ItemChequeo estudiosPrevios = itemNumero(f053, 3);
        assertThat(estudiosPrevios.documento()).isEqualTo("Estudios previos con sus Anexos");
        assertThat(estudiosPrevios.cuandoAplique()).isFalse();
        assertThat(estudiosPrevios.formatos()).containsExactly("GCCON-F-046");
    }

    @Test
    void laMarcaAsteriscoDelFormatoQuedaComoCuandoAplique() {
        ItemChequeo designacion = itemNumero(service.obtener("GCCON-F-053"), 1);

        // En el .xlsx el ítem 1 se llama "Designación Comité Estructurador*": el asterisco es
        // la marca de "cuando aplique", no parte del nombre del documento.
        assertThat(designacion.documento()).isEqualTo("Designación Comité Estructurador");
        assertThat(designacion.cuandoAplique()).isTrue();
    }

    @Test
    void lasInconsistenciasDelFormatoOriginalQuedanComoAdvertenciaNoCorregidas() {
        // GCCON-F-053 numera dos ítems distintos con el número 58.
        assertThat(service.obtener("GCCON-F-053").advertencias())
                .anyMatch(a -> a.contains("58"));
        // GCCON-F-055 no rotula su primera etapa en el archivo original.
        assertThat(service.obtener("GCCON-F-055").etapas().getFirst().rotuladaEnOrigen()).isFalse();
        // GCCON-F-051, -F-052, -F-055 y -F-056 traen en el nombre del archivo una versión
        // distinta a la del encabezado de la hoja.
        assertThat(service.obtener("GCCON-F-052").advertencias())
                .anyMatch(a -> a.contains("versión"));
    }

    @Test
    void laListaDePagoDeclaraSusTiposYCadaItemReferenciaSoloTiposDeclarados() {
        ListaChequeoDetalle grf088 = service.obtener("GRF-F-088");

        assertThat(grf088.tipo()).isEqualTo(TipoListaChequeo.TRAMITE_PAGO);
        assertThat(grf088.proceso()).contains("RECURSOS FINANCIEROS");
        assertThat(grf088.tiposPago()).hasSize(11);

        Set<String> declarados = grf088.tiposPago().stream()
                .map(TipoPagoChequeo::codigo)
                .collect(Collectors.toUnmodifiableSet());
        for (ItemChequeo item : grf088.etapas().getFirst().items()) {
            assertThat(item.tiposPago())
                    .as("ítem %d de GRF-F-088", item.numero())
                    .isNotEmpty()
                    .allMatch(declarados::contains);
        }

        assertThat(itemNumero(grf088, 1).documento()).isEqualTo("Registro presupuestal del compromiso");
        assertThat(itemNumero(grf088, 1).tiposPago()).contains("ADQUISICION_BIENES", "CONVENIOS");
    }

    @Test
    void soloLaListaDePagoUsaTiposDePago() {
        for (ListaChequeoResumen resumen : service.listar(null)) {
            if (resumen.tipo() == TipoListaChequeo.TRAMITE_PAGO) {
                continue;
            }
            ListaChequeoDetalle detalle = service.obtener(resumen.codigo());
            assertThat(detalle.tiposPago()).as("tiposPago de %s", resumen.codigo()).isEmpty();
            assertThat(detalle.etapas())
                    .flatExtracting(EtapaChequeo::items)
                    .allMatch(item -> item.tiposPago().isEmpty());
        }
    }

    @Test
    void ningunItemDelCatalogoLlegaVacio() {
        for (ListaChequeoResumen resumen : service.listar(null)) {
            ListaChequeoDetalle detalle = service.obtener(resumen.codigo());
            assertThat(detalle.etapas()).as("etapas de %s", resumen.codigo()).isNotEmpty();
            for (EtapaChequeo etapa : detalle.etapas()) {
                assertThat(etapa.nombre()).isNotBlank();
                assertThat(etapa.items()).as("ítems de %s / %s", resumen.codigo(), etapa.nombre())
                        .isNotEmpty();
                for (ItemChequeo item : etapa.items()) {
                    assertThat(item.numero()).isPositive();
                    assertThat(item.documento()).isNotBlank();
                    assertThat(item.documento()).doesNotEndWith("*");
                }
            }
        }
    }

    @Test
    void elCodigoSeResuelveSinDistinguirMayusculasNiEspacios() {
        assertThat(service.obtener("  gccon-f-026  ").codigo()).isEqualTo("GCCON-F-026");
    }

    @Test
    void unCodigoDesconocidoNoSeInventa() {
        assertThatThrownBy(() -> service.obtener("GCCON-F-999"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("GCCON-F-999");
    }

    private static ItemChequeo itemNumero(ListaChequeoDetalle lista, int numero) {
        return lista.etapas().stream()
                .flatMap(etapa -> etapa.items().stream())
                .filter(item -> item.numero() == numero)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No existe el ítem " + numero + " en " + lista.codigo()));
    }
}
