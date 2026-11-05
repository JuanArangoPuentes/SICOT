import { renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { setAuthToken } from '../services/api/client'
import { useAlVolverALaVentana } from './useAlVolverALaVentana'

/**
 * Lo que pone al día las LISTAS cuando el otro equipo crea o reasigna algo.
 *
 * <p>El sondeo de novedades no puede cubrir esto: pregunta por un contrato, y
 * lo que falta es un contrato que todavía no está en la lista. Volver a la
 * ventana es el momento en que a alguien le importa.
 */
describe('useAlVolverALaVentana', () => {
  beforeEach(() => {
    setAuthToken('token-de-prueba')
  })

  afterEach(() => {
    setAuthToken(null)
  })

  it('no hace nada al montar: espera a que la ventana vuelva', () => {
    const hacer = vi.fn()

    renderHook(() => useAlVolverALaVentana(hacer))

    expect(hacer).not.toHaveBeenCalled()
  })

  it('actúa al volver la pestaña al primer plano', () => {
    const hacer = vi.fn()
    renderHook(() => useAlVolverALaVentana(hacer))

    document.dispatchEvent(new Event('visibilitychange'))

    expect(hacer).toHaveBeenCalledTimes(1)
  })

  it('actúa al volver el foco a la ventana del escritorio', () => {
    const hacer = vi.fn()
    renderHook(() => useAlVolverALaVentana(hacer))

    window.dispatchEvent(new Event('focus'))

    expect(hacer).toHaveBeenCalledTimes(1)
  })

  /**
   * La sesión abierta sin red (ADR-015, punto 4) no lleva token: lo que se ve
   * es la copia local, y pedir la lista solo daría un error que no es de nadie.
   */
  it('sin token de sesión no hace nada', () => {
    setAuthToken(null)
    const hacer = vi.fn()
    renderHook(() => useAlVolverALaVentana(hacer))

    document.dispatchEvent(new Event('visibilitychange'))
    window.dispatchEvent(new Event('focus'))

    expect(hacer).not.toHaveBeenCalled()
  })

  it('deja de escuchar al desmontarse', () => {
    const hacer = vi.fn()
    const { unmount } = renderHook(() => useAlVolverALaVentana(hacer))

    unmount()
    document.dispatchEvent(new Event('visibilitychange'))

    expect(hacer).not.toHaveBeenCalled()
  })

  /**
   * Quien lo usa define la acción en línea, así que cambia de identidad en
   * cada render. Si el hook la capturara al montar, seguiría llamando a la
   * primera versión con el estado de entonces.
   */
  it('usa siempre la última acción, no la del primer render', () => {
    const primera = vi.fn()
    const segunda = vi.fn()
    const { rerender } = renderHook(({ accion }) => useAlVolverALaVentana(accion), {
      initialProps: { accion: primera },
    })

    rerender({ accion: segunda })
    window.dispatchEvent(new Event('focus'))

    expect(primera).not.toHaveBeenCalled()
    expect(segunda).toHaveBeenCalledTimes(1)
  })

  /**
   * Volver a la ventana dispara `visibilitychange` y `focus` casi a la vez.
   * Antes la lista se pedía dos veces en cada vuelta; una sola vuelta es un
   * solo aviso, y la vuelta siguiente vuelve a avisar.
   */
  it('una vuelta con dos eventos seguidos avisa una sola vez', () => {
    const reloj = vi.spyOn(Date, 'now').mockReturnValue(10_000)
    try {
      const hacer = vi.fn()
      renderHook(() => useAlVolverALaVentana(hacer))

      document.dispatchEvent(new Event('visibilitychange'))
      window.dispatchEvent(new Event('focus'))
      expect(hacer).toHaveBeenCalledTimes(1)

      reloj.mockReturnValue(12_000)
      window.dispatchEvent(new Event('focus'))
      expect(hacer).toHaveBeenCalledTimes(2)
    } finally {
      reloj.mockRestore()
    }
  })
})
