package co.sena.sicot.dto.contrato;

import jakarta.validation.constraints.NotNull;

public record AsignarSupervisorRequest(
        @NotNull(message = "El id del supervisor es obligatorio.")
        Long supervisorId
) {
}
