// Peticiones largas que se cortan al salir de la aplicación.
//
// <h2>Qué pasa, medido</h2>
// En el APK de Android, cuando el supervisor cambia de aplicación, el sistema
// destruye las conexiones abiertas de SICOT a los pocos segundos. Se vio en el
// emulador (Android 15) el 18 de septiembre de 2026: 17 s después de pasar a
// segundo plano, `system_server` registró «Destroyed live tcp sockets for
// uids={…, 10209, …}», y 10209 es el uid de SICOT.
//
// Casi ninguna petición de SICOT dura tanto. Las de la IA sí: la primera
// pregunta al copiloto sobre CPU tarda entre uno y dos minutos y medio, y
// generar un documento, más de dos. Es justo el rato en que un supervisor con
// el teléfono en la mano se va a otra aplicación. El servidor terminaba su
// trabajo —la respuesta se generó en 119 s— y la aplicación la perdía,
// mostrando además un mensaje falso: «verifique que Ollama esté disponible».
//
// <h2>Qué hace este módulo</h2>
// Solo lo mínimo para no mentir y no perder trabajo: saber si la página pasó a
// segundo plano mientras una petición estaba en curso, y esperar a que vuelva.
// Qué hacer con eso lo decide quien llama, porque no es lo mismo en todas: una
// pregunta al copiloto se puede repetir sin consecuencias, pero generar un
// documento oficial dos veces crearía dos actas.
//
// El arreglo de fondo sería que el servidor guarde la respuesta y la aplicación
// la recoja al volver. Eso toca el backend y queda como siguiente paso escrito;
// esto es lo que se puede hacer sin él.

import { ApiError } from './api/client'

/** Vigila si la página se oculta mientras dura una operación. */
export function vigilarSegundoPlano() {
  let seOculto = document.visibilityState === 'hidden'
  const alCambiar = () => {
    if (document.visibilityState === 'hidden') seOculto = true
  }
  document.addEventListener('visibilitychange', alCambiar)
  return {
    /** ¿Pasó la página a segundo plano en algún momento desde que se empezó a vigilar? */
    seOculto: () => seOculto,
    terminar: () => document.removeEventListener('visibilitychange', alCambiar),
  }
}

/** Se resuelve cuando la página vuelve a estar en pantalla (o enseguida, si ya lo está). */
export function esperarPrimerPlano(): Promise<void> {
  if (document.visibilityState === 'visible') return Promise.resolve()
  return new Promise((resolver) => {
    const alCambiar = () => {
      if (document.visibilityState !== 'visible') return
      document.removeEventListener('visibilitychange', alCambiar)
      resolver()
    }
    document.addEventListener('visibilitychange', alCambiar)
  })
}

/** Una petición repetible se cortó otra vez, en el reintento, por pasar a segundo plano. */
export class CortadaPorSegundoPlano extends Error {
  constructor() {
    super('La conexión se volvió a cortar porque SICOT pasó a segundo plano.')
    this.name = 'CortadaPorSegundoPlano'
  }
}

/**
 * Hace una petición que se puede repetir sin consecuencias —una pregunta al
 * copiloto; generar un documento NO, porque crearía dos actas— y, si se corta
 * porque la página pasó a segundo plano, llama a `alCortarse`, espera a que el
 * supervisor vuelva y la repite una sola vez. Si el reintento también se corta
 * por lo mismo, lanza {@link CortadaPorSegundoPlano}, para que quien llama no
 * le eche la culpa a Ollama. Cualquier otro fallo sale tal cual.
 *
 * La promesa no se resuelve hasta que termina el reintento, y eso es lo que
 * importa. Antes el chat lo hacía a mano con `return pregunta(…)` dentro de un
 * `catch`: el `finally` del primer intento corría en cuanto arrancaba el
 * segundo, apagaba «Pensando…» y dejaba al supervisor lanzar otra pregunta
 * mientras el reintento seguía minutos en el servidor. Y la revisión del paso
 * ni siquiera vigilaba: un corte del teléfono salía como «no se pudo conectar
 * con Ollama».
 */
export async function repetirAlVolverSiSeCorta<T>(peticion: () => Promise<T>, alCortarse: () => void): Promise<T> {
  try {
    return await vigilada(peticion)
  } catch (e) {
    if (!(e instanceof CortadaPorSegundoPlano)) throw e
  }
  alCortarse()
  await esperarPrimerPlano()
  return await vigilada(peticion)
}

/** La petición, pero si falla sin respuesta del servidor después de ocultarse la página, lanza CortadaPorSegundoPlano. */
async function vigilada<T>(peticion: () => Promise<T>): Promise<T> {
  const vigia = vigilarSegundoPlano()
  try {
    return await peticion()
  } catch (e) {
    // Un ApiError es una respuesta del servidor: a esa no la cortó el teléfono.
    if (!(e instanceof ApiError) && vigia.seOculto()) throw new CortadaPorSegundoPlano()
    throw e
  } finally {
    vigia.terminar()
  }
}
