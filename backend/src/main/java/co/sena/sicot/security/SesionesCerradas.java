package co.sena.sicot.security;

import co.sena.sicot.entity.SesionCerrada;
import co.sena.sicot.entity.Usuario;
import co.sena.sicot.repository.SesionCerradaRepository;
import co.sena.sicot.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Lo que hace que «cerrar sesión» cierre la sesión.
 *
 * <h2>Qué estaba mal</h2>
 * Cerrar sesión era una operación <b>solo del cliente</b>: la aplicación
 * borraba el token de {@code localStorage} y lo olvidaba. En el servidor ese
 * token seguía siendo válido hasta su expiración, <b>ocho horas</b> más tarde.
 * Quien lo hubiera copiado antes seguía entrando con él, y la persona que
 * acababa de pulsar «Cerrar sesión» tenía todos los motivos para creer que ya
 * no se podía. No había ningún endpoint de cierre de sesión.
 *
 * <h2>Por qué no bastaba lo que ya había</h2>
 * {@link Usuario#revocarSesiones()} existe desde V19 y funciona, pero invalida
 * <b>todas</b> las sesiones de la cuenta de golpe. Es lo correcto para lo que
 * fue pensado —cambiar la contraseña, desactivar la cuenta— y demasiado para
 * esto: un supervisor trabaja a la vez con la aplicación de escritorio y con el
 * APK, y cerrar sesión en el portátil del Centro no puede echarlo del teléfono
 * que lleva a bodega. Hacía falta invalidar <i>un token</i>.
 *
 * <h2>Cómo</h2>
 * Cada token lleva un {@code jti} propio (ver {@link JwtService#generateToken}).
 * Cerrar sesión lo anota en {@code sesiones_cerradas} y
 * {@link JwtAuthenticationFilter} rechaza cualquier token cuyo {@code jti} esté
 * en la tabla. Es una lista de <b>negación</b>: la autenticación sigue sin
 * estado y lo único que se guarda son las excepciones, que son pocas y se
 * borran solas al caducar.
 *
 * <h2>El token sin `jti`</h2>
 * Un token emitido antes de V25 no lo trae, así que no se puede anotar. Para
 * esos, cerrar sesión recurre a revocar la cuenta entera: es más de lo que se
 * pidió, pero nunca menos, y el caso desaparece solo en cuanto todos vuelven a
 * entrar. La alternativa —rechazar los tokens sin {@code jti} al desplegar—
 * habría cerrado la sesión de todo el Centro a mitad de la tarde.
 */
@Service
public class SesionesCerradas {

    private static final Logger log = LoggerFactory.getLogger(SesionesCerradas.class);

    private final SesionCerradaRepository repositorio;
    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;

    public SesionesCerradas(SesionCerradaRepository repositorio, UsuarioRepository usuarioRepository,
                            JwtService jwtService) {
        this.repositorio = repositorio;
        this.usuarioRepository = usuarioRepository;
        this.jwtService = jwtService;
    }

    /**
     * ¿Se cerró la sesión de este token?
     *
     * <p>Corre en <b>cada petición autenticada</b>, así que es deliberadamente
     * una sola búsqueda por clave primaria sobre una tabla que se mantiene
     * pequeña. El filtro ya hacía una consulta por petición para cargar la
     * cuenta; esta es la segunda, y es el precio de que cerrar sesión signifique
     * algo.
     *
     * <p>Un {@code jti} nulo (token anterior a V25) no está en la lista por
     * definición: no hay nada que negar.
     */
    @Transactional(readOnly = true)
    public boolean estaCerrada(String jti) {
        return jti != null && repositorio.existsById(jti);
    }

    /**
     * Cierra la sesión de este token.
     *
     * <p><b>Idempotente a propósito.</b> Cerrar sesión dos veces con el mismo
     * token —un doble clic, un reintento de la cola de cambios— no puede
     * terminar en un error en la cara de quien solo quería salir. La clave
     * primaria lo garantiza en la base y aquí se captura su violación, el mismo
     * patrón de doble llave que usa {@code AlmacenDeTareas.encolar}.
     *
     * @param token el JWT con el que llegó la petición de cierre
     * @param usuario la cuenta autenticada por ese token
     * @return {@code true} si se cerró solo esta sesión; {@code false} si hubo
     *         que revocar la cuenta entera por ser un token sin {@code jti}
     */
    @Transactional
    public boolean cerrar(String token, Usuario usuario) {
        String jti;
        Instant expira;
        try {
            jti = jwtService.extraerJti(token);
            expira = jwtService.extraerExpiracion(token);
        } catch (RuntimeException e) {
            // El token no se puede leer. No debería pasar —el filtro ya lo
            // validó para llegar hasta aquí— pero si pasa, lo seguro es cerrar
            // todo y no dejar la sesión abierta por un fallo de lectura.
            log.warn("No se pudo leer el token al cerrar sesión de {}: se revoca la cuenta entera.",
                    usuario.getEmail());
            revocarCuenta(usuario);
            return false;
        }

        if (jti == null || jti.isBlank()) {
            log.info("Cierre de sesión de {} con un token anterior a V25 (sin jti): se revoca la cuenta entera.",
                    usuario.getEmail());
            revocarCuenta(usuario);
            return false;
        }

        try {
            repositorio.save(new SesionCerrada(jti, usuario, expira));
        } catch (DataIntegrityViolationException e) {
            // Ya estaba cerrada. Es el resultado que se quería.
            log.debug("La sesión {} ya estaba cerrada.", jti);
        }
        log.info("Sesión cerrada para {}; sus otras sesiones siguen abiertas.", usuario.getEmail());
        return true;
    }

    private void revocarCuenta(Usuario usuario) {
        usuarioRepository.findById(usuario.getId()).ifPresent(actual -> {
            actual.revocarSesiones();
            usuarioRepository.save(actual);
        });
    }

    /**
     * Borra las filas cuyo token ya habría caducado solo.
     *
     * <p>Sin esta limpieza la tabla crece una fila por cierre de sesión y para
     * siempre: unas decenas al día en un Centro, que en un año son miles de
     * filas que ya no protegen de nada y que la comprobación de cada petición
     * tiene que seguir indexando. Una vez al día basta, porque el token más
     * largo vive ocho horas.
     *
     * <p>Se le da un margen de un día sobre la expiración en vez de borrar
     * exactamente al caducar: una fila de más no cuesta nada y cubre un reloj
     * desajustado entre el servidor y la base.
     */
    @Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = TimeUnit.DAYS)
    @Transactional
    public void limpiarCaducadas() {
        int borradas = repositorio.borrarCaducadas(Instant.now().minus(java.time.Duration.ofDays(1)));
        if (borradas > 0) {
            log.info("Limpieza de sesiones cerradas: {} fila(s) cuyo token ya había caducado.", borradas);
        }
    }
}
