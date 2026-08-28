package co.sena.sicot.dto.chequeo;

/**
 * Tipo de pago de GRF-F-088. El formato dedica una columna a cada uno y marca con "X" los
 * documentos que exige.
 *
 * @param codigo identificador estable que usa SICOT para referenciar la columna.
 * @param nombre texto literal del encabezado de la columna en el formato oficial.
 */
public record TipoPagoChequeo(String codigo, String nombre) {
}
