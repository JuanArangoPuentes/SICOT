package co.sena.sicot.dto.contrato;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CrearContratoRequest(
        @NotBlank(message = "El número de contrato es obligatorio.")
        @Size(max = 50, message = "El número de contrato no puede superar 50 caracteres.")
        String numeroContrato,

        @NotBlank(message = "El objeto del contrato es obligatorio.")
        String objeto,

        @NotNull(message = "El valor del contrato es obligatorio.")
        @DecimalMin(value = "0.01", message = "El valor debe ser mayor a cero.")
        BigDecimal valor,

        LocalDate fechaInicio,

        LocalDate fechaFin,

        Long supervisorId,

        // ── Identificación real del contrato (Acta de Inicio / Informe de Supervisión SENA) ──
        // Opcionales: un contrato en BORRADOR no siempre los tiene todavía.
        String tipoContrato,

        String contratista,

        String contratistaNit,

        String representanteLegal,

        String lugarEjecucion,

        String numeroRegistroPresupuestal,

        LocalDate fechaRegistroPresupuestal,

        String centroCosto
) {
}
