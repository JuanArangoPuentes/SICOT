package co.sena.sicot.automatizacion;

import co.sena.sicot.automatizacion.reglas.AvisoDeEntregaAcordada;
import co.sena.sicot.entity.enums.EstadoContrato;
import co.sena.sicot.entity.enums.PrioridadAlerta;
import co.sena.sicot.entity.enums.TipoAlerta;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El recordatorio de las entregas acordadas con el contratista (paso 2.4),
 * probado sin Spring ni reloj, como las demás reglas de calendario.
 *
 * <p>Las fechas son las del Lote 4: plazo del 14-10-2026 al 15-12-2026.
 */
class AvisoDeEntregaAcordadaTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 27);

    private final AvisoDeEntregaAcordada regla = new AvisoDeEntregaAcordada();

    @Test
    void sinEntregasAcordadasNoHayNadaQueRecordar() {
        assertThat(regla.evaluar(contrato(List.of()), HOY)).isEmpty();
    }

    @Test
    void tresDiasAntesAvisaParaPrepararseYAvisarASstYAmbiental() {
        List<TareaSolicitada> tareas = regla.evaluar(contrato(List.of(
                entrega(1, HOY.plusDays(3), "Ítems 1 a 20 del pedido"))), HOY);

        assertThat(tareas).hasSize(1);
        TareaSolicitada t = tareas.getFirst();
        assertThat(t.claveIdempotencia()).isEqualTo("entrega-acordada:contrato=1:fecha=2026-10-30:dias=3");
        PayloadDeTarea.CrearAlerta alerta = (PayloadDeTarea.CrearAlerta) t.payload();
        assertThat(alerta.tipoAlerta()).isEqualTo(TipoAlerta.RECORDATORIO);
        assertThat(alerta.prioridad()).isEqualTo(PrioridadAlerta.MEDIA);
        assertThat(alerta.mensaje())
                .startsWith("El 30/10/2026 (en 3 días) es la entrega acordada n.º 1 del contrato CO1.PCCNTR.10057820")
                .contains("«Ítems 1 a 20 del pedido»")
                .contains("SST y ambiental");
    }

    /**
     * El umbral se cruza, no se acierta: una entrega acordada para mañana
     * recibe el aviso de anticipación hoy, aunque no falten exactamente tres
     * días. Y la clave es la misma, así que no se repite al día siguiente.
     */
    @Test
    void unaEntregaAcordadaParaMananaRecibeIgualElAvisoDeAnticipacion() {
        List<TareaSolicitada> tareas = regla.evaluar(contrato(List.of(
                entrega(1, HOY.plusDays(1), "Entrega total"))), HOY);

        assertThat(tareas).singleElement()
                .satisfies(t -> {
                    assertThat(t.claveIdempotencia()).endsWith(":dias=3");
                    assertThat(((PayloadDeTarea.CrearAlerta) t.payload()).mensaje()).startsWith("Mañana (28/10/2026)");
                });
    }

    @Test
    void elDiaDeLaEntregaSaleUnSoloAvisoDePrioridadAlta() {
        List<TareaSolicitada> tareas = regla.evaluar(contrato(List.of(entrega(2, HOY, "Entrega total"))), HOY);

        assertThat(tareas).singleElement().satisfies(t -> {
            assertThat(t.claveIdempotencia()).isEqualTo("entrega-acordada:contrato=1:fecha=2026-10-27:dia");
            PayloadDeTarea.CrearAlerta alerta = (PayloadDeTarea.CrearAlerta) t.payload();
            assertThat(alerta.prioridad()).isEqualTo(PrioridadAlerta.ALTA);
            assertThat(alerta.mensaje()).startsWith("Hoy (27/10/2026) es la entrega acordada n.º 2");
        });
    }

    @Test
    void unaEntregaPasadaOLejanaNoAvisa() {
        assertThat(regla.evaluar(contrato(List.of(
                entrega(1, HOY.minusDays(1), "Ya pasó"),
                entrega(2, HOY.plusDays(4), "Todavía lejos"))), HOY)).isEmpty();
    }

    @Test
    void dosEntregasElMismoDiaVanEnUnSoloAviso() {
        List<TareaSolicitada> tareas = regla.evaluar(contrato(List.of(
                entrega(2, HOY.plusDays(2), "Mobiliario"),
                entrega(3, HOY.plusDays(2), "Herramientas"))), HOY);

        assertThat(tareas).singleElement().satisfies(t -> assertThat(((PayloadDeTarea.CrearAlerta) t.payload())
                .mensaje()).contains("las entregas acordadas n.º 2 y 3").contains("«Mobiliario»; «Herramientas»"));
    }

    @Test
    void unaDescripcionLargaSeCitaRecortada() {
        String larga = "Mouse óptico alámbrico ".repeat(20);
        List<TareaSolicitada> tareas = regla.evaluar(contrato(List.of(entrega(1, HOY.plusDays(2), larga))), HOY);

        String mensaje = ((PayloadDeTarea.CrearAlerta) tareas.getFirst().payload()).mensaje();
        assertThat(mensaje).contains("…»").hasSizeLessThan(400);
    }

    private static FotoDelContrato.Entrega entrega(int numero, LocalDate fecha, String descripcion) {
        return new FotoDelContrato.Entrega(numero, fecha, descripcion);
    }

    private static FotoDelContrato contrato(List<FotoDelContrato.Entrega> entregas) {
        return new FotoDelContrato(1L, "CO1.PCCNTR.10057820", "Suministro de materiales para la formación",
                LocalDate.of(2026, 10, 14), LocalDate.of(2026, 12, 15), EstadoContrato.ACTIVO,
                "Alex Fernando Zapata Ríos", "afzapatar@sena.edu.co", 21, 6, 2, 6, entregas);
    }
}
