package co.sena.sicot.dto.firma;

import java.time.Instant;

public record FirmaResponse(
        Long id,
        Long usuarioId,
        String usuarioNombre,
        String usuarioEmail,
        String firmaId,
        boolean activa,
        Long asignadoPorId,
        String asignadoPorNombre,
        Instant fechaAsignacion
) {
}
