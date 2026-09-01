package co.sena.sicot.dto.documento;

import java.time.Instant;

/**
 * Resultado de comprobar que un documento firmado no cambió desde que se firmó.
 *
 * <p>Se devuelve también en la cabecera {@code X-SICOT-Integridad} de la
 * descarga, para que un script de auditoría pueda comprobarlo sin una segunda
 * petición.
 */
public record VerificacionIntegridadResponse(
        Long documentoId,
        String nombre,
        Estado estado,
        /** Huella guardada en el momento de firmar. {@code null} si no aplica. */
        String hashRegistrado,
        /** Huella recalculada ahora sobre los bytes almacenados. */
        String hashActual,
        String firmaId,
        Instant fechaFirma,
        String firmadoPorNombre,
        /** Explicación en lenguaje llano, lista para mostrarse al funcionario. */
        String mensaje
) {

    public enum Estado {
        /** Firmado y los bytes coinciden con la huella registrada. */
        INTEGRO,
        /** Firmado pero los bytes actuales NO coinciden: el documento cambió. */
        ALTERADO,
        /** Sin firmar: no hay nada que verificar todavía. */
        SIN_FIRMA,
        /**
         * Firmado antes de que existiera la columna de huella. No se puede
         * afirmar ni negar la integridad, y se dice así en vez de asumir lo
         * conveniente.
         */
        NO_VERIFICABLE
    }
}
