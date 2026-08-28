package co.sena.sicot.dto.contrato;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AsignarSupervisorRequest(
        @NotNull(message = "El id del supervisor es obligatorio.")
        @Positive(message = "El identificador del supervisor debe ser un número positivo.")
        Long supervisorId
) {
}
