package co.sena.sicot.dto.contrato;

/**
 * Qué pasó al poner un supervisor en un contrato por su correo.
 *
 * <p>Los tres datos existen para que Gestión sepa <b>si el supervisor ya puede
 * entrar o todavía hay algo que hacer</b>, en vez de dar por hecho que el
 * correo salió. Es la misma regla de siempre en SICOT: antes que un «listo»
 * que puede ser mentira, el estado real.
 *
 * @param contrato          el contrato, ya con su supervisor.
 * @param cuentaCreada      {@code true} si la cuenta nació con esta asignación;
 *                          {@code false} si el supervisor ya estaba en SICOT y
 *                          solo se le asignó el contrato (entonces conserva su
 *                          clave y no se le manda ninguna nueva).
 * @param credencialesEnviadas si el correo con el usuario y la clave salió de
 *                          verdad. Falso cuando el servidor de correo no está
 *                          configurado o rechazó el envío.
 * @param claveParaEntregar la clave recién generada, <b>solo</b> cuando se creó
 *                          la cuenta y el correo NO pudo salir: sin esto, la
 *                          cuenta quedaría creada y nadie —tampoco Gestión—
 *                          sabría con qué entrar. {@code null} en cualquier
 *                          otro caso; en particular, nunca se devuelve la clave
 *                          de una cuenta que ya existía.
 * @param detalle           qué contarle a quien está mirando la pantalla.
 */
public record InvitacionDeSupervisorResponse(
        ContratoResponse contrato,
        boolean cuentaCreada,
        boolean credencialesEnviadas,
        String claveParaEntregar,
        String detalle
) {
}
