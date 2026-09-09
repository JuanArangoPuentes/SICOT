package co.sena.sicot.dto.documento;

import co.sena.sicot.entity.enums.EstadoDocumento;
import co.sena.sicot.entity.enums.TipoDocumento;

import java.time.Instant;

/**
 * Vista de un documento para el cliente.
 *
 * <p>Deliberadamente NO expone {@code contenido}: los listados se construyen
 * con una proyección JPQL sobre estos mismos campos (ver
 * {@code DocumentoRepository.listarPorContrato}) para que los bytes de los
 * archivos nunca salgan de PostgreSQL al pintar una pantalla.
 */
public record DocumentoResponse(
        Long id,
        Long contratoId,
        Long subetapaId,
        /** Entrada del catálogo institucional que representa, o {@code null} si no es la instancia de un formato oficial. */
        Long formatoId,
        /** Código del formato institucional (GCCON-F-031…). {@code null} si no tiene formato asociado. */
        String formatoCodigo,
        /** Nombre del formato institucional. {@code null} si no tiene formato asociado. */
        String formatoNombre,
        String nombre,
        TipoDocumento tipo,
        String rutaArchivo,
        EstadoDocumento estado,
        Long tamanioBytes,
        boolean generadoPorIa,
        String firmaId,
        Instant fechaFirma,
        /** Huella SHA-256 registrada al firmar. {@code null} si no está firmado. */
        String firmaHashSha256,
        /** Nombre de quien firmó. {@code null} si no está firmado. */
        String firmadoPorNombre,
        String subidoPorNombre,
        Instant fechaSubida
) {
}
