package co.sena.sicot.dto.ia;

import java.util.List;

/**
 * Un documento formal que SICOT sabe armar y los datos que pide que el
 * contrato no tiene. Con esto el panel del supervisor le pregunta esos datos
 * antes de generar, en vez de firmar un documento lleno de «[dato pendiente]».
 *
 * @param llevaObservaciones si el formato tiene un apartado donde van las notas
 *                           del supervisor.
 */
public record PlantillaDocumentoResponse(String tipo, String codigo, String nombre, boolean llevaObservaciones,
                                         List<Campo> campos) {

    /** @param ejemplo cómo lo escribe el formato real diligenciado. */
    public record Campo(String clave, String etiqueta, String ejemplo) {
    }
}
