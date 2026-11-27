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
    SISTEMA,

    /**
     * Una herramienta automática que actúa <b>con las credenciales de una
     * persona</b>: hoy, el servidor MCP de {@code mcp/}, que inicia sesión con
     * {@code SICOT_EMAIL} y opera como esa cuenta.
     *
     * <p>{@code usuario_id} sigue puesto —la cuenta responsable es la misma y
     * eso no cambia— y {@code agente} dice qué herramienta fue. Lo que este
     * valor añade es la única distinción que faltaba: «esto lo decidió una
     * persona» frente a «esto lo propuso un modelo actuando con su cuenta».
     *
     * <p>En un expediente de contratación pública esa diferencia es el punto.
     * El proyecto ya tiene la regla —la IA es asesora, nunca autoridad— y hasta
     * el 20-11-2026 la auditoría no podía sostenerla: todo lo que hacía el MCP
     * quedaba registrado exactamente igual que lo que el funcionario hacía con
     * sus manos. Ver {@code V26__quien_actuo_fue_un_agente.sql}.
     */
    AGENTE
}
