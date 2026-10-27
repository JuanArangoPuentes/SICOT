package co.sena.sicot.dto.seguimiento;

/**
 * Resultado de un {@code COUNT ... GROUP BY} por identificador. Existe para que
 * las consultas agrupadas del seguimiento devuelvan un tipo y no un
 * {@code Object[]} que hay que desarmar por posición.
 */
public record ConteoPorId(Long id, Long total) {
}
