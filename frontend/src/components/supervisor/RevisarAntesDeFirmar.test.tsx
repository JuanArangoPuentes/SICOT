import { act, fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RevisarAntesDeFirmar from './RevisarAntesDeFirmar'

vi.mock('@/services/documentoService', () => ({
  verBorrador: vi.fn(),
}))

/**
 * El diálogo donde el supervisor decide si firma. Lo que se fija aquí: que
 * pueda abrir el borrador completo, que un fallo al abrirlo se diga con su
 * causa y que nada se firme sin pulsar «Firmar».
 */

async function servicio() {
  return vi.mocked(await import('@/services/documentoService'))
}

function montar(props: Partial<React.ComponentProps<typeof RevisarAntesDeFirmar>> = {}) {
  const manejadores = { onFirmar: vi.fn(), onUsarNotas: vi.fn(), onCancelar: vi.fn() }
  render(
    <RevisarAntesDeFirmar
      contratoId={1}
      documentoId={9}
      documento="Acta de Inicio"
      nombreArchivo="Acta de Inicio — CTMA-2026-0184"
      notas=""
      observaciones={null}
      redactadasConIa={false}
      motivoNotasTalCual={null}
      {...manejadores}
      {...props}
    />,
  )
  return manejadores
}

describe('RevisarAntesDeFirmar', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('sin observaciones ofrece ver el borrador completo y firmar, sin «Usar mis notas tal cual»', async () => {
    const documentos = await servicio()
    documentos.verBorrador.mockResolvedValue('abierto')
    const { onFirmar } = montar()

    await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Ver borrador completo (PDF)' })))

    expect(documentos.verBorrador).toHaveBeenCalledWith(1, 9, 'Acta de Inicio — CTMA-2026-0184')
    expect(screen.queryByText('Usar mis notas tal cual')).not.toBeInTheDocument()
    expect(onFirmar).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Firmar' }))
    expect(onFirmar).toHaveBeenCalledTimes(1)
  })

  it('mientras abre el borrador no admite otro toque', async () => {
    const documentos = await servicio()
    let terminar: (r: 'abierto') => void = () => {}
    documentos.verBorrador.mockReturnValue(new Promise((r) => (terminar = r)))
    montar()

    fireEvent.click(screen.getByRole('button', { name: 'Ver borrador completo (PDF)' }))
    const ocupado = screen.getByRole('button', { name: 'Abriendo el borrador…' })
    expect(ocupado).toBeDisabled()
    fireEvent.click(ocupado)
    expect(documentos.verBorrador).toHaveBeenCalledTimes(1)

    await act(async () => terminar('abierto'))
    expect(screen.getByRole('button', { name: 'Ver borrador completo (PDF)' })).toBeEnabled()
  })

  it('si el servidor no responde al abrir el borrador, lo dice sin culpar al documento ni al Copiloto', async () => {
    const documentos = await servicio()
    documentos.verBorrador.mockRejectedValue(new TypeError('Failed to fetch'))
    montar()

    await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Ver borrador completo (PDF)' })))

    const aviso = screen.getByRole('alert')
    expect(aviso).toHaveTextContent(/no hubo respuesta del servidor de SICOT/i)
    expect(aviso).not.toHaveTextContent(/copiloto/i)
  })

  it('en el teléfono dice dónde quedó el borrador, porque el «Guardar como» se cierra sin otra señal', async () => {
    const documentos = await servicio()
    documentos.verBorrador.mockResolvedValue('guardado')
    montar()

    await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Ver borrador completo (PDF)' })))

    expect(screen.getByRole('status')).toHaveTextContent(/quedó guardado en el teléfono/i)
  })

  it('con la redacción del Copiloto, la marca frente a las notas y deja usar las notas tal cual', () => {
    const { onUsarNotas } = montar({
      notas: 'se devolvieron al contratista 3 monitores',
      observaciones: 'El contratista entregó correctamente tres monitores nuevos.',
      redactadasConIa: true,
    })

    expect(screen.getByTestId('redaccion-revisada').querySelectorAll('mark').length).toBeGreaterThan(0)
    fireEvent.click(screen.getByRole('button', { name: 'Usar mis notas tal cual' }))
    expect(onUsarNotas).toHaveBeenCalledTimes(1)
  })

  it('«Cancelar» cierra sin firmar', () => {
    const { onCancelar, onFirmar } = montar()

    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }))

    expect(onCancelar).toHaveBeenCalledTimes(1)
    expect(onFirmar).not.toHaveBeenCalled()
  })
})
