package co.sena.sicot.dto.chequeo;

import java.util.List;

/**
 * Un documento exigido por una lista de chequeo, tal como aparece en el formato oficial.
 *
 * @param numero        numeración del ítem en el formato original. Se conserva aunque el
 *                      formato la repita o la salte (ver {@code advertencias} de la lista).
 * @param documento     nombre del documento, sin la marca "*" de "cuando aplique".
 * @param cuandoAplique {@code true} si el formato lo marca con "*", es decir, puede no aplicar.
 * @param observacion   columna OBSERVACIONES del formato, literal (puede traer saltos de línea).
 * @param formatos      códigos de formato citados en el ítem o su observación (ej. GCCON-F-046).
 * @param tiposPago     tipos de pago a los que aplica el ítem; solo lo usa GRF-F-088. Vacío en
 *                      las listas de contratación, donde el ítem aplica a toda la lista.
 */
public record ItemChequeo(
        int numero,
        String documento,
        boolean cuandoAplique,
        String observacion,
        List<String> formatos,
        List<String> tiposPago
) {

    public ItemChequeo {
        formatos = formatos == null ? List.of() : List.copyOf(formatos);
        tiposPago = tiposPago == null ? List.of() : List.copyOf(tiposPago);
    }
}
