package co.sena.sicot.salud;

/**
 * Las piezas de las que SICOT depende y que pueden caerse por separado.
 *
 * <h2>Por qué un enum y no una cadena de texto</h2>
 * Porque el estado de cada pieza lo escribe un componente y lo leen otros tres
 * —el reparador, el informe para el técnico y la interfaz—, y una cadena
 * escrita a mano en cuatro sitios se desincroniza al primer cambio de nombre.
 *
 * <h2>Cada servicio dice qué significa su caída para una persona</h2>
 * El texto que ve el usuario no se redacta en el frontend. Se declara aquí, al
 * lado de lo que falla, por una razón concreta: quien añade un servicio nuevo
 * tiene que decidir en ese momento qué se le dice a quien lo esté usando. Si el
 * mensaje viviera en la interfaz, un servicio nuevo aparecería como «error» a
 * secas, que es exactamente lo que esto viene a quitar.
 *
 * <p>Y los mensajes dicen qué SÍ se puede seguir haciendo, no solo qué falló.
 * SICOT está diseñado para funcionar sin el modelo de IA —el motor de
 * automatizaciones explica los pasos sin él, ver ADR-006 y la nota de IA
 * liviana—, así que un aviso que solo diga «la IA no está disponible» deja al
 * supervisor creyendo que no puede trabajar, cuando puede.
 */
public enum Servicio {

    /**
     * El modelo de IA local (Ollama). Es el único que el sistema puede intentar
     * reparar por su cuenta: volver a cargar el modelo es una llamada HTTP.
     */
    IA("el asistente de IA",
            "El asistente de IA no está disponible. Puede seguir trabajando con normalidad: "
                    + "los pasos, los documentos y las firmas no dependen de él. "
                    + "El técnico ya fue avisado.",
            true, true, 3),

    /**
     * El envío de correo. No se puede reparar desde dentro: si el servidor de
     * correo rechaza la conexión, no hay nada que esta aplicación pueda cambiar.
     */
    CORREO("el envío de correos",
            "No se pudieron enviar correos. Lo que guardó quedó guardado; el aviso por correo "
                    + "a la otra persona no salió. El técnico ya fue avisado.",
            true, false, 3),

    /**
     * El respaldo de la base. No es visible para el usuario: no cambia nada de
     * lo que puede hacer, y el compromiso de recuperación es cosa del técnico.
     */
    RESPALDO("el respaldo de la base de datos", null, false, false, 1),

    /** El disco donde viven los documentos. Tampoco cambia lo que el usuario puede hacer. */
    ALMACENAMIENTO("el almacenamiento de documentos", null, false, false, 1);

    private final String etiqueta;
    private final String mensajeParaElUsuario;
    private final boolean visibleParaElUsuario;
    private final boolean sePuedeIntentarReparar;
    private final int fallosParaDarloPorCaido;

    Servicio(String etiqueta, String mensajeParaElUsuario, boolean visibleParaElUsuario,
             boolean sePuedeIntentarReparar, int fallosParaDarloPorCaido) {
        this.etiqueta = etiqueta;
        this.mensajeParaElUsuario = mensajeParaElUsuario;
        this.visibleParaElUsuario = visibleParaElUsuario;
        this.sePuedeIntentarReparar = sePuedeIntentarReparar;
        this.fallosParaDarloPorCaido = fallosParaDarloPorCaido;
    }

    /**
     * Cuántos fallos seguidos hacen falta para darlo por caído, y por qué no es
     * el mismo número para todos.
     *
     * <p>Una llamada a Ollama puede pasarse del tiempo límite porque el equipo
     * estaba ocupado y la siguiente ir perfecta: ahí tres fallos seguidos
     * distinguen un tropiezo de una caída. El respaldo no se parece en nada:
     * «el más reciente tiene 136 h» no es un tropiezo del que uno se recupere
     * por insistir, y se sabe con certeza en la primera mirada. Pedirle tres
     * comprobaciones —una cada tres horas— retrasaría el aviso nueve horas por
     * una duda que no existe.
     */
    public int fallosParaDarloPorCaido() {
        return fallosParaDarloPorCaido;
    }

    /** Cómo se nombra en un aviso: «falló {@code el asistente de IA}». */
    public String etiqueta() {
        return etiqueta;
    }

    /** Qué se le dice a quien esté usando esto, o {@code null} si no se le dice nada. */
    public String mensajeParaElUsuario() {
        return mensajeParaElUsuario;
    }

    /**
     * Si su caída se le muestra a quien usa SICOT. Los que no cambian nada de lo
     * que el usuario puede hacer no se le cuentan: un aviso que no le sirve de
     * nada le enseña a ignorar los avisos.
     */
    public boolean visibleParaElUsuario() {
        return visibleParaElUsuario;
    }

    /** Si el sistema puede intentar levantarlo solo antes de molestar al técnico. */
    public boolean sePuedeIntentarReparar() {
        return sePuedeIntentarReparar;
    }
}
