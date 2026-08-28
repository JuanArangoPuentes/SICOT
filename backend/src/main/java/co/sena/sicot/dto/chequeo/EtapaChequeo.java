package co.sena.sicot.dto.chequeo;

import java.util.List;

/**
 * Grupo de ítems de una lista de chequeo (etapa precontractual, contractual, de ejecución,
 * poscontractual...).
 *
 * @param nombre           rótulo de la etapa en el formato original.
 * @param rotuladaEnOrigen {@code false} cuando el formato no rotula el grupo y el nombre lo
 *                         dedujo la extracción por la posición de los ítems. Ocurre en
 *                         GCCON-F-055 (fila del título vacía) y en GRF-F-088 (no agrupa por
 *                         etapa). Queda explícito para no presentar como oficial algo que no
 *                         está escrito en el formato.
 */
public record EtapaChequeo(
        String nombre,
        boolean rotuladaEnOrigen,
        List<ItemChequeo> items
) {

    public EtapaChequeo {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
