package co.sena.sicot.automatizacion.reglas;

import co.sena.sicot.automatizacion.AutomatizacionProperties;
import co.sena.sicot.automatizacion.FotoDelContrato;
import co.sena.sicot.automatizacion.PayloadDeTarea;
import co.sena.sicot.automatizacion.ReglaDeCalendario;
import co.sena.sicot.automatizacion.TareaSolicitada;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;

/**
 * Pide un resumen del periodo para el supervisor del contrato.
 *
 * <h2>Por qué ya no está apagada por defecto</h2>
 * Se llamaba {@code ResumenSemanalConIa} y era la única regla del módulo que
 * consumía el modelo local, así que nacía apagada: encender la generación
 * automática de prosa en un sistema de contratación pública es una decisión de
 * quien despliega, no un valor por omisión que aparezca tras un {@code git
 * pull}.
 *
 * <p>El resumen ya no lo escribe el modelo. Las mediciones que llevaron a
 * quitarlo están en el encabezado de
 * {@code V16__resumen_semanal_sin_modelo.sql}; el resultado corto es que
 * ninguno de los tres tamaños de modelo probados sostenía los hechos sin
 * alterarlos, y que los hechos ya venían calculados en Java antes de llamarlo.
 * Ahora el texto se compone con plantillas, es correcto por construcción y no
 * depende de nada externo — igual que las otras cinco reglas. Por eso el
 * interruptor {@code sicot.automatizacion.ia.habilitado} desapareció en vez de
 * quedarse en {@code true}: ya no hay una decisión que tomar.
 *
 * <h2>Por qué es semanal sin tener su propio temporizador</h2>
 * Se evalúa en la pasada diaria como todas, pero su clave de idempotencia
 * incluye la semana ISO ({@code 2026-W37}). La primera evaluación de cada
 * semana encola la tarea; las seis siguientes chocan con la restricción UNIQUE
 * y no producen nada.
 *
 * <p>Un segundo {@code @Scheduled} semanal habría dado el mismo resultado y una
 * cosa más que mantener sincronizada. Además, este mecanismo se recupera solo:
 * si el backend estuvo caído el lunes, el resumen sale el martes en vez de
 * perderse esa semana.
 *
 * <h2>Qué NO decide esta regla</h2>
 * Nada del contenido. Solo dice «toca resumir este contrato». Los hechos los
 * lee {@code RedaccionDeResumen} en el momento de ejecutar.
 */
@Component
public class ResumenSemanal implements ReglaDeCalendario {

    public static final String CODIGO = "resumen-semanal";

    private final AutomatizacionProperties propiedades;

    public ResumenSemanal(AutomatizacionProperties propiedades) {
        this.propiedades = propiedades;
    }

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public List<TareaSolicitada> evaluar(FotoDelContrato contrato, LocalDate hoy) {
        if (contrato.supervisorNombre() == null) {
            // Sin supervisor no hay quien lea el resumen; producirlo sería
            // trabajo para nadie y un renglón más en una bandeja que nadie mira.
            return List.of();
        }

        // WeekFields.ISO y no el del locale de la máquina: con el predeterminado,
        // el mismo día caería en semanas distintas según dónde corra el
        // servidor, y la clave de idempotencia dejaría de ser estable.
        WeekFields iso = WeekFields.ISO;
        String semana = "%d-W%02d".formatted(hoy.get(iso.weekBasedYear()), hoy.get(iso.weekOfWeekBasedYear()));

        return List.of(TareaSolicitada.ahora(
                CODIGO,
                CODIGO + ":contrato=" + contrato.contratoId() + ":semana=" + semana,
                contrato.contratoId(),
                new PayloadDeTarea.RedactarResumen(propiedades.resumen().diasDelPeriodo())));
    }
}
