package co.sena.sicot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class SicotApplication {

    public static void main(String[] args) {
        SpringApplication.run(SicotApplication.class, args);
    }
}
