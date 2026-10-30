import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  accesoSinRedPosible,
  anotarContactoConElServidor,
  entrarSinRed,
  recordarAcceso,
  sesionSinRedVigente,
  venceElAccesoSinRed,
  VENTANA_SIN_RED_MS,
} from './accesoSinRed'
import type { AuthResponse } from './api/types'

/**
 * Entrar sin red es el punto donde ADR-015 reconoce que la seguridad empeora.
 * Estas pruebas miran que empeore solo lo que se aceptó: una ventana acotada,
 * para una sola cuenta de supervisor, sin la contraseña guardada en claro.
 */

const SUPERVISOR: AuthResponse = {
  token: 'jwt',
  usuarioId: 42,
  nombre: 'Supervisora',
  email: 'supervisor@soy.sena.edu.co',
  rol: 'SUPERVISOR',
}
const AHORA = new Date('2026-10-30T09:00:00Z').getTime()
const CLAVE = 'Supervisor123*'

describe('accesoSinRed', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('este entorno de pruebas tiene cifrado, como la app instalada', () => {
    expect(accesoSinRedPosible()).toBe(true)
  })

  it('tras entrar con red, la misma cuenta entra sin red con su contraseña', async () => {
    await recordarAcceso('Supervisor@Soy.Sena.edu.co ', CLAVE, SUPERVISOR, AHORA)

    const r = await entrarSinRed('supervisor@soy.sena.edu.co', CLAVE, AHORA + 60_000)

    expect(r.ok).toBe(true)
    if (r.ok) {
      expect(r.auth).toMatchObject({ usuarioId: 42, rol: 'SUPERVISOR', token: '' })
    }
  })

  it('no guarda la contraseña: solo sal y huella', async () => {
    await recordarAcceso(SUPERVISOR.email, CLAVE, SUPERVISOR, AHORA)

    const guardado = localStorage.getItem('sicot.acceso-sin-red') ?? ''
    expect(guardado).not.toContain(CLAVE)
    expect(JSON.parse(guardado)).toMatchObject({ iteraciones: 600_000 })
  })

  it('con otra contraseña no entra', async () => {
    await recordarAcceso(SUPERVISOR.email, CLAVE, SUPERVISOR, AHORA)

    const r = await entrarSinRed(SUPERVISOR.email, 'otra-clave', AHORA)

    expect(r).toEqual({ ok: false, motivo: 'Credenciales inválidas.' })
  })

  it('con otra cuenta no entra, aunque la contraseña sea la misma', async () => {
    await recordarAcceso(SUPERVISOR.email, CLAVE, SUPERVISOR, AHORA)

    const r = await entrarSinRed('otro@soy.sena.edu.co', CLAVE, AHORA)

    expect(r.ok).toBe(false)
  })

  it('pasada la ventana sin hablar con el servidor, exige conectarse', async () => {
    await recordarAcceso(SUPERVISOR.email, CLAVE, SUPERVISOR, AHORA)

    const r = await entrarSinRed(SUPERVISOR.email, CLAVE, AHORA + VENTANA_SIN_RED_MS)

    expect(r.ok).toBe(false)
    if (!r.ok) expect(r.motivo).toMatch(/72 horas/)
    expect(sesionSinRedVigente(42, AHORA + VENTANA_SIN_RED_MS)).toBe(false)
  })

  it('la ventana se cuenta desde el último contacto con el servidor, no desde el inicio de sesión', async () => {
    await recordarAcceso(SUPERVISOR.email, CLAVE, SUPERVISOR, AHORA)
    const doceHoras = 12 * 60 * 60 * 1000

    anotarContactoConElServidor(42, AHORA + doceHoras)

    expect(venceElAccesoSinRed(42)).toBe(AHORA + doceHoras + VENTANA_SIN_RED_MS)
    // El contacto de otra cuenta no corre la ventana de esta.
    anotarContactoConElServidor(7, AHORA + 2 * doceHoras)
    expect(venceElAccesoSinRed(42)).toBe(AHORA + doceHoras + VENTANA_SIN_RED_MS)
  })

  it('una cuenta de Gestión no se recuerda, y borra la del supervisor anterior', async () => {
    await recordarAcceso(SUPERVISOR.email, CLAVE, SUPERVISOR, AHORA)

    await recordarAcceso(
      'gestion@soy.sena.edu.co',
      'Gestion123*',
      { ...SUPERVISOR, usuarioId: 2, rol: 'GESTION' },
      AHORA,
    )

    expect(localStorage.getItem('sicot.acceso-sin-red')).toBeNull()
    expect((await entrarSinRed('gestion@soy.sena.edu.co', 'Gestion123*', AHORA)).ok).toBe(false)
  })

  it('sin cifrado disponible (una dirección http:// de la red) no se ofrece, y se dice por qué', async () => {
    vi.stubGlobal('crypto', { getRandomValues: globalThis.crypto.getRandomValues.bind(globalThis.crypto) })
    try {
      expect(accesoSinRedPosible()).toBe(false)
      const r = await entrarSinRed(SUPERVISOR.email, CLAVE, AHORA)
      expect(r.ok).toBe(false)
      if (!r.ok) expect(r.motivo).toMatch(/http:\/\//)
    } finally {
      vi.unstubAllGlobals()
    }
  })
})
