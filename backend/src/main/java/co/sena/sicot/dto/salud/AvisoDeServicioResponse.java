package co.sena.sicot.dto.salud;

/**
 * Un servicio caído, dicho como se le cuenta a quien está usando SICOT.
 *
 * <h2>Qué NO lleva, y por qué</h2>
 * Ni el mensaje del error, ni la URL interna de Ollama, ni cuántas veces falló.
 * Eso va al informe del técnico. A quien está trabajando se le dice lo único
 * que le sirve: qué no va a funcionarle, qué sí puede seguir haciendo, y que no
 * hace falta que avise porque ya se avisó.
 *
 * <p>Es el mismo criterio que ya seguía {@code comoFalloDeOllama}: el detalle
 * técnico se queda en el servidor porque es topología interna, y el usuario
 * recibe una explicación honesta.
 *
 * @param servicio  clave estable del servicio ({@code ia}, {@code correo}), para
 *                  que la interfaz pueda decidir dónde pintar el aviso
 * @param mensaje   el texto que se le enseña, ya redactado
 * @param minutos   cuánto lleva así, para que la interfaz pueda decir «desde
 *                  hace un rato» sin inventarse nada
 */
public record AvisoDeServicioResponse(String servicio, String mensaje, long minutos) {
}
