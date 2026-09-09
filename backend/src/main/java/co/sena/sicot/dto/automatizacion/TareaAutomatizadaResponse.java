package co.sena.sicot.dto.automatizacion;

import co.sena.sicot.entity.enums.EstadoTareaAutomatizada;
import co.sena.sicot.entity.enums.TipoTareaAutomatizada;

import java.time.Instant;

/**
 * Una tarea de la cola tal como la ve la pantalla de operación.
 *
 * <p><b>No incluye el payload.</b> Ahí van direcciones de correo y el cuerpo de
 * los mensajes, y esta pantalla existe para responder «¿está corriendo el motor
 * y hay algo atascado?», no para leer la correspondencia. Lo que sí incluye es
 * {@code ultimoError}, que es lo único que hace falta para diagnosticar.
 */
public record TareaAutomatizadaResponse(
        Long id,
        String regla,
        TipoTareaAutomatizada tipo,
        EstadoTareaAutomatizada estado,
        Long contratoId,
        int intentos,
        Instant ejecutarEn,
        String ultimoError,
        Instant fechaCreacion,
        Instant fechaActualizacion
) {
}
