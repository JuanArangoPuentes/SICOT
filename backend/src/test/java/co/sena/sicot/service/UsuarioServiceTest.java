package co.sena.sicot.service;

import co.sena.sicot.dto.usuario.ActualizarUsuarioRequest;
import co.sena.sicot.dto.usuario.CambiarEstadoUsuarioRequest;
import co.sena.sicot.dto.usuario.CrearUsuarioRequest;
import co.sena.sicot.dto.usuario.EnviarCredencialesRequest;
import co.sena.sicot.entity.Usuario;
import co.sena.sicot.entity.enums.Rol;
import co.sena.sicot.exception.BusinessException;
import co.sena.sicot.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private co.sena.sicot.repository.UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UsuarioService usuarioService;

    @Test
    void crearUsuarioConEmailDuplicadoLanzaBusinessException() {
        when(usuarioRepository.existsByEmail("duplicado@soy.sena.edu.co")).thenReturn(true);

        CrearUsuarioRequest request = new CrearUsuarioRequest(
                "Duplicado", "duplicado@soy.sena.edu.co", "ClaveTest123", "3000000000", Rol.SUPERVISOR);

        assertThatThrownBy(() -> usuarioService.crear(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ya existe un usuario");
    }

    @Test
    void crearUsuarioValidoCodificaLaContrasena() {
        when(usuarioRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode("ClaveTest123")).thenReturn("$2a$10$hash");

        Usuario guardado = new Usuario();
        guardado.setId(9L);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(9L);
            return u;
        });

        CrearUsuarioRequest request = new CrearUsuarioRequest(
                "Nuevo Supervisor", "nuevo@soy.sena.edu.co", "ClaveTest123", "3000000000", Rol.SUPERVISOR);

        var response = usuarioService.crear(request);

        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.rol()).isEqualTo(Rol.SUPERVISOR);
        assertThat(response.activo()).isTrue();
        assertThat(response.email()).isEqualTo("nuevo@soy.sena.edu.co");
    }

    /**
     * 40 caracteres pasan la validación del DTO, pero con eñes son 80 bytes y
     * BCrypt los rechaza: antes eso salía como 500 «error interno».
     */
    @Test
    void crearConUnaContrasenaQueNoCabeEnBcryptDiceQueCorregir() {
        when(usuarioRepository.existsByEmail(any())).thenReturn(false);

        CrearUsuarioRequest request = new CrearUsuarioRequest(
                "Nuevo Supervisor", "nuevo@soy.sena.edu.co", "ñ".repeat(40), "3000000000", Rol.SUPERVISOR);

        assertThatThrownBy(() -> usuarioService.crear(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("72 bytes");
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void actualizarConUnaContrasenaQueNoCabeEnBcryptDiceQueCorregir() {
        Usuario existente = new Usuario();
        existente.setId(7L);
        existente.setRol(Rol.SUPERVISOR);
        when(usuarioRepository.findById(7L)).thenReturn(Optional.of(existente));

        ActualizarUsuarioRequest request = new ActualizarUsuarioRequest(
                "Nombre", "email@soy.sena.edu.co", "ñandú-".repeat(10), "3000000000", Rol.SUPERVISOR);

        assertThatThrownBy(() -> usuarioService.actualizar(7L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("72 bytes");
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void actualizarUsuarioInexistenteLanzaResourceNotFoundException() {
        when(usuarioRepository.findById(555L)).thenReturn(Optional.empty());

        ActualizarUsuarioRequest request = new ActualizarUsuarioRequest(
                "Nombre", "email@soy.sena.edu.co", null, "3000000000", Rol.GESTION);

        assertThatThrownBy(() -> usuarioService.actualizar(555L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void desactivarUsuarioLoDejaInactivo() {
        Usuario usuario = new Usuario();
        usuario.setId(3L);
        usuario.setRol(Rol.SUPERVISOR);
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(usuario)).thenAnswer(inv -> inv.getArgument(0));

        var response = usuarioService.cambiarEstado(3L, new CambiarEstadoUsuarioRequest(false));

        assertThat(response.activo()).isFalse();
    }

    @Test
    void noPermiteDesactivarAlUltimoAdministradorActivo() {
        Usuario administrador = new Usuario();
        administrador.setId(1L);
        administrador.setRol(Rol.ADMINISTRADOR);
        administrador.setActivo(true);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(administrador));
        when(usuarioRepository.countByRolAndActivoTrue(Rol.ADMINISTRADOR)).thenReturn(1L);

        assertThatThrownBy(() -> usuarioService.cambiarEstado(1L, new CambiarEstadoUsuarioRequest(false)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("único administrador");
    }

    /**
     * La guarda existía solo en {@code cambiarEstado}, así que el último
     * administrador podía degradarse a sí mismo cambiándose el rol y dejar el
     * sistema sin ninguna cuenta capaz de entrar a /api/usuarios — es decir,
     * irrecuperable por la API.
     */
    @Test
    void noPermiteQuitarleElRolAlUltimoAdministradorActivo() {
        Usuario administrador = new Usuario();
        administrador.setId(1L);
        administrador.setRol(Rol.ADMINISTRADOR);
        administrador.setActivo(true);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(administrador));
        when(usuarioRepository.existsByEmailAndIdNot("admin@soy.sena.edu.co", 1L)).thenReturn(false);
        when(usuarioRepository.countByRolAndActivoTrue(Rol.ADMINISTRADOR)).thenReturn(1L);

        ActualizarUsuarioRequest degradar = new ActualizarUsuarioRequest(
                "Administrador", "admin@soy.sena.edu.co", null, "3000000000", Rol.SUPERVISOR);

        assertThatThrownBy(() -> usuarioService.actualizar(1L, degradar))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("único administrador");
    }

    @Test
    void permiteEditarAlUltimoAdministradorMientrasSigaSiendoAdministrador() {
        Usuario administrador = new Usuario();
        administrador.setId(1L);
        administrador.setRol(Rol.ADMINISTRADOR);
        administrador.setActivo(true);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(administrador));
        when(usuarioRepository.existsByEmailAndIdNot("admin@soy.sena.edu.co", 1L)).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        ActualizarUsuarioRequest cambioDeNombre = new ActualizarUsuarioRequest(
                "Administrador SICOT", "admin@soy.sena.edu.co", null, "3000000000", Rol.ADMINISTRADOR);

        var response = usuarioService.actualizar(1L, cambioDeNombre);

        assertThat(response.nombre()).isEqualTo("Administrador SICOT");
        assertThat(response.rol()).isEqualTo(Rol.ADMINISTRADOR);
    }

    /**
     * Mandar por correo una contraseña distinta de la guardada es entregarle al
     * supervisor una credencial que no funciona sin que nadie lo sepa.
     */
    @Test
    void enviarCredencialesConUnaContrasenaQueNoEsLaGuardadaNoEnviaNada() {
        Usuario supervisor = supervisorConHash("$2a$04$guardado");
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(supervisor));
        when(passwordEncoder.matches("OtraClave123", "$2a$04$guardado")).thenReturn(false);

        var respuesta = usuarioService.enviarCredenciales(5L, new EnviarCredencialesRequest("OtraClave123"));

        assertThat(respuesta.enviado()).isFalse();
        assertThat(respuesta.error()).contains("no es la que tiene guardada");
        verify(emailService, never()).enviarCredenciales(any(), any(), any());
    }

    @Test
    void enviarCredencialesConLaContrasenaGuardadaLaEnvia() {
        Usuario supervisor = supervisorConHash("$2a$04$guardado");
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(supervisor));
        when(passwordEncoder.matches("ClaveTest123", "$2a$04$guardado")).thenReturn(true);

        var respuesta = usuarioService.enviarCredenciales(5L, new EnviarCredencialesRequest("ClaveTest123"));

        assertThat(respuesta.enviado()).isTrue();
        verify(emailService).enviarCredenciales("sup@soy.sena.edu.co", "Supervisor", "ClaveTest123");
    }

    private static Usuario supervisorConHash(String hash) {
        Usuario supervisor = new Usuario();
        supervisor.setId(5L);
        supervisor.setNombre("Supervisor");
        supervisor.setEmail("sup@soy.sena.edu.co");
        supervisor.setPassword(hash);
        supervisor.setRol(Rol.SUPERVISOR);
        supervisor.setActivo(true);
        return supervisor;
    }
}
