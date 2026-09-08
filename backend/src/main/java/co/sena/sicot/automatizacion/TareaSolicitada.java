package co.sena.sicot.automatizacion;

import java.time.Instant;
import java.util.Objects;

/**
 * Lo que una regla devuelve: la intención de hacer algo, todavía sin hacer.
 *
 * <p>Es el contrato que mantiene puras a las reglas. Una regla recibe datos y
 * devuelve cero o más de estas; no toca la base, no manda correos y no llama al
 * modelo. Probarla es llamarla y comparar la lista devuelta — sin Spring, sin
 * H2 y sin servidor SMTP.
 *
 * @param regla              código estable de quien la produjo, para poder
 *                           rastrear después de dónde salió cada alerta
 * @param claveIdempotencia  identifica el <b>hecho</b>, nunca el momento
 * @param contratoId         contrato afectado, o {@code null}
 * @param payload            datos del efecto; de él se deduce el tipo de tarea
 * @param ejecutarEn         a partir de cuándo puede ejecutarse
 */
public record TareaSolicitada(
        String regla,
        String claveIdempotencia,
        Long contratoId,
        PayloadDeTarea payload,
        Instant ejecutarEn
) {

    /**
     * La clave se acota a 200 caracteres porque esa es la anchura de la columna
     * con la restricción UNIQUE. Un truncamiento silencioso en la base sería
     * peor que un fallo: dos hechos distintos con claves largas y prefijo común
     * colisionarían, y el segundo se descartaría como duplicado sin que nadie
     * lo supiera.
     */
    public TareaSolicitada {
        Objects.requireNonNull(regla, "regla");
        Objects.requireNonNull(claveIdempotencia, "claveIdempotencia");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(ejecutarEn, "ejecutarEn");
        if (claveIdempotencia.length() > 200) {
            throw new IllegalArgumentException(
                    "La clave de idempotencia excede 200 caracteres: " + claveIdempotencia);
        }
    }

    /** Para ejecutar cuanto antes, que es el caso normal. */
    public static TareaSolicitada ahora(String regla, String claveIdempotencia, Long contratoId,
                                        PayloadDeTarea payload) {
        return new TareaSolicitada(regla, claveIdempotencia, contratoId, payload, Instant.now());
    }
}
