import { act, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AvisoDeServicioCaido } from './AvisoDeServicioCaido'
import { getServiciosCaidos } from '@/services/saludService'

vi.mock('@/services/saludService', () => ({
  getServiciosCaidos: vi.fn(),
}))

const consultar = vi.mocked(getServiciosCaidos)

const IA_CAIDA = {
  servicio: 'ia',
  mensaje: 'El asistente de IA no está disponible. Puede seguir trabajando con normalidad.',
  minutos: 7,
}

afterEach(() => {
  vi.useRealTimers()
  consultar.mockReset()
})

describe('AvisoDeServicioCaido', () => {
  it('no se ve nada cuando todo funciona', async () => {
    consultar.mockResolvedValue([])

    render(<AvisoDeServicioCaido />)

    await waitFor(() => expect(consultar).toHaveBeenCalled())
    expect(screen.queryByRole('status')).toBeNull()
  })

  it('enseña el mensaje tal cual lo manda el servidor', async () => {
    // El texto se redacta en el backend, al lado de lo que falla. Si esta
    // prueba tuviera que escribir el mensaje esperado palabra por palabra,
    // estaría fijando aquí una redacción que no vive aquí.
    consultar.mockResolvedValue([IA_CAIDA])

    render(<AvisoDeServicioCaido />)

    expect(await screen.findByText(IA_CAIDA.mensaje)).toBeTruthy()
    expect(screen.getByText(/Lleva así 7 minutos/)).toBeTruthy()
    expect(screen.getByText(/No hace falta que avise/)).toBeTruthy()
  })

  it('no enseña nada si la consulta falla: de eso ya avisa la franja de la copia local', async () => {
    consultar.mockRejectedValue(new Error('sin red'))

    render(<AvisoDeServicioCaido />)

    await waitFor(() => expect(consultar).toHaveBeenCalled())
    expect(screen.queryByRole('status')).toBeNull()
  })

  it('la franja desaparece sola cuando el servicio vuelve', async () => {
    // Lo que de verdad importa de este componente. Una franja que se queda
    // puesta después de que el problema se arregló enseña a ignorar las
    // franjas, y el reparador del backend levanta la IA cada dos minutos sin
    // que nadie toque nada.
    vi.useFakeTimers()
    consultar.mockResolvedValueOnce([IA_CAIDA]).mockResolvedValue([])

    render(<AvisoDeServicioCaido />)
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })
    expect(screen.queryByRole('status')).not.toBeNull()

    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000)
    })

    expect(screen.queryByRole('status')).toBeNull()
  })

  it('deja de preguntar cuando el componente se va', async () => {
    // Sin esto, cada pantalla que montara y desmontara la franja dejaría un
    // temporizador más preguntando para siempre.
    vi.useFakeTimers()
    consultar.mockResolvedValue([])

    const { unmount } = render(<AvisoDeServicioCaido />)
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })
    const llamadasAlMontar = consultar.mock.calls.length

    unmount()
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5 * 60_000)
    })

    expect(consultar.mock.calls.length).toBe(llamadasAlMontar)
  })

  it('enseña una franja por cada servicio caído', async () => {
    consultar.mockResolvedValue([
      IA_CAIDA,
      { servicio: 'correo', mensaje: 'No se pudieron enviar correos.', minutos: 0 },
    ])

    render(<AvisoDeServicioCaido />)

    await waitFor(() => expect(screen.queryAllByRole('status').length).toBe(2))
    // Con 0 minutos no se dice cuánto lleva: «lleva así 0 minutos» no informa.
    expect(screen.queryByText(/Lleva así 0/)).toBeNull()
  })
})
