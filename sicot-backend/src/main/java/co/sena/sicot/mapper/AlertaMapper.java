package co.sena.sicot.mapper;

import co.sena.sicot.dto.alerta.AlertaResponse;
import co.sena.sicot.entity.Alerta;

public final class AlertaMapper {

    private AlertaMapper() {
    }

    public static AlertaResponse toResponse(Alerta a) {
        return new AlertaResponse(
                a.getId(),
                a.getContrato() != null ? a.getContrato().getId() : null,
                a.getTipo(),
                a.getPrioridad(),
                a.getMensaje(),
                a.isLeida(),
                a.getFechaCreacion());
    }
}
