package co.sena.sicot.ia;

import java.util.List;

/**
 * Las piezas de un documento formal, tal como las dibuja {@link SimplePdfWriter}.
 *
 * <p>Existen porque los formatos reales del SENA (GCCON-F-018, GCCON-F-030,
 * GIL-F-010, el certificado ESUCON) no son prosa: son fichas de etiqueta y valor
 * con apartados. Un documento que llega como una lista de párrafos solo puede
 * imitarlos pidiéndole al modelo que redacte también los datos, que es donde el
 * modelo los alteraba.
 */
public sealed interface BloqueDocumento {

    /** Encabezado de un apartado («1. ASPECTOS GENERALES»). */
    record Seccion(String titulo) implements BloqueDocumento {
    }

    /** Texto corrido. */
    record Parrafo(String texto) implements BloqueDocumento {
    }

    /** Tabla de dos columnas, etiqueta y valor, como las de los formatos. */
    record Ficha(List<Campo> campos) implements BloqueDocumento {
    }

    /**
     * Una fila de la ficha. Un valor que empieza por «[» es un dato que SICOT no
     * tiene y que el supervisor debe completar; el PDF lo resalta.
     */
    record Campo(String etiqueta, String valor) {
    }
}
