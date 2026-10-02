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
// Repetir una pregunta no cuesta otra inferencia: cada una lleva un
// {@link idDeSolicitud} y el reintento manda el mismo, así que el servidor la
// reconoce y devuelve la respuesta que sigue calculando —o la que ya calculó—
// en vez de empezar otra. Antes el reintento competía por la CPU, y por uno de
// los dos cupos del limitador de IA, con la inferencia huérfana del primer
// intento: la respuesta tardaba casi el doble o acababa en un 429 (auditoría
// del 02-10-2026).
//
// Un teléfono pasa también a segundo plano cuando la pantalla se apaga sola,
// sin que el supervisor salga de SICOT: {@link mantenerPantallaEncendida} la
// pide encendida mientras dura una petición larga de IA.

import { ApiError } from './api/client'

/**
 * Identificador de una pregunta al Copiloto (ChatRequest.idSolicitud): uno por
 * pregunta, y el mismo en su reintento, para que el servidor lo reconozca.
 *
 * `crypto.randomUUID` solo existe en un contexto seguro, y SICOT abierto por
 * http:// desde otro equipo —el servidor de desarrollo en la red local, o un
 * Centro que todavía no tiene TLS (ADR-009)— no lo es. `getRandomValues` sí
 * existe ahí, y 16 bytes al azar bastan: el identificador solo tiene que no
 * repetirse entre las preguntas de un supervisor.
 */
export function idDeSolicitud(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return Array.from(crypto.getRandomValues(new Uint8Array(16)), (b) => b.toString(16).padStart(2, '0')).join('')
}

/**
 * Pide que la pantalla no se apague sola mientras dura una petición larga de
 * IA, y devuelve con qué soltar ese pedido.
 *
 * Con el apagado automático habitual de un teléfono (30 s o 1 min), la página
 * pasa a segundo plano a mitad de una pregunta que dura minutos, y Android le
 * corta la conexión igual que si el supervisor hubiera cambiado de aplicación;
 * generar un documento, que no se repite, fallaba siempre si nadie tocaba la
 * pantalla. Se usa la Screen Wake Lock API: el sistema suelta el pedido cada
 * vez que la página se oculta, así que al volver se pide otro mientras la
 * petición siga.
 *
 * Si el navegador o el WebView no la ofrecen, o el sistema la niega (ahorro de
 * batería), no pasa nada: la petición sigue igual y la pantalla se apaga como
 * antes. Por eso «Pensando…» sigue pidiendo mantener la pantalla encendida.
 */
export function mantenerPantallaEncendida(): () => void {
  const wakeLock = 'wakeLock' in navigator ? navigator.wakeLock : undefined
  if (!wakeLock) return () => {}
  let activo = true
  let pidiendo = false
  let pedido: WakeLockSentinel | null = null
  const pedir = async () => {
    if (!activo || pidiendo || document.visibilityState !== 'visible' || pedido?.released === false) return
    pidiendo = true
    try {
      const nuevo = await wakeLock.request('screen')
      // Si se soltó mientras el sistema lo concedía, se devuelve enseguida.
      if (activo) pedido = nuevo
      else await nuevo.release()
    } catch {
      // Negado o no disponible: la petición de IA no depende de esto.
    } finally {
      pidiendo = false
    }
  }
  const alVolver = () => {
    if (document.visibilityState === 'visible') void pedir()
  }
  document.addEventListener('visibilitychange', alVolver)
  void pedir()
  return () => {
    activo = false
    document.removeEventListener('visibilitychange', alVolver)
    void pedido?.release().catch(() => {})
    pedido = null
  }
}

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
 * `peticion` se llama las dos veces tal cual, así que quien la arma le pone
 * una sola vez su {@link idDeSolicitud}: con eso el reintento recoge la
 * respuesta del primer intento en vez de pagar otra inferencia.
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
