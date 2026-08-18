package co.sena.sicot.dto.formato;

import co.sena.sicot.entity.enums.EstadoFormato;
import co.sena.sicot.entity.enums.TipoDocumento;

import java.time.Instant;

public record FormatoDocumentalResponse(
        Long id,
        String codigo,
        String nombre,
        String version,
        TipoDocumento tipoArchivo,
        String nombreArchivo,
        long tamanioBytes,
        EstadoFormato estado,
        String subidoPorNombre,
        Instant fechaActualizacion
) {
}
