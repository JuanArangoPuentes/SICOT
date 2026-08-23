package co.sena.sicot.service;

import co.sena.sicot.dto.registro.RegistroResponse;
import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Registro;
import co.sena.sicot.exception.ResourceNotFoundException;
import co.sena.sicot.mapper.RegistroMapper;
import co.sena.sicot.repository.ContratoRepository;
import co.sena.sicot.repository.RegistroRepository;
import co.sena.sicot.security.SecurityUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    public RegistroService(RegistroRepository registroRepository, ContratoRepository contratoRepository) {
        this.registroRepository = registroRepository;
        this.contratoRepository = contratoRepository;
    }

    @Transactional
    public void registrar(Contrato contrato, String accion, String descripcion) {
        Registro registro = new Registro();
        registro.setContrato(contrato);
        registro.setUsuario(SecurityUtils.currentUsuario());
        registro.setAccion(accion);
        registro.setDescripcion(descripcion);
        registroRepository.save(registro);
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
