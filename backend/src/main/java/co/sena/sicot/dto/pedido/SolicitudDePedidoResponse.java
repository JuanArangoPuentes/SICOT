package co.sena.sicot.dto.pedido;

import java.math.BigDecimal;
import java.util.List;

/**
 * La solicitud de pedido del contrato: los bienes que el supervisor le pide al
 * contratista en el paso 2.4, con el control de presupuesto del formato real.
 *
 * <p>Los tres valores de presupuesto son las celdas que el formato que usa hoy
 * el supervisor («Solicitud_Maybe.xlsx») trae a la derecha de la tabla:
 * «PRESUPUESTO LOTE — ASIGNADO», «COMPROMETIDO» y «QUEDA». Ahí está el motivo
 * de que SICOT los calcule en vez de solo listar ítems: en el pedido real del
 * Lote 4, de $45.900.300 asignados quedaban $863,58 sin comprometer. Pasarse
 * del valor del contrato es el error que ese bloque existe para evitar, y se
 * ve cuando se arma el pedido, no cuando llega la factura.
 *
 * @param items             los bienes, en el orden en que los verá el
 *                          contratista; vacía si el supervisor no ha cargado
 *                          la solicitud
 * @param presupuestoAsignado el valor del contrato: el tope de lo que se puede
 *                          pedir
 * @param comprometido      la suma de los valores totales de los ítems
 * @param queda             asignado − comprometido; negativo si la solicitud se
 *                          pasa del valor del contrato
 * @param excedeElContrato  {@code true} cuando {@code queda} es negativo. No
 *                          bloquea el guardado: el supervisor puede estar a
 *                          mitad de cargar la tabla, y es él quien sabe si lo
 *                          que no cuadra es una cantidad o el valor registrado
 *                          del contrato. Se le avisa, no se le impide trabajar.
 */
public record SolicitudDePedidoResponse(
        List<ItemDeSolicitud> items,
        BigDecimal presupuestoAsignado,
        BigDecimal comprometido,
        BigDecimal queda,
        boolean excedeElContrato) {
}
