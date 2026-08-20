package co.sena.sicot.dto.documento;

import co.sena.sicot.entity.enums.EstadoDocumento;
import co.sena.sicot.entity.enums.TipoDocumento;

import java.time.Instant;

public record DocumentoResponse(
        Long id,
        Long contratoId,
        Long subetapaId,
        String nombre,
        TipoDocumento tipo,
        String rutaArchivo,
        EstadoDocumento estado,
        Long tamanioBytes,
        boolean generadoPorIa,
        String firmaId,
        Instant fechaFirma,
        String subidoPorNombre,
        Instant fechaSubida
) {
}
