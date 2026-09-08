package co.sena.sicot.automatizacion;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Todo lo configurable del motor, en un solo sitio y con valores por defecto
 * que sirven en producción sin tocar nada.
 *
 * <p>Se agrupa en una clase en vez de repartir {@code @Value} por los
 * componentes por un motivo concreto: quien despliega necesita poder ver la
 * lista completa de perillas sin leer el código. Con {@code @Value} disperso,
 * esa lista no existe en ningún archivo.
 *
 * @param habilitado          si el motor evalúa reglas y consume la cola. En
 *                            {@code false} el sistema sigue funcionando entero:
 *                            simplemente no se generan automatizaciones. Se
 *                            apaga en la suite de pruebas para que ningún
 *                            temporizador dispare a mitad de una comprobación.
 * @param sondeo              cada cuánto se mira la cola. Un minuto es holgado:
 *                            las automatizaciones de SICOT avisan de cosas que
 *                            se miden en días, no en segundos.
 * @param tamanoDelLote       cuántas tareas se reclaman por ronda. Acotado para
 *                            que una acumulación de miles no intente
 *                            procesarse de una vez.
 * @param trabajadores        hilos que ejecutan tareas en paralelo. Pequeño a
 *                            propósito: el trabajo es de espera (correo, modelo)
 *                            y estos hilos son propios, no los de Tomcat, pero
 *                            comparten la base y el modelo con los usuarios.
 * @param maximoDeIntentos    tras cuántos fallos una tarea se da por perdida y
 *                            pasa a FALLIDA para que alguien la mire.
 * @param esperaBase          primer escalón de la espera exponencial entre
 *                            reintentos.
 * @param abandonoTrasVer     desde cuándo se considera abandonada una tarea que
 *                            quedó EN_PROCESO. Cubre el caso de matar el
 *                            backend a mitad de una ejecución: sin esto, esa
 *                            tarea no la reclama nadie nunca más.
 * @param retencionDeTareas   cuánto se conservan las tareas ya resueltas antes
 *                            de purgarlas. Solo afecta a COMPLETADA y
 *                            DESCARTADA: una FALLIDA es evidencia y se queda.
 * @param diasDeAvisoPrevio   umbrales, en días antes del vencimiento, en los que
 *                            se avisa. Uno por alerta: 30, 15 y 7 producen tres
 *                            avisos y no treinta.
 * @param ia                  ajustes del carril de IA.
 */
@ConfigurationProperties(prefix = "sicot.automatizacion")
public record AutomatizacionProperties(
        boolean habilitado,
        Duration sondeo,
        int tamanoDelLote,
        int trabajadores,
        int maximoDeIntentos,
        Duration esperaBase,
        Duration abandonoTrasVer,
        Duration retencionDeTareas,
        List<Integer> diasDeAvisoPrevio,
        Ia ia
) {

    /**
     * @param habilitado      si las reglas que llaman al modelo están activas.
     *                        <b>Apagado por defecto</b>, a propósito: generar
     *                        texto sin que nadie lo haya pedido, en un sistema de
     *                        contratación pública, es una decisión que toma quien
     *                        despliega y no un valor por omisión.
     * @param diasDelPeriodo  cuánto abarca el resumen. Es también el ritmo con
     *                        el que se emite: la regla se evalúa en la pasada
     *                        diaria, pero su clave de idempotencia incluye la
     *                        semana ISO, así que produce exactamente uno por
     *                        contrato y semana sin necesidad de un segundo
     *                        temporizador que mantener sincronizado.
     */
    public record Ia(boolean habilitado, int diasDelPeriodo) {
    }
}
