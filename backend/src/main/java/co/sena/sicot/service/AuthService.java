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
    // Semgrep marca esta constante como "hash BCrypt filtrado". No lo es: es un
    // hash señuelo cuyo texto original no es la contraseña de ninguna cuenta y
    // contra el que nunca se autentica a nadie (ver arriba). Se silencia por
    // regla y no por archivo para que un hash de verdad pegado aquí sí salte.
    private static final String HASH_SEÑUELO =
            // nosemgrep: generic.secrets.security.detected-bcrypt-hash.detected-bcrypt-hash
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
     * <h2>Por qué no es {@code @Transactional}</h2>
     * BCrypt tarda deliberadamente unos 100 ms de CPU (el doble si la
     * contraseña trae espacios). Con todo el método en una transacción, la
     * conexión de Hikari que tomó {@code findByEmail} quedaba retenida mientras
     * tanto: una ráfaga de logins sin autenticar ocupaba las diez conexiones del
     * pool esperando turno de CPU, y el resto de la API respondía con timeouts.
     * La consulta del usuario usa la conexión solo lo que dura; la comparación
     * de contraseñas ocurre sin ninguna.
     *
     * @param origen dirección de red desde la que llega el intento, para el
     *               límite por IP; {@code null} si no se pudo determinar
     */
    public AuthResponse login(LoginRequest request, String origen) {
        String email = request.email().trim().toLowerCase();
        loginAttemptService.reservarIntento(email, origen);

        Usuario usuario;
        boolean passwordCorrecta;
        try {
            usuario = usuarioRepository.findByEmail(email).orElse(null);
            // Se compara siempre, exista el usuario o no: contra su hash real o
            // contra el señuelo. Es lo que iguala los tiempos de respuesta.
            String hash = usuario != null ? usuario.getPassword() : HASH_SEÑUELO;
            passwordCorrecta = coincide(request.password(), hash);
        } catch (RuntimeException e) {
            // No se llegó a saber si las credenciales eran buenas (la base no
            // respondió, por ejemplo): ese intento no puede contar como fallo.
            loginAttemptService.liberarIntento(email, origen);
            throw e;
        }

        // En los dos rechazos la reserva se queda: ya es el fallo contado.
        if (usuario == null || !passwordCorrecta) {
            log.warn("Login fallido para el correo {}", email);
            throw new CredencialesInvalidasException("Credenciales inválidas.");
        }
        if (!usuario.isActivo()) {
            // También cuenta como fallo: si no, una cuenta desactivada sería un
            // objetivo con intentos ilimitados para adivinar su contraseña, que
            // seguirá siendo válida el día que se reactive.
            log.warn("Login rechazado: la cuenta {} está inactiva.", email);
            throw new CredencialesInvalidasException(
                    "El usuario está inactivo. Contacte al administrador.");
        }
        loginAttemptService.registrarExito(email, origen);
        String token = jwtService.generateToken(usuario);
        return new AuthResponse(token, usuario.getId(), usuario.getNombre(),
                usuario.getEmail(), usuario.getRol());
    }

    private boolean coincide(String password, String hash) {
        if (passwordEncoder.matches(password, hash)) {
            return true;
        }
        // Una contraseña pegada desde el correo de credenciales (o desde un
        // chat) suele arrastrar un espacio al final, que no se ve en el campo.
        // El 25-09-2026 eso dejó fuera al supervisor de prueba en el escritorio
        // y en Android con la contraseña correcta: el campo tenía 17 caracteres
        // y la contraseña 16. Desde entonces ninguna contraseña nueva puede
        // empezar ni terminar en espacio (ver CrearUsuarioRequest), así que
        // quitarlos solo puede recuperar lo que la persona quiso escribir.
        // Se prueba primero tal cual, por si una cuenta anterior tuviera uno de
        // verdad, y la segunda comparación también va contra el señuelo cuando
        // el usuario no existe, para no delatar por el tiempo qué correos hay.
        String sinEspacios = password.strip();
        return !sinEspacios.isEmpty() && !sinEspacios.equals(password)
                && passwordEncoder.matches(sinEspacios, hash);
    }
}
