package co.sena.sicot.dto.contrato;

import co.sena.sicot.entity.enums.EstadoContrato;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ContratoResponse(
        Long id,
        String numeroContrato,
        String objeto,
        BigDecimal valor,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        EstadoContrato estado,
        Long supervisorId,
        String supervisorNombre,
        String supervisorEmail,
        String tipoContrato,
        String contratista,
        String contratistaNit,
        String representanteLegal,
        String lugarEjecucion,
        String numeroRegistroPresupuestal,
        LocalDate fechaRegistroPresupuestal,
        String centroCosto,
        Instant fechaCreacion
) {
}
