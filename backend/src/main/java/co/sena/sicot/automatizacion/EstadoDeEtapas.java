package co.sena.sicot.automatizacion;

/**
 * Cuál es la etapa en curso de un contrato y cuántas tiene, resuelto por la base
 * en una sola consulta agrupada.
 *
 * <p>«En curso» es la de menor número que todavía no está COMPLETADA. Se define
 * así y no como «la que está en estado EN_CURSO» porque un contrato recién
 * sembrado tiene sus seis etapas en PENDIENTE: con la otra definición no habría
 * ninguna etapa actual y el cronograma no podría estimarse hasta que alguien
 * tocara algo.
 *
 * @param etapaActual {@code null} cuando todas están cerradas — el contrato
 *                    terminó su flujo y ya no hay nada que estimar
 */
public record EstadoDeEtapas(Long contratoId, Integer etapaActual, long totalEtapas) {
}
