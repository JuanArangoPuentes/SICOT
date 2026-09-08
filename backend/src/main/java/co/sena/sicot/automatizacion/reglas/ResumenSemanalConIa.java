package co.sena.sicot.automatizacion.reglas;

import co.sena.sicot.automatizacion.AutomatizacionProperties;
import co.sena.sicot.automatizacion.FotoDelContrato;
import co.sena.sicot.automatizacion.PayloadDeTarea;
import co.sena.sicot.automatizacion.ReglaDeCalendario;
import co.sena.sicot.automatizacion.TareaSolicitada;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;

/**
 * Pide un resumen del periodo, redactado por el modelo local, para el supervisor
 * del contrato.
 *
 * <h2>Por qué está apagada por defecto</h2>
 * Es la única regla del módulo que produce texto generado, y la única que
 * consume el modelo. Encender la generación automática de prosa en un sistema de
 * contratación pública es una decisión de quien despliega, no un valor por
 * omisión que aparezca solo tras un {@code git pull}. Se activa con
 * {@code sicot.automatizacion.ia.habilitado=true}.
 *
 * <p>Con {@code @ConditionalOnProperty}, apagada significa que el bean ni
 * siquiera existe: no hay ninguna ruta por la que pueda ejecutarse por
 * accidente, y no depende de un {@code if} que alguien pueda mover.
 *
 * <h2>Por qué es semanal sin tener su propio temporizador</h2>
 * Se evalúa en la pasada diaria como todas, pero su clave de idempotencia
 * incluye la semana ISO ({@code 2026-W37}). La primera evaluación de cada semana
 * encola la tarea; las seis siguientes chocan con la restricción UNIQUE y no
 * producen nada.
 *
 * <p>Un segundo {@code @Scheduled} semanal habría dado el mismo resultado y una
 * cosa más que mantener sincronizada. Además, este mecanismo se recupera solo:
 * si el backend estuvo caído el lunes, el resumen sale el martes en vez de
 * perderse esa semana.
 *
 * <h2>Qué NO decide esta regla</h2>
 * Nada del contenido. Solo dice «toca resumir este contrato». Los hechos los
 * lee {@code RedaccionDeResumenIa} en el momento de ejecutar, y el modelo solo
 * los redacta. Ver la segunda regla invariante de ADR-008.
 */
@Component
@ConditionalOnProperty(name = "sicot.automatizacion.ia.habilitado", havingValue = "true")
public class ResumenSemanalConIa implements ReglaDeCalendario {

    public static final String CODIGO = "resumen-semanal-ia";

    private final AutomatizacionProperties propiedades;

    public ResumenSemanalConIa(AutomatizacionProperties propiedades) {
        this.propiedades = propiedades;
    }

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public List<TareaSolicitada> evaluar(FotoDelContrato contrato, LocalDate hoy) {
        if (contrato.supervisorNombre() == null) {
            // Sin supervisor no hay quien lea el resumen; gastar un cupo del
            // modelo en generarlo sería trabajo para nadie.
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
                new PayloadDeTarea.RedactarResumenIa(propiedades.ia().diasDelPeriodo())));
    }
}
