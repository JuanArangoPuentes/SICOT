package co.sena.sicot.service;

import co.sena.sicot.dto.auth.AuthResponse;
import co.sena.sicot.dto.auth.LoginRequest;
import co.sena.sicot.entity.Usuario;
import co.sena.sicot.exception.BusinessException;
import co.sena.sicot.repository.UsuarioRepository;
import co.sena.sicot.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(request.email().trim().toLowerCase())
                .orElseThrow(() -> new BusinessException("Credenciales inválidas."));
        if (!passwordEncoder.matches(request.password(), usuario.getPassword())) {
            throw new BusinessException("Credenciales inválidas.");
        }
        if (!usuario.isActivo()) {
            throw new BusinessException("El usuario está inactivo. Contacte al administrador.");
        }
        String token = jwtService.generateToken(usuario);
        return new AuthResponse(token, usuario.getId(), usuario.getNombre(),
                usuario.getEmail(), usuario.getRol());
    }
}
