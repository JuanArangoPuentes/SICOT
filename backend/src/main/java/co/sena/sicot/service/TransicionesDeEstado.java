package co.sena.sicot.service;

import co.sena.sicot.entity.enums.EstadoContrato;
import co.sena.sicot.entity.enums.EstadoEtapa;
import co.sena.sicot.entity.enums.EstadoSubetapa;
import co.sena.sicot.exception.BusinessException;

/**
 * Reglas de transición de estado de SICOT, reunidas en un solo sitio.
 *
 * <p>El backend es la autoridad funcional del sistema (copilot-instructions
 * &sect;7): si aquí no se rechaza una transición, no la rechaza nadie. Antes de
 * esta clase, {@code EtapaService.cambiarEstadoSubetapa} y
 * {@code ContratoService.cambiarEstado} hacían {@code entidad.setEstado(destino)}
 * sin comprobar nada, así que el "estado" de una subetapa, una etapa o un
 * contrato era un campo de texto libre disfrazado de máquina de estados.
 *
 * <h2>Principio rector</h2>
 * NO se inventa el proceso institucional del SENA. Solo se cierra lo
 * aritméticamente imposible y lo que contradice el propio código. Todo lo demás
 * &mdash; aunque a ojo parezca dudoso &mdash; queda <b>permitido</b> y marcado
 * {@code PENDIENTE_DE_DEFINIR} hasta que una persona lo confirme contra la
 * documentación real.
 *
 * <h2>Mapa de transiciones</h2>
 *
 * <h3>Subetapa ({@code PENDIENTE} → {@code EN_CURSO} → {@code COMPLETADA})</h3>
 * <pre>
 *   desde \ hacia   PENDIENTE      EN_CURSO       COMPLETADA
 *   PENDIENTE       (sin cambio)   AVANCE         AVANCE (salto)
 *   EN_CURSO        RETROCESO      (sin cambio)   AVANCE
 *   COMPLETADA      RETROCESO      RETROCESO      (sin cambio)
 * </pre>
 * El flujo real del supervisor usa {@code PENDIENTE→EN_CURSO} (activar la etapa
 * siguiente), {@code PENDIENTE→COMPLETADA} (marcar un sub-paso de verificación
 * que nunca se "inició") y {@code EN_CURSO→COMPLETADA} (cerrar el sub-paso
 * activo). Los retrocesos son correcciones legítimas: no se rechazan
 * ({@code PENDIENTE_DE_DEFINIR} si el SENA los prohíbe), pero se marcan como
 * {@code RETROCESO} para que la auditoría los distinga.
 *
 * <h3>Etapa</h3>
 * No hay endpoint para fijar el estado de una etapa: siempre se deriva de sus
 * subetapas en {@code EtapaService.recalcularEtapa}. Aquí solo se clasifica el
 * sentido del recálculo (avance / retroceso) para la traza.
 *
 * <h3>Contrato ({@code BORRADOR}, {@code ACTIVO}, {@code SUSPENDIDO},
 * {@code FINALIZADO}, {@code CANCELADO})</h3>
 * Lo único que se cierra sin inventar nada:
 * <ul>
 *   <li><b>{@code * → BORRADOR}</b> (desde cualquier estado que no sea
 *   {@code BORRADOR}): contradice el propio código. {@code ContratoService.crear}
 *   es el <b>único</b> sitio que asigna {@code BORRADOR}, y es el estado con el
 *   que nace un contrato. No existe ningún camino de "devolver a borrador";
 *   {@code FINALIZADO → BORRADOR} entra aquí.</li>
 *   <li><b>{@code X → X}</b>: no es una transición; se trata como no-op.</li>
 * </ul>
 * Todo lo demás queda {@code PENDIENTE_DE_DEFINIR} y <b>permitido</b>, incluidas
 * las que a ojo parecen inválidas (reabrir un contrato {@code FINALIZADO} o
 * {@code CANCELADO}): la documentación del SENA no confirma la máquina de estados
 * del contrato y no se adivina. Se registran igual, con {@code ANTERIOR →
 * DESTINO}, para que la auditoría las vea.
 *
 * <p>El <b>cierre automático</b> del contrato cuando las 6 etapas quedan
 * {@code COMPLETADA} <b>no</b> se implementa a propósito: el cierre de un
 * contrato del Estado es un acto humano (lo ejecuta GESTION/ADMINISTRADOR con
 * {@code PATCH /api/contratos/{id}/estado}), no una consecuencia aritmética del
 * avance de las etapas.
 */
public final class TransicionesDeEstado {

    private TransicionesDeEstado() {
    }

    /** Clasificación de una transición ya validada, para la traza de auditoría. */
    public enum Sentido { AVANCE, RETROCESO, SIN_CAMBIO }

    // ------------------------------------------------------------------
    // Subetapa
    // ------------------------------------------------------------------

    /**
     * Comprueba una transición de estado de subetapa y la clasifica.
     *
     * @throws BusinessException si {@code destino} es {@code null}.
     * @return {@link Sentido#SIN_CAMBIO} si ya estaba en ese estado,
     *         {@link Sentido#AVANCE} si progresa, {@link Sentido#RETROCESO} si
     *         se reabre un estado anterior (permitido, pero se registra como tal).
     */
    public static Sentido validarSubetapa(EstadoSubetapa actual, EstadoSubetapa destino) {
        if (destino == null) {
            throw new BusinessException("El estado destino de la subetapa es obligatorio.");
        }
        if (actual == destino) {
            return Sentido.SIN_CAMBIO;
        }
        if (actual == null) {
            return Sentido.AVANCE;
        }
        return destino.ordinal() > actual.ordinal() ? Sentido.AVANCE : Sentido.RETROCESO;
    }

    // ------------------------------------------------------------------
    // Etapa (solo clasificación del recálculo; no hay transición manual)
    // ------------------------------------------------------------------

    /**
     * Clasifica el resultado de {@code EtapaService.recalcularEtapa}: es un
     * retroceso si el porcentaje baja o el estado regresa a uno anterior
     * (p. ej. una subetapa {@code COMPLETADA} que se reabre baja la etapa de
     * {@code COMPLETADA} a {@code EN_CURSO}).
     */
    public static Sentido sentidoRecalculoEtapa(EstadoEtapa estadoAnterior, int porcentajeAnterior,
                                                EstadoEtapa estadoNuevo, int porcentajeNuevo) {
        if (estadoAnterior == estadoNuevo && porcentajeAnterior == porcentajeNuevo) {
            return Sentido.SIN_CAMBIO;
        }
        boolean retrocede = porcentajeNuevo < porcentajeAnterior
                || estadoNuevo.ordinal() < estadoAnterior.ordinal();
        return retrocede ? Sentido.RETROCESO : Sentido.AVANCE;
    }

    // ------------------------------------------------------------------
    // Contrato
    // ------------------------------------------------------------------

    /**
     * Comprueba una transición de estado de contrato.
     *
     * @throws BusinessException si {@code destino} es {@code null} o si la
     *         transición contradice el modelo del código ({@code * → BORRADOR}).
     * @return {@link Sentido#SIN_CAMBIO} si ya estaba en ese estado (el llamador
     *         debe tratarlo como no-op). En otro caso {@link Sentido#AVANCE} /
     *         {@link Sentido#RETROCESO} como pista aproximada por orden del enum
     *         &mdash; <b>no</b> es una máquina de estados validada: para el
     *         contrato solo se garantiza el rechazo de {@code * → BORRADOR}.
     */
    public static Sentido validarContrato(EstadoContrato actual, EstadoContrato destino) {
        if (destino == null) {
            throw new BusinessException("El estado destino del contrato es obligatorio.");
        }
        if (actual == destino) {
            return Sentido.SIN_CAMBIO;
        }
        if (destino == EstadoContrato.BORRADOR) {
            throw new BusinessException("Un contrato no puede regresar a BORRADOR: es únicamente el estado "
                    + "con el que se crea. Estado actual: " + actual.name() + ".");
        }
        return destino.ordinal() > actual.ordinal() ? Sentido.AVANCE : Sentido.RETROCESO;
    }
}
