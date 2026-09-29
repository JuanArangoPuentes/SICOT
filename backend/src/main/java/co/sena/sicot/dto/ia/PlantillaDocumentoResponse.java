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

    /**
     * @param ejemplo      cómo lo escribe el formato real diligenciado.
     * @param opcional     si falta no deja nada pendiente (se deduce o no siempre aplica).
     * @param dependeDe    clave del dato que lo vuelve necesario (el valor actual, si hubo
     *                     adición), o {@code null}.
     * @param porDocumento si es de este documento y no del contrato (la factura del
     *                     periodo, las multas): no se ofrece ya escrito en el siguiente.
     */
    public record Campo(String clave, String etiqueta, String ejemplo, boolean opcional, String dependeDe,
                        boolean porDocumento) {
    }
}
