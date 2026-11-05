import { useEffect, useRef } from 'react'

import { hayTokenDeSesion } from '../services/api/client'
import { CON_LA_VENTANA_DELANTE_MS, EN_SEGUNDO_PLANO_MS, TRAS_UN_FALLO_MS } from './useNovedadesDelContrato'

/**
 * Vuelve a pedir una vista entera cada pocos segundos mientras está a la vista.
 *
 * ## Por qué existe
 *
 * El supervisor se entera de lo que hacen otros equipos sobre su contrato
 * gracias a {@link useNovedadesDelContrato}. El administrador no tenía nada
 * equivalente: «Seguimiento de supervisores» se pedía al abrir la pestaña y
 * después solo con «Actualizar». En la prueba de dos equipos del 05-11-2026,
 * el supervisor completaba un subpaso en un PC y el administrador seguía
 * viéndolo pendiente en el otro hasta que pulsara el botón.
 *
 * ## Por qué la vista entera y no las novedades
 *
 * El seguimiento reúne a todos los supervisores y todos sus contratos, y el
 * sondeo de novedades es por contrato: habría que abrir uno por cada contrato
 * de la lista. Volver a pedir la vista es una sola consulta y deja la regla en
 * el servidor, igual que hace el supervisor cuando recibe el aviso.
 *
 * ## El ritmo
 *
 * El mismo del sondeo de novedades, para que los dos lados se sientan igual de
 * «en vivo»: cada 5 s con la ventana delante, cada 30 s detrás o tras un fallo,
 * y en el acto al volver a la ventana. Sin token (sesión sin red) no pregunta.
 * Nunca hay dos peticiones a la vez: si el servidor tarda más que el intervalo,
 * la siguiente espera.
 *
 * @param activo    si la vista está en pantalla; con `false` no pregunta nada
 * @param refrescar pide la vista y la pinta; si falla, la pantalla conserva lo
 *                  que ya mostraba
 */
export function useRefrescoEnVivo(activo: boolean, refrescar: () => Promise<unknown>): void {
  // En una referencia por lo mismo que en useNovedadesDelContrato: quien llama
  // la define en línea y cambia en cada render.
  const pedir = useRef(refrescar)
  pedir.current = refrescar

  useEffect(() => {
    if (!activo || typeof window === 'undefined') return

    let cancelado = false
    let temporizador: number | undefined
    let pidiendo = false
    let falloLaUltima = false

    const programar = () => {
      window.clearTimeout(temporizador)
      if (cancelado) return
      const espera = falloLaUltima
        ? TRAS_UN_FALLO_MS
        : document.visibilityState === 'visible'
          ? CON_LA_VENTANA_DELANTE_MS
          : EN_SEGUNDO_PLANO_MS
      temporizador = window.setTimeout(() => void preguntar(), espera)
    }

    const preguntar = async () => {
      if (cancelado || pidiendo) return
      if (!hayTokenDeSesion()) {
        programar()
        return
      }
      pidiendo = true
      try {
        await pedir.current()
        falloLaUltima = false
      } catch {
        falloLaUltima = true
      } finally {
        pidiendo = false
        programar()
      }
    }

    // La primera vez no se pide en el acto: quien monta la vista ya la acaba de
    // cargar. Se empieza a contar desde ahora.
    programar()

    const alVolverLaVentana = () => {
      if (document.visibilityState === 'visible') void preguntar()
    }
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
  }, [activo])
}
