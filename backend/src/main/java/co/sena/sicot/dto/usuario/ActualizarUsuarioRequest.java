package co.sena.sicot.dto.usuario;

import co.sena.sicot.entity.enums.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import co.sena.sicot.security.PoliticaDeContrasena;

public record ActualizarUsuarioRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 150, message = "El nombre no puede superar 150 caracteres.")
        String nombre,

        @NotBlank(message = "El email es obligatorio.")
        @Email(message = "Debe ser un email válido.")
        @Size(max = 150, message = "El email no puede superar 150 caracteres.")
        String email,

        // 72: el tope de BCrypt, que además se cuenta en bytes (ver LimiteDeBcrypt).
        // Solo el tope: el MÍNIMO lo exige @ContrasenaSegura, y no los dos a la
        // vez. Con ambos, una contraseña corta producía dos violaciones para el
        // mismo campo y el mapa `fieldErrors` se queda con la primera que
        // llegue, que no es un orden garantizado: el mensaje que veía la
        // persona cambiaba de una petición a otra.
        @Size(max = 72, message = "La contraseña no puede superar 72 caracteres.")
        // Largo mínimo, lista de bloqueo y secuencias: ver PoliticaDeContrasena.
        // Hasta el 20-11-2026 el mínimo eran 8 y no había lista, así que
        // «Password123» era una contraseña válida en este sistema.
        @ContrasenaSegura
        // Misma regla que al crear: ver CrearUsuarioRequest.
        @Pattern(regexp = "^\\S(?:.*\\S)?$",
                message = "La contraseña no puede empezar ni terminar con espacios.")
        String password,

        @NotBlank(message = "El número de teléfono es obligatorio.")
        @Size(max = 20, message = "El teléfono no puede superar 20 caracteres.")
        String telefono,

        @NotNull(message = "El rol es obligatorio.")
        Rol rol
) {
}
