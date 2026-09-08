package co.sena.sicot.automatizacion;

/**
 * Cuántas subetapas tiene un contrato y cuántas están cerradas, resuelto por la
 * base en una sola consulta agrupada.
 *
 * <p>Existe para que evaluar las reglas de calendario sobre N contratos cueste
 * dos consultas y no {@code 1 + N × (1 + etapas)}. Navegar
 * {@code contrato → etapas → subetapas} desde Java sería la versión lenta del
 * mismo dato, y con la suficiente cantidad de contratos convertiría la
 * evaluación diaria en algo que se nota.
 */
public record ConteoDeSubetapas(Long contratoId, long totales, long completadas) {
}
