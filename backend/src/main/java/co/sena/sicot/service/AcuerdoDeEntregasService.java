package co.sena.sicot.service;

import co.sena.sicot.dto.entrega.AcuerdoDeEntregasResponse;
import co.sena.sicot.dto.entrega.GuardarAcuerdoDeEntregasRequest;
import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.EntregaAcordada;
import co.sena.sicot.exception.BusinessException;
import co.sena.sicot.repository.EntregaAcordadaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Las fechas de entrega que el supervisor acuerda con el contratista en el paso
 * 2.4 del proceso real.
 *
 * <h2>De dónde viene</h2>
 * Del paso 5 de la lista de chequeo de Alex Zapata: «se establecen fechas de
 * entrega o la prestación del servicio, las fechas se definen de común acuerdo
 * con el contratista respetando los tiempos de inicio y fin del contrato».
 * SICOT armaba el correo que propone acordarlas, pero el acuerdo no quedaba en
 * ninguna parte: el paso se cerraba sin una sola fecha, y nadie podía recordarle
 * al supervisor que mañana llegaba una entrega.
 *
 * <h2>Qué hace cumplir</h2>
 * Lo único que la hoja dice de las fechas: que caigan dentro del plazo del
 * contrato. Una entrega acordada para después del fin del contrato no se puede
 * recibir sin una prórroga, y una anterior al inicio no es una entrega del
 * contrato. Se rechaza al guardar, diciendo cuál, en vez de dejar que aparezca
 * como un problema el día de la entrega.
 *
 * <p>Lo que no decide: cuántas entregas hay ni qué se entrega en cada una. Eso
 * es del acuerdo entre el supervisor y el contratista.
 */
@Service
public class AcuerdoDeEntregasService {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ContratoService contratoService;
    private final EntregaAcordadaRepository entregaRepository;
    private final RegistroService registroService;

    public AcuerdoDeEntregasService(ContratoService contratoService, EntregaAcordadaRepository entregaRepository,
                                    RegistroService registroService) {
        this.contratoService = contratoService;
        this.entregaRepository = entregaRepository;
        this.registroService = registroService;
    }

    /**
     * El acuerdo del contrato. El acceso lo restringe
     * {@code ContratoService.buscar}: al SUPERVISOR, a sus contratos. Mismo
     * patrón que la solicitud de pedido.
     */
    @Transactional(readOnly = true)
    public AcuerdoDeEntregasResponse obtener(Long contratoId) {
        Contrato contrato = contratoService.buscar(contratoId);
        return respuesta(contrato, entregaRepository.findByContratoIdOrderByNumeroAsc(contratoId));
    }

    /**
     * Reemplaza el acuerdo por el que manda la pantalla. Las entregas se
     * numeran por fecha; dos el mismo día conservan el orden en que llegaron.
     */
    @Transactional
    public AcuerdoDeEntregasResponse guardar(Long contratoId, GuardarAcuerdoDeEntregasRequest request) {
        Contrato contrato = contratoService.buscar(contratoId);
        List<GuardarAcuerdoDeEntregasRequest.Entrega> entrada =
                request.entregas() == null ? List.of() : request.entregas();
        for (GuardarAcuerdoDeEntregasRequest.Entrega e : entrada) {
            verificarDentroDelPlazo(contrato, e.fecha());
        }

        // Sort estable: a igual fecha queda el orden de la pantalla.
        List<GuardarAcuerdoDeEntregasRequest.Entrega> ordenadas = new ArrayList<>(entrada);
        ordenadas.sort(Comparator.comparing(GuardarAcuerdoDeEntregasRequest.Entrega::fecha));

        // Borrar e insertar, con el flush en medio por la misma razón que en la
        // solicitud de pedido: sin él, Hibernate manda los INSERT antes que el
        // DELETE y chocan con el número único.
        entregaRepository.borrarDelContrato(contratoId);
        entregaRepository.flush();
        List<EntregaAcordada> nuevas = new ArrayList<>();
        int numero = 1;
        for (GuardarAcuerdoDeEntregasRequest.Entrega e : ordenadas) {
            nuevas.add(new EntregaAcordada(contrato, numero++, e.fecha(), e.descripcion().strip()));
        }
        List<EntregaAcordada> guardadas = entregaRepository.saveAll(nuevas);

        // Constancia en el expediente: el acuerdo con el contratista es un hecho
        // del contrato, y cambiar una fecha también lo es.
        registroService.registrar(contrato, "ENTREGAS_ACORDADAS", guardadas.isEmpty()
                ? "Se borró el acuerdo de entregas con el contratista."
                : "Fechas de entrega acordadas con el contratista: " + guardadas.stream()
                        .map(g -> g.getNumero() + ") " + DIA.format(g.getFecha()))
                        .collect(Collectors.joining(", ")) + ".");
        return respuesta(contrato, guardadas);
    }

    /**
     * La regla de la hoja de Alex: «respetando los tiempos de inicio y fin del
     * contrato». Si el contrato no tiene una de las dos fechas registrada, ese
     * lado no se puede comprobar y no se inventa: se comprueba el que haya.
     */
    static void verificarDentroDelPlazo(Contrato contrato, LocalDate fecha) {
        LocalDate inicio = contrato.getFechaInicio();
        LocalDate fin = contrato.getFechaFin();
        boolean antes = inicio != null && fecha.isBefore(inicio);
        boolean despues = fin != null && fecha.isAfter(fin);
        if (!antes && !despues) {
            return;
        }
        String plazo = inicio != null && fin != null
                ? "del " + DIA.format(inicio) + " al " + DIA.format(fin)
                : inicio != null ? "desde el " + DIA.format(inicio) : "hasta el " + DIA.format(fin);
        throw new BusinessException("La entrega del " + DIA.format(fecha) + " cae fuera del plazo del contrato ("
                + plazo + "). Las fechas de entrega se acuerdan dentro del plazo"
                + (despues ? "; para entregar después del fin hace falta una prórroga del contrato." : "."));
    }

    private static AcuerdoDeEntregasResponse respuesta(Contrato contrato, List<EntregaAcordada> entregas) {
        return new AcuerdoDeEntregasResponse(
                entregas.stream()
                        .sorted(Comparator.comparing(EntregaAcordada::getNumero))
                        .map(e -> new AcuerdoDeEntregasResponse.Entrega(e.getNumero(), e.getFecha(),
                                e.getDescripcion()))
                        .toList(),
                contrato.getFechaInicio(),
                contrato.getFechaFin());
    }
}
