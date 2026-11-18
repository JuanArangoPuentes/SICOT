import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiFetch, onUnauthorized, renovandoElToken, setAuthToken } from './client'

/**
 * El cliente HTTP es el único punto por donde pasa todo lo que el frontend le
 * pide al backend, así que un error suyo no se nota en una pantalla: se nota en
 * todas a la vez.
 */
describe('apiFetch', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
    setAuthToken(null)
    onUnauthorized(null)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  function respuesta(status: number, cuerpo?: unknown, cabeceras: Record<string, string> = {}) {
    return new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), {
      status,
      headers: { 'Content-Type': 'application/json', ...cabeceras },
    })
  }

  it('adjunta el token Bearer cuando hay sesión', async () => {
    setAuthToken('token-de-prueba')
    fetchMock.mockResolvedValue(respuesta(200, { ok: true }))

    await apiFetch('/api/contratos')

    const headers = fetchMock.mock.calls[0][1].headers as Headers
    expect(headers.get('Authorization')).toBe('Bearer token-de-prueba')
  })

  it('no manda Content-Type propio cuando el cuerpo es FormData', async () => {
    // El navegador tiene que poner multipart/form-data con su boundary; fijarlo
    // a mano rompe la subida de archivos de una forma difícil de diagnosticar.
    fetchMock.mockResolvedValue(respuesta(200, {}))

    await apiFetch('/api/contratos/1/documentos', {
      method: 'POST',
      body: new FormData(),
    })

    const headers = fetchMock.mock.calls[0][1].headers as Headers
    expect(headers.get('Content-Type')).toBeNull()
  })

  it('convierte una respuesta de error en ApiError con el mensaje del backend', async () => {
    fetchMock.mockResolvedValue(respuesta(400, { status: 400, message: 'El objeto es obligatorio.' }))

    await expect(apiFetch('/api/contratos')).rejects.toMatchObject({
      status: 400,
      message: 'El objeto es obligatorio.',
    })
  })

  it('devuelve undefined sin intentar interpretar el cuerpo de un 204', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))

    await expect(apiFetch('/api/formatos/1')).resolves.toBeUndefined()
  })

  /**
   * El caso que motivó separar la sesión de las credenciales: desde que el
   * backend responde 401 —y no 400— a una contraseña equivocada, tratar todo
   * 401 como "sesión caducada" haría que escribir mal la clave cerrara la
   * sesión y reiniciara la pantalla de login, borrando lo escrito.
   */
  it('un 401 en el login NO dispara el cierre de sesión', async () => {
    const cerrarSesion = vi.fn()
    onUnauthorized(cerrarSesion)
    fetchMock.mockResolvedValue(respuesta(401, { status: 401, message: 'Credenciales inválidas.' }))

    await expect(apiFetch('/api/auth/login', { method: 'POST' })).rejects.toBeInstanceOf(ApiError)

    expect(cerrarSesion).not.toHaveBeenCalled()
  })

  it('un 401 en cualquier otra ruta sí cierra la sesión', async () => {
    const cerrarSesion = vi.fn()
    onUnauthorized(cerrarSesion)
    fetchMock.mockResolvedValue(respuesta(401, { status: 401, message: 'No autenticado' }))

    await expect(apiFetch('/api/contratos')).rejects.toBeInstanceOf(ApiError)

    expect(cerrarSesion).toHaveBeenCalledOnce()
  })

  /**
   * Cambiar la contraseña propia revoca los tokens anteriores de la cuenta: una
   * petición de fondo que salió un instante antes vuelve con 401 y no es que la
   * sesión se haya terminado, es que su token quedó atrás. Tratarlo como lo
   * segundo devolvía al login después de cambiar la contraseña con éxito.
   */
  it('repite con el token vigente —y no cierra la sesión— el 401 de una petición cuyo token quedó atrás', async () => {
    const cerrarSesion = vi.fn()
    onUnauthorized(cerrarSesion)
    setAuthToken('token-revocado')
    fetchMock
      // Mientras el servidor contestaba a esta, la respuesta del cambio de
      // contraseña llegó y puso el token nuevo.
      .mockImplementationOnce(() => {
        setAuthToken('token-nuevo')
        return Promise.resolve(respuesta(401, { status: 401, message: 'No autenticado' }))
      })
      .mockResolvedValue(respuesta(200, { hayNovedades: false }))

    await expect(apiFetch('/api/contratos/7/novedades')).resolves.toEqual({ hayNovedades: false })

    expect(cerrarSesion).not.toHaveBeenCalled()
    expect(fetchMock).toHaveBeenCalledTimes(2)
    const reintento = fetchMock.mock.calls[1][1].headers as Headers
    expect(reintento.get('Authorization')).toBe('Bearer token-nuevo')
  })

  it('si el token vigente tampoco vale, la sesión sí se terminó y se cierra', async () => {
    const cerrarSesion = vi.fn()
    onUnauthorized(cerrarSesion)
    setAuthToken('token-revocado')
    fetchMock
      .mockImplementationOnce(() => {
        setAuthToken('token-nuevo')
        return Promise.resolve(respuesta(401, { status: 401, message: 'No autenticado' }))
      })
      .mockResolvedValue(respuesta(401, { status: 401, message: 'No autenticado' }))

    await expect(apiFetch('/api/contratos/7/novedades')).rejects.toBeInstanceOf(ApiError)

    expect(cerrarSesion).toHaveBeenCalledOnce()
  })

  /**
   * El tramo más estrecho del hueco: el 401 de la petición de fondo llega
   * ANTES que la respuesta con el token nuevo, así que no hay con qué repetirla.
   * Lo que no se puede hacer es cerrar la sesión: el token nuevo viene en camino.
   */
  it('no cierra la sesión por un 401 que llega mientras el token se está renovando', async () => {
    const cerrarSesion = vi.fn()
    onUnauthorized(cerrarSesion)
    setAuthToken('token-revocado')
    fetchMock.mockResolvedValue(respuesta(401, { status: 401, message: 'No autenticado' }))

    await renovandoElToken(async () => {
      await expect(apiFetch('/api/contratos/7/novedades')).rejects.toBeInstanceOf(ApiError)
    })

    expect(cerrarSesion).not.toHaveBeenCalled()
    // Sin intentar repetirla: el token nuevo todavía no había llegado.
    expect(fetchMock).toHaveBeenCalledOnce()
  })

  it('vuelve a cerrar la sesión por un 401 en cuanto la renovación termina', async () => {
    const cerrarSesion = vi.fn()
    onUnauthorized(cerrarSesion)
    setAuthToken('token-revocado')
    fetchMock.mockResolvedValue(respuesta(401, { status: 401, message: 'No autenticado' }))

    await renovandoElToken(async () => undefined)
    await expect(apiFetch('/api/contratos/7/novedades')).rejects.toBeInstanceOf(ApiError)

    expect(cerrarSesion).toHaveBeenCalledOnce()
  })

  it('lee Retry-After en un 429 para poder decir cuánto esperar', async () => {
    fetchMock.mockResolvedValue(
      respuesta(429, { status: 429, message: 'Demasiados intentos fallidos.' }, { 'Retry-After': '900' }),
    )

    try {
      await apiFetch('/api/auth/login', { method: 'POST' })
      expect.unreachable('debía lanzar')
    } catch (e) {
      const error = e as ApiError
      expect(error.esDemasiadasSolicitudes).toBe(true)
      expect(error.reintentarEnSegundos).toBe(900)
    }
  })

  it('ignora un Retry-After que no sea un número de segundos', async () => {
    fetchMock.mockResolvedValue(
      respuesta(
        429,
        { status: 429, message: 'Espere.' },
        {
          'Retry-After': 'Wed, 21 Oct 2026 07:28:00 GMT',
        },
      ),
    )

    try {
      await apiFetch('/api/auth/login', { method: 'POST' })
      expect.unreachable('debía lanzar')
    } catch (e) {
      // Mejor no decir nada que decir "reintente en NaN minutos".
      expect((e as ApiError).reintentarEnSegundos).toBeUndefined()
    }
  })

  it('sigue fallando de forma legible si el cuerpo del error no es JSON', async () => {
    fetchMock.mockResolvedValue(new Response('<html>502 Bad Gateway</html>', { status: 502 }))

    await expect(apiFetch('/api/contratos')).rejects.toMatchObject({
      status: 502,
      message: 'El servidor de SICOT no respondió a tiempo (error 502 del intermediario).',
    })
  })
})
