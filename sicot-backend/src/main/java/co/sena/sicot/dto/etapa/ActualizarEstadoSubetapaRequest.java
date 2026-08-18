package co.sena.sicot.dto.etapa;

import co.sena.sicot.entity.enums.EstadoSubetapa;
import jakarta.validation.constraints.NotNull;

public record ActualizarEstadoSubetapaRequest(
        @NotNull(message = "El estado es obligatorio.")
        EstadoSubetapa estado
) {
}
