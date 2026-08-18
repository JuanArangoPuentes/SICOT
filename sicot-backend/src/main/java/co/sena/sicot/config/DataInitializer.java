package co.sena.sicot.config;

import co.sena.sicot.entity.Usuario;
import co.sena.sicot.entity.enums.Rol;
import co.sena.sicot.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

/**
 * Crea los usuarios iniciales de desarrollo (SOLO cuando la tabla está vacía).
 * Las contraseñas son de prueba — cámbielas en producción.
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner seedUsers(UsuarioRepository usuarioRepository,
                                PasswordEncoder passwordEncoder) {
        return args -> {
            if (usuarioRepository.count() > 0) {
                return;
            }
            Usuario admin = new Usuario();
            admin.setNombre("Administrador SICOT");
            admin.setEmail("administrador@soy.sena.edu.co");
            admin.setPassword(passwordEncoder.encode("Admin123*"));
            admin.setRol(Rol.ADMINISTRADOR);

            Usuario gestion = new Usuario();
            gestion.setNombre("Unidad de Gestión Contractual");
            gestion.setEmail("gestion@soy.sena.edu.co");
            gestion.setPassword(passwordEncoder.encode("Gestion123*"));
            gestion.setRol(Rol.GESTION);

            Usuario supervisor = new Usuario();
            supervisor.setNombre("Alex Fernando Zapata");
            supervisor.setEmail("supervisor@soy.sena.edu.co");
            supervisor.setPassword(passwordEncoder.encode("Supervisor123*"));
            supervisor.setRol(Rol.SUPERVISOR);

            usuarioRepository.saveAll(List.of(admin, gestion, supervisor));
            log.info("Usuarios de desarrollo creados (administrador@, gestion@, supervisor@soy.sena.edu.co).");
        };
    }
}
