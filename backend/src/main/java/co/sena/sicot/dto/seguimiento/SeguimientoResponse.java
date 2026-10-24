package co.sena.sicot.dto.seguimiento;

import java.time.Instant;
import java.util.List;

/**
 * En qué parte del proceso va cada supervisor, contrato por contrato, tal como
 * lo consulta el Administrador.
 *
 * <p>Los contratos abiertos que todavía no tienen supervisor van aparte y no se
 * esconden: un contrato activo sin nadie que lo supervise es precisamente lo
 * primero que el Administrador tiene que ver.
 *
 * @param generadoEn instante de la consulta; el panel lo muestra para que nadie
 *                   confunda una foto de hace una hora con el estado de ahora.
 */
public record SeguimientoResponse(
        List<SupervisorSeguimiento> supervisores,
        List<ContratoSeguimiento> contratosSinSupervisor,
        Instant generadoEn
) {
}
