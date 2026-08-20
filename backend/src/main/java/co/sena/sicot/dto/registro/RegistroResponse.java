package co.sena.sicot.dto.registro;

import java.time.Instant;

public record RegistroResponse(
        Long id,
        Long contratoId,
        Long usuarioId,
        String usuarioNombre,
        String accion,
        String descripcion,
        Instant fecha
) {
}
