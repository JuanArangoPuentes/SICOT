package co.sena.sicot.dto.ia;

import jakarta.validation.constraints.NotBlank;

public record GenerarDocumentoRequest(
        @NotBlank(message = "El tipo de documento es obligatorio (ver PlantillaDocumentoIA.CATALOGO).")
        String tipo,
        Long subetapaId
) {
}
