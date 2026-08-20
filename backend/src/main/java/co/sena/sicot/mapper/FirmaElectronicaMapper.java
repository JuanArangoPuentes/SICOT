package co.sena.sicot.mapper;

import co.sena.sicot.dto.firma.FirmaResponse;
import co.sena.sicot.entity.FirmaElectronica;
import co.sena.sicot.entity.Usuario;

public final class FirmaElectronicaMapper {

    private FirmaElectronicaMapper() {
    }

    public static FirmaResponse toResponse(FirmaElectronica f) {
        Usuario u = f.getUsuario();
        Usuario asignadoPor = f.getAsignadoPor();
        return new FirmaResponse(
                f.getId(),
                u != null ? u.getId() : null,
                u != null ? u.getNombre() : null,
                u != null ? u.getEmail() : null,
                f.getFirmaId(),
                f.isActiva(),
                asignadoPor != null ? asignadoPor.getId() : null,
                asignadoPor != null ? asignadoPor.getNombre() : null,
                f.getFechaAsignacion());
    }
}
