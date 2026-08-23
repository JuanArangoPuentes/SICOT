package co.sena.sicot.service;

import co.sena.sicot.dto.contrato.ActualizarContratoRequest;
import co.sena.sicot.dto.contrato.AsignarSupervisorRequest;
import co.sena.sicot.dto.contrato.CambiarEstadoContratoRequest;
import co.sena.sicot.dto.contrato.ContratoResponse;
import co.sena.sicot.dto.contrato.CrearContratoRequest;
import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Usuario;
import co.sena.sicot.entity.enums.EstadoContrato;
import co.sena.sicot.exception.BusinessException;
import co.sena.sicot.exception.ResourceNotFoundException;
import co.sena.sicot.entity.enums.Rol;
import co.sena.sicot.mapper.ContratoMapper;
import co.sena.sicot.repository.ContratoRepository;
import co.sena.sicot.repository.EtapaRepository;
import co.sena.sicot.repository.UsuarioRepository;
import co.sena.sicot.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ContratoService {

    private final ContratoRepository contratoRepository;
    private final UsuarioRepository usuarioRepository;
    private final RegistroService registroService;
    private final EtapaRepository etapaRepository;

    public ContratoService(ContratoRepository contratoRepository,
                           UsuarioRepository usuarioRepository,
                           RegistroService registroService,
                           EtapaRepository etapaRepository) {
        this.contratoRepository = contratoRepository;
        this.usuarioRepository = usuarioRepository;
        this.registroService = registroService;
        this.etapaRepository = etapaRepository;
    }

    @Transactional(readOnly = true)
    public List<ContratoResponse> listar(Long supervisorId, EstadoContrato estado) {
        Usuario actual = SecurityUtils.currentUsuario();
        if (actual != null && actual.getRol() == Rol.SUPERVISOR) {
            // Un supervisor solo puede listar SUS propios contratos — se ignora
            // cualquier supervisorId que haya pedido en la consulta para que no
            // pueda ver los contratos de otro supervisor con solo cambiar el
            // parámetro, y sin filtro no ve el listado completo del sistema.
            supervisorId = actual.getId();
        }
        List<Contrato> contratos;
        if (supervisorId != null && estado != null) {
            contratos = contratoRepository.findBySupervisorIdAndEstado(supervisorId, estado);
        } else if (supervisorId != null) {
            contratos = contratoRepository.findBySupervisorId(supervisorId);
        } else if (estado != null) {
            contratos = contratoRepository.findByEstado(estado);
        } else {
            contratos = contratoRepository.findAll();
        }
        return contratos.stream().map(ContratoMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ContratoResponse obtener(Long id) {
        return ContratoMapper.toResponse(buscar(id));
    }

    @Transactional
    public ContratoResponse crear(CrearContratoRequest request) {
        String numero = request.numeroContrato().trim();
        if (contratoRepository.existsByNumeroContrato(numero)) {
            throw new BusinessException("Ya existe un contrato con el número " + numero + ".");
        }
        if (request.fechaFin() != null && request.fechaInicio() != null
                && request.fechaFin().isBefore(request.fechaInicio())) {
            throw new BusinessException("La fecha de fin no puede ser anterior a la fecha de inicio.");
        }

        Contrato contrato = new Contrato();
        contrato.setNumeroContrato(numero);
        contrato.setObjeto(request.objeto().trim());
        contrato.setValor(request.valor());
        contrato.setFechaInicio(request.fechaInicio());
        contrato.setFechaFin(request.fechaFin());
        contrato.setEstado(EstadoContrato.BORRADOR);
        contrato.setTipoContrato(request.tipoContrato());
        contrato.setContratista(request.contratista());
        contrato.setContratistaNit(request.contratistaNit());
        contrato.setRepresentanteLegal(request.representanteLegal());
        contrato.setLugarEjecucion(request.lugarEjecucion());
        contrato.setNumeroRegistroPresupuestal(request.numeroRegistroPresupuestal());
        contrato.setFechaRegistroPresupuestal(request.fechaRegistroPresupuestal());
        contrato.setCentroCosto(request.centroCosto());
        if (request.supervisorId() != null) {
            contrato.setSupervisor(buscarSupervisor(request.supervisorId()));
        }
        Contrato guardado = contratoRepository.save(contrato);
        etapaRepository.saveAll(GcconP010Plantilla.crearEtapas(guardado));
        registroService.registrar(guardado, "CONTRATO_CREADO",
                "Contrato " + numero + " creado en estado BORRADOR.");
        return ContratoMapper.toResponse(guardado);
    }

    @Transactional
    public ContratoResponse actualizar(Long id, ActualizarContratoRequest request) {
        Contrato contrato = buscar(id);
        String numero = request.numeroContrato().trim();
        if (contratoRepository.existsByNumeroContratoAndIdNot(numero, id)) {
            throw new BusinessException("Ya existe un contrato con el número " + numero + ".");
        }
        if (request.fechaFin() != null && request.fechaInicio() != null
                && request.fechaFin().isBefore(request.fechaInicio())) {
            throw new BusinessException("La fecha de fin no puede ser anterior a la fecha de inicio.");
        }
        contrato.setNumeroContrato(numero);
        contrato.setObjeto(request.objeto().trim());
        contrato.setValor(request.valor());
        contrato.setFechaInicio(request.fechaInicio());
        contrato.setFechaFin(request.fechaFin());
        contrato.setTipoContrato(request.tipoContrato());
        contrato.setContratista(request.contratista());
        contrato.setContratistaNit(request.contratistaNit());
        contrato.setRepresentanteLegal(request.representanteLegal());
        contrato.setLugarEjecucion(request.lugarEjecucion());
        contrato.setNumeroRegistroPresupuestal(request.numeroRegistroPresupuestal());
        contrato.setFechaRegistroPresupuestal(request.fechaRegistroPresupuestal());
        contrato.setCentroCosto(request.centroCosto());
        Contrato guardado = contratoRepository.save(contrato);
        registroService.registrar(guardado, "CONTRATO_ACTUALIZADO",
                "Se actualizó la información general del contrato " + numero + ".");
        return ContratoMapper.toResponse(guardado);
    }

    @Transactional
    public ContratoResponse asignarSupervisor(Long id, AsignarSupervisorRequest request) {
        Contrato contrato = buscar(id);
        Usuario supervisor = buscarSupervisor(request.supervisorId());
        contrato.setSupervisor(supervisor);
        Contrato guardado = contratoRepository.save(contrato);
        registroService.registrar(guardado, "SUPERVISOR_ASIGNADO",
                "Supervisor asignado: " + supervisor.getNombre() + " (" + supervisor.getEmail() + ").");
        return ContratoMapper.toResponse(guardado);
    }

    @Transactional
    public ContratoResponse cambiarEstado(Long id, CambiarEstadoContratoRequest request) {
        Contrato contrato = buscar(id);
        contrato.setEstado(request.estado());
        Contrato guardado = contratoRepository.save(contrato);
        registroService.registrar(guardado, "ESTADO_CAMBIADO",
                "Estado del contrato cambiado a " + request.estado().name() + ".");
        return ContratoMapper.toResponse(guardado);
    }

    @Transactional(readOnly = true)
    public Contrato buscar(Long id) {
        Contrato contrato = contratoRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Contrato", id));
        SecurityUtils.verificarAccesoAlContrato(contrato);
        return contrato;
    }

    private Usuario buscarSupervisor(Long supervisorId) {
        Usuario supervisor = usuarioRepository.findById(supervisorId)
                .orElseThrow(() -> ResourceNotFoundException.of("Usuario (supervisor)", supervisorId));
        if (supervisor.getRol() != Rol.SUPERVISOR) {
            throw new BusinessException("El usuario seleccionado no tiene rol de SUPERVISOR.");
        }
        return supervisor;
    }
}
