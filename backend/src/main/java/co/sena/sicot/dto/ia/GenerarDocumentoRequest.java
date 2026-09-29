package co.sena.sicot.dto.ia;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * @param notas lo que el supervisor escribió sobre lo que hizo en el paso. Es
 *              opcional: sin notas, el apartado de observaciones queda marcado
 *              como pendiente en el documento en vez de inventarse.
 * @param datos datos del documento que el contrato no tiene —número de factura,
 *              póliza, cédulas…—, por clave (ver {@code PlantillaDocumentoIA.campos},
 *              que se consultan en {@code GET /api/ia/plantillas}). Opcional: los
 *              que falten salen como «[dato pendiente…]».
 * @param redactarConIa {@code false} para que las observaciones vayan tal como el supervisor las
 *              escribió, sin pasar por el Copiloto. Lo usa el panel cuando el supervisor, al ver
 *              la redacción antes de firmar, prefiere sus notas. Sin valor, se redacta.
 */
public record GenerarDocumentoRequest(
        @NotBlank(message = "El tipo de documento es obligatorio (ver PlantillaDocumentoIA.CATALOGO).")
        @Size(max = 60, message = "El tipo de documento no es válido.")
        String tipo,
        Long subetapaId,
        @Size(max = 4000, message = "Las notas del supervisor no pueden superar 4000 caracteres.")
        String notas,
        @Size(max = 40, message = "Demasiados datos para un solo documento.")
        Map<String, String> datos,
        Boolean redactarConIa
) {
    /** Compatibilidad con quien genera sin datos complementarios. */
    public GenerarDocumentoRequest(String tipo, Long subetapaId, String notas) {
        this(tipo, subetapaId, notas, null, null);
    }

    public GenerarDocumentoRequest(String tipo, Long subetapaId, String notas, Map<String, String> datos) {
        this(tipo, subetapaId, notas, datos, null);
    }

}
