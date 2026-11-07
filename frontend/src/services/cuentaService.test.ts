import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { apiFetch } from './api/client'
import { olvidarAccesoSinRed, recordarAcceso } from './accesoSinRed'
import { cambiarMiContrasena } from './cuentaService'
import { alRenovarSesion, leerSesionGuardada } from './session'

vi.mock('./api/client', async (original) => ({
  ...(await original<typeof import('./api/client')>()),
  apiFetch: vi.fn(),
}))
vi.mock('./accesoSinRed', async (original) => ({
  ...(await original<typeof import('./accesoSinRed')>()),
  olvidarAccesoSinRed: vi.fn(),
  recordarAcceso: vi.fn().mockResolvedValue(undefined),
}))

const pedir = vi.mocked(apiFetch)
const olvidar = vi.mocked(olvidarAccesoSinRed)
const recordar = vi.mocked(recordarAcceso)

const NUEVA_SESION = {
  token: 'token-nuevo',
  usuarioId: 3,
  nombre: 'Sup',
  email: 's@sicot.test',
  rol: 'SUPERVISOR' as const,
}

/**
 * Lo que pasa en el equipo después de que el servidor acepta la contraseña
 * nueva. Si algo de esto falla no se ve en pantalla: la sesión se cerraría sola
 * con el token revocado, o el portátil seguiría entrando sin red con la
 * contraseña anterior.
 */
describe('cambiarMiContrasena', () => {
  beforeEach(() => {
    localStorage.clear()
    pedir.mockReset().mockResolvedValue(NUEVA_SESION)
    olvidar.mockClear()
    recordar.mockClear()
  })

  afterEach(() => localStorage.clear())

  it('envía la actual y la nueva al endpoint de la cuenta', async () => {
    await cambiarMiContrasena('s@sicot.test', 'Super2026', 'OtraClave456*')

    expect(pedir).toHaveBeenCalledWith(
      '/api/cuenta/contrasena',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({ contrasenaActual: 'Super2026', contrasenaNueva: 'OtraClave456*' }),
      }),
    )
  })

  it('se queda con la sesión nueva y se lo dice a quien escucha', async () => {
    const oyente = vi.fn()
    const dejar = alRenovarSesion(oyente)

    await cambiarMiContrasena('s@sicot.test', 'Super2026', 'OtraClave456*')

    expect(leerSesionGuardada()?.token).toBe('token-nuevo')
    expect(oyente).toHaveBeenCalledWith(NUEVA_SESION)
    dejar()
  })

  it('borra la huella sin red anterior antes de guardar la de la contraseña nueva', async () => {
    await cambiarMiContrasena('s@sicot.test', 'Super2026', 'OtraClave456*')

    expect(olvidar).toHaveBeenCalled()
    expect(recordar).toHaveBeenCalledWith('s@sicot.test', 'OtraClave456*', NUEVA_SESION)
    expect(olvidar.mock.invocationCallOrder[0]).toBeLessThan(recordar.mock.invocationCallOrder[0])
  })

  it('si el servidor rechaza, no toca la sesión ni la huella', async () => {
    pedir.mockRejectedValue(new Error('La contraseña actual no es correcta.'))

    await expect(cambiarMiContrasena('s@sicot.test', 'mal', 'OtraClave456*')).rejects.toThrow()

    expect(leerSesionGuardada()).toBeNull()
    expect(olvidar).not.toHaveBeenCalled()
    expect(recordar).not.toHaveBeenCalled()
  })
})
