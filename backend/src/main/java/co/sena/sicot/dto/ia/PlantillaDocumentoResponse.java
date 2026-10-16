package co.sena.sicot.dto.ia;

import java.util.List;

/**
 * Un documento formal que SICOT sabe armar y los datos que pide que el
 * contrato no tiene. Con esto el panel del supervisor le pregunta esos datos
 * antes de generar, en vez de firmar un documento lleno de «[dato pendiente]».
 *
 * @param llevaObservaciones si el formato tiene un apartado donde van las notas
 *                           del supervisor.
 * @param tablas             tablas del formato que se llenan fila por fila
 *                           (obligaciones, amparos, órdenes de pago).
 */
public record PlantillaDocumentoResponse(String tipo, String codigo, String nombre, boolean llevaObservaciones,
                                         List<Campo> campos, List<Tabla> tablas) {

    /**
     * @param ejemplo      cómo lo escribe el formato real diligenciado.
     * @param opcional     si falta no deja nada pendiente (se deduce o no siempre aplica).
     * @param dependeDe    clave del dato que lo vuelve necesario (el valor actual, si hubo
     *                     adición), o {@code null}.
     * @param porDocumento si es de este documento y no del contrato (la factura del
     *                     periodo, las multas): no se ofrece ya escrito en el siguiente.
     * @param largo        si es un párrafo (área de texto, hasta 2000 caracteres) y no un
     *                     dato de una línea (hasta 600).
     */
    public record Campo(String clave, String etiqueta, String ejemplo, boolean opcional, String dependeDe,
                        boolean porDocumento, boolean largo) {
    }

    /** @param ayuda qué va en la tabla, para quien la diligencia. */
    public record Tabla(String clave, String etiqueta, String ayuda, List<Columna> columnas) {
    }

    /**
     * @param delContrato si lo escrito en la columna es del contrato (el texto de la
     *                    obligación) y se ofrece ya escrito en el siguiente documento, o de
     *                    este documento (lo hecho en el periodo) y no se arrastra.
     */
    public record Columna(String etiqueta, String ejemplo, boolean delContrato) {
    }
}
