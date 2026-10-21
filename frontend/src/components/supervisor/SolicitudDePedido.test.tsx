import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SolicitudDePedido from './SolicitudDePedido'
import { contrato } from '@/test/dobles'
import type { SolicitudDePedidoResponse } from '@/services/api/types'

/**
 * El paso 2.4 con el adjunto que pidió Alex el 20-10-2026: el supervisor pega
 * los bienes de la oferta económica, SICOT dice cuánto compromete del contrato
 * y entrega el Excel que va al correo.
 *
 * Las cifras son las del pedido real del Lote 4.
 */
vi.mock('@/services/contratoService', () => ({
  obtenerSolicitudDePedido: vi.fn(),
  guardarSolicitudDePedido: vi.fn(),
  descargarSolicitudDePedido: vi.fn(),
}))

const { obtenerSolicitudDePedido, guardarSolicitudDePedido, descargarSolicitudDePedido } = await import(
  '@/services/contratoService'
)

const LOTE_4 = contrato({
  id: 9,
  numeroContrato: 'CO1.PCCNTR.10057820',
  valor: 45_900_300,
  contratista: 'SUMINISTROS MAYBE S.A.S',
})

const VACIA: SolicitudDePedidoResponse = {
  items: [],
  presupuestoAsignado: 45_900_300,
  comprometido: 0,
  queda: 45_900_300,
  excedeElContrato: false,
}

const CON_DOS_BIENES: SolicitudDePedidoResponse = {
  items: [
    {
      numero: 1,
      descripcion: 'Mouse Óptico Alámbrico 1000 DPI',
      cantidad: 83,
      valorUnitario: 29462.51,
      valorTotal: 2445388.33,
    },
    {
      numero: 2,
      descripcion: 'Unidad de Estado Sólido 500 GB',
      cantidad: 20,
      valorUnitario: 183122.52,
      valorTotal: 3662450.4,
    },
  ],
  presupuestoAsignado: 45_900_300,
  comprometido: 6_107_838.73,
  queda: 39_792_461.27,
  excedeElContrato: false,
}

describe('2.4 — solicitud de pedido al contratista', () => {
  beforeEach(() => {
    vi.mocked(obtenerSolicitudDePedido).mockReset()
    vi.mocked(guardarSolicitudDePedido).mockReset()
    vi.mocked(descargarSolicitudDePedido).mockReset()
  })

  it('sin bienes cargados dice de dónde sacarlos, en vez de mostrar una tabla vacía', async () => {
    vi.mocked(obtenerSolicitudDePedido).mockResolvedValue(VACIA)
    render(<SolicitudDePedido contrato={LOTE_4} />)

    expect(await screen.findByText(/Todavía no ha cargado los bienes/)).toBeTruthy()
    expect(screen.getByText(/oferta económica/)).toBeTruthy()
    // Sin bienes no hay nada que descargar, y el botón no se ofrece.
    expect(screen.queryByText('Descargar el Excel para adjuntar')).toBeNull()
  })

  it('el supervisor pega la tabla de la oferta y SICOT dice cuántos bienes reconoció', async () => {
    vi.mocked(obtenerSolicitudDePedido).mockResolvedValue(VACIA)
    vi.mocked(guardarSolicitudDePedido).mockResolvedValue(CON_DOS_BIENES)
    render(<SolicitudDePedido contrato={LOTE_4} />)

    fireEvent.click(await screen.findByText('Cargar los bienes de la oferta'))
    fireEvent.change(screen.getByLabelText('Bienes pegados de la oferta económica'), {
      target: {
        value: [
          'ITEMS\tDESCRIPCIÓN\tCANTIDAD\tVALOR UNITARIO CON IVA',
          '1\tMouse Óptico Alámbrico 1000 DPI\t83\t$ 29,462.51',
          '2\tUnidad de Estado Sólido 500 GB\t20\t$ 183,122.52',
        ].join('\n'),
      },
    })

    // Se ve antes de guardar: el encabezado se descarta solo y quedan dos.
    expect(screen.getByText('Se reconocieron 2 bien(es).')).toBeTruthy()

    fireEvent.click(screen.getByText('Guardar la solicitud'))
    await waitFor(() =>
      expect(guardarSolicitudDePedido).toHaveBeenCalledWith(9, [
        { numero: 1, descripcion: 'Mouse Óptico Alámbrico 1000 DPI', cantidad: 83, valorUnitario: 29462.51 },
        { numero: 2, descripcion: 'Unidad de Estado Sólido 500 GB', cantidad: 20, valorUnitario: 183122.52 },
      ]),
    )
    expect(await screen.findByText('Mouse Óptico Alámbrico 1000 DPI')).toBeTruthy()
  })

  it('muestra lo comprometido y lo que queda del contrato', async () => {
    vi.mocked(obtenerSolicitudDePedido).mockResolvedValue(CON_DOS_BIENES)
    render(<SolicitudDePedido contrato={LOTE_4} />)

    expect(await screen.findByText(/Comprometido:/)).toBeTruthy()
    expect(screen.getByText(/Queda:/)).toBeTruthy()
    // Sin exceso no se alarma al supervisor.
    expect(screen.queryByText(/excede el valor del contrato/)).toBeNull()
  })

  it('avisa cuando el pedido se pasa del valor del contrato', async () => {
    vi.mocked(obtenerSolicitudDePedido).mockResolvedValue({
      ...CON_DOS_BIENES,
      presupuestoAsignado: 1_000_000,
      queda: -5_107_838.73,
      excedeElContrato: true,
    })
    render(<SolicitudDePedido contrato={LOTE_4} />)

    expect(await screen.findByText(/La solicitud excede el valor del contrato/)).toBeTruthy()
  })

  it('descarga el Excel con el número del contrato en el nombre', async () => {
    vi.mocked(obtenerSolicitudDePedido).mockResolvedValue(CON_DOS_BIENES)
    vi.mocked(descargarSolicitudDePedido).mockResolvedValue('guardado')
    render(<SolicitudDePedido contrato={LOTE_4} />)

    fireEvent.click(await screen.findByText('Descargar el Excel para adjuntar'))
    await waitFor(() => expect(descargarSolicitudDePedido).toHaveBeenCalledWith(9, 'CO1.PCCNTR.10057820'))
    expect(await screen.findByText(/quedó guardado/)).toBeTruthy()
  })

  it('no deja guardar un pegado que no se pudo leer', async () => {
    vi.mocked(obtenerSolicitudDePedido).mockResolvedValue(VACIA)
    render(<SolicitudDePedido contrato={LOTE_4} />)

    fireEvent.click(await screen.findByText('Cargar los bienes de la oferta'))
    fireEvent.change(screen.getByLabelText('Bienes pegados de la oferta económica'), {
      target: { value: 'esto no es una tabla' },
    })

    expect(screen.getByText('No se reconoció ninguna fila.')).toBeTruthy()
    expect(screen.getByText('Guardar la solicitud').hasAttribute('disabled')).toBe(true)
    expect(guardarSolicitudDePedido).not.toHaveBeenCalled()
  })
})
