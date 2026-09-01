package co.sena.sicot.mapper;

import co.sena.sicot.dto.documento.DocumentoResponse;
import co.sena.sicot.entity.Documento;

/**
 * Convierte una entidad {@link Documento} ya cargada en su respuesta.
 *
 * <p>Solo se usa en las operaciones de un documento a la vez (subir, generar,
 * firmar), donde la entidad completa ya está en memoria por necesidad. Los
 * LISTADOS no pasan por aquí: usan la proyección de
 * {@code DocumentoRepository.listarPorContrato}, que construye el mismo record
 * directamente desde la consulta sin traer la columna {@code contenido}.
 */
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
                d.getFirmaHashSha256(),
                d.getFirmadoPor() != null ? d.getFirmadoPor().getNombre() : null,
                d.getSubidoPor() != null ? d.getSubidoPor().getNombre() : null,
                d.getFechaSubida());
    }
}
