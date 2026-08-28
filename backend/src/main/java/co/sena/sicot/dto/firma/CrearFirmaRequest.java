package co.sena.sicot.dto.firma;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CrearFirmaRequest(
        @NotNull(message = "El usuario es obligatorio.")
        @Positive(message = "El identificador del usuario debe ser un número positivo.")
        Long usuarioId
) {
}
