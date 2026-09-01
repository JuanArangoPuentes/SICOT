package co.sena.sicot.dto.contrato;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Los límites de longitud replican exactamente la columna correspondiente de la
 * tabla {@code contratos} (ver {@code V1__create_sicot_schema.sql}); así un texto
 * demasiado largo se rechaza como 400 con {@code fieldErrors} —indicando qué
 * campo corregir— en vez de convertirse en un 409 opaco al chocar con la base.
 * {@code objeto} usa columna {@code TEXT} (sin límite en la base): el máximo aquí
 * es un tope de sensatez para que un pegado accidental no llegue a persistirse.
 * {@code ActualizarContratoRequest} declara los mismos límites campo por campo.
 */
public record CrearContratoRequest(
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

        @Positive(message = "El identificador del supervisor debe ser un número positivo.")
        Long supervisorId,

        // ── Identificación real del contrato (Acta de Inicio / Informe de Supervisión SENA) ──
        // Opcionales: un contrato en BORRADOR no siempre los tiene todavía.
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
     * anterior a su inicio. Coincide con {@code ck_contratos_fechas} de la base;
     * declararla aquí la convierte en un 400 con {@code fieldErrors} en vez de un
     * fallo de integridad.
     */
    @AssertTrue(message = "La fecha de fin no puede ser anterior a la fecha de inicio.")
    private boolean isFechasCoherentes() {
        return fechaInicio == null || fechaFin == null || !fechaFin.isBefore(fechaInicio);
    }
}
