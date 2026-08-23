package co.sena.sicot.service;

import co.sena.sicot.dto.auth.AuthResponse;
import co.sena.sicot.dto.auth.LoginRequest;
import co.sena.sicot.entity.Usuario;
import co.sena.sicot.exception.BusinessException;
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

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        loginAttemptService.verificarNoBloqueado(email);

        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
        if (usuario == null || !passwordEncoder.matches(request.password(), usuario.getPassword())) {
            loginAttemptService.registrarFallo(email);
            log.warn("Login fallido para el correo {}", email);
            throw new BusinessException("Credenciales inválidas.");
        }
        if (!usuario.isActivo()) {
            throw new BusinessException("El usuario está inactivo. Contacte al administrador.");
        }
        loginAttemptService.registrarExito(email);
        String token = jwtService.generateToken(usuario);
        return new AuthResponse(token, usuario.getId(), usuario.getNombre(),
                usuario.getEmail(), usuario.getRol());
    }
}
