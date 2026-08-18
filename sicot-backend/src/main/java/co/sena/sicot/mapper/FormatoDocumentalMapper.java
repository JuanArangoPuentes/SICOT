package co.sena.sicot.mapper;

import co.sena.sicot.dto.formato.FormatoDocumentalResponse;
import co.sena.sicot.entity.FormatoDocumental;

public final class FormatoDocumentalMapper {

    private FormatoDocumentalMapper() {
    }

    public static FormatoDocumentalResponse toResponse(FormatoDocumental f) {
        return new FormatoDocumentalResponse(
                f.getId(),
                f.getCodigo(),
                f.getNombre(),
                f.getVersion(),
                f.getTipoArchivo(),
                f.getNombreArchivo(),
                f.getTamanioBytes(),
                f.getEstado(),
                f.getSubidoPor() != null ? f.getSubidoPor().getNombre() : null,
                f.getFechaActualizacion());
    }
}
