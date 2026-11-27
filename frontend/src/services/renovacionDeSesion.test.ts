import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError, apiFetch, onUnauthorized, setAuthToken } from './api/client'
import { anotarContactoConElServidor, recordarAcceso } from './accesoSinRed'
import { colaDe } from './colaDeCambios'
import { copiaLocalDe } from './copiaLocal'
import { cambiarMiContrasena } from './cuentaService'
import { leerSesionGuardada, saveSession } from './session'
import type { AuthResponse } from './api/types'

// Lo que cuelga de adoptar una sesión —la copia local, la cola, la huella sin
// red— tiene sus propias pruebas y aquí solo haría ruido: lo que se vigila es
// el token que llevan las peticiones y quién decide que la sesión se terminó.
vi.mock('./accesoSinRed', async (original) => ({
  ...(await original<typeof import('./accesoSinRed')>()),
  anotarContactoConElServidor: vi.fn(),
  olvidarAccesoSinRed: vi.fn(),
  recordarAcceso: vi.fn(),
}))
vi.mock('./copiaLocal', async (original) => ({
  ...(await original<typeof import('./copiaLocal')>()),
  copiaLocalDe: vi.fn(),
}))
vi.mock('./colaDeCambios', async (original) => ({
  ...(await original<typeof import('./colaDeCambios')>()),
  colaDe: vi.fn(),
  reenviarLaCola: vi.fn(),
}))

const SESION_VIEJA: AuthResponse = {
  token: 'token-revocado',
  usuarioId: 3,
  nombre: 'Sup',
  email: 's@sicot.test',
  rol: 'SUPERVISOR',
}
const SESION_NUEVA: AuthResponse = { ...SESION_VIEJA, token: 'token-nuevo' }

const NOVEDADES = '/api/contratos/7/novedades'

function respuesta(status: number, cuerpo?: unknown) {
  return new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

/** Una respuesta que se suelta cuando la prueba decide, para ordenar la carrera. */
function enVuelo() {
  let soltar!: (res: Response) => void
  const promesa = new Promise<Response>((listo) => {
    soltar = listo
  })
  return { promesa, soltar }
}

function autorizacionDe(llamada: unknown[]): string | null {
  return ((llamada[1] as RequestInit).headers as Headers).get('Authorization')
}

function esSondeo(llamada: unknown[]): boolean {
  return String(llamada[0]).includes('/novedades')
}

/**
 * Cambiar la contraseña propia y seguir trabajando en este equipo, que es lo
 * que promete la ventana: «En este equipo sigue trabajando sin volver a
 * entrar».
 *
 * <h2>El fallo que esto cubre</h2>
 * El servidor revoca los tokens anteriores de la cuenta mientras atiende el
 * cambio, y el cliente no se entera hasta que le llega la respuesta. En ese
 * hueco —un viaje de red— el sondeo de novedades (ADR-016, cada cinco
 * segundos), el refresco en vivo o el precalentado del Copiloto salen con el
 * token ya revocado y vuelven con un 401 que no es una sesión muerta. Antes de
 * esto, ese 401 disparaba el cierre global: la contraseña se cambiaba con éxito
 * y la aplicación volvía sola a la pantalla de acceso. De forma intermitente,
 * porque depende de que una petición de fondo caiga justo ahí (observado el
 * 18-11-2026 con un SUPERVISOR en el panel de contrato).
 */
describe('cambiar la contraseña con peticiones de fondo en marcha', () => {
  const fetchMock = vi.fn()
  const cerrarSesion = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
    cerrarSesion.mockReset()
    localStorage.clear()
    setAuthToken(null)
    onUnauthorized(cerrarSesion)
    vi.mocked(recordarAcceso).mockResolvedValue(undefined)
    vi.mocked(anotarContactoConElServidor).mockReturnValue(undefined)
    vi.mocked(colaDe).mockResolvedValue(undefined as never)
    vi.mocked(copiaLocalDe).mockResolvedValue(undefined as never)
  })

  afterEach(() => {
    onUnauthorized(null)
    setAuthToken(null)
    localStorage.clear()
    vi.unstubAllGlobals()
  })

  /**
   * Reparte las respuestas: el cambio de contraseña y el primer sondeo quedan
   * en vuelo hasta que la prueba los suelta; del segundo sondeo en adelante, el
   * servidor contesta que sí hay novedades.
   */
  function servidor() {
    const cambio = enVuelo()
    const sondeo = enVuelo()
    let sondeos = 0
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/api/cuenta/contrasena')) return cambio.promesa
      if (url.includes('/novedades')) {
        sondeos += 1
        return sondeos === 1 ? sondeo.promesa : Promise.resolve(respuesta(200, { hayNovedades: true }))
      }
      throw new Error(`petición no prevista: ${url}`)
    })
    return { cambio, sondeo }
  }

  it('repite con el token nuevo el sondeo que salió con el revocado, y la sesión sigue abierta', async () => {
    saveSession(SESION_VIEJA)
    const { cambio, sondeo } = servidor()

    const cambiando = cambiarMiContrasena('s@sicot.test', 'Super2026', 'OtraClave456*')
    // El sondeo sale ahora, con el token que el servidor está revocando.
    const sondeando = apiFetch<{ hayNovedades: boolean }>(NOVEDADES)

    cambio.soltar(respuesta(200, SESION_NUEVA))
    await cambiando

    // Y su 401 llega después de que el token nuevo ya esté puesto, que es el
    // orden normal: el 401 no puede volver antes de haber salido la petición.
    sondeo.soltar(respuesta(401, { status: 401, message: 'No autenticado' }))

    await expect(sondeando).resolves.toEqual({ hayNovedades: true })
    expect(cerrarSesion).not.toHaveBeenCalled()
    expect(leerSesionGuardada()?.token).toBe('token-nuevo')

    const sondeosHechos = fetchMock.mock.calls.filter(esSondeo)
    expect(sondeosHechos).toHaveLength(2)
    expect(autorizacionDe(sondeosHechos[0])).toBe('Bearer token-revocado')
    expect(autorizacionDe(sondeosHechos[1])).toBe('Bearer token-nuevo')
  })

  it('tampoco cierra la sesión si el 401 del sondeo llega antes que el token nuevo', async () => {
    saveSession(SESION_VIEJA)
    const { cambio, sondeo } = servidor()

    const cambiando = cambiarMiContrasena('s@sicot.test', 'Super2026', 'OtraClave456*')
    const sondeando = apiFetch(NOVEDADES)

    // El tramo más estrecho del hueco: no hay token nuevo con el que repetir,
    // así que el sondeo falla para quien lo pidió —que lo ignora— y nada más.
    sondeo.soltar(respuesta(401, { status: 401, message: 'No autenticado' }))
    await expect(sondeando).rejects.toBeInstanceOf(ApiError)
    expect(cerrarSesion).not.toHaveBeenCalled()

    cambio.soltar(respuesta(200, SESION_NUEVA))
    await cambiando

    // Y el siguiente, segundos después, ya va con el token nuevo.
    await expect(apiFetch(NOVEDADES)).resolves.toEqual({ hayNovedades: true })
    expect(cerrarSesion).not.toHaveBeenCalled()
  })

  it('el token nuevo está en el cliente HTTP antes de que nadie vuelva a pedir nada', async () => {
    saveSession(SESION_VIEJA)
    const { cambio, sondeo } = servidor()
    sondeo.soltar(respuesta(200, { hayNovedades: true }))

    const cambiando = cambiarMiContrasena('s@sicot.test', 'Super2026', 'OtraClave456*')
    cambio.soltar(respuesta(200, SESION_NUEVA))
    await cambiando

    // Sin pasar por ningún render de React: lo pone `renovarSesion`.
    await apiFetch(NOVEDADES)
    const ultima = fetchMock.mock.calls[fetchMock.mock.calls.length - 1]
    expect(autorizacionDe(ultima)).toBe('Bearer token-nuevo')
  })
})
