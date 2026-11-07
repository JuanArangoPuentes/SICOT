// Darse cuenta de que el servidor volvió, en una sesión abierta sin red.
//
// <h2>Por qué hace falta</h2>
// La sesión sin conexión (ADR-015, punto 4) no lleva token. Con red de vuelta,
// nada en ella se entera: las pantallas siguen sirviendo la copia, la cola no
// envía porque no tiene con qué presentarse, y el supervisor puede pasar el
// día creyendo que sigue sin conexión con el servidor del Centro al lado. Lo
// vio la prueba con el servidor real del 30-10-2026.
//
// <h2>Cómo pregunta</h2>
// Con un `GET` a `/api/auth/login`, que no existe como `GET`: el servidor de
// SICOT contesta 405 con su JSON, sin sesión y sin contar como intento de
// acceso. Pasa por el mismo proxy que el resto de la API (`/actuator` no
// siempre está enrutado). Cualquier respuesta que no sea la página de error de
// un intermediario es el servidor de SICOT contestando.
//
// No cierra la sesión por su cuenta: avisa, y el supervisor decide cuándo
// volver a entrar. Cerrarla sola le podía cortar a media escritura.

import { CODIGOS_DE_PASARELA, apiBase, hayTokenDeSesion } from './api/client'
import { leerSesionGuardada } from './session'

/** Cada cuánto se pregunta. Es una petición mínima y solo en una sesión sin red. */
const INTERVALO_MS = 15_000

let deVuelta = false
const oyentes = new Set<(deVuelta: boolean) => void>()

function anunciar(valor: boolean) {
  if (valor === deVuelta) return
  deVuelta = valor
  oyentes.forEach((o) => o(valor))
}

/** ¿El servidor volvió a contestar mientras la sesión sigue siendo sin red? */
export function servidorDeVuelta(): boolean {
  return deVuelta
}

export function alVolverElServidor(oyente: (deVuelta: boolean) => void): () => void {
  oyentes.add(oyente)
  return () => {
    oyentes.delete(oyente)
  }
}

/** Una pregunta. Exportada para las pruebas; la aplicación usa {@link vigilarLaVueltaDelServidor}. */
export async function sondearElServidor(): Promise<boolean> {
  if (hayTokenDeSesion() || leerSesionGuardada()?.sinConexion !== true) {
    anunciar(false)
    return false
  }
  try {
    const r = await fetch(`${apiBase()}/api/auth/login`, { method: 'GET', cache: 'no-store' })
    const contesto = !CODIGOS_DE_PASARELA.has(r.status)
    anunciar(contesto)
    return contesto
  } catch {
    anunciar(false)
    return false
  }
}

let vigilando = false

export function vigilarLaVueltaDelServidor(): void {
  if (vigilando || typeof window === 'undefined') return
  vigilando = true
  window.addEventListener('online', () => void sondearElServidor())
  window.setInterval(() => void sondearElServidor(), INTERVALO_MS)
}

/** Solo para las pruebas. */
export function reiniciarLaVueltaParaPruebas(): void {
  deVuelta = false
  oyentes.clear()
}
