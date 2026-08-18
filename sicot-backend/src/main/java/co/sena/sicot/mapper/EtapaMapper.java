package co.sena.sicot.mapper;

import co.sena.sicot.dto.etapa.EtapaResponse;
import co.sena.sicot.dto.etapa.SubetapaResponse;
import co.sena.sicot.entity.Etapa;
import co.sena.sicot.entity.Subetapa;

import java.util.List;

public final class EtapaMapper {

    private EtapaMapper() {
    }

    public static SubetapaResponse toSubetapaResponse(Subetapa s) {
        return new SubetapaResponse(
                s.getId(),
                s.getCodigo(),
                s.getNombre(),
                s.getDescripcion(),
                s.getEstado(),
                s.getResponsable());
    }

    public static EtapaResponse toResponse(Etapa e) {
        List<SubetapaResponse> subEtapas = e.getSubEtapas().stream()
                .map(EtapaMapper::toSubetapaResponse)
                .toList();
        return new EtapaResponse(e.getId(), e.getNumero(), e.getNombre(),
                e.getEstado(), e.getPorcentaje(), subEtapas);
    }
}
