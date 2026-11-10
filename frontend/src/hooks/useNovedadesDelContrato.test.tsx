import { renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { setAuthToken } from '../services/api/client'
import { getNovedades } from '../services/novedadesService'
import { useNovedadesDelContrato } from './useNovedadesDelContrato'

vi.mock('../services/novedadesService', () => ({ getNovedades: vi.fn() }))

const pedirNovedades = vi.mocked(getNovedades)

/** Deja correr el reloj falso y vaciar las promesas que eso desate. */
const dejarPasar = (ms: number) => vi.advanceTimersByTimeAsync(ms)

function unaNovedad(id: number) {
  return {
    id,
    contratoId: 7,
    usuarioId: 3,
    usuarioNombre: 'Alex',
    accion: 'SUBETAPA_AVANZADA',
    descripcion: 'Subetapa 2.2 avanzó de PENDIENTE a COMPLETADA.',
    fecha: '2026-11-03T14:00:00Z',
    origen: 'USUARIO' as const,
  }
}

/**
 * El sondeo que hace que dos equipos sobre el mismo contrato se vean casi al
 * instante.
 *
 * <p>Lo que estas pruebas vigilan es lo que, si se rompe, no se nota mirando la
 * pantalla: que la primera respuesta no se confunda con una novedad (o el panel
 * se recargaría solo nada más abrirlo), que la marca avance (o avisaría una y
 * otra vez de lo mismo), y que una sesión sin token —la de ADR-015— no
 * pregunte nada.
 *
 * <p>No se usa `waitFor`: con el reloj falso, su propia espera también queda
 * congelada y la prueba se cuelga hasta agotar el tiempo. Aquí el reloj se
 * adelanta a mano, que además es lo que se quiere comprobar.
 */
describe('useNovedadesDelContrato', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    pedirNovedades.mockReset()
    setAuthToken('token-de-prueba')
  })

  afterEach(() => {
    vi.useRealTimers()
    setAuthToken(null)
  })

  it('la primera respuesta solo fija la marca y no avisa de nada', async () => {
    pedirNovedades.mockResolvedValue({ ultimoId: 12, novedades: [] })
    const avisar = vi.fn()

    renderHook(() => useNovedadesDelContrato(7, avisar))
    await dejarPasar(0)

    expect(pedirNovedades).toHaveBeenCalledWith(7, null)
    expect(avisar).not.toHaveBeenCalled()
  })

  it('avisa cuando el otro equipo movió algo, y pregunta desde la marca anterior', async () => {
    pedirNovedades
      .mockResolvedValueOnce({ ultimoId: 12, novedades: [] })
      .mockResolvedValueOnce({ ultimoId: 13, novedades: [unaNovedad(13)] })
    const avisar = vi.fn()

    renderHook(() => useNovedadesDelContrato(7, avisar))
    await dejarPasar(0)
    expect(pedirNovedades).toHaveBeenCalledTimes(1)

    await dejarPasar(5_000)

    expect(pedirNovedades).toHaveBeenNthCalledWith(2, 7, 12)
    expect(avisar).toHaveBeenCalledTimes(1)
  })

  it('no vuelve a avisar de lo mismo: la siguiente pregunta parte de la marca nueva', async () => {
    pedirNovedades
      .mockResolvedValueOnce({ ultimoId: 12, novedades: [] })
      .mockResolvedValueOnce({ ultimoId: 13, novedades: [unaNovedad(13)] })
      .mockResolvedValue({ ultimoId: 13, novedades: [] })
    const avisar = vi.fn()

    renderHook(() => useNovedadesDelContrato(7, avisar))
    await dejarPasar(0)
    await dejarPasar(5_000)
    expect(avisar).toHaveBeenCalledTimes(1)

    await dejarPasar(5_000)

    expect(pedirNovedades).toHaveBeenNthCalledWith(3, 7, 13)
    expect(avisar).toHaveBeenCalledTimes(1)
  })

  /**
   * La sesión abierta sin red (ADR-015, punto 4) no lleva token. Sondear ahí
   * sería preguntar por novedades sobre una copia guardada: la respuesta sería
   * siempre «no ha pasado nada», que es justo lo que no se sabe.
   */
  it('sin token de sesión no pregunta nada', async () => {
    setAuthToken(null)
    pedirNovedades.mockResolvedValue({ ultimoId: 1, novedades: [] })

    renderHook(() => useNovedadesDelContrato(7, vi.fn()))
    await dejarPasar(20_000)

    expect(pedirNovedades).not.toHaveBeenCalled()
  })

  it('sin contrato seleccionado no pregunta nada', async () => {
    pedirNovedades.mockResolvedValue({ ultimoId: 1, novedades: [] })

    renderHook(() => useNovedadesDelContrato(null, vi.fn()))
    await dejarPasar(20_000)

    expect(pedirNovedades).not.toHaveBeenCalled()
  })

  /**
   * Si el camino al servidor se cortó, insistir cada cinco segundos no lo
   * arregla: solo llena la consola de errores que no son de nadie. Se espacia
   * y se sigue preguntando.
   */
  it('tras un fallo espacia las preguntas en vez de insistir', async () => {
    pedirNovedades.mockRejectedValue(new TypeError('Failed to fetch'))

    renderHook(() => useNovedadesDelContrato(7, vi.fn()))
    await dejarPasar(0)
    expect(pedirNovedades).toHaveBeenCalledTimes(1)

    await dejarPasar(5_000)
    expect(pedirNovedades).toHaveBeenCalledTimes(1)

    await dejarPasar(25_000)
    expect(pedirNovedades).toHaveBeenCalledTimes(2)
  })

  /**
   * Visto con el servidor real el 3-11-2026: si el backend estaba reiniciando
   * cuando la pantalla cargó, las lecturas se sirvieron de la copia guardada y
   * la franja «lo que ve es la copia» se quedaba puesta aunque el servidor ya
   * hubiera vuelto. Nadie la relevaba: este sondeo no pasa por la copia, así
   * que su «no ha pasado nada» no cambia lo que se está viendo.
   */
  it('al volver el servidor avisa aunque no haya novedades, para relevar la copia local', async () => {
    pedirNovedades
      .mockResolvedValueOnce({ ultimoId: 12, novedades: [] })
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValue({ ultimoId: 12, novedades: [] })
    const avisar = vi.fn()

    renderHook(() => useNovedadesDelContrato(7, avisar))
    await dejarPasar(0)

    // La segunda falla: el camino al servidor se cortó.
    await dejarPasar(5_000)
    expect(avisar).not.toHaveBeenCalled()

    // La tercera vuelve a responder, sin nada nuevo.
    await dejarPasar(30_000)
    expect(avisar).toHaveBeenCalledTimes(1)

    // Y a partir de ahí se calla: ya no viene de un fallo.
    await dejarPasar(5_000)
    expect(avisar).toHaveBeenCalledTimes(1)
  })

  it('al cambiar de contrato vuelve a empezar sin marca', async () => {
    pedirNovedades.mockResolvedValue({ ultimoId: 12, novedades: [] })

    const { rerender } = renderHook(({ id }) => useNovedadesDelContrato(id, vi.fn()), {
      initialProps: { id: 7 as number },
    })
    await dejarPasar(0)
    expect(pedirNovedades).toHaveBeenCalledWith(7, null)

    rerender({ id: 9 })
    await dejarPasar(0)

    expect(pedirNovedades).toHaveBeenCalledWith(9, null)
  })

  /** Al volver a la ventana se pregunta en el acto: es cuando importa. */
  it('al volver la ventana al primer plano pregunta sin esperar al temporizador', async () => {
    pedirNovedades.mockResolvedValue({ ultimoId: 12, novedades: [] })

    renderHook(() => useNovedadesDelContrato(7, vi.fn()))
    await dejarPasar(0)
    expect(pedirNovedades).toHaveBeenCalledTimes(1)

    document.dispatchEvent(new Event('visibilitychange'))
    await dejarPasar(0)

    expect(pedirNovedades).toHaveBeenCalledTimes(2)
  })
})
