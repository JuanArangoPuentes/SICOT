package co.sena.sicot.salud;

/**
 * En qué estado está un {@link Servicio}.
 *
 * <h2>Por qué hay un estado intermedio</h2>
 * Porque un fallo suelto no es una caída. El modelo de IA tarda y a veces una
 * llamada se pasa del tiempo límite mientras la siguiente va bien; si el primer
 * tropiezo pintara el servicio como caído, la interfaz enseñaría un aviso rojo
 * que se contradice solo en diez segundos, y el técnico recibiría un informe por
 * algo que ya se arregló.
 *
 * <p>Así que el primer fallo deja el servicio DEGRADADO —se anota, se intenta
 * reparar, no se le dice nada a nadie— y solo la insistencia lo pasa a CAIDO,
 * que es cuando se avisa al usuario y se prepara el informe. El número exacto
 * de fallos está en {@link SaludDelSistema}.
 */
public enum EstadoDeServicio {

    /** Respondió bien la última vez que se usó. */
    OPERATIVO,

    /**
     * Falló al menos una vez pero todavía no lo suficiente como para darlo por
     * perdido. El sistema está intentando repararlo y no molesta a nadie.
     */
    DEGRADADO,

    /**
     * Falló de forma sostenida y los intentos de reparación no sirvieron. Aquí
     * sí se avisa: al usuario lo que le toca, y al técnico el informe entero.
     */
    CAIDO
}
