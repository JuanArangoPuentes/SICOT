package co.sena.sicot.service;

import co.sena.sicot.dto.auth.AuthResponse;
import co.sena.sicot.dto.auth.LoginRequest;
import co.sena.sicot.entity.Usuario;
import co.sena.sicot.exception.CredencialesInvalidasException;
import co.sena.sicot.repository.UsuarioRepository;
import co.sena.sicot.security.JwtService;
import co.sena.sicot.security.LoginAttemptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /**
     * Hash BCrypt de una contraseña que no es la de nadie. Se compara contra
     * él cuando el correo no existe, para que el tiempo de respuesta sea el
     * mismo que el de un correo real con contraseña equivocada.
     *
     * <p>Sin esto hay un oráculo de tiempos: BCrypt tarda deliberadamente
     * ~100 ms, así que «este correo no existe» respondía casi al instante y
     * «existe pero la clave está mal» tardaba diez veces más. Esa diferencia
     * es medible desde fuera y convierte el login en un verificador de qué
     * correos institucionales tienen cuenta — precisamente lo que el mensaje
     * de error idéntico trataba de ocultar.
     */
    private static final String HASH_SEÑUELO =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, LoginAttemptService loginAttemptService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
    }

    /**
     * @param origen dirección de red desde la que llega el intento, para el
     *               límite por IP; {@code null} si no se pudo determinar
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request, String origen) {
        String email = request.email().trim().toLowerCase();
        loginAttemptService.verificarNoBloqueado(email, origen);

        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);

        // Se compara siempre, exista el usuario o no: contra su hash real o
        // contra el señuelo. Es lo que iguala los tiempos de respuesta.
        boolean passwordCorrecta = passwordEncoder.matches(
                request.password(),
                usuario != null ? usuario.getPassword() : HASH_SEÑUELO);

        if (usuario == null || !passwordCorrecta) {
            loginAttemptService.registrarFallo(email, origen);
            log.warn("Login fallido para el correo {}", email);
            throw new CredencialesInvalidasException("Credenciales inválidas.");
        }
        if (!usuario.isActivo()) {
            // También cuenta como fallo: si no, una cuenta desactivada sería un
            // objetivo con intentos ilimitados para adivinar su contraseña, que
            // seguirá siendo válida el día que se reactive.
            loginAttemptService.registrarFallo(email, origen);
            log.warn("Login rechazado: la cuenta {} está inactiva.", email);
            throw new CredencialesInvalidasException(
                    "El usuario está inactivo. Contacte al administrador.");
        }
        loginAttemptService.registrarExito(email);
        String token = jwtService.generateToken(usuario);
        return new AuthResponse(token, usuario.getId(), usuario.getNombre(),
                usuario.getEmail(), usuario.getRol());
    }
}
