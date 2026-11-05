package co.sena.sicot.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Lo que manda una persona para cambiar su propia contraseña.
 *
 * <p>La actual se exige aunque la petición ya traiga una sesión válida: una
 * sesión abierta en un equipo compartido del Centro, o un token copiado, no
 * deben bastar para quedarse con la cuenta cambiándole la contraseña a su
 * dueño.
 *
 * <p>La nueva sigue exactamente las reglas de {@code CrearUsuarioRequest}: entre
 * 8 y 72 caracteres y sin espacios en los extremos, porque una contraseña que
 * termina en espacio no se ve en el campo y fue la que dejó fuera al supervisor
 * de prueba el 25-09-2026. El límite de 72 bytes de BCrypt (las tildes cuentan
 * doble) lo comprueba el servicio con {@code LimiteDeBcrypt}.
 */
public record CambiarContrasenaRequest(
        @NotBlank(message = "Escriba su contraseña actual.")
        // Tope generoso y solo para no comparar textos de cualquier tamaño:
        // BCrypt no mira más allá de 72 bytes, pero el cuerpo llega entero.
        @Size(max = 200, message = "La contraseña actual no puede superar 200 caracteres.")
        String contrasenaActual,

        @NotBlank(message = "Escriba la contraseña nueva.")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.")
        @Pattern(regexp = "^\\S(?:.*\\S)?$",
                message = "La contraseña no puede empezar ni terminar con espacios.")
        String contrasenaNueva
) {
}
