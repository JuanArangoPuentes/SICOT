package co.sena.sicot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * La zona horaria del Centro: la que decide qué día es «hoy» y qué significa
 * «a las 6».
 *
 * <h2>El fallo que corrige</h2>
 * El contenedor del backend no define {@code TZ}, así que la JVM corre en UTC.
 * Cada {@code LocalDate.now()} devolvía la fecha de Greenwich, que entre las
 * 19:00 y las 24:00 de Bogotá ya es mañana. En ese tramo, el semáforo daba por
 * vencido un plazo que vence hoy, y un PDF generado salía con la fecha del día
 * siguiente. Las tareas {@code @Scheduled(cron = …)} sin zona corrían cinco
 * horas antes de lo que dice su comentario. La vigilancia del respaldo de «las
 * 04:00», por ejemplo, corría a las 23:00, antes del respaldo de las 02:00 que
 * existe para vigilar (MDL-214).
 *
 * <h2>Por qué un {@link Clock} inyectado y no la zona de la JVM</h2>
 * <ul>
 *   <li>Los instantes ya son correctos: {@code Instant}, columnas
 *       {@code timestamptz} e Hibernate en UTC. Cambiar la zona de la JVM
 *       arriesga a todo lo que hoy funciona para arreglar cuatro llamadas.</li>
 *   <li>Con un reloj inyectado, una prueba fija la hora exacta y comprueba la
 *       frontera de las 19:00. Con la zona de la JVM, la prueba dependería de
 *       la máquina que la corre.</li>
 *   <li>No depende de que el operador recuerde definir {@code TZ} en el
 *       servidor. El valor por defecto ya es el correcto para el SENA, que
 *       opera en una sola zona horaria y sin horario de verano.</li>
 * </ul>
 *
 * <p>Una zona inválida detiene el arranque: {@code ZoneId.of} la rechaza. Es a
 * propósito, porque la alternativa silenciosa de {@code TimeZone} es caer a GMT,
 * que es justamente el fallo que esta clase viene a quitar.
 */
@Configuration
public class ZonaHoraria {

    private static final Logger log = LoggerFactory.getLogger(ZonaHoraria.class);

    /**
     * La propiedad, lista para {@code @Scheduled(zone = ZonaHoraria.PROPIEDAD)}.
     * Las anotaciones solo aceptan constantes, y así las tareas programadas y el
     * reloj leen la zona del mismo sitio.
     */
    public static final String PROPIEDAD = "${sicot.zona-horaria}";

    @Bean
    public Clock reloj(@Value(PROPIEDAD) String zona) {
        ZoneId id = ZoneId.of(zona);
        log.info("Zona horaria del Centro: {}. Es la que decide qué día es hoy y a qué hora corren las tareas programadas.", id);
        return Clock.system(id);
    }
}
