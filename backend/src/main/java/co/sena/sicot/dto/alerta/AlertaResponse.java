package co.sena.sicot.dto.alerta;

import co.sena.sicot.entity.enums.PrioridadAlerta;
import co.sena.sicot.entity.enums.TipoAlerta;

import java.time.Instant;

public record AlertaResponse(
        Long id,
        Long contratoId,
        TipoAlerta tipo,
        PrioridadAlerta prioridad,
        String mensaje,
        boolean leida,
        Instant fechaCreacion
) {
}
