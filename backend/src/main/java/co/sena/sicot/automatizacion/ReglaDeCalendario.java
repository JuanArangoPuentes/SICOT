package co.sena.sicot.automatizacion;

import java.time.LocalDate;
import java.util.List;

/**
 * Regla que depende del paso del tiempo y no de que alguien haga algo.
 *
 * <p>Existen porque hay hechos que nadie provoca. Que a un contrato le queden
 * treinta días no es la acción de nadie: es el calendario avanzando. Ningún
 * evento se dispara ese día, así que sin una evaluación periódica ese hecho no
 * se detecta nunca — y es justo la clase de aviso que un supervisor necesita.
 *
 * <h2>La fecha entra como parámetro</h2>
 * Y no se lee de {@code LocalDate.now()} dentro de la regla. Así una prueba
 * puede preguntar «¿qué haría esta regla el 15 de marzo?» sin manipular el reloj
 * del sistema. Una regla que consulta la hora por su cuenta solo se puede probar
 * el día correcto, que en la práctica significa que no se prueba.
 *
 * <h2>Idempotencia obligatoria</h2>
 * Estas reglas se evalúan todos los días sobre los mismos contratos. Sin una
 * clave que identifique el hecho y no el momento, cada mañana producirían la
 * misma alerta otra vez. Ver {@code V15__motor_de_automatizaciones.sql}.
 */
public interface ReglaDeCalendario {

    /**
     * Identificador estable, en kebab-case. Queda escrito en cada tarea que
     * produce esta regla, así que cambiarlo rompe el rastro de las ya creadas:
     * se elige una vez y no se toca.
     */
    String codigo();

    /**
     * @param contrato datos ya leídos; ver {@link FotoDelContrato} para por qué
     *                 no llega la entidad
     * @param hoy      fecha de evaluación, inyectada para poder probarla
     */
    List<TareaSolicitada> evaluar(FotoDelContrato contrato, LocalDate hoy);
}
