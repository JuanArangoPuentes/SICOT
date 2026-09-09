package co.sena.sicot.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Cómo va un contrato respecto a su plazo. <b>El único cálculo de cronograma del
 * sistema.</b>
 *
 * <h2>Por qué existe esta clase</h2>
 * Porque llegó a haber dos, y no decían lo mismo.
 *
 * <p>El panel del supervisor calculaba el semáforo en el navegador repartiendo
 * el plazo en seis segmentos iguales y comparando hoy con el final del segmento
 * de la etapa activa. La regla de automatización calculaba otra cosa: la brecha
 * entre fracción de plazo consumida y fracción de subetapas cerradas. Dos
 * algoritmos, dos lenguajes, dos definiciones de «atraso», respondiendo a la
 * misma pregunta.
 *
 * <p>El resultado era que SICOT podía decir «el paso 3 va a tiempo» en la
 * pantalla y «brecha de 39 puntos, revise el avance» en la bandeja de alertas
 * del mismo contrato, el mismo día. Las dos frases del mismo sistema, y la
 * supervisión de un contrato público decidiéndose sobre una de ellas.
 *
 * <p>Aquí está el cálculo, una vez. Lo usan la API que alimenta el panel
 * ({@code GET /api/contratos/{id}/cronograma}) y la regla que persiste la
 * alerta. Si el criterio cambia, cambia para los dos o no compila.
 *
 * <h2>Es una estimación y el texto lo dice</h2>
 * No hay un plazo oficial del SENA por etapa de GCCON-P-010. Repartir el plazo
 * en seis partes iguales es una aproximación razonable y <b>no</b> una regla
 * institucional. Los mensajes lo declaran explícitamente: inventar una regla
 * normativa que nadie aprobó sería peor que no estimar nada.
 *
 * @param semaforo         lectura de un vistazo
 * @param fraccionDePlazo  parte del plazo ya transcurrida (0..1), o {@code null}
 *                         si faltan fechas
 * @param fraccionDeAvance parte del flujo ya cerrada (0..1), o {@code null} si el
 *                         contrato no tiene subetapas sembradas
 * @param brecha           {@code fraccionDePlazo - fraccionDeAvance}, o {@code null}
 * @param etapaActual      número de la etapa en curso (1..6), o {@code null}
 * @param cierreEsperado   fecha en que la etapa actual debería cerrarse según la
 *                         estimación, o {@code null}
 * @param diasDeAtraso     días transcurridos desde {@code cierreEsperado};
 *                         negativo significa que va con holgura
 * @param mensaje          texto listo para mostrar, en español administrativo
 */
public record Cronograma(
        Semaforo semaforo,
        Double fraccionDePlazo,
        Double fraccionDeAvance,
        Double brecha,
        Integer etapaActual,
        LocalDate cierreEsperado,
        Integer diasDeAtraso,
        String mensaje
) {

    public enum Semaforo {
        /** Faltan datos para afirmar nada. No es «va bien»: es «no se sabe». */
        SIN_DATOS,
        VERDE,
        AMARILLO,
        ROJO
    }

    /**
     * Brecha, en puntos, a partir de la cual el atraso deja de explicarse por el
     * ritmo desigual normal de las etapas.
     */
    private static final double BRECHA_AMARILLA = 0.15;
    private static final double BRECHA_ROJA = 0.30;

    /**
     * @param etapaActual   número de la etapa en curso, o {@code null} si todas
     *                      están cerradas o ninguna ha empezado
     * @param totalEtapas   6 en GCCON-P-010; entra como parámetro para no
     *                      hornear el número del proceso en el cálculo
     */
    public static Cronograma calcular(LocalDate fechaInicio, LocalDate fechaFin,
                                      long subetapasTotales, long subetapasCompletadas,
                                      Integer etapaActual, int totalEtapas,
                                      LocalDate hoy) {

        Double plazo = fraccionDePlazo(fechaInicio, fechaFin, hoy);
        Double avance = subetapasTotales > 0 ? subetapasCompletadas / (double) subetapasTotales : null;

        if (plazo == null || avance == null) {
            return new Cronograma(Semaforo.SIN_DATOS, plazo, avance, null, etapaActual, null, null,
                    plazo == null
                            ? "El contrato no tiene fechas de inicio y fin completas, así que no se puede estimar su cronograma."
                            : "El contrato no tiene subetapas registradas, así que no se puede estimar su avance.");
        }

        double brecha = plazo - avance;
        Semaforo semaforo = brecha >= BRECHA_ROJA ? Semaforo.ROJO
                : brecha >= BRECHA_AMARILLA ? Semaforo.AMARILLO
                : Semaforo.VERDE;

        LocalDate cierreEsperado = cierreEsperadoDe(fechaInicio, fechaFin, etapaActual, totalEtapas);
        Integer diasDeAtraso = cierreEsperado == null
                ? null
                : (int) ChronoUnit.DAYS.between(cierreEsperado, hoy);

        return new Cronograma(semaforo, plazo, avance, brecha, etapaActual, cierreEsperado, diasDeAtraso,
                redactar(semaforo, plazo, avance, brecha, subetapasTotales, subetapasCompletadas,
                        etapaActual, cierreEsperado, diasDeAtraso));
    }

    /** {@code true} cuando el atraso amerita dejar constancia y no solo pintarlo. */
    public boolean mereceAlerta() {
        return semaforo == Semaforo.ROJO;
    }

    /**
     * Tramo de brecha redondeado a decenas. Es lo que hace que un atraso que
     * empeora vuelva a avisar (información nueva) y uno estable no (ruido).
     */
    public int tramoDeBrecha() {
        return brecha == null ? 0 : (int) (Math.floor(brecha * 10) * 10);
    }

    private static Double fraccionDePlazo(LocalDate inicio, LocalDate fin, LocalDate hoy) {
        if (inicio == null || fin == null || !fin.isAfter(inicio)) {
            return null;
        }
        double total = ChronoUnit.DAYS.between(inicio, fin);
        double transcurrido = ChronoUnit.DAYS.between(inicio, hoy);
        if (transcurrido <= 0) {
            return 0.0;
        }
        return Math.min(1.0, transcurrido / total);
    }

    /**
     * Reparte el plazo en {@code totalEtapas} segmentos iguales y devuelve el
     * final del segmento de la etapa en curso. Es la estimación que ya usaba el
     * panel del supervisor, ahora calculada una sola vez y del lado del servidor.
     */
    private static LocalDate cierreEsperadoDe(LocalDate inicio, LocalDate fin,
                                              Integer etapaActual, int totalEtapas) {
        if (inicio == null || fin == null || etapaActual == null || totalEtapas <= 0
                || !fin.isAfter(inicio)) {
            return null;
        }
        long totalDias = ChronoUnit.DAYS.between(inicio, fin);
        long hastaElFinalDeLaEtapa = Math.round(totalDias * (etapaActual / (double) totalEtapas));
        return inicio.plusDays(hastaElFinalDeLaEtapa);
    }

    private static String redactar(Semaforo semaforo, double plazo, double avance, double brecha,
                                   long totales, long completadas,
                                   Integer etapaActual, LocalDate cierreEsperado, Integer diasDeAtraso) {
        String base = "El contrato lleva el %.0f%% del plazo consumido y el %.0f%% del flujo completado (%d de %d subetapas)."
                .formatted(plazo * 100, avance * 100, completadas, totales);

        String detalleEtapa = "";
        if (etapaActual != null && cierreEsperado != null && diasDeAtraso != null) {
            detalleEtapa = diasDeAtraso > 0
                    ? " La etapa %d debía cerrarse hacia el %s según el cronograma estimado, hace %d día(s)."
                            .formatted(etapaActual, cierreEsperado, diasDeAtraso)
                    : " La etapa %d debería cerrarse hacia el %s según el cronograma estimado."
                            .formatted(etapaActual, cierreEsperado);
        }

        String juicio = switch (semaforo) {
            case ROJO -> " Va con un atraso de %.0f puntos: revise el avance de las etapas pendientes.".formatted(brecha * 100);
            case AMARILLO -> " Va algo por detrás del plazo (%.0f puntos).".formatted(brecha * 100);
            case VERDE -> " El avance acompaña al plazo.";
            case SIN_DATOS -> "";
        };

        // El aviso de que es una estimación va SIEMPRE, no solo cuando hay
        // atraso: si solo apareciera en los casos malos, la lectura buena se
        // interpretaría como un dato oficial. No existe un plazo por etapa
        // aprobado por el SENA, y el sistema no debe aparentar que sí.
        return base + detalleEtapa + juicio
                + " (Estimación calculada con las fechas del contrato; no hay un plazo oficial por etapa.)";
    }
}
