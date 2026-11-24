package co.sena.sicot.salud;

/**
 * Por donde un componente cuenta que algo le funcionó o le falló.
 *
 * <h2>Por qué una interfaz y no {@link SaludDelSistema} directamente</h2>
 * Para que el que avisa no dependa del que lleva la cuenta. {@code OllamaClient}
 * solo necesita decir «esto falló»; no tiene por qué arrastrar en sus pruebas el
 * registro de métricas, el envío de correo ni el directorio de informes que
 * {@code SaludDelSistema} necesita para existir.
 *
 * <p>De ahí también {@link #NINGUNO}: los constructores de prueba de
 * {@code OllamaClient} pasan ese, y las pruebas que ya existían siguen valiendo
 * sin tocar una línea, porque avisar de la salud no es lo que están probando.
 */
public interface AvisoDeSalud {

    /**
     * El servicio respondió bien. Se llama en el camino de éxito, no solo en el
     * de fallo: sin esto, un servicio que se recupera seguiría marcado como
     * caído hasta que alguien reiniciara el backend, y la interfaz enseñaría un
     * aviso falso — que es peor que no enseñar ninguno.
     */
    void exito(Servicio servicio);

    /**
     * El servicio falló.
     *
     * @param detalle qué dijo exactamente el fallo. Va al informe del técnico,
     *                así que aquí se pone el mensaje de verdad —el del timeout,
     *                el del modelo que no está— y no una versión bonita.
     */
    void fallo(Servicio servicio, String detalle);

    /** No le cuenta a nadie. Para pruebas y para cuando no hay registro puesto. */
    AvisoDeSalud NINGUNO = new AvisoDeSalud() {
        @Override
        public void exito(Servicio servicio) {
            // Nada a propósito.
        }

        @Override
        public void fallo(Servicio servicio, String detalle) {
            // Nada a propósito.
        }
    };
}
