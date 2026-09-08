package co.sena.sicot.automatizacion;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.util.List;

/**
 * Decide qué hay que hacer. Nunca lo hace: encola.
 *
 * <h2>Las dos entradas</h2>
 * <ol>
 *   <li><b>Eventos.</b> Todo cambio relevante del backend pasa ya por
 *       {@code RegistroService.registrar}, que publica un
 *       {@link EventoDeNegocio}. Este componente lo recibe y ofrece a cada
 *       {@link ReglaDeEvento}.</li>
 *   <li><b>Calendario.</b> Una pasada diaria evalúa cada
 *       {@link ReglaDeCalendario} contra los contratos vigentes.</li>
 * </ol>
 *
 * <h2>Por qué AFTER_COMMIT y no un {@code @EventListener} normal</h2>
 * Un oyente corriente se ejecuta dentro de la transacción que publicó el evento.
 * Si esa transacción termina en {@code rollback} —una validación posterior que
 * falla, un choque de bloqueo optimista— el cambio de negocio se deshace pero la
 * automatización ya se disparó. El resultado es un correo al supervisor
 * anunciando una asignación que nunca ocurrió, y no hay forma de retirarlo.
 *
 * <p>{@link TransactionPhase#AFTER_COMMIT} garantiza que solo se reacciona a
 * hechos consumados. Es la diferencia entre «alguien intentó esto» y «esto pasó».
 *
 * <h2>Por qué una regla que falla no tumba a las demás</h2>
 * Las reglas se evalúan en un bucle con captura individual. Sin eso, una regla
 * nueva con un fallo dejaría sin evaluar a todas las que vinieran detrás en la
 * lista, y el síntoma sería «dejaron de llegar las alertas de vencimiento»
 * cuando lo que se tocó fue otra cosa por completo. Ese diagnóstico cuesta
 * horas.
 */
@Component
public class MotorDeAutomatizacion {

    private static final Logger log = LoggerFactory.getLogger(MotorDeAutomatizacion.class);

    private final List<ReglaDeEvento> reglasDeEvento;
    private final List<ReglaDeCalendario> reglasDeCalendario;
    private final LectorDeContratos lectorDeContratos;
    private final AlmacenDeTareas almacen;
    private final Counter tareasEncoladas;
    private final Counter reglasConFallo;

    /**
     * Spring inyecta todas las implementaciones anotadas con {@code @Component}.
     * No hay registro central que mantener: añadir una regla es crear la clase.
     */
    public MotorDeAutomatizacion(List<ReglaDeEvento> reglasDeEvento,
                                 List<ReglaDeCalendario> reglasDeCalendario,
                                 LectorDeContratos lectorDeContratos,
                                 AlmacenDeTareas almacen,
                                 MeterRegistry registry) {
        this.reglasDeEvento = reglasDeEvento;
        this.reglasDeCalendario = reglasDeCalendario;
        this.lectorDeContratos = lectorDeContratos;
        this.almacen = almacen;
        this.tareasEncoladas = Counter.builder("sicot.automatizacion.tareas.encoladas")
                .description("Tareas nuevas creadas por las reglas (las duplicadas no cuentan)")
                .register(registry);
        this.reglasConFallo = Counter.builder("sicot.automatizacion.reglas.fallos")
                .description("Veces que una regla lanzó una excepción al evaluarse")
                .register(registry);

        log.info("Motor de automatizaciones listo: {} regla(s) de evento, {} de calendario.",
                reglasDeEvento.size(), reglasDeCalendario.size());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alOcurrirUnEvento(EventoDeNegocio evento) {
        for (ReglaDeEvento regla : reglasDeEvento) {
            try {
                if (regla.aplicaA(evento)) {
                    encolarTodas(regla.codigo(), regla.evaluar(evento));
                }
            } catch (Exception e) {
                // Capturar por regla y seguir: ver el encabezado de la clase.
                reglasConFallo.increment();
                log.error("La regla de evento '{}' falló procesando {} del contrato {}. "
                                + "El resto de reglas siguen evaluándose.",
                        regla.codigo(), evento.accion(), evento.contratoId(), e);
            }
        }
    }

    /**
     * Evalúa las reglas de calendario contra todos los contratos vigentes.
     *
     * <p>La fecha entra como parámetro para poder ejercitarla en pruebas sin
     * tocar el reloj del sistema. En producción la pone
     * {@link PlanificadorDeAutomatizaciones}.
     *
     * @return cuántas tareas nuevas se encolaron
     */
    public int evaluarCalendario(LocalDate hoy) {
        List<FotoDelContrato> contratos = lectorDeContratos.vigentes();
        if (contratos.isEmpty()) {
            log.debug("Evaluación de calendario: no hay contratos ACTIVO.");
            return 0;
        }

        int encoladas = 0;
        for (FotoDelContrato contrato : contratos) {
            for (ReglaDeCalendario regla : reglasDeCalendario) {
                try {
                    encoladas += encolarTodas(regla.codigo(), regla.evaluar(contrato, hoy));
                } catch (Exception e) {
                    reglasConFallo.increment();
                    log.error("La regla de calendario '{}' falló sobre el contrato {}. "
                                    + "El resto sigue evaluándose.",
                            regla.codigo(), contrato.numeroContrato(), e);
                }
            }
        }
        log.info("Evaluación de calendario del {}: {} contrato(s) revisados, {} tarea(s) nuevas.",
                hoy, contratos.size(), encoladas);
        return encoladas;
    }

    private int encolarTodas(String codigoDeRegla, List<TareaSolicitada> solicitudes) {
        int nuevas = 0;
        for (TareaSolicitada solicitud : solicitudes) {
            if (almacen.encolar(solicitud).isPresent()) {
                nuevas++;
                tareasEncoladas.increment();
                log.debug("Regla '{}' encoló {}", codigoDeRegla, solicitud.claveIdempotencia());
            }
        }
        return nuevas;
    }
}
