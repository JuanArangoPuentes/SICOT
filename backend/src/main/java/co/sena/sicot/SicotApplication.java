package co.sena.sicot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableScheduling} habilita las tareas periódicas del sistema. Hoy la
 * única es {@link co.sena.sicot.config.VigilanciaDeAlmacenamiento}, que mide a
 * diario cuánto ocupan los archivos dentro de PostgreSQL para avisar antes de
 * alcanzar el umbral de ADR-003.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class SicotApplication {

    public static void main(String[] args) {
        SpringApplication.run(SicotApplication.class, args);
    }
}
