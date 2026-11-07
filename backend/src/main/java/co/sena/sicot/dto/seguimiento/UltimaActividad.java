package co.sena.sicot.dto.seguimiento;

import java.time.Instant;

/** El registro de auditoría más reciente de un contrato. */
public record UltimaActividad(
        Long contratoId,
        Instant fecha,
        String accion,
        String descripcion
) {
}
