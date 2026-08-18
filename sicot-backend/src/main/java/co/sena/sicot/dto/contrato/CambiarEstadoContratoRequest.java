package co.sena.sicot.dto.contrato;

import co.sena.sicot.entity.enums.EstadoContrato;
import jakarta.validation.constraints.NotNull;

public record CambiarEstadoContratoRequest(
        @NotNull(message = "El estado es obligatorio.")
        EstadoContrato estado
) {
}
