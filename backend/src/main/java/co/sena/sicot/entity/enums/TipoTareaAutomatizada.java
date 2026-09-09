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
 * <p>Cada valor tiene que estar en el CHECK de
 * {@code V15__motor_de_automatizaciones.sql}: añadir uno aquí sin migrarlo
 * compila, pasa la suite sobre H2 y falla en el primer INSERT contra
 * PostgreSQL. La prueba {@code RestriccionesDeEnumEnMigracionesTest} está para
 * atrapar exactamente eso.
 */
public enum TipoTareaAutomatizada {

    /** Persiste una {@code Alerta} en el contrato. Efecto interno, no puede fallar por causas externas. */
    CREAR_ALERTA,

    /** Envía un correo real. Depende de un servidor SMTP configurado y puede fallar de forma transitoria. */
    ENVIAR_CORREO,

    /** Pide al modelo local que redacte un texto a partir de datos ya extraídos de la base. */
    REDACTAR_RESUMEN_IA
}
