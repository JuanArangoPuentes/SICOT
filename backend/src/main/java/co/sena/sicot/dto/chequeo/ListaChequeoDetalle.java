package co.sena.sicot.dto.chequeo;

import java.util.List;

/**
 * Una lista de chequeo institucional completa, transcrita del .xlsx publicado en CompromISO.
 *
 * <p>El contenido es copia literal del formato oficial: SICOT no reescribe nombres de documento
 * ni completa lo que el formato deja incompleto. Cuando el archivo original trae una
 * inconsistencia (numeración repetida, versión del nombre del archivo distinta a la del
 * encabezado, una etapa sin rotular), queda registrada en {@link #advertencias()} en vez de
 * corregirse en silencio.
 *
 * @param version      versión del encabezado de la hoja, que manda sobre la del nombre del archivo.
 * @param alcance      a qué proceso aplica la lista, según el propio formato (ej. "MÍNIMA CUANTÍA").
 * @param archivoOrigen nombre del .xlsx del que se transcribió, para poder rastrear el origen.
 * @param tiposPago    tipos de pago declarados; solo GRF-F-088 los usa.
 * @param notas        pies de página del formato, literales (ej. "* Cuando aplique.").
 * @param advertencias observaciones de la transcripción — son de SICOT, no del formato oficial.
 */
public record ListaChequeoDetalle(
        String codigo,
        String nombre,
        String version,
        String proceso,
        TipoListaChequeo tipo,
        String alcance,
        String archivoOrigen,
        String hojaOrigen,
        List<TipoPagoChequeo> tiposPago,
        List<EtapaChequeo> etapas,
        List<String> notas,
        List<String> advertencias
) {

    public ListaChequeoDetalle {
        tiposPago = tiposPago == null ? List.of() : List.copyOf(tiposPago);
        etapas = etapas == null ? List.of() : List.copyOf(etapas);
        notas = notas == null ? List.of() : List.copyOf(notas);
        advertencias = advertencias == null ? List.of() : List.copyOf(advertencias);
    }

    public int totalItems() {
        return etapas.stream().mapToInt(etapa -> etapa.items().size()).sum();
    }
}
