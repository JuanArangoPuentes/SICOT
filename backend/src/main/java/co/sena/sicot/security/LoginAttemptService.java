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

    /**
     * Ventana en la que se acumulan los fallos. Pasado este tiempo desde el
     * último intento fallido, el contador vuelve a empezar: cinco errores de
     * tecleo repartidos a lo largo de un mes no son un ataque de fuerza bruta.
     */
    private static final Duration VENTANA_INTENTOS = Duration.ofMinutes(15);

    /**
     * Tope de emails vigilados a la vez. La clave del mapa la elige quien llama
     * al login, así que sin este tope un atacante que envíe emails aleatorios
     * distintos haría crecer el mapa hasta agotar la memoria. Al superarlo se
     * purgan las entradas que ya caducaron; el número es holgado para el uso
     * real de SICOT (decenas de cuentas) y aun así acota el crecimiento.
     */
    private static final int MAX_ENTRADAS = 10_000;

    private final ConcurrentHashMap<String, Estado> intentosPorEmail = new ConcurrentHashMap<>();

    public void verificarNoBloqueado(String email) {
        Estado estado = intentosPorEmail.get(normalizar(email));
        if (estado != null && estado.sigueBloqueado()) {
            throw new BusinessException(
                    "Demasiados intentos fallidos con este correo. Intente de nuevo en unos minutos.");
        }
    }

    public void registrarFallo(String email) {
        if (intentosPorEmail.size() >= MAX_ENTRADAS) {
            purgarEntradasCaducadas();
        }
        intentosPorEmail.compute(normalizar(email), (clave, actual) -> {
            Instant ahora = Instant.now();
            // El contador se reinicia si la entrada anterior ya caducó. Sin esto,
            // una cuenta que alguna vez llegó a 5 fallos quedaba atrapada: cada
            // error posterior, por aislado que fuera, la volvía a bloquear otros
            // 15 minutos, indefinidamente, y solo un login exitoso lo limpiaba
            // — imposible si justamente lo que pasa es que no recuerda la clave.
            int intentos = (actual == null || actual.caducado(ahora)) ? 1 : actual.intentos + 1;
            Instant bloqueadoHasta = intentos >= MAX_INTENTOS ? ahora.plus(DURACION_BLOQUEO) : null;
            return new Estado(intentos, bloqueadoHasta, ahora);
        });
    }

    public void registrarExito(String email) {
        intentosPorEmail.remove(normalizar(email));
    }

    private void purgarEntradasCaducadas() {
        Instant ahora = Instant.now();
        intentosPorEmail.values().removeIf(estado -> estado.caducado(ahora));
    }

    private String normalizar(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private record Estado(int intentos, Instant bloqueadoHasta, Instant ultimoIntento) {

        private boolean sigueBloqueado() {
            return bloqueadoHasta != null && bloqueadoHasta.isAfter(Instant.now());
        }

        /**
         * Una entrada caduca cuando ya no está bloqueada y su último intento
         * quedó fuera de la ventana: en ese punto no aporta nada y puede
         * olvidarse o reiniciarse.
         */
        private boolean caducado(Instant ahora) {
            if (bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora)) {
                return false;
            }
            return ultimoIntento.plus(VENTANA_INTENTOS).isBefore(ahora);
        }
    }
}
