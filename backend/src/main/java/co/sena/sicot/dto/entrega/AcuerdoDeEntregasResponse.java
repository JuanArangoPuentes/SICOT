package co.sena.sicot.dto.entrega;

import java.time.LocalDate;
import java.util.List;

/**
 * El acuerdo de entregas del contrato (paso 2.4 del proceso real).
 *
 * @param entregas          en orden de fecha; vacía si todavía no se acordó
 *                          ninguna
 * @param inicioDelContrato el primer día en que se puede acordar una entrega,
 *                          o {@code null} si el contrato no lo tiene
 *                          registrado
 * @param finDelContrato    el último; la hoja de Alex pide «respetar los
 *                          tiempos de inicio y fin del contrato», y la pantalla
 *                          los usa para no ofrecer días fuera del plazo
 */
public record AcuerdoDeEntregasResponse(
        List<Entrega> entregas,
        LocalDate inicioDelContrato,
        LocalDate finDelContrato) {

    /** Una entrega acordada, con el número que le asignó SICOT por fecha. */
    public record Entrega(int numero, LocalDate fecha, String descripcion) {
    }
}
