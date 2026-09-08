package co.sena.sicot.automatizacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * El reloj del motor: lo único de este módulo que decide <b>cuándo</b>.
 *
 * <h2>Por qué está separado del ejecutor y del motor</h2>
 * Para que la suite de pruebas no dependa del reloj. Todo lo que hace esta clase
 * es llamar a métodos públicos de otros dos beans; apagándola por configuración,
 * una prueba puede encolar, ejecutar y comprobar el resultado en el orden que
 * quiera, de forma determinista.
 *
 * <p>Con el {@code @Scheduled} dentro del ejecutor eso no sería posible: los
 * temporizadores dispararían en mitad de las comprobaciones y la suite pasaría o
 * fallaría según lo cargada que estuviera la máquina. Ese tipo de prueba
 * intermitente termina desactivada, y con ella la cobertura real del módulo.
 *
 * <h2>Por qué {@code fixedDelay} y no {@code fixedRate}</h2>
 * {@code fixedDelay} cuenta desde que termina la ronda anterior, así que dos
 * rondas nunca se solapan. Con {@code fixedRate}, una ronda lenta se pisaría con
 * la siguiente y ambas competirían por las mismas tareas; funcionaría igual
 * gracias a la reserva atómica, pero se estaría gastando esa garantía en tapar
 * un solapamiento que no hace falta tener.
 */
@Component
@ConditionalOnProperty(name = "sicot.automatizacion.habilitado", havingValue = "true", matchIfMissing = true)
public class PlanificadorDeAutomatizaciones {

    private static final Logger log = LoggerFactory.getLogger(PlanificadorDeAutomatizaciones.class);

    private final MotorDeAutomatizacion motor;
    private final EjecutorDeTareas ejecutor;
    private final AutomatizacionProperties propiedades;

    public PlanificadorDeAutomatizaciones(MotorDeAutomatizacion motor,
                                          EjecutorDeTareas ejecutor,
                                          AutomatizacionProperties propiedades) {
        this.motor = motor;
        this.ejecutor = ejecutor;
        this.propiedades = propiedades;
        log.info("Automatizaciones activas: sondeo cada {}, {} trabajador(es), hasta {} intento(s) por tarea.",
                propiedades.sondeo(), propiedades.trabajadores(), propiedades.maximoDeIntentos());
    }

    /** Consume la cola. Ver {@link EjecutorDeTareas#procesarPendientes()}. */
    @Scheduled(fixedDelayString = "${sicot.automatizacion.sondeo:PT1M}")
    public void consumirLaCola() {
        try {
            ejecutor.procesarPendientes();
        } catch (Exception e) {
            // Una excepción que escape de un método @Scheduled cancela las
            // ejecuciones futuras de ESA tarea en silencio. El síntoma sería
            // «las automatizaciones dejaron de correr» sin nada más en el log
            // que una traza de hace días.
            log.error("Fallo inesperado consumiendo la cola de automatizaciones. "
                    + "El sondeo continúa en la siguiente ronda.", e);
        }
    }

    /**
     * Evalúa las reglas de calendario, todos los días a las 06:00.
     *
     * <p>Antes de la jornada, para que los avisos del día estén puestos cuando
     * el supervisor entra, y no a mediodía. Y después de las 03:00 en que corre
     * {@code VigilanciaDeAlmacenamiento}, para no solapar las dos tareas
     * pesadas de la noche.
     */
    @Scheduled(cron = "0 0 6 * * *")
    public void evaluarCalendario() {
        try {
            motor.evaluarCalendario(LocalDate.now());
        } catch (Exception e) {
            log.error("Fallo inesperado evaluando las reglas de calendario. "
                    + "Se reintentará mañana; la cola no se ve afectada.", e);
        }
    }

    /**
     * Rescate al arrancar: nada más levantar, se consume lo que quedara
     * pendiente del despliegue anterior.
     *
     * <p>El primer sondeo tardaría lo que diga {@code sicot.automatizacion.sondeo}
     * en llegar. No es mucho, pero un despliegue que interrumpió el envío de un
     * aviso debería retomarlo en cuanto pueda, no en cuanto toque.
     */
    @Scheduled(initialDelay = 15_000, fixedDelay = Long.MAX_VALUE)
    public void recuperarAlArrancar() {
        try {
            int procesadas = ejecutor.procesarPendientes();
            if (procesadas > 0) {
                log.info("Al arrancar se recuperaron {} tarea(s) que quedaron pendientes.", procesadas);
            }
        } catch (Exception e) {
            log.error("Fallo recuperando tareas pendientes al arrancar.", e);
        }
    }

    /** Expone la configuración efectiva para la pantalla de operación. */
    public AutomatizacionProperties configuracion() {
        return propiedades;
    }
}
