package co.sena.sicot.dto.usuario;

import jakarta.validation.constraints.NotBlank;

public record EnviarCredencialesRequest(
        @NotBlank(message = "La contraseña es obligatoria.")
        String password
) {
}
