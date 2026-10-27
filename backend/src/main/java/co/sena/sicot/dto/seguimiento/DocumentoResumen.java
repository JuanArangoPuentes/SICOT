package co.sena.sicot.dto.seguimiento;

import co.sena.sicot.entity.enums.EstadoDocumento;

import java.time.Instant;

/**
 * Lo que el seguimiento necesita saber de un documento, sin su contenido.
 *
 * <p>Se lee con una proyección y no con la entidad por el mismo motivo que
 * {@code DocumentoRepository.listarPorContrato}: la entidad trae los bytes del
 * archivo, y en esta consulta van los documentos de todos los contratos
 * abiertos a la vez.
 *
 * @param subetapaCodigo código de la subetapa a la que pertenece («2.7»); el
 *                       panel lo usa para saber cuál de los documentos
 *                       formales del supervisor es.
 */
public record DocumentoResumen(
        Long id,
        Long contratoId,
        String subetapaCodigo,
        String nombre,
        EstadoDocumento estado,
        boolean generadoPorIa,
        boolean firmado,
        Instant fechaSubida,
        Instant fechaFirma
) {
}
