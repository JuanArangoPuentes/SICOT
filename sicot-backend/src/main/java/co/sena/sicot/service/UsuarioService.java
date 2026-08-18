package co.sena.sicot.service;

import co.sena.sicot.dto.usuario.ActualizarUsuarioRequest;
import co.sena.sicot.dto.usuario.CambiarEstadoUsuarioRequest;
import co.sena.sicot.dto.usuario.CrearUsuarioRequest;
import co.sena.sicot.dto.usuario.EnviarCredencialesRequest;
import co.sena.sicot.dto.usuario.EnviarCredencialesResponse;
import co.sena.sicot.dto.usuario.UsuarioResponse;
import co.sena.sicot.entity.Usuario;
import co.sena.sicot.exception.BusinessException;
import co.sena.sicot.exception.ResourceNotFoundException;
import co.sena.sicot.mapper.UsuarioMapper;
import co.sena.sicot.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SmsService smsService;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                          EmailService emailService, SmsService smsService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.smsService = smsService;
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return usuarioRepository.findAll().stream()
                .map(UsuarioMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtener(Long id) {
        return UsuarioMapper.toResponse(buscar(id));
    }

    @Transactional
    public UsuarioResponse crear(CrearUsuarioRequest request) {
        String email = request.email().trim().toLowerCase();
        if (usuarioRepository.existsByEmail(email)) {
            throw new BusinessException("Ya existe un usuario con el email " + email + ".");
        }
        Usuario usuario = new Usuario();
        usuario.setNombre(request.nombre().trim());
        usuario.setEmail(email);
        usuario.setPassword(passwordEncoder.encode(request.password()));
        usuario.setTelefono(request.telefono().trim());
        usuario.setRol(request.rol());
        usuario.setActivo(true);
        return UsuarioMapper.toResponse(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioResponse actualizar(Long id, ActualizarUsuarioRequest request) {
        Usuario usuario = buscar(id);
        String email = request.email().trim().toLowerCase();
        if (usuarioRepository.existsByEmailAndIdNot(email, id)) {
            throw new BusinessException("Ya existe un usuario con el email " + email + ".");
        }
        usuario.setNombre(request.nombre().trim());
        usuario.setEmail(email);
        usuario.setTelefono(request.telefono().trim());
        usuario.setRol(request.rol());
        if (request.password() != null && !request.password().isBlank()) {
            usuario.setPassword(passwordEncoder.encode(request.password()));
        }
        return UsuarioMapper.toResponse(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioResponse cambiarEstado(Long id, CambiarEstadoUsuarioRequest request) {
        Usuario usuario = buscar(id);
        if (usuario.getRol() != null
                && !request.activo()
                && usuario.getRol().name().equals("ADMINISTRADOR")
                && usuarioRepository.count() <= 1) {
            throw new BusinessException("No se puede desactivar el único administrador del sistema.");
        }
        usuario.setActivo(request.activo());
        return UsuarioMapper.toResponse(usuarioRepository.save(usuario));
    }

    @Transactional(readOnly = true)
    public EnviarCredencialesResponse enviarCredenciales(Long id, EnviarCredencialesRequest request) {
        Usuario usuario = buscar(id);
        try {
            switch (request.metodo()) {
                case CORREO -> emailService.enviarCredenciales(usuario.getEmail(), usuario.getNombre(), request.password());
                case SMS -> smsService.enviarCredenciales(usuario.getTelefono(), usuario.getNombre(), request.password());
            }
            return new EnviarCredencialesResponse(true, null);
        } catch (Exception e) {
            log.warn("No se pudo enviar credenciales ({}) a la cuenta {}: {}", request.metodo(), usuario.getEmail(), e.getMessage());
            return new EnviarCredencialesResponse(false, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Usuario buscar(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Usuario", id));
    }
}
