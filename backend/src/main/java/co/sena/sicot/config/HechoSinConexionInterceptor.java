package co.sena.sicot.config;

import co.sena.sicot.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Lee la hora en que el equipo del supervisor dice haber hecho un cambio que
 * guardó sin red y envía ahora (ADR-015, cola de cambios).
 *
 * <h2>Por qué existe</h2>
 * Un cambio hecho en bodega a las 9:00 y enviado al volver la red a las 15:00
 * llega al servidor a las 15:00. Si la auditoría solo guardara esa hora, el
 * expediente diría que el supervisor cerró el paso cuando ya estaba de vuelta,
 * que es falso. Si en cambio la sustituyera por la del portátil, el expediente
 * pasaría a depender de un reloj que su dueño puede cambiar, que en un
 * expediente público es peor. Se guardan **las dos**, cada una con su nombre:
 * {@code fecha} es cuándo lo recibió el servidor —la única que SICOT garantiza—
 * y {@code hechoSinConexionEn} es la que declaró el equipo.
 *
 * <h2>Por qué un interceptor y no leerla en {@code RegistroService}</h2>
 * Porque una cabecera mal formada tiene que rechazar la petición <b>antes</b>
 * del cambio de negocio. Leída al registrar, el error saldría después de
 * cerrar el sub-paso y obligaría a deshacerlo. Aquí sale como un 400 normal,
 * con el mismo cuerpo de error que el resto de la API, y nada se toca.
 *
 * <p>No se valida que la hora sea razonable (pasada, reciente). Un reloj
 * adelantado es justo lo que la auditoría tiene que dejar a la vista, no
 * esconder: se guarda tal como llegó, rotulada como declarada por el equipo.
 */
@Component
public class HechoSinConexionInterceptor implements HandlerInterceptor {

    public static final String CABECERA = "X-SICOT-Hecho-Sin-Conexion";

    static final String ATRIBUTO = HechoSinConexionInterceptor.class.getName() + ".hora";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String valor = request.getHeader(CABECERA);
        if (valor == null || valor.isBlank()) {
            return true;
        }
        try {
            request.setAttribute(ATRIBUTO, Instant.parse(valor.trim()));
        } catch (DateTimeParseException e) {
            throw new BusinessException("La cabecera " + CABECERA
                    + " debe ser una fecha y hora en UTC con formato ISO-8601, como 2026-10-30T14:05:00Z.");
        }
        return true;
    }

    /**
     * La hora declarada en la petición que se está atendiendo, si la trae.
     * Fuera de una petición (las tareas del motor de automatizaciones corren
     * en otro hilo) no hay ninguna.
     */
    public static Optional<Instant> enLaSolicitudActual() {
        RequestAttributes atributos = RequestContextHolder.getRequestAttributes();
        if (atributos == null) {
            return Optional.empty();
        }
        return Optional.ofNullable((Instant) atributos.getAttribute(ATRIBUTO, RequestAttributes.SCOPE_REQUEST));
    }
}
