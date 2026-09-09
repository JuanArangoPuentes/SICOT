package co.sena.sicot.automatizacion.acciones;

import co.sena.sicot.automatizacion.AccionDeTarea;
import co.sena.sicot.automatizacion.PayloadJson;
import co.sena.sicot.automatizacion.LectorDeContratos;
import co.sena.sicot.automatizacion.PayloadDeTarea;
import co.sena.sicot.automatizacion.ResultadoDeAccion;
import co.sena.sicot.automatizacion.TareaEnEjecucion;
import co.sena.sicot.entity.enums.TipoTareaAutomatizada;
import co.sena.sicot.service.AlertaService;
import org.springframework.stereotype.Component;

/**
 * Persiste la alerta que pidió una regla.
 *
 * <p>Es el efecto que llena el hueco que motivó todo el módulo: hasta ADR-008,
 * la tabla {@code alertas} existía con sus diez tipos y nadie insertaba nunca
 * una fila.
 *
 * <p>Pasa por {@code AlertaService} y no por el repositorio, cumpliendo la
 * primera regla invariante del módulo: ninguna automatización escribe a la base
 * por su cuenta. Hoy la diferencia parece cosmética —una alerta no tiene reglas
 * de negocio complejas— pero es lo que garantiza que, el día que se añada
 * validación o auditoría al crear alertas, las automatizaciones queden cubiertas
 * sin que nadie tenga que acordarse de ellas.
 */
@Component
public class CreacionDeAlerta implements AccionDeTarea {

    private final AlertaService alertaService;
    private final LectorDeContratos lectorDeContratos;
    private final PayloadJson payloadJson;

    public CreacionDeAlerta(AlertaService alertaService,
                            LectorDeContratos lectorDeContratos,
                            PayloadJson payloadJson) {
        this.alertaService = alertaService;
        this.lectorDeContratos = lectorDeContratos;
        this.payloadJson = payloadJson;
    }

    @Override
    public TipoTareaAutomatizada tipo() {
        return TipoTareaAutomatizada.CREAR_ALERTA;
    }

    @Override
    public ResultadoDeAccion ejecutar(TareaEnEjecucion tarea) {
        PayloadDeTarea.CrearAlerta datos =
                payloadJson.leer(tarea.payload(), PayloadDeTarea.CrearAlerta.class);

        // Un contrato borrado entre encolar y ejecutar no es un fallo del
        // sistema: es una alerta que ya no tiene destinatario. Sin esta
        // comprobación, la clave foránea reventaría cinco veces seguidas hasta
        // dejar la tarea FALLIDA, y alguien saldría a buscar un problema
        // inexistente.
        if (tarea.contratoId() != null && lectorDeContratos.porId(tarea.contratoId()).isEmpty()) {
            return ResultadoDeAccion.descartada("El contrato ya no existe.");
        }

        alertaService.crearDelSistema(
                tarea.contratoId(), datos.tipoAlerta(), datos.prioridad(), datos.mensaje());
        return ResultadoDeAccion.hecha();
    }
}
