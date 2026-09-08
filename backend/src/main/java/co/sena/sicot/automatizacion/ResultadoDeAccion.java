package co.sena.sicot.automatizacion;

/**
 * Cómo terminó el intento de ejecutar una tarea, cuando terminó sin excepción.
 *
 * <h2>Las tres salidas posibles, y por qué son tres y no dos</h2>
 * <ul>
 *   <li><b>Hecha.</b> El efecto ocurrió.</li>
 *   <li><b>Descartada.</b> El efecto ya no tiene sentido y no lo tendrá por
 *       reintentar: el correo no está configurado, el contrato se borró. No es
 *       un error.</li>
 *   <li><b>Excepción.</b> Algo falló y podría no fallar la próxima vez. Eso no
 *       se representa aquí: se lanza, y el ejecutor programa el reintento.</li>
 * </ul>
 *
 * <p>Sin la salida intermedia, todo lo que no fuera un éxito sería un fallo, y
 * la pantalla de operación tendría siempre entradas rojas por situaciones que no
 * requieren que nadie haga nada. Una lista de fallos que nunca está vacía es una
 * lista que nadie mira, y para cuando aparece un fallo de verdad ya se perdió la
 * costumbre de mirarla.
 */
public record ResultadoDeAccion(boolean ejecutada, String motivoDeDescarte) {

    public static ResultadoDeAccion hecha() {
        return new ResultadoDeAccion(true, null);
    }

    public static ResultadoDeAccion descartada(String motivo) {
        return new ResultadoDeAccion(false, motivo);
    }
}
