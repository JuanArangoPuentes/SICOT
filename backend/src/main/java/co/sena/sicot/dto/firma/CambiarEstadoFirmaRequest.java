package co.sena.sicot.dto.firma;

import jakarta.validation.constraints.NotNull;

public record CambiarEstadoFirmaRequest(
        @NotNull(message = "El estado activa es obligatorio.")
        Boolean activa
) {
}
