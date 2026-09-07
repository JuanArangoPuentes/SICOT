package co.sena.sicot.security;

import co.sena.sicot.entity.Usuario;
import co.sena.sicot.entity.enums.Rol;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El ciclo de vida del token de sesión.
 *
 * <p>Importa probarlo aparte del resto porque el JWT es la única barrera entre
 * un usuario cualquiera y los contratos del Estado que administra SICOT, y
 * porque sus fallos no se parecen a un fallo: un token que no caduca, o uno
 * cuya firma no se verifica, no rompen nada visible — simplemente dejan pasar a
 * quien no debía. El camino feliz (login correcto → token válido → acceso) ya
 * lo cubre {@code AuthIntegrationTest}; aquí se recorren los caminos que
 * interesan a quien ataca.
 *
 * <p>Sin Spring a propósito: {@link JwtService} recibe su secreto y su
 * caducidad por constructor, así que se puede instanciar con los valores que
 * cada caso necesita —incluida una caducidad ya vencida— sin levantar un
 * contexto ni tocar la base.
 */
class JwtServiceTest {

    private static final String SECRETO = base64De(
            "secreto-de-pruebas-de-sicot-suficientemente-largo-para-hmac-sha");
    private static final String OTRO_SECRETO = base64De(
            "otro-secreto-distinto-igual-de-largo-para-firmar-tokens-ajenos");

    private static final long OCHO_HORAS_MS = 8 * 60 * 60 * 1000L;

    /**
     * Caducidad negativa: el token nace ya vencido. Es la única forma de probar
     * la expiración sin dejar la prueba esperando en reloj real.
     */
    private static final long YA_VENCIDO_MS = -1_000L;

    @Test
    void unTokenReciennEmitidoIdentificaASuDuenoYEsValido() {
        JwtService jwt = new JwtService(SECRETO, OCHO_HORAS_MS);
        Usuario supervisor = usuario("supervisor@soy.sena.edu.co", Rol.SUPERVISOR);

        String token = jwt.generateToken(supervisor);

        assertThat(jwt.extractEmail(token)).isEqualTo("supervisor@soy.sena.edu.co");
        assertThat(jwt.isTokenValid(token, "supervisor@soy.sena.edu.co")).isTrue();
    }

    @Test
    void unTokenCaducadoYaNoSePuedeLeer() {
        JwtService jwt = new JwtService(SECRETO, YA_VENCIDO_MS);
        String token = jwt.generateToken(usuario("supervisor@soy.sena.edu.co", Rol.SUPERVISOR));

        // El rechazo ocurre al analizar el token, no al comprobarlo: la firma se
        // verifica y la fecha se mira en el mismo paso. Por eso el filtro tiene
        // que atrapar JwtException y no confiar solo en isTokenValid().
        assertThatThrownBy(() -> jwt.extractEmail(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void unTokenFirmadoConOtroSecretoEsRechazado() {
        JwtService emisorLegitimo = new JwtService(SECRETO, OCHO_HORAS_MS);
        JwtService atacante = new JwtService(OTRO_SECRETO, OCHO_HORAS_MS);

        // El atacante fabrica un token con los datos que quiera —incluido el rol
        // de administrador— pero sin la llave del servidor.
        String tokenFalsificado = atacante.generateToken(
                usuario("intruso@soy.sena.edu.co", Rol.ADMINISTRADOR));

        assertThatThrownBy(() -> emisorLegitimo.extractEmail(tokenFalsificado))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void unTokenLegitimoConElCuerpoManipuladoEsRechazado() {
        JwtService jwt = new JwtService(SECRETO, OCHO_HORAS_MS);
        String token = jwt.generateToken(usuario("supervisor@soy.sena.edu.co", Rol.SUPERVISOR));

        // Se altera un carácter del cuerpo (la parte del medio) dejando intacta
        // la firma: es el ataque obvio contra un token que se transporta entero
        // en una cabecera y que cualquiera puede leer.
        String[] partes = token.split("\\.");
        char primero = partes[1].charAt(0);
        partes[1] = (primero == 'A' ? 'B' : 'A') + partes[1].substring(1);
        String manipulado = String.join(".", partes);

        assertThatThrownBy(() -> jwt.extractEmail(manipulado))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void unTokenValidoNoSirveParaSuplantarAOtroCorreo() {
        JwtService jwt = new JwtService(SECRETO, OCHO_HORAS_MS);
        String token = jwt.generateToken(usuario("supervisor@soy.sena.edu.co", Rol.SUPERVISOR));

        assertThat(jwt.isTokenValid(token, "administrador@soy.sena.edu.co")).isFalse();
    }

    @Test
    void unaCadenaQueNoEsUnTokenNoRevientaConUnErrorInesperado() {
        JwtService jwt = new JwtService(SECRETO, OCHO_HORAS_MS);

        // Importa el tipo de la excepción, no solo que falle: el filtro solo
        // atrapa JwtException e IllegalArgumentException. Cualquier otra cosa
        // saldría como error 500 en vez de como "no autenticado".
        assertThatThrownBy(() -> jwt.extractEmail("esto-no-es-un-jwt"))
                .isInstanceOfAny(JwtException.class, IllegalArgumentException.class);
    }

    private Usuario usuario(String email, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setNombre("Usuario de prueba");
        usuario.setEmail(email);
        usuario.setRol(rol);
        return usuario;
    }

    private static String base64De(String texto) {
        return Base64.getEncoder().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }
}
