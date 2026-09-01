package co.sena.sicot.service;

import co.sena.sicot.dto.firma.CambiarEstadoFirmaRequest;
import co.sena.sicot.dto.firma.CrearFirmaRequest;
import co.sena.sicot.dto.firma.FirmaResponse;
import co.sena.sicot.dto.firma.MiFirmaResponse;
import co.sena.sicot.entity.FirmaElectronica;
import co.sena.sicot.entity.Usuario;
import co.sena.sicot.exception.BusinessException;
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
    public MiFirmaResponse miFirma() {
        Usuario usuario = SecurityUtils.currentUsuario();
        return firmaRepository.findFirstByUsuarioIdAndActivaTrue(usuario.getId())
                .map(f -> new MiFirmaResponse(true, f.getFirmaId()))
                .orElseGet(() -> new MiFirmaResponse(false, null));
    }

    @Transactional(readOnly = true)
    public List<FirmaResponse> listar() {
        return firmaRepository.findAllByOrderByFechaAsignacionDesc().stream()
                .map(FirmaElectronicaMapper::toResponse)
                .toList();
    }

    /**
     * Asigna una firma nueva, revocando antes la que el usuario tuviera activa.
     *
     * <p>La base garantiza como máximo una firma activa por usuario (índice
     * parcial {@code uq_firma_activa_por_usuario}). Este método no lo comprobaba
     * y simplemente insertaba: sobre una base con ese índice, reasignarle firma
     * a alguien que ya tenía una fallaba con un 409 genérico de "posible
     * duplicado", y la operación —perfectamente legítima para un
     * administrador— era imposible de completar por la API.
     *
     * <p>La rotación es lo que un administrador quiere decir al asignar una
     * firma a alguien que ya tiene: la anterior deja de servir y la nueva pasa
     * a ser la vigente. Ambas quedan en la tabla, así que los documentos ya
     * firmados con la anterior siguen siendo rastreables, y la revocación
     * queda en auditoría en vez de ocurrir en silencio.
     */
    @Transactional
    public FirmaResponse crear(CrearFirmaRequest request) {
        Usuario usuario = usuarioRepository.findById(request.usuarioId())
                .orElseThrow(() -> ResourceNotFoundException.of("Usuario", request.usuarioId()));

        revocarFirmaVigenteDe(usuario);

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

        // Restaurar una firma vieja cuando el usuario ya tiene otra vigente
        // chocaría contra uq_firma_activa_por_usuario. Se rechaza con un mensaje
        // que dice qué hacer, en vez de rotar por cuenta propia: cuál de las dos
        // debe quedar vigente es una decisión del administrador, no algo que el
        // sistema pueda adivinar (a diferencia de `crear`, donde la intención de
        // que la nueva reemplace a la anterior es inequívoca).
        if (request.activa() && !firma.isActiva()) {
            firmaRepository.findFirstByUsuarioIdAndActivaTrue(firma.getUsuario().getId())
                    .ifPresent(vigente -> {
                        throw new BusinessException(
                                firma.getUsuario().getNombre() + " ya tiene la firma " + vigente.getFirmaId()
                                        + " activa. Revóquela antes de restaurar " + firma.getFirmaId() + ".");
                    });
        }

        firma.setActiva(request.activa());
        FirmaElectronica guardada = firmaRepository.save(firma);

        registroService.registrar(null, request.activa() ? "FIRMA_RESTAURADA" : "FIRMA_REVOCADA",
                "Firma " + guardada.getFirmaId() + " de " + guardada.getUsuario().getNombre()
                        + (request.activa() ? " restaurada." : " revocada."));
        return FirmaElectronicaMapper.toResponse(guardada);
    }

    /**
     * Revoca la firma vigente del usuario y la escribe en la base de inmediato.
     *
     * <p>El {@code saveAndFlush} no es decorativo. Hibernate ordena su lote de
     * escrituras por tipo de operación —primero los INSERT, después los
     * UPDATE— sin importar el orden en que se llamó al repositorio. Sin forzar
     * el volcado aquí, el INSERT de la firma nueva llegaría a PostgreSQL antes
     * del UPDATE que desactiva la anterior, habría dos filas activas en ese
     * instante y el índice parcial rechazaría la operación.
     */
    private void revocarFirmaVigenteDe(Usuario usuario) {
        firmaRepository.findFirstByUsuarioIdAndActivaTrue(usuario.getId())
                .ifPresent(vigente -> {
                    vigente.setActiva(false);
                    firmaRepository.saveAndFlush(vigente);
                    registroService.registrar(null, "FIRMA_REVOCADA",
                            "Firma " + vigente.getFirmaId() + " de " + usuario.getNombre()
                                    + " revocada automáticamente al asignarle una firma nueva.");
                });
    }
}
