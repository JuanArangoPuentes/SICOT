package co.sena.sicot.dto.registro;

import java.util.List;

/**
 * Lo que ha pasado en un contrato desde la última vez que se preguntó, para que
 * un equipo se entere de lo que hizo otro sin tener que recargar la pantalla.
 *
 * <h2>Por qué la marca es un identificador y no una hora</h2>
 * Porque los dos equipos no tienen por qué estar en hora, y no hace falta que
 * lo estén. Con una marca de tiempo, el equipo adelantado preguntaría «desde
 * una hora que aún no ha llegado» y se saltaría cambios para siempre, y el
 * atrasado los repetiría sin parar; devolver la hora del servidor lo arregla a
 * medias, porque sigue habiendo que elegir un margen y ese margen o repite o
 * pierde. El identificador de la auditoría es creciente, lo asigna la base, y
 * «mayor que N» no admite interpretación: ni repite ni pierde, y el reloj de
 * nadie interviene.
 *
 * <h2>Esto es un aviso, no los datos</h2>
 * Quien recibe una novedad no la aplica: vuelve a pedir las pantallas enteras,
 * que es lo único que el servidor garantiza. Por eso la lista va acotada y, si
 * se corta, {@link #ultimoId} salta igual al más reciente: perder el detalle de
 * una entrada vieja no cambia nada cuando lo que se va a hacer es releerlo todo.
 *
 * @param ultimoId  hasta dónde cubre esta respuesta; es lo que hay que mandar
 *                  como {@code desdeId} en la siguiente pregunta. Nunca
 *                  retrocede
 * @param novedades entradas de auditoría posteriores al {@code desdeId} pedido,
 *                  de la más reciente a la más vieja; vacía casi siempre, que
 *                  es el caso que esta consulta está hecha para que salga
 *                  barato
 */
public record NovedadesResponse(
        Long ultimoId,
        List<RegistroResponse> novedades
) {
}
