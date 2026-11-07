import { renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { setAuthToken } from '../services/api/client'
import { useRefrescoEnVivo } from './useRefrescoEnVivo'

/** Deja correr el reloj falso y vaciar las promesas que eso desate. */
const dejarPasar = (ms: number) => vi.advanceTimersByTimeAsync(ms)

/**
 * El refresco que hace que el administrador vea en «Seguimiento de
 * supervisores» lo que el supervisor acaba de hacer en otro equipo.
 *
 * <p>Lo que se vigila es lo que no se nota mirando la pantalla: que pregunte al
 * ritmo del supervisor, que no apile peticiones si el servidor tarda, que se
 * calle sin token o con la vista cerrada, y que un fallo no lo apague.
 */
describe('useRefrescoEnVivo', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    setAuthToken('token-de-prueba')
  })

  afterEach(() => {
    vi.useRealTimers()
    setAuthToken(null)
  })

  it('no pide en el acto (la vista acaba de cargar) y luego pide cada 5 s', async () => {
    const refrescar = vi.fn().mockResolvedValue(undefined)
    renderHook(() => useRefrescoEnVivo(true, refrescar))

    await dejarPasar(0)
    expect(refrescar).not.toHaveBeenCalled()

    await dejarPasar(5_000)
    expect(refrescar).toHaveBeenCalledTimes(1)
    await dejarPasar(5_000)
    expect(refrescar).toHaveBeenCalledTimes(2)
  })

  it('con la vista cerrada no pide nada', async () => {
    const refrescar = vi.fn().mockResolvedValue(undefined)
    renderHook(() => useRefrescoEnVivo(false, refrescar))

    await dejarPasar(30_000)
    expect(refrescar).not.toHaveBeenCalled()
  })

  it('al cerrar la vista deja de pedir', async () => {
    const refrescar = vi.fn().mockResolvedValue(undefined)
    const { rerender } = renderHook(({ activo }) => useRefrescoEnVivo(activo, refrescar), {
      initialProps: { activo: true },
    })
    await dejarPasar(5_000)
    expect(refrescar).toHaveBeenCalledTimes(1)

    rerender({ activo: false })
    await dejarPasar(30_000)
    expect(refrescar).toHaveBeenCalledTimes(1)
  })

  it('no apila peticiones si el servidor tarda más que el intervalo', async () => {
    let terminar: () => void = () => {}
    const refrescar = vi.fn(() => new Promise<void>((resolve) => (terminar = resolve)))
    renderHook(() => useRefrescoEnVivo(true, refrescar))

    await dejarPasar(5_000)
    expect(refrescar).toHaveBeenCalledTimes(1)
    // Sigue sin responder: ni el reloj ni volver a la ventana lanzan otra.
    window.dispatchEvent(new Event('focus'))
    await dejarPasar(20_000)
    expect(refrescar).toHaveBeenCalledTimes(1)

    terminar()
    await dejarPasar(5_000)
    expect(refrescar).toHaveBeenCalledTimes(2)
  })

  it('sin token (sesión sin red) no pregunta, y vuelve a hacerlo cuando lo hay', async () => {
    setAuthToken(null)
    const refrescar = vi.fn().mockResolvedValue(undefined)
    renderHook(() => useRefrescoEnVivo(true, refrescar))

    await dejarPasar(10_000)
    expect(refrescar).not.toHaveBeenCalled()

    setAuthToken('token-de-prueba')
    await dejarPasar(5_000)
    expect(refrescar).toHaveBeenCalledTimes(1)
  })

  it('tras un fallo espera 30 s y sigue intentando', async () => {
    const refrescar = vi.fn().mockRejectedValueOnce(new Error('sin red')).mockResolvedValue(undefined)
    renderHook(() => useRefrescoEnVivo(true, refrescar))

    await dejarPasar(5_000)
    expect(refrescar).toHaveBeenCalledTimes(1)
    await dejarPasar(5_000)
    expect(refrescar).toHaveBeenCalledTimes(1)
    await dejarPasar(25_000)
    expect(refrescar).toHaveBeenCalledTimes(2)
  })

  it('al volver a la ventana pide en el acto', async () => {
    const refrescar = vi.fn().mockResolvedValue(undefined)
    renderHook(() => useRefrescoEnVivo(true, refrescar))
    await dejarPasar(1_000)

    window.dispatchEvent(new Event('focus'))
    await dejarPasar(0)
    expect(refrescar).toHaveBeenCalledTimes(1)
  })
})
