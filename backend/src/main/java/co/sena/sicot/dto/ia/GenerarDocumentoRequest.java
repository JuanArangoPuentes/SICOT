package co.sena.sicot.dto.ia;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
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
 * @param tablas filas de las tablas del formato (obligaciones, amparos, órdenes de pago), por
 *              clave de {@code PlantillaDocumentoIA.tablas}; cada fila, sus celdas en el orden
 *              de las columnas. Opcional: una tabla sin filas sale como «[dato pendiente…]».
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
        Boolean redactarConIa,
        @Size(max = 10, message = "Demasiadas tablas para un solo documento.")
        @io.swagger.v3.oas.annotations.media.Schema(description = "Filas de las tablas del formato, por clave "
                + "(ver GET /api/ia/plantillas, «tablas»). Cada fila es la lista de sus celdas en el orden de las "
                + "columnas; las filas vacías se ignoran y una celda vacía sale como «[dato pendiente]».",
                example = "{\"ordenesDePago\": [[\"70614726\", \"11/03/2026\", \"$16.798.000,00\"]]}")
        Map<String, List<List<String>>> tablas
) {
    /** Compatibilidad con quien genera sin datos complementarios. */
    public GenerarDocumentoRequest(String tipo, Long subetapaId, String notas) {
        this(tipo, subetapaId, notas, null, null, null);
    }

    public GenerarDocumentoRequest(String tipo, Long subetapaId, String notas, Map<String, String> datos) {
        this(tipo, subetapaId, notas, datos, null, null);
    }

    public GenerarDocumentoRequest(String tipo, Long subetapaId, String notas, Map<String, String> datos,
                                   Boolean redactarConIa) {
        this(tipo, subetapaId, notas, datos, redactarConIa, null);
    }

}
