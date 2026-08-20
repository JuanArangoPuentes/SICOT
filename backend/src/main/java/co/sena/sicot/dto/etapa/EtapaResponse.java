package co.sena.sicot.dto.etapa;

import co.sena.sicot.entity.enums.EstadoEtapa;

import java.util.List;

public record EtapaResponse(
        Long id,
        int numero,
        String nombre,
        EstadoEtapa estado,
        int porcentaje,
        List<SubetapaResponse> subEtapas
) {
}
