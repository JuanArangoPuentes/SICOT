package co.sena.sicot.ia;

import java.util.List;

/**
 * Un documento listo para dibujar: el formato oficial al que pertenece, su
 * contenido y los datos de trazabilidad que no son parte del formato pero que
 * el PDF debe llevar.
 *
 * @param formato        formato institucional (encabezado, pie, letra, logo).
 * @param titulo         título para las propiedades del PDF («Acta de Inicio — CO1.PCCNTR.…»).
 * @param numeroContrato contrato al que pertenece.
 * @param autor          quien lo suscribe (el supervisor), para las propiedades del PDF.
 * @param origen         cómo se produjo, dicho con precisión («Generado en SICOT», «… con apoyo del
 *                       Copiloto IA»): quien lee un acta tiene derecho a saber si intervino un modelo.
 * @param bloques        contenido, de arriba abajo.
 * @param firmanteId     id del usuario cuyo nombre va en el bloque de firma del supervisor, o {@code null}.
 *                       Queda en las propiedades del PDF para que solo esa persona pueda firmarlo.
 */
public record DocumentoFormal(
        FormatoInstitucional formato,
        String titulo,
        String numeroContrato,
        String autor,
        String origen,
        List<BloqueDocumento> bloques,
        Long firmanteId) {

    /** Sin firmante previsto: un documento que no lleva el bloque de firma de una persona concreta. */
    public DocumentoFormal(FormatoInstitucional formato, String titulo, String numeroContrato, String autor,
                           String origen, List<BloqueDocumento> bloques) {
        this(formato, titulo, numeroContrato, autor, origen, bloques, null);
    }
}
