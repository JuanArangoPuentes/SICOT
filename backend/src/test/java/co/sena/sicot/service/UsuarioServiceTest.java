package co.sena.sicot.service;

import co.sena.sicot.dto.usuario.ActualizarUsuarioRequest;
import co.sena.sicot.dto.usuario.CambiarEstadoUsuarioRequest;
import co.sena.sicot.dto.usuario.CrearUsuarioRequest;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private co.sena.sicot.repository.UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

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
}
