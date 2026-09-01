package co.sena.sicot.service;

import co.sena.sicot.dto.usuario.ActualizarUsuarioRequest;
import co.sena.sicot.dto.usuario.CambiarEstadoUsuarioRequest;
import co.sena.sicot.dto.usuario.CrearUsuarioRequest;
import co.sena.sicot.dto.usuario.EnviarCredencialesRequest;
import co.sena.sicot.dto.usuario.EnviarCredencialesResponse;
import co.sena.sicot.dto.usuario.UsuarioResponse;
import co.sena.sicot.entity.Usuario;
import co.sena.sicot.entity.enums.Rol;
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

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                          EmailService emailService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
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
        // Cambiar el rol del último administrador lo deja fuera de /api/usuarios,
        // que es el único camino para volver a crear o promover a alguien: el
        // sistema quedaría sin forma de recuperarse por la API.
        if (request.rol() != Rol.ADMINISTRADOR) {
            verificarQueNoEsElUltimoAdministrador(usuario,
                    "No se puede cambiar el rol del único administrador del sistema.");
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
        if (!request.activo()) {
            verificarQueNoEsElUltimoAdministrador(usuario,
                    "No se puede desactivar el único administrador del sistema.");
        }
        usuario.setActivo(request.activo());
        return UsuarioMapper.toResponse(usuarioRepository.save(usuario));
    }

    /**
     * Impide dejar el sistema sin ningún administrador activo.
     *
     * Se comprueba en los dos caminos que pueden provocarlo — desactivar la
     * cuenta y cambiarle el rol — porque cualquiera de los dos, aplicado al
     * último administrador, deja el sistema irrecuperable desde la API: nadie
     * podría volver a entrar a {@code /api/usuarios} para arreglarlo.
     *
     * La comparación es por identidad de enum, no por {@code name().equals(...)}:
     * comparar contra el texto "ADMINISTRADOR" sobrevive a un renombrado de la
     * constante y desactivaría esta guarda en silencio.
     */
    private void verificarQueNoEsElUltimoAdministrador(Usuario usuario, String mensaje) {
        if (usuario.getRol() == Rol.ADMINISTRADOR
                && usuario.isActivo()
                && usuarioRepository.countByRolAndActivoTrue(Rol.ADMINISTRADOR) <= 1) {
            throw new BusinessException(mensaje);
        }
    }

    /**
     * Envía las credenciales por correo.
     *
     * Deliberadamente SIN {@code @Transactional}: el envío SMTP es una llamada
     * de red que puede tardar, y mantener la transacción abierta retendría una
     * conexión del pool de HikariCP (10 por defecto) todo ese tiempo. Con unos
     * pocos envíos simultáneos hacia un servidor de correo lento se agotaría el
     * pool y la aplicación entera —login incluido— quedaría bloqueada, no solo
     * el envío. {@code buscar(id)} tiene su propia transacción corta.
     *
     * Los tiempos de espera de SMTP se configuran en application.properties; sin
     * ellos JavaMail espera indefinidamente, que es el mecanismo real detrás de
     * ese agotamiento.
     */
    public EnviarCredencialesResponse enviarCredenciales(Long id, EnviarCredencialesRequest request) {
        Usuario usuario = buscar(id);
        try {
            emailService.enviarCredenciales(usuario.getEmail(), usuario.getNombre(), request.password());
            return new EnviarCredencialesResponse(true, null);
        } catch (Exception e) {
            log.warn("No se pudo enviar credenciales por correo a {}: {}", usuario.getEmail(), e.getMessage());
            // No se devuelve e.getMessage() al cliente: los errores de JavaMail
            // incluyen host, puerto y respuesta cruda del servidor de correo.
            return new EnviarCredencialesResponse(false,
                    "No se pudo enviar el correo. Verifique la configuración de correo del servidor.");
        }
    }

    @Transactional(readOnly = true)
    public Usuario buscar(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Usuario", id));
    }
}
