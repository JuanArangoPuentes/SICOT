package co.sena.sicot.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Le pone un identificador único a cada petición y lo mete en el contexto de
 * logging (MDC) y en la respuesta.
 *
 * <h2>Para qué sirve</h2>
 * Cuando alguien reporta «me salió un error hace un rato», sin esto no hay
 * forma de encontrar esa petición concreta: el log es una sucesión de líneas
 * de distintos usuarios entremezcladas por los hilos de Tomcat, y las de una
 * misma petición ni siquiera están juntas. Con el identificador, todas las
 * líneas que generó una petición comparten la misma marca, el usuario ve esa
 * marca en la cabecera {@code X-Request-Id} de su respuesta, y basta un
 * {@code grep} para reconstruir qué pasó.
 *
 * <h2>Detalles que importan</h2>
 * <ul>
 *   <li>Se ejecuta <b>antes que todo lo demás</b> ({@code HIGHEST_PRECEDENCE}),
 *       incluido el filtro de JWT, para que también queden trazados los fallos
 *       de autenticación — que son justo los que más cuesta diagnosticar.</li>
 *   <li>Si el cliente manda su propio {@code X-Request-Id} se respeta, pero
 *       <b>saneado y truncado</b>: ese valor termina en el log, y un valor con
 *       saltos de línea permitiría inyectar líneas falsas en el registro.</li>
 *   <li>El {@code finally} que limpia el MDC no es opcional: los hilos de
 *       Tomcat se reutilizan, y sin limpiar, la siguiente petición atendida por
 *       ese mismo hilo heredaría el identificador de la anterior.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelacionDeSolicitudFilter extends OncePerRequestFilter {

    public static final String CABECERA = "X-Request-Id";
    public static final String CLAVE_MDC = "requestId";

    private static final int LONGITUD_MAXIMA = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String id = sanear(request.getHeader(CABECERA));
        if (id == null) {
            id = UUID.randomUUID().toString().substring(0, 8);
        }
        MDC.put(CLAVE_MDC, id);
        response.setHeader(CABECERA, id);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CLAVE_MDC);
        }
    }

    /**
     * Deja pasar solo caracteres inocuos para un log de una sola línea. Devuelve
     * {@code null} si no queda nada aprovechable, para generar uno propio.
     */
    private String sanear(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpio = valor.trim().replaceAll("[^A-Za-z0-9._-]", "");
        if (limpio.isEmpty()) {
            return null;
        }
        return limpio.length() > LONGITUD_MAXIMA ? limpio.substring(0, LONGITUD_MAXIMA) : limpio;
    }
}
