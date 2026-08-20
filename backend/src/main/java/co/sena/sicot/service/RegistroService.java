package co.sena.sicot.service;

import co.sena.sicot.dto.registro.RegistroResponse;
import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Registro;
import co.sena.sicot.mapper.RegistroMapper;
import co.sena.sicot.repository.RegistroRepository;
import co.sena.sicot.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RegistroService {

    private final RegistroRepository registroRepository;

    public RegistroService(RegistroRepository registroRepository) {
        this.registroRepository = registroRepository;
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
        return registroRepository.findByContratoIdOrderByFechaDesc(contratoId)
                .stream().map(RegistroMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<RegistroResponse> listarTodos() {
        return registroRepository.findAllByOrderByFechaDesc()
                .stream().map(RegistroMapper::toResponse).toList();
    }
}
