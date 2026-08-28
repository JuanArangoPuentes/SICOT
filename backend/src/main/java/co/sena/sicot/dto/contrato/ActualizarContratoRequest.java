package co.sena.sicot.dto.contrato;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Mismos límites que {@link CrearContratoRequest}, campo por campo: un campo no
 * puede aceptar más caracteres al actualizar que al crear. Los máximos replican
 * las columnas de la tabla {@code contratos}.
 */
public record ActualizarContratoRequest(
        @NotBlank(message = "El número de contrato es obligatorio.")
        @Size(max = 50, message = "El número de contrato no puede superar 50 caracteres.")
        String numeroContrato,

        @NotBlank(message = "El objeto del contrato es obligatorio.")
        @Size(max = 4000, message = "El objeto del contrato no puede superar 4000 caracteres.")
        String objeto,

        @NotNull(message = "El valor del contrato es obligatorio.")
        @DecimalMin(value = "0.01", message = "El valor debe ser mayor a cero.")
        @Digits(integer = 16, fraction = 2,
                message = "El valor del contrato admite máximo 16 dígitos enteros y 2 decimales.")
        BigDecimal valor,

        LocalDate fechaInicio,

        LocalDate fechaFin,

        // ── Identificación real del contrato (Acta de Inicio / Informe de Supervisión SENA) ──
        @Size(max = 100, message = "El tipo de contrato no puede superar 100 caracteres.")
        String tipoContrato,

        @Size(max = 255, message = "El nombre del contratista no puede superar 255 caracteres.")
        String contratista,

        @Size(max = 30, message = "El NIT del contratista no puede superar 30 caracteres.")
        String contratistaNit,

        @Size(max = 255, message = "El representante legal no puede superar 255 caracteres.")
        String representanteLegal,

        @Size(max = 255, message = "El lugar de ejecución no puede superar 255 caracteres.")
        String lugarEjecucion,

        @Size(max = 50, message = "El número de registro presupuestal no puede superar 50 caracteres.")
        String numeroRegistroPresupuestal,

        // La relación entre fechaRegistroPresupuestal y la vigencia del contrato es
        // una regla del proceso institucional del SENA, no una regla aritmética:
        // queda PENDIENTE_DE_DEFINIR y no se valida aquí para no inventar proceso.
        LocalDate fechaRegistroPresupuestal,

        @Size(max = 100, message = "El centro de costo no puede superar 100 caracteres.")
        String centroCosto
) {

    /**
     * Regla aritmética (no institucional): el fin de la vigencia no puede ser
     * anterior a su inicio. Coincide con {@code ck_contratos_fechas} de la base.
     */
    @AssertTrue(message = "La fecha de fin no puede ser anterior a la fecha de inicio.")
    private boolean isFechasCoherentes() {
        return fechaInicio == null || fechaFin == null || !fechaFin.isBefore(fechaInicio);
    }
}
