package co.sena.sicot.automatizacion;

import co.sena.sicot.entity.enums.TipoTareaAutomatizada;

/**
 * Quien sabe producir de verdad uno de los efectos de la cola.
 *
 * <p>Se descubren solas, igual que las reglas: el ejecutor recibe la lista de
 * todas las implementaciones y las indexa por {@link #tipo()}. Añadir un efecto
 * nuevo es crear la clase y su variante de payload; no hay ningún {@code switch}
 * central que actualizar, que es el sitio donde se olvida el caso nuevo.
 *
 * <h2>Cómo se comunica un fallo</h2>
 * Lanzando. El ejecutor traduce la excepción en un reintento con espera
 * creciente, y tras agotar los intentos deja la tarea FALLIDA con el mensaje.
 *
 * <p>Una acción <b>no</b> debe capturar sus propios errores para devolver
 * «descartada»: eso convertiría un problema transitorio —el servidor de correo
 * tuvo un mal minuto— en una tarea que nadie va a reintentar nunca.
 * {@link ResultadoDeAccion#descartada} es solo para lo que no va a mejorar por
 * reintentarse.
 */
public interface AccionDeTarea {

    TipoTareaAutomatizada tipo();

    /**
     * @param tarea la que este trabajador ya reservó
     * @throws RuntimeException si falló y merece reintentarse
     */
    ResultadoDeAccion ejecutar(TareaEnEjecucion tarea);
}
