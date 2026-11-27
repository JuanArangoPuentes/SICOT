import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CambiosSinEnviar } from './CambiosSinEnviar'
import type { Cambio } from '@/services/colaDeCambios'

/**
 * La franja de los cambios sin enviar es donde el supervisor **decide un
 * conflicto**: si envía su versión de una lista o se queda con la que otro
 * guardó en el servidor mientras él estaba sin red. Estaba al 27 % de línea y
 * al **11 % de rama**, es decir, de cada once caminos de esa decisión se
 * ejecutaba uno.
 *
 * <h2>Lo que se fija aquí</h2>
 * ADR-015 dice que la parte que «todo el mundo subestima» es justo esta: el
 * trabajo sigue en el equipo hasta que el supervisor decide, y para decidir
 * tiene que VER las dos versiones y qué hace cada botón. Si esta pantalla
 * miente —si no se ve el rechazo, si el botón de reintentar borra, si con la
 * cola vacía se queda una franja de alarma— el resultado no es una molestia:
 * es trabajo de campo perdido, o la lista de otra persona pisada sin que nadie
 * lo viera.
 */

const reenviarLaCola = vi.fn()
const reintentarCambio = vi.fn()
const descartarCambio = vi.fn()
const hayTokenDeSesion = vi.fn()
let cambios: Cambio[] = []

vi.mock('@/services/colaDeCambios', async () => {
  const real = await vi.importActual<typeof import('@/services/colaDeCambios')>('@/services/colaDeCambios')
  return {
    ...real,
    cambiosDeLaCola: () => cambios,
    alCambiarLaCola: () => () => {},
    reenviarLaCola: (...a: unknown[]) => reenviarLaCola(...a),
    reintentarCambio: (...a: unknown[]) => reintentarCambio(...a),
    descartarCambio: (...a: unknown[]) => descartarCambio(...a),
  }
})

vi.mock('@/services/api/client', async () => {
  const real = await vi.importActual<typeof import('@/services/api/client')>('@/services/api/client')
  return { ...real, hayTokenDeSesion: () => hayTokenDeSesion() }
})

const BASE = {
  usuario: '9',
  hechoEn: '2026-11-20T09:00:00.000Z',
  intentosFallidos: 0,
} as const

const pendiente = (id: number): Cambio => ({
  ...BASE,
  id,
  estado: 'pendiente',
  intencion: { tipo: 'estado-subetapa', contratoId: 1, subetapaId: 21, codigo: '2.1', estado: 'COMPLETADA' },
})

const conflictoDeEntregas = (id: number): Cambio => ({
  ...BASE,
  id,
  estado: 'rechazado',
  motivo: 'Mientras usted estaba sin conexión, alguien cambió esta lista en el servidor.',
  intencion: {
    tipo: 'entregas-acordadas',
    contratoId: 1,
    entregas: [{ fecha: '2026-12-01', descripcion: 'Lote 4 — sillas' }],
  },
  enElServidor: { contratoId: 1, entregas: [{ fecha: '2026-12-15', descripcion: 'Lote 4 — mesas' }] },
})

const rechazoDelServidor = (id: number): Cambio => ({
  ...BASE,
  id,
  estado: 'rechazado',
  motivo: 'La subetapa 2.1 ya está completada.',
  intencion: { tipo: 'estado-subetapa', contratoId: 1, subetapaId: 21, codigo: '2.1', estado: 'COMPLETADA' },
})

describe('CambiosSinEnviar', () => {
  beforeEach(() => {
    cambios = []
    reenviarLaCola.mockReset()
    reintentarCambio.mockReset()
    descartarCambio.mockReset()
    hayTokenDeSesion.mockReset()
    reenviarLaCola.mockResolvedValue({ enviados: 1, rechazados: 0, detenido: null })
    hayTokenDeSesion.mockReturnValue(true)
  })

  it('con la cola vacía no pinta nada: una franja permanente se aprende a ignorar', () => {
    const { container } = render(<CambiosSinEnviar />)
    expect(container).toBeEmptyDOMElement()
  })

  it('dice cuántos cambios esperan, en singular y en plural', () => {
    cambios = [pendiente(1)]
    const uno = render(<CambiosSinEnviar />)
    expect(screen.getByText(/1 cambio guardado en este equipo, pendiente de enviar/)).toBeInTheDocument()
    uno.unmount()

    cambios = [pendiente(1), pendiente(2)]
    render(<CambiosSinEnviar />)
    expect(screen.getByText(/2 cambios guardados en este equipo, pendientes de enviar/)).toBeInTheDocument()
  })

  it('sin sesión con el servidor no ofrece «Enviar ahora»: explica qué falta', () => {
    // Una sesión abierta sin red no lleva token (ADR-015, punto 4). Ofrecer el
    // botón ahí sería prometer algo que no puede ocurrir.
    hayTokenDeSesion.mockReturnValue(false)
    cambios = [pendiente(1)]
    render(<CambiosSinEnviar />)

    expect(screen.queryByRole('button', { name: 'Enviar ahora' })).not.toBeInTheDocument()
    expect(screen.getByText(/cuando vuelva a entrar con conexión/)).toBeInTheDocument()
  })

  it('un rechazo abre la lista solo: cerrada, la decisión no se ve', () => {
    cambios = [conflictoDeEntregas(1)]
    render(<CambiosSinEnviar />)

    expect(screen.getByText(/1 cambio que el servidor no aceptó/)).toBeInTheDocument()
    // Sin pulsar nada ya se ve el motivo y las dos versiones.
    expect(screen.getByText(/No entró:/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ocultar' })).toBeInTheDocument()
  })

  it('en un conflicto enseña LAS DOS versiones, para que se pueda decidir', () => {
    cambios = [conflictoDeEntregas(1)]
    render(<CambiosSinEnviar />)

    expect(screen.getByText(/Lo suyo: 2026-12-01 \(Lote 4 — sillas\)/)).toBeInTheDocument()
    expect(screen.getByText(/En el servidor ahora: 2026-12-15 \(Lote 4 — mesas\)/)).toBeInTheDocument()
    // Y el botón dice exactamente qué va a pasar: reemplazar lo del otro.
    expect(screen.getByRole('button', { name: 'Enviar la mía de todos modos' })).toBeInTheDocument()
  })

  it('un rechazo que NO es conflicto ofrece reintentar, no «enviar la mía»', () => {
    // Un 400 del servidor no tiene dos versiones que comparar: ofrecer
    // «enviar la mía de todos modos» insinuaría que se puede forzar algo que
    // el servidor ya dijo que no.
    cambios = [rechazoDelServidor(1)]
    render(<CambiosSinEnviar />)

    expect(screen.getByText('No entró: La subetapa 2.1 ya está completada.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Volver a intentar' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Enviar la mía de todos modos' })).not.toBeInTheDocument()
  })

  it('los dos botones de un rechazo hacen cosas DISTINTAS y sobre ese cambio', () => {
    cambios = [conflictoDeEntregas(42)]
    render(<CambiosSinEnviar />)

    fireEvent.click(screen.getByRole('button', { name: 'Enviar la mía de todos modos' }))
    expect(reintentarCambio).toHaveBeenCalledWith(42)
    expect(descartarCambio).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: 'Descartar el mío' }))
    expect(descartarCambio).toHaveBeenCalledWith(42)
    expect(reintentarCambio).toHaveBeenCalledTimes(1)
  })

  it('«Enviar ahora» reenvía la cola', async () => {
    cambios = [pendiente(1)]
    render(<CambiosSinEnviar />)

    fireEvent.click(screen.getByRole('button', { name: 'Enviar ahora' }))
    await waitFor(() => expect(reenviarLaCola).toHaveBeenCalledTimes(1))
  })

  it.each([
    ['sin-red', /Sigue sin haber conexión/],
    ['sin-sesion', /hay que entrar de nuevo con conexión/],
    ['servidor', /El servidor no respondió bien/],
  ])('si el reenvío se detiene por %s, lo dice con lo que hay que hacer', async (motivo, texto) => {
    reenviarLaCola.mockResolvedValue({ enviados: 0, rechazados: 0, detenido: motivo })
    cambios = [pendiente(1)]
    render(<CambiosSinEnviar />)

    fireEvent.click(screen.getByRole('button', { name: 'Enviar ahora' }))
    expect(await screen.findByText(texto)).toBeInTheDocument()
  })

  it('«Ver cuáles» pliega y despliega la lista', () => {
    cambios = [pendiente(1)]
    render(<CambiosSinEnviar />)

    const boton = screen.getByRole('button', { name: 'Ver cuáles' })
    expect(boton).toHaveAttribute('aria-expanded', 'false')

    fireEvent.click(boton)
    expect(screen.getByRole('button', { name: 'Ocultar' })).toHaveAttribute('aria-expanded', 'true')
    // Y se ve qué cambio es, con la hora que declaró el equipo.
    expect(screen.getByText(/según la hora de este equipo/)).toBeInTheDocument()
  })

  it('con rechazos y pendientes a la vez se cuentan las dos cosas por separado', () => {
    cambios = [pendiente(1), conflictoDeEntregas(2)]
    render(<CambiosSinEnviar />)

    expect(screen.getByText(/1 cambio guardado en este equipo/)).toBeInTheDocument()
    expect(screen.getByText(/1 cambio que el servidor no aceptó/)).toBeInTheDocument()
  })
})
