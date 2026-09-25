package co.sena.sicot.dto.ia;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param notas lo que el supervisor escribió sobre lo que hizo en el paso. Es
 *              opcional: sin notas, el apartado de observaciones queda marcado
 *              como pendiente en el documento en vez de inventarse.
 */
public record GenerarDocumentoRequest(
        @NotBlank(message = "El tipo de documento es obligatorio (ver PlantillaDocumentoIA.CATALOGO).")
        @Size(max = 60, message = "El tipo de documento no es válido.")
        String tipo,
        Long subetapaId,
        @Size(max = 4000, message = "Las notas del supervisor no pueden superar 4000 caracteres.")
        String notas
) {
}
