import { act, fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import VistaDocumentos from './VistaDocumentos'
import { ErrorAlGuardar } from '@/services/guardarArchivo'
import { contrato, documento } from '@/test/dobles'
import type { DocumentoResponse } from '@/services/api/types'

vi.mock('@/services/documentoService', () => ({
  verificarIntegridad: vi.fn(),
  descargarDocumento: vi.fn(),
}))

/**
 * La vista de Documentos es donde el supervisor comprueba qué firmó. Cada
 * prueba fija un caso en que, hasta la auditoría del 02-10-2026, la vista
 * afirmaba algo que no sabía: «Verificando…» para siempre tras un fallo,
 * «Sin generar aún» con el servidor caído, una fecha de otro día.
 */

async function servicio() {
  return vi.mocked(await import('@/services/documentoService'))
}

async function montar(props: Partial<React.ComponentProps<typeof VistaDocumentos>> = {}) {
  const resultado = render(
    <VistaDocumentos
      contrato={contrato()}
      docsContrato={[]}
      cargando={false}
      error={false}
      onReintentar={vi.fn()}
      tieneFirma={true}
      onIrASubPaso={vi.fn()}
      {...props}
    />,
  )
  await act(async () => {})
  return resultado
}

const actaFirmada = (): DocumentoResponse =>
  documento({
    id: 7,
    nombre: 'Acta de Inicio — CTMA-2026-0184',
    generadoPorIa: true,
    estado: 'APROBADO',
    firmaId: 'FIRMA-TEST',
  })

describe('VistaDocumentos', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('si la verificación de integridad falla, no se queda en «Verificando…» y deja reintentarla', async () => {
    const documentos = await servicio()
    documentos.verificarIntegridad.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    await montar({ docsContrato: [actaFirmada()] })

    expect(screen.queryByText('Verificando…')).not.toBeInTheDocument()
    expect(screen.getAllByText('No se pudo verificar').length).toBeGreaterThan(0)

    documentos.verificarIntegridad.mockResolvedValueOnce({
      documentoId: 7,
      nombre: 'Acta de Inicio — CTMA-2026-0184',
      estado: 'INTEGRO',
      hashRegistrado: 'a',
      hashActual: 'a',
      firmaId: 'FIRMA-TEST',
    } as never)
    await act(async () => fireEvent.click(screen.getAllByRole('button', { name: /reintentar la verificación/i })[0]))

    expect(documentos.verificarIntegridad).toHaveBeenCalledTimes(2)
    expect(screen.queryByText('No se pudo verificar')).not.toBeInTheDocument()
    expect(screen.getAllByText('Íntegro').length).toBeGreaterThan(0)
  })

  it('si no se pudieron consultar los documentos, lo dice en vez de «Sin generar aún»', async () => {
    const onReintentar = vi.fn()
    await montar({ error: true, onReintentar })

    expect(screen.queryByText('Sin generar aún')).not.toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent(/no se pudieron consultar los documentos/i)
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(onReintentar).toHaveBeenCalled()
  })

  it('mientras consulta los documentos no afirma que estén sin generar', async () => {
    await montar({ cargando: true })

    expect(screen.queryByText('Sin generar aún')).not.toBeInTheDocument()
    expect(screen.getByText(/consultando los documentos del contrato/i)).toBeInTheDocument()
  })

  it('con la consulta hecha y sin documentos, sí dice que están sin generar', async () => {
    await montar()

    expect(screen.getAllByText('Sin generar aún')).toHaveLength(5)
  })

  it('la fecha de carga es la del Centro, no la de UTC', async () => {
    // 30/09 a las 20:15 en Colombia.
    await montar({ docsContrato: [documento({ fechaSubida: '2026-10-01T01:15:00Z' })] })

    expect(screen.getByText(/30\/09\/2026/)).toBeInTheDocument()
    expect(screen.queryByText(/01\/10\/2026/)).not.toBeInTheDocument()
  })

  it('un segundo toque en «Descargar» no abre otra descarga mientras la primera sigue', async () => {
    const documentos = await servicio()
    let terminar: (r: 'navegador') => void = () => {}
    documentos.descargarDocumento.mockReturnValue(new Promise((r) => (terminar = r)))
    await montar({ docsContrato: [documento()] })

    fireEvent.click(screen.getByRole('button', { name: 'Descargar' }))
    const ocupado = screen.getByRole('button', { name: 'Descargando…' })
    expect(ocupado).toBeDisabled()
    fireEvent.click(ocupado)
    expect(documentos.descargarDocumento).toHaveBeenCalledTimes(1)

    await act(async () => terminar('navegador'))
    expect(screen.getByRole('button', { name: 'Descargar' })).toBeEnabled()
  })

  it('si el teléfono no deja guardar el archivo, lo dice con su motivo y no invita a reintentar', async () => {
    const documentos = await servicio()
    documentos.descargarDocumento.mockRejectedValue(new ErrorAlGuardar('No queda espacio en el dispositivo'))
    await montar({ docsContrato: [documento()] })

    await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Descargar' })))

    const aviso = screen.getByRole('alert')
    expect(aviso).toHaveTextContent(/no se pudo guardar «Acta de Inicio» en el teléfono/i)
    expect(aviso).toHaveTextContent('No queda espacio en el dispositivo')
    expect(aviso).not.toHaveTextContent(/intente de nuevo/i)
  })

  it('si el servidor no respondió a la descarga, no culpa al archivo', async () => {
    const documentos = await servicio()
    documentos.descargarDocumento.mockRejectedValue(new TypeError('Failed to fetch'))
    await montar({ docsContrato: [documento()] })

    await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Descargar' })))

    expect(screen.getByRole('alert')).toHaveTextContent(/no hubo respuesta del servidor de SICOT/i)
  })
})
