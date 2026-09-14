package co.sena.sicot.entity.enums;

/**
 * Qué efecto produce una tarea de la cola de automatizaciones.
 *
 * <p>La lista es corta a propósito y crece solo cuando aparece un efecto de
 * verdad distinto, no una variante del mismo. «Avisar del vencimiento» y
 * «avisar del atraso» son la misma tarea ({@link #CREAR_ALERTA}) con distinto
 * contenido; lo que las diferencia es la regla que las produjo, y eso ya queda
 * en la columna {@code regla}.
 *
 * <p>Cada valor tiene que estar en el CHECK que fija la última migración que lo
 * tocó ({@code V16__resumen_semanal_sin_modelo.sql}): añadir uno aquí sin
 * migrarlo compila, pasa la suite sobre H2 y falla en el primer INSERT contra
 * PostgreSQL. La prueba {@code RestriccionesDeEnumEnMigracionesTest} está para
 * atrapar exactamente eso.
 */
public enum TipoTareaAutomatizada {

    /** Persiste una {@code Alerta} en el contrato. Efecto interno, no puede fallar por causas externas. */
    CREAR_ALERTA,

    /** Envía un correo real. Depende de un servidor SMTP configurado y puede fallar de forma transitoria. */
    ENVIAR_CORREO,

    /**
     * Compone el resumen del periodo a partir de datos ya extraídos de la base.
     *
     * <p>Se llamó {@code REDACTAR_RESUMEN_IA} mientras el texto lo escribía el
     * modelo local. Dejó de hacerlo: ver el encabezado de
     * {@code V16__resumen_semanal_sin_modelo.sql} para las mediciones que
     * motivaron el cambio. Como todas las demás, es un efecto interno que no
     * depende de nada externo y por tanto no puede fallar de forma transitoria.
     */
    REDACTAR_RESUMEN
}
