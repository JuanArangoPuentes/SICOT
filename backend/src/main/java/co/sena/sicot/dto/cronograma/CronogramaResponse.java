package co.sena.sicot.dto.cronograma;

import co.sena.sicot.service.Cronograma;

import java.time.LocalDate;

/**
 * Cómo va un contrato respecto a su plazo, tal como lo consume el panel.
 *
 * <h2>Por qué este endpoint existe</h2>
 * Porque el semáforo se calculaba en el navegador y la alerta persistida en el
 * servidor, con criterios distintos. SICOT podía decir «va a tiempo» en la
 * pantalla y «atrasado 39 puntos» en la bandeja del mismo contrato el mismo día.
 *
 * <p>El cálculo ahora vive una sola vez, en {@link Cronograma}, del lado que
 * FR-002 declara autoridad de las reglas de negocio. El frontend pinta lo que
 * recibe.
 */
public record CronogramaResponse(
        Cronograma.Semaforo semaforo,
        Double fraccionDePlazo,
        Double fraccionDeAvance,
        Double brecha,
        Integer etapaActual,
        LocalDate cierreEsperado,
        Integer diasDeAtraso,
        String mensaje
) {

    public static CronogramaResponse de(Cronograma c) {
        return new CronogramaResponse(c.semaforo(), c.fraccionDePlazo(), c.fraccionDeAvance(),
                c.brecha(), c.etapaActual(), c.cierreEsperado(), c.diasDeAtraso(), c.mensaje());
    }
}
