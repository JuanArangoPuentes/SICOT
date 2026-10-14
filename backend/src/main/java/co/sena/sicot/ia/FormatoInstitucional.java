package co.sena.sicot.ia;

/**
 * Cómo se presenta un formato oficial del SENA: su página, su logo, su
 * encabezado, su pie y su tipo de letra. Lo que cambia de un documento a otro
 * del mismo formato son los datos; esto no.
 *
 * <p>Cada número sale de medir el formato real que el Centro usa (carpeta
 * «Documentos/SICOT» del equipo del proyecto, y el .docx original cuando lo
 * hay): márgenes del {@code pgMar} de Word, posición del logo en el
 * encabezado, líneas base del pie. El pie con el código y la versión se copia
 * letra por letra, con la puntuación que trae cada formato («GCCON-F-018
 * V.04», «GCCON-F-031 V04»), porque es lo que dice qué versión se diligenció.
 * Todas las coordenadas van en puntos; las verticales se miden desde el borde
 * superior de la hoja, como en Word, y {@link PdfInstitucional} las convierte.
 *
 * @param codigo           código del formato («GCCON-F-018»), o {@code null} si
 *                         no tiene uno oficial confirmado (certificado ESUCON).
 * @param version          versión tal como la escribe el formato («08» en la
 *                         GIL-F-010), o {@code null}.
 * @param pie              texto literal del código y versión en el pie, o
 *                         {@code null} si el formato no lleva pie.
 * @param encabezado       qué va debajo del logo en la primera página.
 * @param proceso          proceso del Sistema Integrado de Gestión.
 * @param nombreDelFormato nombre del formato tal como aparece en su encabezado.
 * @param familia          tipo de letra del cuerpo.
 * @param tamanoTexto      tamaño del cuerpo en puntos.
 * @param logo             qué logo y dónde.
 * @param pagina           márgenes y zona del cuerpo.
 * @param estiloPie        tipografía y altura del pie, o {@code null} si no lleva.
 */
public record FormatoInstitucional(
        String codigo,
        String version,
        String pie,
        Encabezado encabezado,
        String proceso,
        String nombreDelFormato,
        Familia familia,
        float tamanoTexto,
        Logo logo,
        Pagina pagina,
        EstiloPie estiloPie) {

    public enum Encabezado {
        /** Barras PROCESO / NOMBRE DEL FORMATO y la clasificación en seis celdas (GCCON-F-030). */
        PROCESO_Y_CLASIFICACION,
        /** Solo la clasificación en seis celdas (GCCON-F-031 diligenciado: la portada no va). */
        CLASIFICACION_EN_CELDAS,
        /** Solo la clasificación con casillas dibujadas (GCCON-F-018). */
        CLASIFICACION_CON_CASILLAS,
        /** Nada: el certificado del supervisor empieza por su propio encabezado de texto. */
        NINGUNO,
        /** La hoja de cálculo GIL-F-010, que se dibuja completa y fija (ver {@code HojaActaDeRecibo}). */
        HOJA_DE_CALCULO
    }

    /** Calibri en Word → Carlito; Arial → Liberation Sans. Ver resources/documentos/LEEME.md. */
    public enum Familia { CALIBRI, ARIAL }

    public enum Imagen {
        /** #39A900, el del encabezado de GCCON-F-030, GCCON-F-031 y GIL-F-010. */
        VERDE,
        /** #00AF00, la versión que trae el .docx del GCCON-F-018. */
        VERDE_ACTA_DE_INICIO,
        /** Monocromo, el del certificado ESUCON. */
        NEGRO
    }

    /**
     * @param x               borde izquierdo, o {@code NaN} para centrarlo en la hoja.
     * @param desdeArriba     distancia del borde superior de la hoja al del logo.
     * @param todasLasPaginas si va en el encabezado de Word (todas) o solo en la primera.
     */
    public record Logo(Imagen imagen, float x, float desdeArriba, float ancho, float alto, boolean todasLasPaginas) {
    }

    /**
     * @param margenIzquierdo borde izquierdo del cuerpo.
     * @param margenDerecho   distancia del borde derecho del cuerpo al de la hoja.
     * @param inicioCuerpo    desde arriba: dónde empieza el cuerpo en las páginas siguientes a la primera.
     * @param finCuerpo       desde arriba: por debajo de aquí no se escribe cuerpo.
     */
    public record Pagina(float margenIzquierdo, float margenDerecho, float inicioCuerpo, float finCuerpo) {
    }

    /**
     * Número de página a la derecha y código centrado debajo, con sus líneas
     * base medidas desde abajo.
     */
    public record EstiloPie(Familia familiaNumero, float tamanoNumero, float baseNumero,
                            Familia familiaCodigo, float tamanoCodigo, float baseCodigo) {
    }
}
