package co.sena.sicot.config;

import co.sena.sicot.entity.Usuario;
import co.sena.sicot.entity.enums.Rol;
import co.sena.sicot.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Crea la primera cuenta ADMINISTRADOR de un despliegue real.
 *
 * <h2>El problema que resuelve</h2>
 * {@link DataInitializer} solo actúa en los perfiles {@code dev} y {@code test},
 * y con razón: siembra cuentas cuyas contraseñas están publicadas en el README.
 * Pero eso dejaba un despliegue de producción en un punto muerto perfecto:
 * {@code POST /api/usuarios} exige {@code hasRole('ADMINISTRADOR')}, el único
 * endpoint público de todo el backend es {@code POST /api/auth/login}, y la
 * tabla {@code usuarios} de una base nueva está vacía. El sistema arrancaba
 * sano, respondía {@code {"status":"UP"}} — y nadie podía entrar nunca. La
 * única salida era escribir a mano un INSERT con un hash BCrypt directamente
 * en PostgreSQL, algo que no estaba documentado en ninguna parte y que deja la
 * contraseña en el historial de la terminal.
 *
 * <h2>Cómo se usa</h2>
 * Se definen dos variables en el {@code .env} del servidor antes del primer
 * arranque:
 * <pre>
 *   SICOT_ADMIN_EMAIL=nombre.apellido&#64;sena.edu.co
 *   SICOT_ADMIN_PASSWORD=&lt;contraseña larga y única&gt;
 * </pre>
 * Después del primer arranque pueden retirarse: este componente no vuelve a
 * hacer nada mientras exista al menos un usuario.
 *
 * <h2>Tres decisiones que conviene no revertir</h2>
 * <ol>
 *   <li><b>Solo actúa con la tabla vacía.</b> No "asegura" que el admin exista
 *       ni le restablece la contraseña en cada arranque: eso convertiría una
 *       variable de entorno olvidada en una puerta trasera permanente, y haría
 *       que un cambio de contraseña hecho desde la aplicación se revirtiera
 *       solo en el siguiente reinicio.</li>
 *   <li><b>Si la tabla está vacía y no hay configuración, el arranque falla.</b>
 *       Es el mismo criterio que ya aplica {@code JWT_SECRET}: más vale un
 *       error explícito al desplegar que un sistema en pie al que nadie puede
 *       entrar y cuya causa no aparece en ningún log.</li>
 *   <li><b>Exige una contraseña larga.</b> Es la credencial con más privilegios
 *       del sistema y la única que no nace de otra cuenta, así que no se acepta
 *       lo mínimo que aceptaría un usuario normal.</li>
 * </ol>
 *
 * <p>No se activa en {@code dev} ni en {@code test} porque allí manda
 * {@code DataInitializer}; tener los dos sembrando la misma tabla sería una
 * carrera con resultado impredecible.
 */
@Configuration
@Profile("!dev & !test")
public class AdministradorInicial {

    private static final Logger log = LoggerFactory.getLogger(AdministradorInicial.class);

    /**
     * Longitud mínima de la contraseña de arranque. Por encima del mínimo de 8
     * que valida {@code CrearUsuarioRequest} para el resto de cuentas: aquella
     * la escribe un administrador para otra persona y puede cambiarse desde la
     * aplicación; esta es la llave del despliegue entero y suele quedarse
     * puesta mucho más tiempo del previsto.
     */
    private static final int LONGITUD_MINIMA_PASSWORD = 12;

    @Bean
    CommandLineRunner crearAdministradorInicial(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            @Value("${sicot.bootstrap.admin-email:}") String email,
            @Value("${sicot.bootstrap.admin-password:}") String password,
            @Value("${sicot.bootstrap.admin-nombre:Administrador SICOT}") String nombre) {

        return args -> {
            if (usuarioRepository.count() > 0) {
                log.debug("Ya existen usuarios: no se crea el administrador inicial.");
                return;
            }

            String correoLimpio = email == null ? "" : email.trim();
            if (correoLimpio.isBlank() || password == null || password.isBlank()) {
                throw new IllegalStateException("""
                        No hay ningún usuario en la base y no se configuró el administrador inicial, \
                        así que este despliegue quedaría inaccesible: nadie podría iniciar sesión ni \
                        crear cuentas. Defina SICOT_ADMIN_EMAIL y SICOT_ADMIN_PASSWORD en el .env del \
                        servidor y vuelva a arrancar. Una vez creada la cuenta puede retirar ambas \
                        variables: solo se usan cuando la tabla de usuarios está vacía.""");
            }
            if (password.length() < LONGITUD_MINIMA_PASSWORD) {
                throw new IllegalStateException(
                        "SICOT_ADMIN_PASSWORD debe tener al menos " + LONGITUD_MINIMA_PASSWORD
                                + " caracteres: es la cuenta con más privilegios del sistema. "
                                + "Genere una con `openssl rand -base64 24`.");
            }

            Usuario admin = new Usuario();
            admin.setNombre(nombre == null || nombre.isBlank() ? "Administrador SICOT" : nombre.trim());
            admin.setEmail(correoLimpio);
            admin.setPassword(passwordEncoder.encode(password));
            admin.setRol(Rol.ADMINISTRADOR);
            admin.setActivo(true);
            usuarioRepository.save(admin);

            // El correo sí se escribe (identifica la cuenta y hace falta para
            // soporte); la contraseña no aparece en ningún log, ni siquiera
            // truncada.
            log.warn("Administrador inicial creado para {}. Cambie su contraseña desde la aplicación "
                    + "y retire SICOT_ADMIN_PASSWORD del entorno.", correoLimpio);
        };
    }
}
