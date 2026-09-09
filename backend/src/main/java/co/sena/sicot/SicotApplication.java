package co.sena.sicot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableScheduling} habilita las tareas periódicas del sistema:
 * {@link co.sena.sicot.config.VigilanciaDeAlmacenamiento}, que mide a diario
 * cuánto ocupan los archivos dentro de PostgreSQL para avisar antes de alcanzar
 * el umbral de ADR-003, y el
 * {@link co.sena.sicot.automatizacion.PlanificadorDeAutomatizaciones}, que
 * evalúa las reglas de calendario y consume la cola de trabajo (ADR-008).
 *
 * <p>{@code @ConfigurationPropertiesScan} registra los grupos de configuración
 * tipada, hoy
 * {@link co.sena.sicot.automatizacion.AutomatizacionProperties}. Sin él habría
 * que enumerarlos uno a uno con {@code @EnableConfigurationProperties}, que es
 * la lista que alguien olvida actualizar al añadir el siguiente.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
@ConfigurationPropertiesScan
public class SicotApplication {

    public static void main(String[] args) {
        SpringApplication.run(SicotApplication.class, args);
    }
}
