import { afterEach, describe, expect, it } from 'vitest'
import { esperarPrimerPlano, vigilarSegundoPlano } from './segundoPlano'

function ponerVisibilidad(estado: 'visible' | 'hidden') {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => estado })
  document.dispatchEvent(new Event('visibilitychange'))
}

afterEach(() => ponerVisibilidad('visible'))

describe('vigilarSegundoPlano', () => {
  it('recuerda que la página se ocultó aunque ya haya vuelto', () => {
    // Es el caso real: cuando la petición falla, el supervisor ya volvió a
    // SICOT. Lo que importa es si se fue mientras esperaba, no dónde está ahora.
    const vigia = vigilarSegundoPlano()
    expect(vigia.seOculto()).toBe(false)
    ponerVisibilidad('hidden')
    ponerVisibilidad('visible')
    expect(vigia.seOculto()).toBe(true)
    vigia.terminar()
  })

  it('no acusa nada si la página nunca salió de pantalla', () => {
    const vigia = vigilarSegundoPlano()
    ponerVisibilidad('visible')
    expect(vigia.seOculto()).toBe(false)
    vigia.terminar()
  })
})

describe('esperarPrimerPlano', () => {
  it('se resuelve al volver a la aplicación', async () => {
    ponerVisibilidad('hidden')
    let resuelta = false
    const espera = esperarPrimerPlano().then(() => (resuelta = true))
    await Promise.resolve()
    expect(resuelta).toBe(false)
    ponerVisibilidad('visible')
    await espera
    expect(resuelta).toBe(true)
  })

  it('se resuelve enseguida si ya está en pantalla', async () => {
    await expect(esperarPrimerPlano()).resolves.toBeUndefined()
  })
})
