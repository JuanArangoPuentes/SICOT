package co.sena.sicot.automatizacion;

import co.sena.sicot.entity.TareaAutomatizada;
import co.sena.sicot.entity.enums.TipoTareaAutomatizada;

/**
 * La tarea que un trabajador acaba de reservar, en la forma que ven las
 * acciones.
 *
 * <h2>Por qué no se les pasa la entidad</h2>
 * Por la misma razón que las reglas reciben {@link FotoDelContrato} y no un
 * {@code Contrato}, pero aquí el problema es más agudo. El ciclo de una tarea
 * usa tres transacciones distintas —reservar, ejecutar, anotar el resultado— y
 * la entidad se lee en la primera. Para cuando la acción corre, esa sesión de
 * Hibernate ya se cerró: {@code tarea.getContrato()} sería un proxy huérfano
 * que estalla al leerlo, y pasárselo a un servicio que abre su propia
 * transacción daría el error todavía más confuso de una entidad de otra sesión.
 *
 * <p>Con identificadores y texto plano, la acción recarga lo que necesita dentro
 * de la transacción donde va a usarlo. El error deja de ser posible en vez de
 * ser algo que hay que recordar.
 *
 * @param contratoId puede ser {@code null}: hay tareas que no pertenecen a
 *                   ningún contrato
 */
public record TareaEnEjecucion(
        Long id,
        String regla,
        TipoTareaAutomatizada tipo,
        Long contratoId,
        String payload
) {

    /** Se construye dentro de la transacción que leyó la fila, mientras la sesión sigue abierta. */
    public static TareaEnEjecucion de(TareaAutomatizada tarea) {
        return new TareaEnEjecucion(
                tarea.getId(),
                tarea.getRegla(),
                tarea.getTipo(),
                tarea.getContrato() != null ? tarea.getContrato().getId() : null,
                tarea.getPayload());
    }
}
