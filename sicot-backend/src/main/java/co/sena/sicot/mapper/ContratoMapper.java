package co.sena.sicot.mapper;

import co.sena.sicot.dto.contrato.ContratoResponse;
import co.sena.sicot.entity.Contrato;
import co.sena.sicot.entity.Usuario;

public final class ContratoMapper {

    private ContratoMapper() {
    }

    public static ContratoResponse toResponse(Contrato c) {
        Usuario sup = c.getSupervisor();
        return new ContratoResponse(
                c.getId(),
                c.getNumeroContrato(),
                c.getObjeto(),
                c.getValor(),
                c.getFechaInicio(),
                c.getFechaFin(),
                c.getEstado(),
                sup != null ? sup.getId() : null,
                sup != null ? sup.getNombre() : null,
                sup != null ? sup.getEmail() : null,
                c.getFechaCreacion());
    }
}
