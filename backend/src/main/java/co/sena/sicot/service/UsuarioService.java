package co.sena.sicot.service;

import co.sena.sicot.dto.usuario.ActualizarUsuarioRequest;
import co.sena.sicot.dto.usuario.CambiarEstadoUsuarioRequest;
import co.sena.sicot.dto.usuario.CrearUsuarioRequest;
import co.sena.sicot.dto.usuario.EnviarCredencialesRequest;
import co.sena.sicot.dto.usuario.EnviarCredencialesResponse;
import co.sena.sicot.dto.usuario.UsuarioResponse;
import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Usuario;
import co.sena.sicot.entity.enums.Rol;
import co.sena.sicot.exception.BusinessException;
import co.sena.sicot.exception.ResourceNotFoundException;
import co.sena.sicot.mapper.UsuarioMapper;
import co.sena.sicot.repository.ContratoRepository;
import co.sena.sicot.repository.UsuarioRepository;
import co.sena.sicot.security.LimiteDeBcrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    /** Los demás se resumen en «y N más»: el mensaje tiene que caber en un aviso. */
    private static final int MAX_CONTRATOS_EN_MENSAJE = 10;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ContratoRepository contratoRepository;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                          EmailService emailService, ContratoRepository contratoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.contratoRepository = contratoRepository;
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
        verificarQueCabeEnBcrypt(request.password());
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
        verificarQueCabeEnBcrypt(request.password());
        // Cambiar el rol del último administrador lo deja fuera de /api/usuarios,
        // que es el único camino para volver a crear o promover a alguien: el
        // sistema quedaría sin forma de recuperarse por la API.
        if (request.rol() != Rol.ADMINISTRADOR) {
            verificarQueNoEsElUltimoAdministrador(usuario,
                    "No se puede cambiar el rol del único administrador del sistema.");
        }
        if (usuario.getRol() == Rol.SUPERVISOR && request.rol() != Rol.SUPERVISOR) {
            verificarQueNoSupervisaContratosAbiertos(usuario, "quitarle el rol de supervisor a");
        }
        usuario.setNombre(request.nombre().trim());
        usuario.setEmail(email);
        usuario.setTelefono(request.telefono().trim());
        usuario.setRol(request.rol());
        if (request.password() != null && !request.password().isBlank()) {
            usuario.setPassword(passwordEncoder.encode(request.password()));
            // Restablecer la contraseña es lo que se hace ante una cuenta que
            // se sospecha comprometida: los tokens emitidos con la anterior
            // tienen que dejar de valer ya, no dentro de ocho horas.
            usuario.revocarSesiones();
        }
        return UsuarioMapper.toResponse(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioResponse cambiarEstado(Long id, CambiarEstadoUsuarioRequest request) {
        Usuario usuario = buscar(id);
        if (!request.activo()) {
            verificarQueNoEsElUltimoAdministrador(usuario,
                    "No se puede desactivar el único administrador del sistema.");
            verificarQueNoSupervisaContratosAbiertos(usuario, "desactivar a");
        }
        if (usuario.isActivo() && !request.activo()) {
            // Mientras está inactiva el filtro ya la rechaza; esto es para que
            // reactivarla no resucite los tokens que tenía antes.
            usuario.revocarSesiones();
        }
        usuario.setActivo(request.activo());
        return UsuarioMapper.toResponse(usuarioRepository.save(usuario));
    }

    /**
     * La validación del DTO cuenta caracteres y BCrypt cuenta bytes: una
     * contraseña de menos de 72 caracteres con varias tildes puede pasar la
     * primera y reventar la segunda. Se comprueba antes de codificar para
     * responder con un 400 que diga qué corregir en vez de un 500.
     */
    private void verificarQueCabeEnBcrypt(String password) {
        if (!LimiteDeBcrypt.cabe(password)) {
            throw new BusinessException(LimiteDeBcrypt.MENSAJE);
        }
    }

    /**
     * Impide dejar contratos en curso a cargo de alguien que ya no puede
     * supervisarlos.
     *
     * <p>Desactivar a un supervisor, o quitarle el rol, no avisaba de nada: sus
     * contratos seguían asignados a una persona que no puede iniciar sesión, el
     * aviso de asignación y el resumen semanal iban a una cuenta muerta y nadie
     * podía firmar los documentos del contrato, sin que el sistema lo dijera en
     * ningún momento. Ahora la operación se rechaza nombrando los contratos que
     * hay que reasignar antes.
     *
     * <p>Si lo urgente es cortar el acceso de una cuenta comprometida, no hace
     * falta esperar a reasignar: restablecer la contraseña cierra al momento
     * todas sus sesiones (ver {@code Usuario.revocarSesiones}).
     */
    private void verificarQueNoSupervisaContratosAbiertos(Usuario usuario, String accion) {
        List<Contrato> abiertos = contratoRepository.findBySupervisorIdAndEstadoInOrderByNumeroContratoAsc(
                usuario.getId(), SeguimientoService.ABIERTOS);
        if (abiertos.isEmpty()) {
            return;
        }
        String numeros = abiertos.stream()
                .limit(MAX_CONTRATOS_EN_MENSAJE)
                .map(Contrato::getNumeroContrato)
                .collect(Collectors.joining(", "));
        if (abiertos.size() > MAX_CONTRATOS_EN_MENSAJE) {
            numeros += " y " + (abiertos.size() - MAX_CONTRATOS_EN_MENSAJE) + " más";
        }
        throw new BusinessException("No se puede " + accion + " " + usuario.getNombre() + ": tiene asignados "
                + abiertos.size() + " contrato(s) en curso (" + numeros + "). Reasígnelos a otro supervisor "
                + "y vuelva a intentarlo.");
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
        // El cuerpo trae la contraseña en claro y nada garantizaba que fuera la
        // guardada: un error al escribirla, o dos administradores restableciendo
        // la misma cuenta a la vez, mandaban por correo una contraseña que no
        // funciona, y nadie se enteraba hasta que el supervisor quedaba
        // bloqueado. Se responde como un envío no realizado, con el motivo, para
        // que la pantalla lo muestre tal cual en vez de culpar a la red.
        if (!passwordEncoder.matches(request.password(), usuario.getPassword())) {
            log.warn("No se enviaron credenciales a {}: la contraseña indicada no es la guardada.",
                    usuario.getEmail());
            return new EnviarCredencialesResponse(false,
                    "No se envió el correo: la contraseña indicada no es la que tiene guardada la cuenta. "
                            + "Restablézcala de nuevo y envíe la que quede asignada.");
        }
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
