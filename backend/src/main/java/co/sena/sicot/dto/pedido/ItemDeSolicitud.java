package co.sena.sicot.dto.pedido;

import java.math.BigDecimal;

/**
 * Una fila de la solicitud de pedido, como la verá el contratista en el Excel.
 *
 * @param numero        el número del ítem en la oferta económica
 * @param descripcion   la especificación completa del bien
 * @param cantidad      unidades a recibir; 0 significa «estaba en la oferta y
 *                      no se pide», tal como aparece en el formato real
 * @param valorUnitario valor unitario con IVA
 * @param valorTotal    cantidad × valor unitario, calculado por SICOT. Va en la
 *                      respuesta para que la pantalla y el Excel muestren la
 *                      misma cifra sin repetir la multiplicación en el
 *                      navegador.
 */
public record ItemDeSolicitud(
        Integer numero,
        String descripcion,
        Integer cantidad,
        BigDecimal valorUnitario,
        BigDecimal valorTotal) {
}
