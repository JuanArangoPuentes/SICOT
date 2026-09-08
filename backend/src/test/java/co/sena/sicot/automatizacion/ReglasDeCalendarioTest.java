package co.sena.sicot.automatizacion;

import co.sena.sicot.automatizacion.reglas.AvisoDeContratoVencido;
import co.sena.sicot.automatizacion.reglas.AvisoDeCronogramaAtrasado;
import co.sena.sicot.automatizacion.reglas.AvisoDeVencimientoProximo;
import co.sena.sicot.entity.enums.EstadoContrato;
import co.sena.sicot.entity.enums.PrioridadAlerta;
import co.sena.sicot.entity.enums.TipoAlerta;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las reglas de calendario, probadas sin Spring, sin base de datos y sin reloj.
 *
 * <p>Que esto sea posible es el motivo de que las reglas devuelvan
 * {@link TareaSolicitada} en vez de producir efectos, y de que reciban una
 * {@link FotoDelContrato} en vez de la entidad. Una regla que creara la alerta
 * por su cuenta exigiría levantar el contexto entero para comprobar una
 * comparación de fechas; una que leyera {@code LocalDate.now()} solo se podría
 * probar el día correcto.
 */
class ReglasDeCalendarioTest {

    private static final LocalDate HOY = LocalDate.of(2026, 3, 15);

    private final AutomatizacionProperties propiedades = new AutomatizacionProperties(
            true, Duration.ofMinutes(1), 25, 2, 5,
            Duration.ofMinutes(1), Duration.ofMinutes(30), Duration.ofDays(30),
            List.of(30, 15, 7),
            new AutomatizacionProperties.Ia(false, 7));

    // ── AvisoDeVencimientoProximo ──────────────────────────────────────────

    @Test
    void avisaUnaVezPorUmbralCruzadoYNoUnaPorDia() {
        AvisoDeVencimientoProximo regla = new AvisoDeVencimientoProximo(propiedades);

        // Quedan 10 días: se cruzaron ya los umbrales de 30 y de 15, pero no el de 7.
        List<TareaSolicitada> tareas = regla.evaluar(contrato(HOY.minusDays(80), HOY.plusDays(10)), HOY);

        assertThat(tareas).hasSize(2);
        assertThat(tareas).extracting(TareaSolicitada::claveIdempotencia)
                .containsExactlyInAnyOrder(
                        "vencimiento-proximo:contrato=1:dias=30",
                        "vencimiento-proximo:contrato=1:dias=15");
    }

    /**
     * El umbral se cruza, no se acierta. Con una comparación de igualdad, un
     * contrato creado a diez días de vencer no recibiría nunca los avisos de 30
     * ni de 15, y un día con el backend caído a las 06:00 perdería ese umbral
     * para siempre.
     */
    @Test
    void unContratoCreadoDentroDelUmbralRecibeIgualLosAvisosAnteriores() {
        AvisoDeVencimientoProximo regla = new AvisoDeVencimientoProximo(propiedades);

        List<TareaSolicitada> tareas = regla.evaluar(contrato(HOY, HOY.plusDays(3)), HOY);

        assertThat(tareas).hasSize(3);
    }

    @Test
    void laPrioridadSubeAMedidaQueSeAgotaElPlazo() {
        AvisoDeVencimientoProximo regla = new AvisoDeVencimientoProximo(propiedades);

        List<TareaSolicitada> tareas = regla.evaluar(contrato(HOY.minusDays(80), HOY.plusDays(5)), HOY);

        assertThat(tareas).extracting(t -> ((PayloadDeTarea.CrearAlerta) t.payload()).prioridad())
                .containsExactlyInAnyOrder(PrioridadAlerta.BAJA, PrioridadAlerta.MEDIA, PrioridadAlerta.ALTA);
    }

    @Test
    void sinFechaDeFinNoInventaUnVencimiento() {
        AvisoDeVencimientoProximo regla = new AvisoDeVencimientoProximo(propiedades);

        assertThat(regla.evaluar(contrato(HOY.minusDays(10), null), HOY)).isEmpty();
    }

    /** Del contrato ya vencido avisa la otra regla, y con otra prioridad. */
    @Test
    void unContratoYaVencidoNoGeneraAvisoDeProximidad() {
        AvisoDeVencimientoProximo regla = new AvisoDeVencimientoProximo(propiedades);

        assertThat(regla.evaluar(contrato(HOY.minusDays(100), HOY.minusDays(1)), HOY)).isEmpty();
    }

    // ── AvisoDeContratoVencido ─────────────────────────────────────────────

    @Test
    void avisaUnaSolaVezDeUnContratoVencidoYSinCerrar() {
        AvisoDeContratoVencido regla = new AvisoDeContratoVencido();

        List<TareaSolicitada> tareas = regla.evaluar(contrato(HOY.minusDays(100), HOY.minusDays(20)), HOY);

        assertThat(tareas).hasSize(1);
        assertThat(tareas.getFirst().claveIdempotencia()).isEqualTo("contrato-vencido:contrato=1");
        PayloadDeTarea.CrearAlerta alerta = (PayloadDeTarea.CrearAlerta) tareas.getFirst().payload();
        assertThat(alerta.prioridad()).isEqualTo(PrioridadAlerta.ALTA);
        assertThat(alerta.tipoAlerta()).isEqualTo(TipoAlerta.VENCIMIENTO);
        // El sistema señala; no cambia el estado por su cuenta. Ver ADR-008.
        assertThat(alerta.mensaje()).contains("SICOT no cambia el estado por su cuenta");
    }

    @Test
    void noAvisaDeUnContratoQueVenceHoy() {
        AvisoDeContratoVencido regla = new AvisoDeContratoVencido();

        assertThat(regla.evaluar(contrato(HOY.minusDays(100), HOY), HOY)).isEmpty();
    }

    // ── AvisoDeCronogramaAtrasado ──────────────────────────────────────────

    @Test
    void avisaCuandoLaBrechaEntrePlazoYAvanceSuperaElUmbral() {
        AvisoDeCronogramaAtrasado regla = new AvisoDeCronogramaAtrasado();

        // 50 % del plazo consumido, 3 de 27 subetapas cerradas (11 %): brecha de 39 puntos.
        FotoDelContrato contrato = new FotoDelContrato(1L, "CT-001", "Suministro",
                HOY.minusDays(50), HOY.plusDays(50), EstadoContrato.ACTIVO,
                "Ana", "ana@soy.sena.edu.co", 27, 3, 2, 6);

        List<TareaSolicitada> tareas = regla.evaluar(contrato, HOY);

        assertThat(tareas).hasSize(1);
        assertThat(tareas.getFirst().claveIdempotencia()).isEqualTo("cronograma-atrasado:contrato=1:tramo=30");
    }

    @Test
    void noAvisaCuandoElAvanceAcompanaAlPlazo() {
        AvisoDeCronogramaAtrasado regla = new AvisoDeCronogramaAtrasado();

        FotoDelContrato contrato = new FotoDelContrato(1L, "CT-001", "Suministro",
                HOY.minusDays(50), HOY.plusDays(50), EstadoContrato.ACTIVO,
                "Ana", "ana@soy.sena.edu.co", 27, 13, 3, 6);

        assertThat(regla.evaluar(contrato, HOY)).isEmpty();
    }

    /**
     * Un contrato sin subetapas sembradas no permite afirmar nada sobre el
     * avance. Callar es la respuesta correcta: alertar con un dato inventado
     * («0 % de avance») sería peor que no alertar.
     */
    @Test
    void sinSubetapasSembradasNoAfirmaNadaSobreElAvance() {
        AvisoDeCronogramaAtrasado regla = new AvisoDeCronogramaAtrasado();

        FotoDelContrato contrato = new FotoDelContrato(1L, "CT-001", "Suministro",
                HOY.minusDays(90), HOY.plusDays(10), EstadoContrato.ACTIVO,
                "Ana", "ana@soy.sena.edu.co", 0, 0, 1, 6);

        assertThat(regla.evaluar(contrato, HOY)).isEmpty();
    }

    /** Un atraso que empeora es información nueva y merece un segundo aviso. */
    @Test
    void unAtrasoQueEmpeoraCambiaDeTramoYVuelveAAvisar() {
        AvisoDeCronogramaAtrasado regla = new AvisoDeCronogramaAtrasado();

        FotoDelContrato moderado = new FotoDelContrato(1L, "CT-001", "Suministro",
                HOY.minusDays(50), HOY.plusDays(50), EstadoContrato.ACTIVO, "Ana", "a@b.co", 27, 3, 2, 6);
        FotoDelContrato grave = new FotoDelContrato(1L, "CT-001", "Suministro",
                HOY.minusDays(90), HOY.plusDays(10), EstadoContrato.ACTIVO, "Ana", "a@b.co", 27, 3, 2, 6);

        assertThat(regla.evaluar(moderado, HOY).getFirst().claveIdempotencia())
                .isNotEqualTo(regla.evaluar(grave, HOY).getFirst().claveIdempotencia());
    }

    private FotoDelContrato contrato(LocalDate inicio, LocalDate fin) {
        return new FotoDelContrato(1L, "CT-001", "Suministro de mobiliario",
                inicio, fin, EstadoContrato.ACTIVO, "Ana Gómez", "ana@soy.sena.edu.co", 27, 5, 2, 6);
    }
}
