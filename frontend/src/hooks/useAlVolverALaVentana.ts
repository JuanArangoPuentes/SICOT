import { useEffect, useRef } from 'react'

import { hayTokenDeSesion } from '../services/api/client'

/** Eventos de una misma vuelta a la ventana que cuentan como uno solo. */
const UN_SOLO_AVISO_MS = 1_000

/**
 * Hace algo cada vez que la ventana vuelve al primer plano.
 *
 * ## Por qué existe
 *
 * Las listas de SICOT —los contratos de Gestión, los del supervisor— se piden
 * una vez al montar y no se vuelven a pedir. Con más de un equipo eso deja la
 * lista congelada: un contrato que Gestión crea en un computador no aparece en
 * el otro hasta recargar la página, y el supervisor al que acaban de designar
 * no ve su contrato aunque tenga SICOT abierto.
 *
 * El sondeo de novedades ({@link useNovedadesDelContrato}) no cubre esto a
 * propósito: pregunta **por un contrato**, y lo que falta aquí es un contrato
 * que todavía no está en la lista. Volver a la ventana es el momento exacto en
 * que a alguien le importa, y no cuesta nada.
 *
 * ## Lo que no hace
 *
 * No sondea. Entre una vuelta y otra no pregunta nada: para el expediente que
 * se está mirando ya está el otro hook, y una lista de contratos no cambia
 * cada cinco segundos.
 *
 * Y no hace nada sin token de sesión. Una sesión abierta sin red (ADR-015,
 * punto 4) no lleva token: lo que se ve es la copia local, y pedir la lista
 * solo produciría un error que no es de nadie.
 */
export function useAlVolverALaVentana(hacer: () => void): void {
  // En una referencia: quien llama la define en línea, así que cambia de
  // identidad en cada render y volvería a suscribirse sin parar.
  const accion = useRef(hacer)
  accion.current = hacer

  useEffect(() => {
    if (typeof window === 'undefined') return

    // Volver a la ventana dispara casi siempre DOS eventos seguidos
    // —`visibilitychange` y `focus`—, y sin esto la lista se pedía dos veces
    // en cada vuelta: en el teléfono, cada vez que se desbloquea. Uno basta.
    let ultimaVez = Number.NEGATIVE_INFINITY
    const alVolver = () => {
      if (document.visibilityState !== 'visible') return
      if (!hayTokenDeSesion()) return
      const ahora = Date.now()
      if (ahora - ultimaVez < UN_SOLO_AVISO_MS) return
      ultimaVez = ahora
      accion.current()
    }
    document.addEventListener('visibilitychange', alVolver)
    window.addEventListener('focus', alVolver)
    window.addEventListener('online', alVolver)

    return () => {
      document.removeEventListener('visibilitychange', alVolver)
      window.removeEventListener('focus', alVolver)
      window.removeEventListener('online', alVolver)
    }
  }, [])
}
