package co.sena.sicot.automatizacion.reglas;

import co.sena.sicot.automatizacion.EventoDeNegocio;
import co.sena.sicot.automatizacion.FotoDelContrato;
import co.sena.sicot.automatizacion.LectorDeContratos;
import co.sena.sicot.automatizacion.PayloadDeTarea;
import co.sena.sicot.automatizacion.ReglaDeEvento;
import co.sena.sicot.automatizacion.TareaSolicitada;
import co.sena.sicot.entity.enums.PrioridadAlerta;
import co.sena.sicot.entity.enums.TipoAlerta;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Avisa al supervisor de que le asignaron un contrato.
 *
 * <h2>Por qué esta regla existe</h2>
 * Hoy, cuando GESTIÓN asigna un supervisor, la asignación queda en la base y en
 * la auditoría — y ahí se acaba. La persona se entera la próxima vez que entra a
 * SICOT y mira, o cuando alguien se lo dice por otro canal. En un proceso donde
 * el reloj del contrato ya está corriendo, «se entera cuando entre» es una
 * ventana de días.
 *
 * <h2>Dos efectos, dos tareas</h2>
 * La alerta en el panel y el correo se encolan por separado a propósito. Son
 * fallos independientes: si el servidor de correo está caído, la alerta ya está
 * puesta y visible; si no hay dirección, el correo se descarta sin afectar a la
 * alerta. Una sola tarea que hiciera las dos cosas obligaría a repetir la
 * primera al reintentar la segunda, y el supervisor acabaría con cinco alertas
 * idénticas por un problema de SMTP.
 */
@Component
public class NotificacionDeSupervisorAsignado implements ReglaDeEvento {

    public static final String CODIGO = "supervisor-asignado";

    /** El mismo código que escribe {@code ContratoService} en la auditoría. */
    private static final String ACCION = "SUPERVISOR_ASIGNADO";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LectorDeContratos lectorDeContratos;

    public NotificacionDeSupervisorAsignado(LectorDeContratos lectorDeContratos) {
        this.lectorDeContratos = lectorDeContratos;
    }

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public boolean aplicaA(EventoDeNegocio evento) {
        return evento.esAccion(ACCION) && evento.tieneContrato();
    }

    @Override
    public List<TareaSolicitada> evaluar(EventoDeNegocio evento) {
        Optional<FotoDelContrato> encontrado = lectorDeContratos.porId(evento.contratoId());
        if (encontrado.isEmpty()) {
            return List.of();
        }
        FotoDelContrato contrato = encontrado.get();
        if (contrato.supervisorNombre() == null) {
            // La acción quedó registrada pero el contrato ya no tiene supervisor:
            // pasa si se asignó y se retiró en la misma sesión. No hay a quién avisar.
            return List.of();
        }

        // La clave incluye el supervisor, no la fecha: reasignar el contrato a
        // otra persona SÍ debe avisar de nuevo — es un hecho distinto—, mientras
        // que reprocesar el mismo evento no.
        String sufijo = ":contrato=" + contrato.contratoId() + ":supervisor=" + contrato.supervisorEmail();

        String cuerpo = ("Hola %s,\n\n"
                + "Se le asignó la supervisión del contrato %s en SICOT.\n\n"
                + "Objeto: %s\n"
                + "Plazo: %s a %s\n\n"
                + "Ingrese a SICOT para revisar las etapas del procedimiento GCCON-P-010 y el estado actual "
                + "del contrato.\n\n"
                + "— SICOT · Centro Tecnológico del Mobiliario (SENA)")
                .formatted(contrato.supervisorNombre(), contrato.numeroContrato(), contrato.objeto(),
                        contrato.fechaInicio() == null ? "sin definir" : FECHA.format(contrato.fechaInicio()),
                        contrato.fechaFin() == null ? "sin definir" : FECHA.format(contrato.fechaFin()));

        List<TareaSolicitada> tareas = new java.util.ArrayList<>();
        tareas.add(TareaSolicitada.ahora(
                CODIGO,
                CODIGO + ":alerta" + sufijo,
                contrato.contratoId(),
                new PayloadDeTarea.CrearAlerta(
                        TipoAlerta.SOLICITUD,
                        PrioridadAlerta.MEDIA,
                        "Se le asignó la supervisión del contrato %s. Objeto: %s."
                                .formatted(contrato.numeroContrato(), contrato.objeto()))));

        if (contrato.tieneSupervisorConCorreo()) {
            tareas.add(TareaSolicitada.ahora(
                    CODIGO,
                    CODIGO + ":correo" + sufijo,
                    contrato.contratoId(),
                    new PayloadDeTarea.EnviarCorreo(
                            contrato.supervisorEmail(),
                            "SICOT — Se le asignó la supervisión del contrato " + contrato.numeroContrato(),
                            cuerpo)));
        }
        return tareas;
    }
}
