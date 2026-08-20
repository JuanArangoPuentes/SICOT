package co.sena.sicot.dto.usuario;

import co.sena.sicot.entity.enums.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrearUsuarioRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 150, message = "El nombre no puede superar 150 caracteres.")
        String nombre,

        @NotBlank(message = "El email es obligatorio.")
        @Email(message = "Debe ser un email válido.")
        @Size(max = 150, message = "El email no puede superar 150 caracteres.")
        String email,

        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres.")
        String password,

        @NotBlank(message = "El número de teléfono es obligatorio.")
        @Size(max = 20, message = "El teléfono no puede superar 20 caracteres.")
        String telefono,

        @NotNull(message = "El rol es obligatorio.")
        Rol rol
) {
}
