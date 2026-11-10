import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { setAuthToken } from './api/client'
import { alVolverElServidor, reiniciarLaVueltaParaPruebas, sondearElServidor } from './vueltaDelServidor'

/**
 * La sesión abierta sin red no tiene token, así que nada en ella se enteraba
 * de que el servidor había vuelto (prueba con el servidor real, 30-10-2026).
 */
describe('vueltaDelServidor', () => {
  const fetchFalso = vi.fn()

  function sesion(sinConexion: boolean) {
    localStorage.setItem(
      'sicot.session',
      JSON.stringify({ token: '', usuarioId: 3, nombre: 'S', email: 's@x', rol: 'SUPERVISOR', sinConexion }),
    )
  }

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchFalso)
    fetchFalso.mockReset()
    reiniciarLaVueltaParaPruebas()
    setAuthToken(null)
    localStorage.clear()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('en una sesión sin red, cualquier respuesta de SICOT es que volvió', async () => {
    sesion(true)
    fetchFalso.mockResolvedValue(new Response('{"status":405}', { status: 405 }))
    const oyente = vi.fn()
    alVolverElServidor(oyente)

    expect(await sondearElServidor()).toBe(true)
    expect(oyente).toHaveBeenCalledWith(true)
    expect(fetchFalso.mock.calls[0][0]).toMatch(/\/api\/auth\/login$/)
  })

  it('sin camino al servidor sigue sin red', async () => {
    sesion(true)
    fetchFalso.mockRejectedValue(new TypeError('Failed to fetch'))

    expect(await sondearElServidor()).toBe(false)
  })

  it('la página de error de un intermediario no cuenta como vuelta', async () => {
    sesion(true)
    fetchFalso.mockResolvedValue(new Response('<html>Bad Gateway</html>', { status: 502 }))

    expect(await sondearElServidor()).toBe(false)
  })

  it('con una sesión normal no pregunta nada', async () => {
    sesion(false)
    setAuthToken('jwt')

    expect(await sondearElServidor()).toBe(false)
    expect(fetchFalso).not.toHaveBeenCalled()
  })
})
