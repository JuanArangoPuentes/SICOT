package co.sena.sicot.service;

import co.sena.sicot.dto.firma.CambiarEstadoFirmaRequest;
import co.sena.sicot.dto.firma.CrearFirmaRequest;
import co.sena.sicot.dto.firma.FirmaResponse;
import co.sena.sicot.entity.FirmaElectronica;
import co.sena.sicot.entity.Usuario;
import co.sena.sicot.exception.ResourceNotFoundException;
import co.sena.sicot.mapper.FirmaElectronicaMapper;
import co.sena.sicot.repository.FirmaElectronicaRepository;
import co.sena.sicot.repository.UsuarioRepository;
import co.sena.sicot.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class FirmaElectronicaService {

    private final FirmaElectronicaRepository firmaRepository;
    private final UsuarioRepository usuarioRepository;
    private final RegistroService registroService;

    public FirmaElectronicaService(FirmaElectronicaRepository firmaRepository,
                                    UsuarioRepository usuarioRepository,
                                    RegistroService registroService) {
        this.firmaRepository = firmaRepository;
        this.usuarioRepository = usuarioRepository;
        this.registroService = registroService;
    }

    @Transactional(readOnly = true)
    public List<FirmaResponse> listar() {
        return firmaRepository.findAllByOrderByFechaAsignacionDesc().stream()
                .map(FirmaElectronicaMapper::toResponse)
                .toList();
    }

    @Transactional
    public FirmaResponse crear(CrearFirmaRequest request) {
        Usuario usuario = usuarioRepository.findById(request.usuarioId())
                .orElseThrow(() -> ResourceNotFoundException.of("Usuario", request.usuarioId()));

        FirmaElectronica firma = new FirmaElectronica();
        firma.setUsuario(usuario);
        firma.setFirmaId("FIRMA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        firma.setActiva(true);
        firma.setAsignadoPor(SecurityUtils.currentUsuario());
        FirmaElectronica guardada = firmaRepository.save(firma);

        registroService.registrar(null, "FIRMA_ASIGNADA",
                "Firma " + guardada.getFirmaId() + " asignada a " + usuario.getNombre()
                        + " (" + usuario.getEmail() + ").");
        return FirmaElectronicaMapper.toResponse(guardada);
    }

    @Transactional
    public FirmaResponse cambiarEstado(Long id, CambiarEstadoFirmaRequest request) {
        FirmaElectronica firma = firmaRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Firma electrónica", id));
        firma.setActiva(request.activa());
        FirmaElectronica guardada = firmaRepository.save(firma);

        registroService.registrar(null, request.activa() ? "FIRMA_RESTAURADA" : "FIRMA_REVOCADA",
                "Firma " + guardada.getFirmaId() + " de " + guardada.getUsuario().getNombre()
                        + (request.activa() ? " restaurada." : " revocada."));
        return FirmaElectronicaMapper.toResponse(guardada);
    }
}
