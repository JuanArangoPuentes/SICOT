import { useEffect, useRef } from 'react'

import { hayTokenDeSesion } from '../services/api/client'
import { getNovedades } from '../services/novedadesService'

/**
 * Se entera de lo que hizo otro equipo sobre el mismo contrato, sin que nadie
 * recargue nada.
 *
 * ## Por qué existe
 *
 * SICOT pedía cada lectura una sola vez, al abrir el contrato, y no la volvía a
 * pedir nunca. Con un solo equipo eso no se nota. Con dos —el computador y el
 * teléfono del mismo supervisor, o Gestión y el supervisor— el expediente que
 * se está mirando se queda congelado en el instante en que se abrió: lo que el
 * otro cierre, cargue o firme no aparece hasta cambiar de contrato, volver a
 * entrar o recargar la página a mano.
 *
 * ## Lo que hace, y lo que deliberadamente no hace
 *
 * Pregunta cada pocos segundos si hay novedades y, cuando las hay, **avisa**.
 * No trae datos: quien recibe el aviso vuelve a pedir las pantallas enteras,
 * porque el servidor es la única autoridad sobre el expediente (ADR-015) y una
 * novedad aplicada a mano en el cliente sería una segunda implantación de las
 * reglas. Por eso el aviso no lleva carga útil: es una señal, no un parche.
 *
 * ## El ritmo
 *
 * Con la ventana delante, cada pocos segundos: es una consulta por clave
 * primaria contra una tabla indexada, y es lo que hace que la propagación entre
 * dos equipos de la misma red se sienta inmediata. Con la ventana detrás se
 * espacia —nadie está mirando— y al volver se pregunta en el acto, que es
 * justo cuando importa. Tras un fallo también se espacia: si el camino al
 * servidor se cortó, insistir cada pocos segundos no lo arregla y llena la
 * consola de errores que no son de nadie.
 *
 * ## Cuándo no pregunta
 *
 * Sin token de sesión. Una sesión abierta sin red (ADR-015, punto 4) no lleva
 * token, así que este sondeo se calla entero: lo que se está viendo es la copia
 * local, y decir «no hay novedades» sobre ella sería mentir. De avisar de que
 * no hay conexión ya se encargan el aviso de la copia y la franja de cambios
 * sin enviar.
 *
 * @param contratoId      contrato que se está mirando, o `null` si no hay uno
 * @param alHaberNovedades qué hacer cuando el servidor dice que algo cambió
 */

/**
 * Con la ventana delante. Es el ritmo que decide qué tan «en vivo» se siente.
 * Lo comparte {@link useRefrescoEnVivo}, para que el administrador vea los
 * cambios al mismo ritmo que el supervisor.
 */
export const CON_LA_VENTANA_DELANTE_MS = 5_000

/** Con la ventana detrás: nadie está mirando, y al volver se pregunta en el acto. */
export const EN_SEGUNDO_PLANO_MS = 30_000

/** Tras un fallo del camino al servidor. */
export const TRAS_UN_FALLO_MS = 30_000

export function useNovedadesDelContrato(contratoId: number | null | undefined, alHaberNovedades: () => void): void {
  // En una referencia y no en las dependencias: quien llama define el aviso en
  // línea, así que cambia de identidad en cada render y reiniciaría el sondeo
  // —perdiendo la marca— sesenta veces por minuto.
  const avisar = useRef(alHaberNovedades)
  avisar.current = alHaberNovedades

  useEffect(() => {
    if (contratoId == null || typeof window === 'undefined') return

    let cancelado = false
    let temporizador: number | undefined
    // La marca que devolvió el servidor. `null` hasta la primera respuesta.
    let marca: number | null = null
    let preguntando = false
    let falloLaUltima = false

    const cuandoVolverAPreguntar = () => {
      if (falloLaUltima) return TRAS_UN_FALLO_MS
      return document.visibilityState === 'visible' ? CON_LA_VENTANA_DELANTE_MS : EN_SEGUNDO_PLANO_MS
    }

    const programar = () => {
      window.clearTimeout(temporizador)
      if (cancelado) return
      temporizador = window.setTimeout(() => void preguntar(), cuandoVolverAPreguntar())
    }

    const preguntar = async () => {
      if (cancelado || preguntando) return
      if (!hayTokenDeSesion()) {
        programar()
        return
      }
      preguntando = true
      const veniaDeUnFallo = falloLaUltima
      try {
        const respuesta = await getNovedades(contratoId, marca)
        falloLaUltima = false
        if (cancelado) return
        // La primera respuesta solo fija la marca: la pantalla acaba de cargar
        // el estado entero, así que nada de lo que ya está ahí es una novedad.
        const yaHabiaMarca = marca !== null
        marca = respuesta.ultimoId
        if (!yaHabiaMarca) return
        if (respuesta.novedades.length > 0) {
          avisar.current()
          return
        }
        // Sin novedades, pero el camino al servidor acaba de volver. Hay que
        // avisar igual: mientras estaba cortado, las pantallas se sirvieron de
        // la copia guardada (ADR-015) y siguen mostrándola —con su franja de
        // «lo que ve es la copia»— hasta que alguien vuelva a pedir. Nadie lo
        // haría: esta consulta no pasa por la copia, así que lo que el
        // servidor conteste nunca la releva por su cuenta.
        if (veniaDeUnFallo) avisar.current()
      } catch {
        falloLaUltima = true
      } finally {
        preguntando = false
        programar()
      }
    }

    void preguntar()

    const alVolverLaVentana = () => {
      if (document.visibilityState === 'visible') void preguntar()
    }
    // Los tres: `visibilitychange` cubre cambiar de pestaña y el teléfono, que
    // detiene los temporizadores en segundo plano; `focus`, cambiar de ventana
    // en el escritorio sin ocultar la pestaña; `online`, volver de un corte.
    document.addEventListener('visibilitychange', alVolverLaVentana)
    window.addEventListener('focus', alVolverLaVentana)
    window.addEventListener('online', alVolverLaVentana)

    return () => {
      cancelado = true
      window.clearTimeout(temporizador)
      document.removeEventListener('visibilitychange', alVolverLaVentana)
      window.removeEventListener('focus', alVolverLaVentana)
      window.removeEventListener('online', alVolverLaVentana)
    }
  }, [contratoId])
}
