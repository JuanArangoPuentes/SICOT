package co.sena.sicot.entity.enums;

/**
 * Quién produjo una entrada de auditoría.
 *
 * <p>Existe porque {@code registros.usuario_id} nulo es ambiguo: podría
 * significar «lo hizo el sistema» o «se perdió el dato». Con un valor explícito,
 * la primera lectura es un hecho afirmado y no la ausencia de otro — que en un
 * expediente de contratación pública es justo la diferencia entre información
 * completa e información sospechosa. Ver docs/decisiones/ADR-008.
 */
public enum OrigenRegistro {

    /** Una persona autenticada, cuyo identificador queda en {@code usuario_id}. */
    USUARIO,

    /** Una automatización del motor. No hay usuario asociado, y es correcto que no lo haya. */
    SISTEMA
}
