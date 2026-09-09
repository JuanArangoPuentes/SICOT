package co.sena.sicot.security;

import co.sena.sicot.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService {

    /**
     * Mínimo de HMAC-SHA256 y de la RFC 7518 §3.2: la clave debe tener al menos
     * tantos bytes como la salida del hash. jjwt lo exige también, pero aquí se
     * comprueba antes para poder decir qué pasa y cómo arreglarlo.
     */
    private static final int MINIMO_BYTES = 32;

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${sicot.security.jwt-secret}") String secret,
                      @Value("${sicot.security.jwt-expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(claveValidada(secret));
        this.expirationMs = expirationMs;
    }

    /**
     * Convierte la propiedad en bytes de clave, o detiene el arranque.
     *
     * <p>Este método es el que hace que el arranque sea <i>fail closed</i>. El
     * perfil por defecto es "prod" y prod no trae ningún secreto de
     * conveniencia, así que arrancar sin configurar {@code JWT_SECRET} llega
     * aquí y muere. Antes esa misma situación la resolvía el perfil "dev" por
     * defecto: el backend arrancaba tan campante firmando tokens con una clave
     * publicada en el repositorio, que es un fallo que nadie ve hasta que ya
     * ocurrió.
     *
     * <p>Los tres mensajes dicen qué falta y cómo generarlo. Un error de
     * arranque que obliga a leer el código fuente para entenderlo termina en
     * alguien copiando el primer valor que encuentre, y el primer valor que se
     * encuentra en este repositorio es justo el que no debe usarse.
     */
    private static byte[] claveValidada(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("""
                    Falta JWT_SECRET. El backend no arranca sin una clave de firma propia.

                    Genere una y póngala en el .env del despliegue:
                      openssl rand -base64 48

                    (Sólo los perfiles "dev" y "test" traen un valor de conveniencia, y ese \
                    valor está publicado en este repositorio: no sirve para un despliegue real.)""");
        }

        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(secret.trim());
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "JWT_SECRET no es Base64 válido. Genérelo con: openssl rand -base64 48", e);
        }

        if (bytes.length < MINIMO_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET decodifica a " + bytes.length + " bytes y HMAC-SHA256 exige al menos "
                            + MINIMO_BYTES + ". Genérelo con: openssl rand -base64 48");
        }
        return bytes;
    }

    public String generateToken(Usuario usuario) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(usuario.getEmail())
                .claim("usuarioId", usuario.getId())
                .claim("nombre", usuario.getNombre())
                .claim("rol", usuario.getRol().name())
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    public String extractEmail(String token) {
        return claims(token).getSubject();
    }

    public boolean isTokenValid(String token, String email) {
        Claims claims = claims(token);
        return claims.getSubject().equals(email) && claims.getExpiration().after(new Date());
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
