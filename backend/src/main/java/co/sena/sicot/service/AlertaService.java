package co.sena.sicot.service;

import co.sena.sicot.dto.alerta.AlertaResponse;
import co.sena.sicot.entity.Alerta;
import co.sena.sicot.exception.ResourceNotFoundException;
import co.sena.sicot.mapper.AlertaMapper;
import co.sena.sicot.repository.AlertaRepository;
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

    public AlertaService(AlertaRepository alertaRepository, ContratoService contratoService) {
        this.alertaRepository = alertaRepository;
        this.contratoService = contratoService;
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

    @Transactional
    public AlertaResponse marcarLeida(Long id) {
        Alerta alerta = alertaRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Alerta", id));
        SecurityUtils.verificarAccesoAlContrato(alerta.getContrato());
        alerta.setLeida(true);
        return AlertaMapper.toResponse(alertaRepository.save(alerta));
    }
}
