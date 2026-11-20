package co.sena.sicot.automatizacion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El planificador es lo único del motor que decide <b>cuándo</b>, y era la clase
 * menos probada del backend: 3,1 % de líneas cubiertas. Su cuerpo son cuatro
 * métodos de tres líneas, así que es fácil darla por obvia — y precisamente por
 * eso conviene fijar lo que hace, porque de ella depende que el resto del motor
 * llegue a correr.
 *
 * <h2>Lo que estas pruebas protegen de verdad</h2>
 * El {@code try/catch} de cada método. Su comentario explica el porqué con
 * precisión: <i>una excepción que escape de un método {@code @Scheduled}
 * cancela las ejecuciones futuras de ESA tarea en silencio</i>. Es el fallo más
 * difícil de diagnosticar que puede tener este módulo —las automatizaciones
 * «dejan de correr» sin nada en el log salvo una traza de hace días— y hasta
 * ahora ninguna prueba comprobaba que el {@code catch} estuviera ahí. Quitarlo
 * en un refactor no rompía nada visible.
 *
 * <p>Se prueba con dobles y no arrancando la aplicación a propósito: lo que hay
 * que comprobar es que el planificador <b>no deje escapar</b> lo que le lancen
 * sus colaboradores, y para eso hay que poder hacer que lancen.
 */
class PlanificadorDeAutomatizacionesTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private MotorDeAutomatizacion motor;
    private EjecutorDeTareas ejecutor;
    private AlmacenDeTareas almacen;
    private PlanificadorDeAutomatizaciones planificador;

    @BeforeEach
    void prepararDobles() {
        motor = mock(MotorDeAutomatizacion.class);
        ejecutor = mock(EjecutorDeTareas.class);
        almacen = mock(AlmacenDeTareas.class);
        planificador = new PlanificadorDeAutomatizaciones(motor, ejecutor, almacen, propiedades(), relojFijo());
    }

    @Test
    @DisplayName("consumir la cola delega en el ejecutor")
    void consumirDelegaEnElEjecutor() {
        planificador.consumirLaCola();

        verify(ejecutor).procesarPendientes();
    }

    @Test
    @DisplayName("si el ejecutor explota, el sondeo NO muere: una excepción que escape cancelaría las rondas futuras")
    void unFalloConsumiendoNoCancelaElSondeo() {
        when(ejecutor.procesarPendientes()).thenThrow(new IllegalStateException("la base no responde"));

        assertThatCode(() -> planificador.consumirLaCola())
                .as("Spring cancela en silencio las ejecuciones futuras de un @Scheduled que lanza")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el calendario se evalúa con la fecha del reloj del Centro, no con la de la JVM")
    void elCalendarioUsaElRelojInyectado() {
        planificador.evaluarCalendario();

        // La fecha importa: las reglas de vencimiento comparan contra «hoy», y
        // «hoy» en Bogotá no es «hoy» en UTC durante cinco horas de cada día.
        verify(motor).evaluarCalendario(LocalDate.of(2026, 11, 20));
    }

    @Test
    @DisplayName("si una regla de calendario explota, ni se cancela la tarea ni se toca la cola")
    void unFalloEnElCalendarioNoArrastraALaCola() {
        doThrow(new RuntimeException("una regla con un dato inesperado"))
                .when(motor).evaluarCalendario(any());

        assertThatCode(() -> planificador.evaluarCalendario()).doesNotThrowAnyException();
        verify(ejecutor, never()).procesarPendientes();
        verify(almacen, never()).purgarResueltas();
    }

    @Test
    @DisplayName("la purga diaria delega en el almacén y tampoco deja escapar un fallo")
    void laPurgaDelegaYNoPropaga() {
        planificador.purgarLaCola();
        verify(almacen).purgarResueltas();

        doThrow(new RuntimeException("bloqueo en la tabla")).when(almacen).purgarResueltas();
        assertThatCode(() -> planificador.purgarLaCola()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("al arrancar se recupera lo que quedó pendiente del despliegue anterior")
    void alArrancarSeRecuperaLoPendiente() {
        when(ejecutor.procesarPendientes()).thenReturn(3);

        planificador.recuperarAlArrancar();

        verify(ejecutor).procesarPendientes();
    }

    @Test
    @DisplayName("si la recuperación al arrancar falla, el arranque sigue: no puede impedir que el backend suba")
    void unFalloAlArrancarNoTumbaElArranque() {
        when(ejecutor.procesarPendientes()).thenThrow(new RuntimeException("la cola está bloqueada"));

        assertThatCode(() -> planificador.recuperarAlArrancar()).doesNotThrowAnyException();
    }

    // ── Utilidades ──────────────────────────────────────────────────────────

    /** 20 de noviembre de 2026, 08:00 en Bogotá. */
    private static Clock relojFijo() {
        return Clock.fixed(Instant.parse("2026-11-20T13:00:00Z"), BOGOTA);
    }

    private static AutomatizacionProperties propiedades() {
        return new AutomatizacionProperties(true, Duration.ofMinutes(1), 25, 2, 3,
                Duration.ofMinutes(1), Duration.ofMinutes(30), Duration.ofDays(30),
                java.util.List.of(30, 15, 7), new AutomatizacionProperties.Resumen(7));
    }
}
