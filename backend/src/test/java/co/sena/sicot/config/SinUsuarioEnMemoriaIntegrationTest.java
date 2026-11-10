package co.sena.sicot.config;

import co.sena.sicot.PruebaDeIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Que Spring Boot no cree el usuario en memoria con contraseña aleatoria.
 *
 * <p>{@code application.properties} excluye esa autoconfiguración por su nombre
 * completo de clase, y ese nombre cambia de paquete en Spring Boot 4 (ADR-014,
 * «los tres puntos que la red de pruebas no ve», el 1). Una exclusión que nombra
 * una clase que ya no existe no da error: Boot la descarta en silencio, vuelve a
 * crear el usuario y escribe en el log de producción el «Using generated
 * security password… your security configuration must be updated before running
 * in production» que la exclusión existe para evitar. No es explotable —no hay
 * formLogin ni httpBasic—, pero ninguna otra prueba lo notaría.
 *
 * <p>SICOT no declara ningún {@code UserDetailsService} propio: la autenticación
 * la resuelve {@code JwtAuthenticationFilter} contra la tabla {@code usuarios}.
 * Así que cualquiera que aparezca en el contexto es el de la autoconfiguración.
 */
class SinUsuarioEnMemoriaIntegrationTest extends PruebaDeIntegracion {

    @Autowired
    private ApplicationContext contexto;

    @Test
    void elContextoNoTieneNingunUserDetailsService() {
        assertThat(contexto.getBeanNamesForType(UserDetailsService.class))
                .as("la exclusión de UserDetailsServiceAutoConfiguration dejó de aplicarse")
                .isEmpty();
    }
}
