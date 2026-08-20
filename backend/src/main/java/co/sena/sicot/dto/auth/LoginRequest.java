package co.sena.sicot.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "El email es obligatorio.")
        @Email(message = "Debe ser un email válido.")
        @Size(max = 150, message = "El email no puede superar 150 caracteres.")
        String email,

        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(max = 100, message = "La contraseña no puede superar 100 caracteres.")
        String password
) {
}
