package co.sena.sicot.dto.firma;

import jakarta.validation.constraints.NotNull;

public record CrearFirmaRequest(
        @NotNull(message = "El usuario es obligatorio.")
        Long usuarioId
) {
}
