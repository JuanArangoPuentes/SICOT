package co.sena.sicot.dto.ia;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenerarDocumentoRequest(
        @NotBlank(message = "El tipo de documento es obligatorio (ver PlantillaDocumentoIA.CATALOGO).")
        @Size(max = 60, message = "El tipo de documento no es válido.")
        String tipo,
        Long subetapaId
) {
}
