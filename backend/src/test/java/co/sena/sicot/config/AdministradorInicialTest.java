package co.sena.sicot.config;

import co.sena.sicot.entity.Usuario;
import co.sena.sicot.entity.enums.Rol;
import co.sena.sicot.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El arranque del primer administrador era el bloqueo más grave del despliegue:
 * sin él, una base nueva con perfil de producción quedaba con la tabla de
 * usuarios vacía, {@code POST /api/usuarios} exigiendo rol ADMINISTRADOR y el
 * login como único endpoint público. El sistema arrancaba sano y nadie podía
 * entrar nunca.
 *
 * <p>Se prueba invocando directamente el {@code CommandLineRunner} porque el
 * {@code @Configuration} está anotado {@code @Profile("!dev & !test")} y por
 * definición no se carga en el contexto de pruebas.
 */
class AdministradorInicialTest {

    private static final String PASSWORD_VALIDA = "Contrasena-Larga-2026";

    private AdministradorInicial configuracion;
    private PasswordEncoder encoder;
    private UsuarioRepository repositorio;
    private List<Usuario> guardados;

    @BeforeEach
    void preparar() {
        configuracion = new AdministradorInicial();
        encoder = new BCryptPasswordEncoder();
        repositorio = mock(UsuarioRepository.class);
        guardados = new ArrayList<>();
        when(repositorio.save(any(Usuario.class))).thenAnswer(invocacion -> {
            Usuario usuario = invocacion.getArgument(0);
            guardados.add(usuario);
            return usuario;
        });
    }

    @Test
    void creaElAdministradorCuandoLaBaseEstaVacia() throws Exception {
        when(repositorio.count()).thenReturn(0L);

        runner("admin@sena.edu.co", PASSWORD_VALIDA, "Coordinación CTMA").run();

        assertThat(guardados).hasSize(1);
        Usuario admin = guardados.getFirst();
        assertThat(admin.getEmail()).isEqualTo("admin@sena.edu.co");
        assertThat(admin.getNombre()).isEqualTo("Coordinación CTMA");
        assertThat(admin.getRol()).isEqualTo(Rol.ADMINISTRADOR);
        assertThat(admin.isActivo()).isTrue();
    }

    /** La contraseña nunca se guarda en claro. */
    @Test
    void laContrasenaQuedaCifradaConBCrypt() throws Exception {
        when(repositorio.count()).thenReturn(0L);

        runner("admin@sena.edu.co", PASSWORD_VALIDA, null).run();

        String almacenada = guardados.getFirst().getPassword();
        assertThat(almacenada).isNotEqualTo(PASSWORD_VALIDA).startsWith("$2");
        assertThat(encoder.matches(PASSWORD_VALIDA, almacenada)).isTrue();
    }

    /**
     * Idempotencia: con usuarios ya existentes no debe tocar nada. Si
     * "asegurara" el administrador en cada arranque, una variable de entorno
     * olvidada sería una puerta trasera permanente y cualquier cambio de
     * contraseña hecho desde la aplicación se revertiría al reiniciar.
     */
    @Test
    void noHaceNadaSiYaHayUsuarios() throws Exception {
        when(repositorio.count()).thenReturn(3L);

        runner("admin@sena.edu.co", PASSWORD_VALIDA, null).run();

        assertThat(guardados).isEmpty();
    }

    @Test
    void noHaceNadaSiYaHayUsuariosAunqueNoEsteConfigurado() {
        when(repositorio.count()).thenReturn(3L);

        assertThatCode(() -> runner("", "", null).run()).doesNotThrowAnyException();
        assertThat(guardados).isEmpty();
    }

    /**
     * Con la base vacía y sin configuración, el arranque debe fallar: el
     * despliegue quedaría inaccesible y un error explícito es infinitamente
     * mejor que un sistema en pie al que nadie puede entrar.
     */
    @Test
    void fallaElArranqueSiLaBaseEstaVaciaYNoHayConfiguracion() {
        when(repositorio.count()).thenReturn(0L);

        assertThatThrownBy(() -> runner("", "", null).run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SICOT_ADMIN_EMAIL")
                .hasMessageContaining("SICOT_ADMIN_PASSWORD");
        assertThat(guardados).isEmpty();
    }

    @Test
    void fallaSiFaltaSoloLaContrasena() {
        when(repositorio.count()).thenReturn(0L);

        assertThatThrownBy(() -> runner("admin@sena.edu.co", "", null).run())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rechazaUnaContrasenaCorta() {
        when(repositorio.count()).thenReturn(0L);

        assertThatThrownBy(() -> runner("admin@sena.edu.co", "corta123", null).run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("al menos 12 caracteres");
        assertThat(guardados).isEmpty();
    }

    @Test
    void recortaEspaciosDelCorreoYUsaElNombrePorDefecto() throws Exception {
        when(repositorio.count()).thenReturn(0L);

        runner("  admin@sena.edu.co  ", PASSWORD_VALIDA, "   ").run();

        assertThat(guardados.getFirst().getEmail()).isEqualTo("admin@sena.edu.co");
        assertThat(guardados.getFirst().getNombre()).isEqualTo("Administrador SICOT");
    }

    private CommandLineRunner runner(String email, String password, String nombre) {
        when(repositorio.findByEmail(any())).thenReturn(Optional.empty());
        return configuracion.crearAdministradorInicial(repositorio, encoder, email, password,
                nombre == null ? "Administrador SICOT" : nombre);
    }
}
