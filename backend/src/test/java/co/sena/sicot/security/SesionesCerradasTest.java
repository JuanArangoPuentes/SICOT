package co.sena.sicot.security;

import co.sena.sicot.PruebaDeIntegracion;
import co.sena.sicot.entity.SesionCerrada;
import co.sena.sicot.entity.Usuario;
import co.sena.sicot.repository.SesionCerradaRepository;
import co.sena.sicot.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Los dos caminos de {@link SesionesCerradas} que
 * {@code CierreDeSesionIntegrationTest} no puede recorrer por la API, porque
 * dependen de cómo es el token o de cuánto tiempo ha pasado:
 *
 * <ol>
 *   <li><b>El token sin {@code jti}</b> — los emitidos antes de V25. No se
 *       pueden anotar individualmente, así que cerrar sesión con uno recurre a
 *       revocar la cuenta entera. Es el camino de compatibilidad, el que decide
 *       si un despliegue cierra la sesión de todo el Centro a mitad de la tarde
 *       o no, y por construcción desaparece a las ocho horas: si no se prueba
 *       ahora, no se prueba nunca.</li>
 *   <li><b>La limpieza diaria</b> — sin ella la tabla crece una fila por cierre
 *       de sesión y para siempre, y la comprobación que corre en cada petición
 *       tiene que seguir indexándolas.</li>
 * </ol>
 */
class SesionesCerradasTest extends PruebaDeIntegracion {

    @Autowired
    private SesionesCerradas sesionesCerradas;

    @Autowired
    private SesionCerradaRepository repositorio;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    @DisplayName("un token sin jti cierra la cuenta entera: más de lo pedido, nunca menos")
    void unTokenSinJtiRevocaLaCuenta() {
        Usuario usuario = algunUsuario();
        long versionAntes = usuario.getVersionCredenciales();

        // Un token válido y bien firmado, pero sin `jti`: exactamente lo que
        // emitía el backend antes de V25.
        String tokenAntiguo = io.jsonwebtoken.Jwts.builder()
                .subject(usuario.getEmail())
                .claim("versionCredenciales", versionAntes)
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + 3_600_000))
                .signWith(claveDePruebas())
                .compact();

        boolean soloEsaSesion = sesionesCerradas.cerrar(tokenAntiguo, usuario);

        assertThat(soloEsaSesion)
                .as("devuelve false para que quien llama sepa que cerró más de una sesión")
                .isFalse();
        assertThat(repositorio.count())
                .as("no hay jti que anotar: la tabla no crece con una fila inútil")
                .isZero();
        assertThat(usuarioRepository.findById(usuario.getId()).orElseThrow().getVersionCredenciales())
                .as("la cuenta queda revocada, que es el único mecanismo disponible para ese token")
                .isEqualTo(versionAntes + 1);
    }

    @Test
    @DisplayName("un token ilegible también cierra la cuenta entera en vez de dejar la sesión abierta")
    void unTokenIlegibleRevocaLaCuenta() {
        Usuario usuario = algunUsuario();
        long versionAntes = usuario.getVersionCredenciales();

        // No debería ocurrir —el filtro ya validó el token para llegar aquí—
        // pero si ocurre, lo seguro es cerrar y no ignorar la petición.
        assertThatCode(() -> sesionesCerradas.cerrar("esto-no-es-un-jwt", usuario)).doesNotThrowAnyException();

        assertThat(usuarioRepository.findById(usuario.getId()).orElseThrow().getVersionCredenciales())
                .isEqualTo(versionAntes + 1);
    }

    @Test
    @DisplayName("cerrar dos veces el mismo token no falla: la segunda es un no-op")
    void cerrarDosVecesEsIdempotente() {
        Usuario usuario = algunUsuario();
        String token = jwtService.generateToken(usuario);

        assertThat(sesionesCerradas.cerrar(token, usuario)).isTrue();
        // Un doble clic, o un reintento de la cola de cambios: no puede
        // terminar en un error en la cara de quien solo quería salir.
        assertThatCode(() -> sesionesCerradas.cerrar(token, usuario)).doesNotThrowAnyException();
        assertThat(repositorio.count()).isEqualTo(1);
        assertThat(sesionesCerradas.estaCerrada(jwtService.extraerJti(token))).isTrue();
    }

    @Test
    @DisplayName("un jti nulo nunca está en la lista: no hay nada que negar")
    void unJtiNuloNoEstaCerrado() {
        assertThat(sesionesCerradas.estaCerrada(null)).isFalse();
    }

    @Test
    @DisplayName("la limpieza borra lo caducado y conserva lo que todavía protege")
    void laLimpiezaBorraSoloLoCaducado() {
        Usuario usuario = algunUsuario();
        // Dos días: pasado el margen de un día que deja la limpieza.
        repositorio.save(new SesionCerrada("caducada", usuario, Instant.now().minus(Duration.ofDays(2))));
        repositorio.save(new SesionCerrada("vigente", usuario, Instant.now().plus(Duration.ofHours(4))));

        sesionesCerradas.limpiarCaducadas();

        assertThat(repositorio.findAll())
                .as("una fila cuyo token ya caducó no protege de nada; una vigente sí")
                .extracting(SesionCerrada::getJti)
                .containsExactly("vigente");
    }

    private Usuario algunUsuario() {
        return usuarioRepository.findByEmail("supervisor@soy.sena.edu.co").orElseThrow();
    }

    /** La misma clave del perfil de pruebas, para poder forjar un token de antes de V25. */
    private static javax.crypto.SecretKey claveDePruebas() {
        return io.jsonwebtoken.security.Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64.decode(
                "ZGl2ZWxvcG1lbnQtc2ljb3Qtc2VjcmV0by1qd3Qta2V5LW11eS1sYXJnbzY0LWJ5dGVzLXNlZ3Vybw=="));
    }
}
