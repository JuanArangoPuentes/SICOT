package co.sena.sicot.service;

import co.sena.sicot.dto.etapa.EtapaResponse;
import co.sena.sicot.dto.etapa.SubetapaResponse;
import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Etapa;
import co.sena.sicot.entity.Subetapa;
import co.sena.sicot.entity.enums.EstadoEtapa;
import co.sena.sicot.entity.enums.EstadoSubetapa;
import co.sena.sicot.exception.ResourceNotFoundException;
import co.sena.sicot.mapper.EtapaMapper;
import co.sena.sicot.repository.EtapaRepository;
import co.sena.sicot.repository.SubetapaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EtapaService {

    private final EtapaRepository etapaRepository;
    private final SubetapaRepository subetapaRepository;
    private final ContratoService contratoService;
    private final RegistroService registroService;

    public EtapaService(EtapaRepository etapaRepository, SubetapaRepository subetapaRepository,
                        ContratoService contratoService, RegistroService registroService) {
        this.etapaRepository = etapaRepository;
        this.subetapaRepository = subetapaRepository;
        this.contratoService = contratoService;
        this.registroService = registroService;
    }

    @Transactional
    public List<EtapaResponse> listarPorContrato(Long contratoId) {
        Contrato contrato = contratoService.buscar(contratoId);
        List<Etapa> etapas = etapaRepository.findByContratoIdOrderByNumeroAsc(contratoId);
        // Contratos creados antes de que se agregara el seeding automático (ver
        // ContratoService.crear) quedaron sin etapas. Se completan aquí, de forma
        // perezosa e idempotente, en vez de requerir una migración de datos manual.
        if (etapas.isEmpty()) {
            etapas = etapaRepository.saveAll(GcconP010Plantilla.crearEtapas(contrato));
        }
        return etapas.stream()
                .map(EtapaMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EtapaResponse obtenerEtapaDeContrato(Long contratoId, Long etapaId) {
        contratoService.buscar(contratoId);
        Etapa etapa = buscar(etapaId);
        if (!etapa.getContrato().getId().equals(contratoId)) {
            throw ResourceNotFoundException.of("Etapa", etapaId);
        }
        return EtapaMapper.toResponse(etapa);
    }

    @Transactional(readOnly = true)
    public List<SubetapaResponse> listarSubetapas(Long etapaId) {
        return subetapaRepository.findByEtapaIdOrderByCodigoAsc(etapaId).stream()
                .map(EtapaMapper::toSubetapaResponse)
                .toList();
    }

    @Transactional
    public SubetapaResponse cambiarEstadoSubetapa(Long subetapaId, EstadoSubetapa nuevoEstado) {
        Subetapa subetapa = subetapaRepository.findById(subetapaId)
                .orElseThrow(() -> ResourceNotFoundException.of("Subetapa", subetapaId));
        subetapa.setEstado(nuevoEstado);
        subetapaRepository.save(subetapa);
        recalcularEtapa(subetapa.getEtapa());
        return EtapaMapper.toSubetapaResponse(subetapa);
    }

    @Transactional(readOnly = true)
    public Etapa buscar(Long id) {
        return etapaRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Etapa", id));
    }

    private void recalcularEtapa(Etapa etapa) {
        List<Subetapa> subetapas = subetapaRepository.findByEtapaIdOrderByCodigoAsc(etapa.getId());
        if (subetapas.isEmpty()) {
            return;
        }
        long completadas = subetapas.stream().filter(s -> s.getEstado() == EstadoSubetapa.COMPLETADA).count();
        boolean enProgreso = subetapas.stream()
                .anyMatch(s -> s.getEstado() == EstadoSubetapa.EN_CURSO || s.getEstado() == EstadoSubetapa.COMPLETADA);
        int porcentaje = (int) Math.round(completadas * 100.0 / subetapas.size());

        EstadoEtapa nuevoEstado;
        if (porcentaje == 100) {
            nuevoEstado = EstadoEtapa.COMPLETADA;
        } else if (enProgreso) {
            nuevoEstado = EstadoEtapa.EN_CURSO;
        } else {
            nuevoEstado = EstadoEtapa.PENDIENTE;
        }

        boolean cambio = etapa.getEstado() != nuevoEstado || etapa.getPorcentaje() != porcentaje;
        etapa.setEstado(nuevoEstado);
        etapa.setPorcentaje(porcentaje);
        etapaRepository.save(etapa);

        if (cambio) {
            Contrato contrato = etapa.getContrato();
            registroService.registrar(contrato, "ETAPA_ACTUALIZADA",
                    "Etapa " + etapa.getNumero() + " (" + etapa.getNombre() + ") ahora está en "
                            + nuevoEstado.name() + " al " + porcentaje + "%.");
        }
    }
}
