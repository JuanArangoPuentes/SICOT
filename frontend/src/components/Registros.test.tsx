import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Registros, { type Registro } from './Registros'
import { guardarArchivo } from '@/services/guardarArchivo'

vi.mock('@/services/guardarArchivo', () => ({
  guardarArchivo: vi.fn(),
}))

const registro: Registro = {
  id: 'r1',
  tipo: 'Documento',
  accion: 'Documento firmado',
  actor: 'Ana Gómez',
  fecha: '02/10/2026 15:15',
  asunto: 'Acta de Inicio firmada.',
}

function exportar() {
  render(<Registros extra={[registro]} />)
  fireEvent.click(screen.getByRole('button', { name: /descargar registros/i }))
}

// Exportar la bitácora descartaba el resultado y el error de guardarArchivo: en
// el APK, ni al guardar ni al fallar pasaba nada visible (el síntoma de MDL-184).
describe('Registros · descargar en CSV', () => {
  beforeEach(() => {
    vi.mocked(guardarArchivo).mockReset()
  })

  it('en el teléfono confirma que quedó guardado', async () => {
    vi.mocked(guardarArchivo).mockResolvedValue('guardado')

    exportar()

    expect(await screen.findByRole('status')).toHaveTextContent(
      /sicot-registros-.*\.csv» quedó guardado en el teléfono/,
    )
  })

  it('si no se pudo escribir, lo dice', async () => {
    vi.mocked(guardarArchivo).mockRejectedValue(new Error('permiso denegado'))

    exportar()

    expect(await screen.findByRole('alert')).toHaveTextContent(/no se pudo guardar/i)
  })

  it('en el navegador no añade nada: el gestor de descargas ya avisa', async () => {
    vi.mocked(guardarArchivo).mockResolvedValue('navegador')

    exportar()

    await vi.waitFor(() => expect(guardarArchivo).toHaveBeenCalled())
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
