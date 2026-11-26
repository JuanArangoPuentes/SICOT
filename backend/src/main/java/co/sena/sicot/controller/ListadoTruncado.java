package co.sena.sicot.controller;

import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * Cuando un listado llega a su tope, lo dice.
 *
 * <h2>Qué estaba mal</h2>
 * Varios listados de SICOT tienen un techo duro —500 contratos, 500 alertas,
 * 500 registros, 200 tareas— y los comentarios que lo fijan son explícitos
 * sobre el porqué: «no es una paginación de verdad —que el frontend todavía no
 * sabe recorrer— sino un techo que impide que una pantalla cargue la tabla
 * entera». Ese razonamiento es correcto.
 *
 * <p>Lo que faltaba es el otro medio paso: la respuesta <b>no decía que había
 * truncado</b>. El día que un Centro pase de 500 contratos, la pantalla mostrará
 * 500 y parecerá completa. En un registro de expedientes de contratación
 * pública, una lista que miente por omisión es peor que un error: quien la
 * consulta toma decisiones creyendo que vio todo, y nada —ni un aviso, ni una
 * fila de más, ni una entrada en el log— le dice lo contrario.
 *
 * <h2>Por qué una cabecera y no un sobre en el cuerpo</h2>
 * Envolver la respuesta ({@code {datos: [...], total: n, truncado: true}}) es
 * la forma correcta en una API nueva, y aquí habría significado cambiar la
 * forma de seis endpoints que ya consumen la aplicación web, el instalador de
 * escritorio y el APK —tres clientes que se despliegan por separado y no se
 * actualizan a la vez (ADR-012, ADR-017)—. Una cabecera añade la información
 * sin romper a nadie: los clientes que no la miran siguen funcionando igual que
 * hoy, y la pantalla que la mira avisa.
 *
 * <p>Cuando el frontend aprenda a paginar de verdad, lo correcto es sustituir
 * esto por una paginación con cursor y retirar la cabecera. Mientras eso no
 * exista, esto es lo que convierte un silencio en un aviso.
 */
public final class ListadoTruncado {

    /** «Sí, falta algo»: solo se envía cuando de verdad falta. */
    public static final String CABECERA_TRUNCADO = "X-SICOT-Listado-Truncado";

    /** Cuántos elementos como máximo devuelve este endpoint. */
    public static final String CABECERA_TOPE = "X-SICOT-Listado-Tope";

    private ListadoTruncado() {
    }

    /**
     * Devuelve la lista con las cabeceras puestas si llegó al tope.
     *
     * <p>«Llegó al tope» se deduce de que el tamaño sea exactamente el tope, y
     * eso incluye el caso límite en que había justo esa cantidad y no faltaba
     * nada. Es a propósito: con una consulta limitada a N no hay forma de
     * distinguir «hay N» de «hay más de N» sin pagar un {@code COUNT} extra en
     * cada listado, y de los dos errores posibles este es el inofensivo —avisar
     * de más una vez cada muchos años, en vez de callar justo cuando importa—.
     * El aviso dice «puede que falten», no «faltan».
     */
    public static <T> ResponseEntity<List<T>> conAviso(List<T> elementos, int tope) {
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.ok()
                .header(CABECERA_TOPE, String.valueOf(tope));
        if (elementos.size() >= tope) {
            respuesta.header(CABECERA_TRUNCADO, "true");
        }
        return respuesta.body(elementos);
    }
}
