import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './api/client'
import {
  CortadaPorSegundoPlano,
  esperarPrimerPlano,
  repetirAlVolverSiSeCorta,
  vigilarSegundoPlano,
} from './segundoPlano'

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

describe('repetirAlVolverSiSeCorta', () => {
  /** Lo que llega cuando el teléfono cierra la conexión: no hay respuesta del servidor. */
  const cortada = () => Promise.reject(new TypeError('Failed to fetch'))

  it('no se resuelve hasta que termina el reintento', async () => {
    let terminarReintento!: (r: string) => void
    const peticion = vi
      .fn<() => Promise<string>>()
      .mockImplementationOnce(() => {
        ponerVisibilidad('hidden')
        return cortada()
      })
      .mockImplementationOnce(() => new Promise((res) => (terminarReintento = res)))
    const alCortarse = vi.fn()
    let resultado: string | undefined

    const pregunta = repetirAlVolverSiSeCorta(peticion, alCortarse).then((r) => (resultado = r))
    await vi.waitFor(() => expect(alCortarse).toHaveBeenCalledOnce())
    ponerVisibilidad('visible')
    await vi.waitFor(() => expect(peticion).toHaveBeenCalledTimes(2))
    // El reintento está en curso: quien llama tiene que seguir esperando.
    await Promise.resolve()
    expect(resultado).toBeUndefined()

    terminarReintento('respuesta')
    await pregunta
    expect(resultado).toBe('respuesta')
  })

  it('si el reintento también se corta, lo dice en vez de dejar un error de red', async () => {
    const peticion = vi.fn<() => Promise<string>>().mockImplementation(() => {
      ponerVisibilidad('hidden')
      return cortada()
    })

    const pregunta = repetirAlVolverSiSeCorta(peticion, () => ponerVisibilidad('visible'))

    await expect(pregunta).rejects.toBeInstanceOf(CortadaPorSegundoPlano)
    expect(peticion).toHaveBeenCalledTimes(2)
  })

  /**
   * Una respuesta del servidor —un 503 de Ollama, un 429 del limitador— no la
   * cortó el teléfono: repetirla al volver sería ocultar el motivo real.
   */
  it('un error del servidor sale tal cual aunque la página se haya ocultado', async () => {
    const error = new ApiError(503, 'El Copiloto IA no está disponible.')
    const peticion = vi.fn<() => Promise<string>>().mockImplementation(() => {
      ponerVisibilidad('hidden')
      return Promise.reject(error)
    })
    const alCortarse = vi.fn()

    await expect(repetirAlVolverSiSeCorta(peticion, alCortarse)).rejects.toBe(error)
    expect(peticion).toHaveBeenCalledOnce()
    expect(alCortarse).not.toHaveBeenCalled()
  })
})
