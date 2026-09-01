package co.sena.sicot.dto.chequeo;

/** Fila del índice del catálogo: identifica una lista de chequeo sin traer todos sus ítems. */
public record ListaChequeoResumen(
        String codigo,
        String nombre,
        String version,
        String proceso,
        TipoListaChequeo tipo,
        String alcance,
        int totalEtapas,
        int totalItems,
        int totalAdvertencias
) {
}
