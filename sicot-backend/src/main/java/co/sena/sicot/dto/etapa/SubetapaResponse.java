package co.sena.sicot.dto.etapa;

import co.sena.sicot.entity.enums.EstadoSubetapa;

public record SubetapaResponse(
        Long id,
        String codigo,
        String nombre,
        String descripcion,
        EstadoSubetapa estado,
        String responsable
) {
}
