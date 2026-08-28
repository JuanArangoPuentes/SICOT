package co.sena.sicot.dto.usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnviarCredencialesRequest(
        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(max = 100, message = "La contraseña no puede superar 100 caracteres.")
        String password
) {
}
