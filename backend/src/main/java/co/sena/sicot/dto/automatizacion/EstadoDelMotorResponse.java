package co.sena.sicot.dto.automatizacion;

import java.util.List;
import java.util.Map;

/**
 * Resumen de salud del motor de automatizaciones.
 *
 * <p>Responde las tres preguntas que se hacen al operar el sistema, en el orden
 * en que se hacen: ¿está encendido?, ¿se está acumulando trabajo?, ¿hay algo
 * roto? Sin esto, la cola es una tabla que solo se puede mirar entrando a la
 * base de datos, y en la práctica eso significa que nadie la mira hasta que
 * alguien reporta que no le llegan las alertas.
 *
 * @param habilitado  si el planificador está activo en este despliegue
 * @param porEstado   cuántas tareas hay en cada estado
 * @param reglas      códigos de las reglas cargadas, para verificar de un
 *                    vistazo que una recién desplegada quedó registrada
 */
public record EstadoDelMotorResponse(
        boolean habilitado,
        Map<String, Long> porEstado,
        List<String> reglas
) {
}
