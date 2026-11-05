package co.sena.sicot.dto.pedido;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * La solicitud de pedido completa que manda la pantalla del paso 2.4.
 *
 * <p>Se guarda entera y reemplaza la anterior, porque así trabaja el
 * supervisor: vuelve a pegar la tabla de la oferta económica.
 *
 * <p>Los topes replican las columnas de {@code items_solicitud_pedido} y la
 * realidad del formato: el pedido del Lote 4 tiene 35 ítems y descripciones de
 * más de 900 caracteres. Con los límites aquí, un pegado equivocado se rechaza
 * como 400 diciendo qué fila corregir, en vez de chocar contra la base.
 *
 * @param items las filas del pedido; una lista vacía borra la solicitud, que es
 *              cómo se deshace un pegado equivocado
 */
public record GuardarSolicitudDePedidoRequest(
        @NotNull(message = "La solicitud de pedido es obligatoria (puede ir vacía para borrarla).")
        @Size(max = 300, message = "La solicitud de pedido admite máximo 300 ítems.")
        List<@Valid Item> items) {

    /**
     * @param numero        obligatorio y único dentro de la solicitud: es como
     *                      el supervisor y el contratista se refieren al bien
     * @param cantidad      0 es válido, ver {@link ItemDeSolicitud}
     * @param valorUnitario con IVA; los 12 dígitos enteros son el mismo tope
     *                      que el valor del contrato
     */
    public record Item(
            @NotNull(message = "El número del ítem es obligatorio.")
            @Min(value = 1, message = "El número del ítem debe ser 1 o mayor.")
            @Max(value = 9999, message = "El número del ítem no puede superar 9999.")
            Integer numero,

            @NotBlank(message = "La descripción del ítem es obligatoria.")
            @Size(max = 4000, message = "La descripción del ítem no puede superar 4000 caracteres.")
            String descripcion,

            @NotNull(message = "La cantidad es obligatoria.")
            @Min(value = 0, message = "La cantidad no puede ser negativa.")
            @Max(value = 1_000_000, message = "La cantidad no puede superar 1.000.000 de unidades.")
            Integer cantidad,

            @NotNull(message = "El valor unitario es obligatorio.")
            @jakarta.validation.constraints.DecimalMin(value = "0.00",
                    message = "El valor unitario no puede ser negativo.")
            @Digits(integer = 12, fraction = 2,
                    message = "El valor unitario admite máximo 12 dígitos enteros y 2 decimales.")
            BigDecimal valorUnitario) {
    }
}
