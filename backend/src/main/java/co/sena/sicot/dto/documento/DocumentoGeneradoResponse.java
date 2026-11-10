package co.sena.sicot.dto.documento;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * El documento recién generado y cómo quedó su apartado de observaciones.
 *
 * <p>Los campos del documento van al mismo nivel que siempre (quien lee
 * {@code id} o {@code nombre} no cambia). Lo nuevo existe para que el
 * supervisor vea la redacción del Copiloto ANTES de firmar: hasta el
 * 29-09-2026 el panel generaba y firmaba de un golpe, y en la prueba en vivo
 * la redacción del modelo cambió «se devolvieron al contratista 3 monitores»
 * por «el contratista ha devuelto tres monitores». Ninguna comprobación por
 * palabras ve un cambio de sujeto así; la persona que firma, sí.
 *
 * @param observaciones                 el texto que quedó en el apartado de observaciones, o
 *                                      {@code null} si el formato no tiene o no hubo notas.
 * @param observacionesRedactadasConIa  si ese texto lo redactó el Copiloto (y pasó las
 *                                      comprobaciones de fidelidad); {@code false} si son las
 *                                      notas del supervisor tal cual.
 * @param motivoNotasTalCual            por qué van las notas tal cual cuando se pidió la
 *                                      redacción («la IA no respondió», «la redacción de la IA
 *                                      perdía cifras de sus notas»…), o {@code null}.
 * @param huellaDelBorrador             SHA-256 del borrador tal como quedó. El panel la devuelve al
 *                                      firmar ({@code ?huellaRevisada=}) para que se firme exactamente
 *                                      lo que el supervisor leyó: si otra pestaña o un reintento lo
 *                                      regeneró entre medias, la firma se rechaza.
 */
public record DocumentoGeneradoResponse(
        @JsonUnwrapped DocumentoResponse documento,
        String observaciones,
        boolean observacionesRedactadasConIa,
        String motivoNotasTalCual,
        String huellaDelBorrador) {
}
