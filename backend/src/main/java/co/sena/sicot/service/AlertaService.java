package co.sena.sicot.service;

import co.sena.sicot.dto.alerta.AlertaResponse;
import co.sena.sicot.entity.Alerta;
import co.sena.sicot.entity.enums.PrioridadAlerta;
import co.sena.sicot.entity.enums.TipoAlerta;
import co.sena.sicot.exception.ResourceNotFoundException;
import co.sena.sicot.mapper.AlertaMapper;
import co.sena.sicot.repository.AlertaRepository;
import co.sena.sicot.repository.ContratoRepository;
import co.sena.sicot.security.SecurityUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AlertaService {

    // Tope del listado global: sin esto, "todas las alertas" crecería sin
    // límite con el uso normal del sistema hasta cargar la tabla entera.
    private static final int MAX_ALERTAS_LISTADO = 500;

    private final AlertaRepository alertaRepository;
    private final ContratoService contratoService;
    // El repositorio directo, no ContratoService, para la ruta del sistema:
    // ContratoService.buscar comprueba el acceso del usuario autenticado, y en
    // un hilo del motor no hay ninguno. Mismo criterio que en RegistroService.
    private final ContratoRepository contratoRepository;

    public AlertaService(AlertaRepository alertaRepository,
                         ContratoService contratoService,
                         ContratoRepository contratoRepository) {
        this.alertaRepository = alertaRepository;
        this.contratoService = contratoService;
        this.contratoRepository = contratoRepository;
    }

    @Transactional(readOnly = true)
    public List<AlertaResponse> listarPorContrato(Long contratoId) {
        contratoService.buscar(contratoId);
        return alertaRepository.findByContratoIdOrderByFechaCreacionDesc(contratoId).stream()
                .map(AlertaMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AlertaResponse> listarTodas() {
        return alertaRepository.findAllByOrderByFechaCreacionDesc(PageRequest.of(0, MAX_ALERTAS_LISTADO)).stream()
                .map(AlertaMapper::toResponse)
                .toList();
    }

    /**
     * Crea una alerta a nombre del sistema, no de una persona.
     *
     * <h2>Por qué es un método aparte y no un {@code crear} genérico</h2>
     * Hasta ADR-008, <b>nada</b> en SICOT creaba alertas: la tabla existía desde
     * V1 con sus diez tipos y este servicio solo sabía listarlas y marcarlas
     * leídas. La pantalla de alertas de un contrato real estaba vacía por
     * construcción.
     *
     * <p>Al abrir la escritura, la pregunta es quién puede escribir. La respuesta
     * es: solo el motor de automatizaciones. No hay ningún caso de uso en el que
     * una persona cree una alerta a mano —el sistema avisa, la gente lee— y un
     * método genérico invitaría a que un controlador nuevo lo expusiera algún
     * día sin que nadie decidiera nada. El nombre deja escrito quién es el
     * llamador legítimo.
     *
     * <p>No comprueba acceso al contrato a propósito: no hay usuario autenticado
     * en un hilo del motor, y la comprobación existe para acotar lo que ve un
     * SUPERVISOR, no para acotar lo que hace el sistema.
     */
    @Transactional
    public Alerta crearDelSistema(Long contratoId, TipoAlerta tipo, PrioridadAlerta prioridad, String mensaje) {
        Alerta alerta = new Alerta();
        if (contratoId != null) {
            // Se resuelve aquí dentro y no lo recibe ya resuelto: quien llama es
            // un hilo del motor cuya transacción de lectura ya se cerró, así que
            // una entidad que viniera de fuera sería un proxy de otra sesión.
            alerta.setContrato(contratoRepository.findById(contratoId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Contrato", contratoId)));
        }
        alerta.setTipo(tipo);
        alerta.setPrioridad(prioridad);
        alerta.setMensaje(mensaje);
        return alertaRepository.save(alerta);
    }

    @Transactional
    public AlertaResponse marcarLeida(Long id) {
        Alerta alerta = alertaRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Alerta", id));
        SecurityUtils.verificarAccesoAlContrato(alerta.getContrato());
        alerta.setLeida(true);
        return AlertaMapper.toResponse(alertaRepository.save(alerta));
    }
}
