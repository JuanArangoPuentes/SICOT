import { renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { useRecursoDelContrato } from './useRecursoDelContrato'

/**
 * El hook que sacó la carga de datos de `SupervisorPanel.tsx`.
 *
 * Estas pruebas existen porque el patrón que reemplaza estaba copiado seis
 * veces dentro de un componente de 948 líneas, y era imposible de probar sin
 * montar el panel entero. Las dos primeras cubren los fallos que ese patrón
 * copiado producía de verdad.
 */
describe('useRecursoDelContrato', () => {
  it('carga el recurso del contrato indicado', async () => {
    const cargar = vi.fn().mockResolvedValue(['alerta'])

    const { result } = renderHook(() => useRecursoDelContrato(7, cargar, []))

    await waitFor(() => expect(result.current.cargando).toBe(false))
    expect(result.current.datos).toEqual(['alerta'])
    expect(result.current.error).toBe(false)
    expect(cargar).toHaveBeenCalledWith(7)
  })

  it('no pide nada cuando no hay contrato seleccionado', () => {
    const cargar = vi.fn()

    const { result } = renderHook(() => useRecursoDelContrato(null, cargar, []))

    expect(cargar).not.toHaveBeenCalled()
    expect(result.current.datos).toEqual([])
  })

  /**
   * El fallo que este hook existe para cerrar: pintar "no hay alertas" cuando
   * en realidad no se pudo consultar el servicio le asegura al supervisor que
   * todo está en orden justo cuando el sistema no lo sabe.
   */
  it('distingue "falló" de "no hay datos"', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    const cargar = vi.fn().mockRejectedValue(new Error('500'))

    const { result } = renderHook(() => useRecursoDelContrato(7, cargar, []))

    await waitFor(() => expect(result.current.error).toBe(true))
    expect(result.current.datos).toEqual([])
    expect(result.current.cargando).toBe(false)
  })

  /**
   * La condición de carrera al cambiar de contrato: sin cancelación, la
   * respuesta lenta del contrato anterior pisa a la del actual y el panel
   * muestra datos del contrato equivocado.
   */
  it('descarta la respuesta del contrato anterior si llega tarde', async () => {
    let resolverLenta: (v: string[]) => void = () => {}
    const cargar = vi.fn()
      .mockImplementationOnce(() => new Promise<string[]>(res => { resolverLenta = res }))
      .mockResolvedValueOnce(['del contrato 8'])

    const { result, rerender } = renderHook(
      ({ id }: { id: number }) => useRecursoDelContrato(id, cargar, [] as string[]),
      { initialProps: { id: 7 } },
    )

    rerender({ id: 8 })
    await waitFor(() => expect(result.current.datos).toEqual(['del contrato 8']))

    // La petición del contrato 7 termina AHORA, después de haber cambiado.
    resolverLenta(['del contrato 7'])

    await waitFor(() => expect(result.current.datos).toEqual(['del contrato 8']))
  })

  it('vuelve a pedir cuando se le indica recargar', async () => {
    const cargar = vi.fn()
      .mockResolvedValueOnce(['primera'])
      .mockResolvedValueOnce(['segunda'])

    const { result } = renderHook(() => useRecursoDelContrato(7, cargar, [] as string[]))

    await waitFor(() => expect(result.current.datos).toEqual(['primera']))
    result.current.recargar()
    await waitFor(() => expect(result.current.datos).toEqual(['segunda']))
  })
})
