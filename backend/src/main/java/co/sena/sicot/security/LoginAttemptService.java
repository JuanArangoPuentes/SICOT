package co.sena.sicot.security;

import co.sena.sicot.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mitigación simple de fuerza bruta sobre /api/auth/login: bloquea un email
 * durante un rato tras varios intentos fallidos seguidos. En memoria (no hay
 * más de una instancia del backend corriendo a la vez, así que no hace falta
 * un almacén compartido); se reinicia si el backend se reinicia, lo cual es
 * aceptable para este riesgo.
 */
@Component
public class LoginAttemptService {

    private static final int MAX_INTENTOS = 5;
    private static final Duration DURACION_BLOQUEO = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, Estado> intentosPorEmail = new ConcurrentHashMap<>();

    public void verificarNoBloqueado(String email) {
        Estado estado = intentosPorEmail.get(normalizar(email));
        if (estado != null && estado.bloqueadoHasta != null && estado.bloqueadoHasta.isAfter(Instant.now())) {
            throw new BusinessException(
                    "Demasiados intentos fallidos con este correo. Intente de nuevo en unos minutos.");
        }
    }

    public void registrarFallo(String email) {
        intentosPorEmail.compute(normalizar(email), (key, actual) -> {
            int intentos = (actual == null ? 0 : actual.intentos) + 1;
            Instant bloqueadoHasta = intentos >= MAX_INTENTOS ? Instant.now().plus(DURACION_BLOQUEO) : null;
            return new Estado(intentos, bloqueadoHasta);
        });
    }

    public void registrarExito(String email) {
        intentosPorEmail.remove(normalizar(email));
    }

    private String normalizar(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private static final class Estado {
        private final int intentos;
        private final Instant bloqueadoHasta;

        private Estado(int intentos, Instant bloqueadoHasta) {
            this.intentos = intentos;
            this.bloqueadoHasta = bloqueadoHasta;
        }
    }
}
