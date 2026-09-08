package co.sena.sicot.service;

import co.sena.sicot.automatizacion.EventoDeNegocio;
import co.sena.sicot.dto.registro.RegistroResponse;
import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Registro;
import co.sena.sicot.entity.enums.OrigenRegistro;
import co.sena.sicot.entity.Usuario;
import co.sena.sicot.exception.ResourceNotFoundException;
import co.sena.sicot.mapper.RegistroMapper;
import co.sena.sicot.repository.ContratoRepository;
import co.sena.sicot.repository.RegistroRepository;
import co.sena.sicot.security.SecurityUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * La auditoría del sistema, y —desde ADR-008— también su fuente de eventos.
 *
 * <h2>Por qué el motor de automatizaciones se engancha aquí</h2>
 * Porque este método ya es el punto por el que pasa <b>todo</b> cambio relevante
 * de SICOT. Los trece sitios que modifican algo que importa —crear un contrato,
 * asignar supervisor, cambiar estado, avanzar o revertir una subetapa, firmar un
 * documento, detectar integridad comprometida, asignar o revocar una firma— ya
 * llamaban aquí para dejar constancia.
 *
 * <p>La alternativa era publicar el evento en cada uno de esos trece sitios. Eso
 * habría creado dos listas paralelas —la de acciones auditadas y la de acciones
 * publicadas— que divergen la primera vez que alguien añade una acción nueva y
 * se acuerda de la auditoría pero no del evento. El síntoma sería «las
 * automatizaciones no reaccionan a X», meses después y sin nada roto a la vista.
 *
 * <p>Con un solo punto, esa divergencia no puede existir: si quedó en la
 * auditoría, se publicó.
 */
@Service
public class RegistroService {

    // Tope del listado global: la auditoría se acumula con cada acción del
    // sistema y sin límite terminaría cargando la tabla completa.
    private static final int MAX_REGISTROS_LISTADO = 500;

    private final RegistroRepository registroRepository;
    // Se inyecta el repositorio (no ContratoService) a propósito: ContratoService
    // ya depende de RegistroService para registrar auditoría, así que depender
    // de vuelta de ContratoService crearía un ciclo de beans en Spring.
    private final ContratoRepository contratoRepository;
    private final ApplicationEventPublisher publicador;

    public RegistroService(RegistroRepository registroRepository,
                           ContratoRepository contratoRepository,
                           ApplicationEventPublisher publicador) {
        this.registroRepository = registroRepository;
        this.contratoRepository = contratoRepository;
        this.publicador = publicador;
    }

    /**
     * Deja constancia de una acción de una persona y publica el evento
     * correspondiente.
     *
     * <p>El evento se publica <b>dentro</b> de esta transacción, pero los
     * oyentes del motor están marcados {@code AFTER_COMMIT}: si la transacción
     * de negocio termina en {@code rollback}, ninguna automatización se dispara.
     * Es la diferencia entre reaccionar a «alguien intentó esto» y a «esto pasó».
     */
    @Transactional
    public void registrar(Contrato contrato, String accion, String descripcion) {
        guardar(contrato, accion, descripcion, SecurityUtils.currentUsuario(), OrigenRegistro.USUARIO);
    }

    /**
     * Deja constancia de una acción del propio sistema.
     *
     * <p>No publica evento, y es a propósito: una automatización que registrara
     * un evento capaz de disparar otra automatización abriría la puerta a ciclos
     * —la regla A produce un registro que despierta a la regla B, que produce
     * otro que despierta a la A— y esos ciclos no se manifiestan hasta que hay
     * suficientes reglas como para que nadie los vea venir leyendo el código.
     *
     * <p>Si algún día hace falta encadenar automatizaciones, la forma correcta es
     * que una tarea encole otra de forma explícita, no que se llamen entre sí a
     * través de la auditoría.
     */
    @Transactional
    public void registrarDelSistema(Contrato contrato, String accion, String descripcion) {
        guardar(contrato, accion, descripcion, null, OrigenRegistro.SISTEMA);
    }

    private void guardar(Contrato contrato, String accion, String descripcion,
                         Usuario usuario, OrigenRegistro origen) {
        Registro registro = new Registro();
        registro.setContrato(contrato);
        registro.setUsuario(usuario);
        registro.setAccion(accion);
        registro.setDescripcion(descripcion);
        registro.setOrigen(origen);
        registroRepository.save(registro);

        if (origen == OrigenRegistro.USUARIO) {
            // El número se lee aquí, con la sesión de Hibernate abierta. Después
            // del commit el contrato sería un proxy desatachado y pedirle
            // cualquier campo reventaría dentro del motor, lejos de la causa.
            publicador.publishEvent(new EventoDeNegocio(
                    contrato != null ? contrato.getId() : null,
                    contrato != null ? contrato.getNumeroContrato() : null,
                    accion,
                    descripcion,
                    Instant.now()));
        }
    }

    @Transactional(readOnly = true)
    public List<RegistroResponse> listarPorContrato(Long contratoId) {
        Contrato contrato = contratoRepository.findById(contratoId)
                .orElseThrow(() -> ResourceNotFoundException.of("Contrato", contratoId));
        SecurityUtils.verificarAccesoAlContrato(contrato);
        return registroRepository.findByContratoIdOrderByFechaDesc(contratoId)
                .stream().map(RegistroMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<RegistroResponse> listarTodos() {
        return registroRepository.findAllByOrderByFechaDesc(PageRequest.of(0, MAX_REGISTROS_LISTADO))
                .stream().map(RegistroMapper::toResponse).toList();
    }
}
