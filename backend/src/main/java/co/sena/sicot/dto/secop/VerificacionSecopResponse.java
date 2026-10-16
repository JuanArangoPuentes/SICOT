package co.sena.sicot.dto.secop;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Lo que el SECOP II publica del contrato como datos abiertos, puesto al lado
 * de lo que SICOT tiene registrado.
 *
 * <p>Existe por el paso 4 del proceso real del supervisor («entrar al SECOP y
 * verificar cantidades, especificaciones y valores»). El sitio del SECOP no se
 * puede leer desde un programa —responde con un reCAPTCHA—, pero la misma
 * información se publica en datos.gov.co, que es una API pública y gratuita.
 * La verificación sigue siendo del supervisor: esto le ahorra transcribir y le
 * señala las diferencias, no decide por él.
 *
 * @param disponible  {@code false} si no se pudo consultar (sin conexión,
 *                    datos.gov.co caído, o el contrato todavía no está
 *                    publicado); entonces {@code motivo} dice por qué y el
 *                    resto viene vacío
 * @param diferencias solo los campos que no coinciden; vacía si todo cuadra
 * @param documentos  el índice del expediente, cuando los datos abiertos lo
 *                    publican
 * @param avisoDocumentos por qué no hay índice, cuando no lo hay; {@code null}
 *                    si hay documentos. Una lista vacía sin explicación dejaba
 *                    al supervisor sin saber si el contrato no tiene
 *                    expediente, si falló la consulta, o si esa fuente no
 *                    cubre su contrato —que es lo que pasa siempre con los
 *                    contratos nuevos—
 */
public record VerificacionSecopResponse(
        boolean disponible,
        String motivo,
        Instant consultadoEn,
        String enlaceProceso,
        Ficha ficha,
        List<Diferencia> diferencias,
        List<Documento> documentos,
        String avisoDocumentos,
        List<Garantia> garantias
) {

    /** Los datos del contrato tal como los publica el SECOP II. */
    public record Ficha(
            String idContrato,
            String objeto,
            String estado,
            String proveedor,
            String documentoProveedor,
            String representanteLegal,
            BigDecimal valor,
            String duracion,
            LocalDate fechaFirma,
            LocalDate fechaFin,
            LocalDate inicioLiquidacion,
            LocalDate finLiquidacion,
            String procesoDeCompra,
            String supervisor,
            String ordenadorDelGasto,
            String modalidad,
            String tipoContrato,
            Boolean obligacionAmbiental,
            BigDecimal valorFacturado,
            BigDecimal valorPagado,
            BigDecimal valorPendienteDePago,
            String enlaceProceso
    ) {
    }

    /** Un campo en el que SICOT y el SECOP no dicen lo mismo. */
    public record Diferencia(String campo, String enSicot, String enSecop) {
    }

    /**
     * Un documento publicado en el expediente del contrato. Se muestra el
     * índice y el enlace para que el supervisor lo abra en su navegador; SICOT
     * no lo descarga, y no podría: el enlace de descarga del SECOP responde 403
     * a cualquier programa.
     */
    public record Documento(String nombre, Long tamanoBytes, LocalDate fechaCarga, String url) {
    }

    /** Una póliza registrada para el contrato. */
    public record Garantia(String aseguradora, String numero, String tipo, BigDecimal valor,
                           LocalDate vigenteHasta, String estado) {
    }

    public static VerificacionSecopResponse noDisponible(String motivo, Instant consultadoEn, String enlaceProceso) {
        return new VerificacionSecopResponse(false, motivo, consultadoEn, enlaceProceso, null,
                List.of(), List.of(), null, List.of());
    }
}
