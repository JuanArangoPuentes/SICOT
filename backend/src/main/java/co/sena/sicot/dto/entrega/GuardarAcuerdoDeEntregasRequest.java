package co.sena.sicot.dto.entrega;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Las fechas de entrega que el supervisor acordó con el contratista, completas.
 *
 * <p>Se guardan enteras y reemplazan las anteriores, como la solicitud de
 * pedido: si el contratista pide mover una entrega, el supervisor corrige la
 * lista y la vuelve a guardar. El número de cada entrega no viaja: lo asigna
 * SICOT por fecha, porque «la entrega 2» tiene que ser la segunda en el tiempo.
 *
 * @param entregas una lista vacía borra el acuerdo
 */
public record GuardarAcuerdoDeEntregasRequest(
        @NotNull(message = "El acuerdo de entregas es obligatorio (puede ir vacío para borrarlo).")
        @Size(max = 60, message = "El acuerdo admite máximo 60 entregas.")
        @Valid
        List<Entrega> entregas) {

    /**
     * @param fecha       el día acordado; tiene que caer dentro del plazo del
     *                    contrato, y eso lo comprueba el servicio
     * @param descripcion qué se entrega o qué servicio se presta ese día
     */
    public record Entrega(
            @NotNull(message = "La fecha de la entrega es obligatoria.")
            LocalDate fecha,

            @NotBlank(message = "Diga qué se entrega en cada fecha (por ejemplo, «entrega total» o «ítems 1 a 20»).")
            @Size(max = 2000, message = "La descripción de la entrega no puede superar 2000 caracteres.")
            String descripcion) {
    }
}
