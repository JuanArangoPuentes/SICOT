package co.sena.sicot.service;

import co.sena.sicot.dto.usuario.ActualizarUsuarioRequest;
import co.sena.sicot.dto.usuario.CambiarEstadoUsuarioRequest;
import co.sena.sicot.dto.usuario.CrearUsuarioRequest;
import co.sena.sicot.dto.usuario.UsuarioResponse;
import co.sena.sicot.entity.Usuario;
import co.sena.sicot.exception.BusinessException;
import co.sena.sicot.exception.ResourceNotFoundException;
import co.sena.sicot.mapper.UsuarioMapper;
import co.sena.sicot.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
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
    public Usuario buscar(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Usuario", id));
    }
}
