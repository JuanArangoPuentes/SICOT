package co.sena.sicot.service;

import co.sena.sicot.dto.auth.LoginRequest;
import co.sena.sicot.entity.Usuario;
import co.sena.sicot.entity.enums.Rol;
import co.sena.sicot.exception.CredencialesInvalidasException;
import co.sena.sicot.repository.UsuarioRepository;
import co.sena.sicot.security.JwtService;
import co.sena.sicot.security.LoginAttemptService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cómo usa el login la reserva de intentos de {@code LoginAttemptService}: el
 * intento se cuenta antes de comprobar la contraseña y solo se devuelve si era
 * correcta o si no llegó a decidirse.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "supervisor@soy.sena.edu.co";
    private static final String IP = "10.0.0.7";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private LoginAttemptService loginAttemptService;

    @InjectMocks
    private AuthService authService;

    @Test
    void elIntentoSeReservaAntesDeComprobarLaContrasena() {
        Usuario usuario = usuarioActivo();
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Correcta123*", usuario.getPassword())).thenReturn(true);
        when(jwtService.generateToken(usuario)).thenReturn("token");

        authService.login(new LoginRequest(" Supervisor@Soy.Sena.Edu.Co ", "Correcta123*"), IP);

        var orden = inOrder(loginAttemptService, passwordEncoder);
        orden.verify(loginAttemptService).reservarIntento(EMAIL, IP);
        orden.verify(passwordEncoder).matches("Correcta123*", usuario.getPassword());
        orden.verify(loginAttemptService).registrarExito(EMAIL, IP);
    }

    /** La reserva ya es el fallo: no hay nada que devolver ni que sumar. */
    @Test
    void unaContrasenaEquivocadaDejaLaReservaContada() {
        Usuario usuario = usuarioActivo();
        when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "Equivocada1"), IP))
                .isInstanceOf(CredencialesInvalidasException.class);

        verify(loginAttemptService).reservarIntento(EMAIL, IP);
        verify(loginAttemptService, never()).registrarExito(anyString(), anyString());
        verify(loginAttemptService, never()).liberarIntento(anyString(), anyString());
    }

    /** Una caída de la base no sabe si la contraseña era buena: no cuenta como fallo. */
    @Test
    void siLaBaseNoRespondeElIntentoSeDevuelve() {
        when(usuarioRepository.findByEmail(EMAIL))
                .thenThrow(new DataAccessResourceFailureException("sin conexión"));

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "Correcta123*"), IP))
                .isInstanceOf(DataAccessResourceFailureException.class);

        verify(loginAttemptService).liberarIntento(EMAIL, IP);
        verify(loginAttemptService, never()).registrarExito(anyString(), anyString());
    }

    /**
     * BCrypt tarda ~100 ms de CPU. Dentro de una transacción retenía una
     * conexión del pool todo ese tiempo, y una ráfaga de logins sin autenticar
     * dejaba al resto de la API sin conexiones.
     */
    @Test
    void elLoginNoRetieneUnaTransaccionMientrasCorreBcrypt() throws Exception {
        assertThat(AuthService.class.isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(AuthService.class.getMethod("login", LoginRequest.class, String.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
    }

    private static Usuario usuarioActivo() {
        Usuario usuario = new Usuario();
        usuario.setId(3L);
        usuario.setNombre("Supervisor");
        usuario.setEmail(EMAIL);
        usuario.setPassword("$2a$04$hash");
        usuario.setRol(Rol.SUPERVISOR);
        usuario.setActivo(true);
        return usuario;
    }
}
