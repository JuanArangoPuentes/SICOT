package co.sena.sicot.mapper;

import co.sena.sicot.dto.documento.DocumentoResponse;
import co.sena.sicot.entity.Documento;

public final class DocumentoMapper {

    private DocumentoMapper() {
    }

    public static DocumentoResponse toResponse(Documento d) {
        return new DocumentoResponse(
                d.getId(),
                d.getContrato().getId(),
                d.getSubetapa() != null ? d.getSubetapa().getId() : null,
                d.getNombre(),
                d.getTipo(),
                d.getRutaArchivo(),
                d.getEstado(),
                d.getTamanioBytes(),
                d.isGeneradoPorIa(),
                d.getFirmaId(),
                d.getFechaFirma(),
                d.getSubidoPor() != null ? d.getSubidoPor().getNombre() : null,
                d.getFechaSubida());
    }
}
