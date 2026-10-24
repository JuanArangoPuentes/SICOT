package co.sena.sicot.dto.contacto;

import co.sena.sicot.entity.enums.RolDeContacto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Los profesionales de SST y ambiental del contrato, completos. Reemplazan los
 * anteriores, como el acuerdo de entregas.
 *
 * @param contactos una lista vacía los borra
 */
public record GuardarContactosRequest(
        @NotNull(message = "La lista de contactos es obligatoria (puede ir vacía para borrarlos).")
        @Size(max = 20, message = "Se admiten máximo 20 contactos por contrato.")
        @Valid
        List<@NotNull(message = "Cada contacto debe traer su rol y su correo.") Contacto> contactos) {

    public record Contacto(
            @NotNull(message = "Diga si el contacto es de SST o de ambiental.")
            RolDeContacto rol,

            @Size(max = 255, message = "El nombre no puede superar 255 caracteres.")
            String nombre,

            @NotBlank(message = "El correo del contacto es obligatorio.")
            @Email(message = "El correo del contacto no es una dirección válida.")
            @Size(max = 255, message = "El correo no puede superar 255 caracteres.")
            String correo) {
    }
}
