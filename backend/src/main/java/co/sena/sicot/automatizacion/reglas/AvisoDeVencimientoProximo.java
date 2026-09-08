package co.sena.sicot.automatizacion.reglas;

import co.sena.sicot.automatizacion.AutomatizacionProperties;
import co.sena.sicot.automatizacion.FotoDelContrato;
import co.sena.sicot.automatizacion.PayloadDeTarea;
import co.sena.sicot.automatizacion.ReglaDeCalendario;
import co.sena.sicot.automatizacion.TareaSolicitada;
import co.sena.sicot.entity.enums.PrioridadAlerta;
import co.sena.sicot.entity.enums.TipoAlerta;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Avisa cuando al contrato le quedan pocos días de plazo.
 *
 * <h2>Por qué avisa en umbrales y no todos los días</h2>
 * Un aviso diario desde treinta días antes son treinta alertas para un solo
 * hecho. Con los umbrales de {@code sicot.automatizacion.dias-de-aviso-previo}
 * (30, 15 y 7 por defecto) son tres, y cada una llega en un momento en que
 * todavía se puede hacer algo distinto: a treinta días se planifica, a siete se
 * corre.
 *
 * <p>Lo que hace que sean tres y no treinta es la clave de idempotencia, que
 * incluye el umbral y no la fecha: {@code vencimiento-proximo:contrato=7:dias=30}.
 * La primera evaluación que cruza cada umbral crea la alerta; todas las
 * siguientes chocan con la restricción UNIQUE y no producen nada.
 *
 * <h2>El umbral se cruza, no se acierta</h2>
 * La condición es «quedan {@code umbral} días o menos», no «quedan exactamente
 * {@code umbral} días». Con la igualdad, un contrato creado a diez días de
 * vencer no recibiría nunca el aviso de treinta ni el de quince, y un día en que
 * el backend estuviera caído a las 06:00 se saltaría el umbral para siempre. Es
 * la diferencia entre un aviso que depende de que todo salga bien y uno que
 * llega igual.
 */
@Component
public class AvisoDeVencimientoProximo implements ReglaDeCalendario {

    public static final String CODIGO = "vencimiento-proximo";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AutomatizacionProperties propiedades;

    public AvisoDeVencimientoProximo(AutomatizacionProperties propiedades) {
        this.propiedades = propiedades;
    }

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public List<TareaSolicitada> evaluar(FotoDelContrato contrato, LocalDate hoy) {
        if (contrato.fechaFin() == null) {
            // Sin fecha de fin no hay vencimiento que anunciar. Suponer una
            // sería inventar el dato que justifica la alerta.
            return List.of();
        }

        long diasRestantes = ChronoUnit.DAYS.between(hoy, contrato.fechaFin());
        if (diasRestantes < 0) {
            // Ya venció: de eso avisa AvisoDeContratoVencido, y con otra
            // prioridad. Dos reglas avisando del mismo contrato el mismo día
            // serían dos renglones para un solo problema.
            return List.of();
        }

        List<TareaSolicitada> tareas = new ArrayList<>();
        for (int umbral : propiedades.diasDeAvisoPrevio()) {
            if (diasRestantes > umbral) {
                continue;
            }
            tareas.add(TareaSolicitada.ahora(
                    CODIGO,
                    CODIGO + ":contrato=" + contrato.contratoId() + ":dias=" + umbral,
                    contrato.contratoId(),
                    new PayloadDeTarea.CrearAlerta(
                            TipoAlerta.VENCIMIENTO,
                            prioridadPara(umbral),
                            "Al contrato %s le quedan %d día(s) de plazo (vence el %s). Objeto: %s."
                                    .formatted(contrato.numeroContrato(), diasRestantes,
                                            FECHA.format(contrato.fechaFin()), contrato.objeto()))));
        }
        return tareas;
    }

    /**
     * Cuanto menos margen queda, más alto el aviso. Un umbral de una semana o
     * menos ya no admite planificación, así que sube a ALTA.
     */
    private PrioridadAlerta prioridadPara(int umbral) {
        if (umbral <= 7) {
            return PrioridadAlerta.ALTA;
        }
        return umbral <= 15 ? PrioridadAlerta.MEDIA : PrioridadAlerta.BAJA;
    }
}
