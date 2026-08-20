package co.sena.sicot.dto.usuario;

import jakarta.validation.constraints.NotNull;

public record CambiarEstadoUsuarioRequest(
        @NotNull(message = "El estado activo es obligatorio.")
        Boolean activo
) {
}
