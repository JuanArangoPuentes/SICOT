package co.sena.sicot.automatizacion;

import java.util.List;

/**
 * Regla que reacciona a algo que acaba de pasar.
 *
 * <p>Se descubren solas: basta anotar la implementación con {@code @Component}
 * y Spring la inyecta en la lista que recorre {@link MotorDeAutomatizacion}. No
 * hay ningún registro central que actualizar, que es justo el archivo que
 * alguien olvida al añadir la regla número ocho.
 *
 * <h2>Lo que una regla no puede hacer</h2>
 * Escribir en la base, mandar correos o llamar al modelo. Devuelve
 * {@link TareaSolicitada} y el motor se encarga. Esa restricción es lo que hace
 * que las reglas se puedan probar sin levantar nada y que los reintentos se
 * escriban una sola vez para todas.
 */
public interface ReglaDeEvento {

    /**
     * Identificador estable, en kebab-case. Queda escrito en cada tarea que
     * produce esta regla, así que cambiarlo rompe el rastro de las tareas ya
     * creadas: se elige una vez y no se toca.
     */
    String codigo();

    /**
     * Filtro barato. Se llama para cada evento del sistema, así que debe mirar
     * la acción y poco más — nada de consultas.
     */
    boolean aplicaA(EventoDeNegocio evento);

    /**
     * Decide qué hacer. Aquí sí se puede consultar la base (en la transacción
     * que abre el motor), pero nunca escribir.
     */
    List<TareaSolicitada> evaluar(EventoDeNegocio evento);
}
