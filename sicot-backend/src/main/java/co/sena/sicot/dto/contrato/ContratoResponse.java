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
        Instant fechaCreacion
) {
}
