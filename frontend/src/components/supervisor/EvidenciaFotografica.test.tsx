import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import EvidenciaFotografica from './EvidenciaFotografica'

/**
 * Lo que se comprueba aquí es lo que hace evidencia a una evidencia: que la
 * foto llegue al servidor <b>asociada a su subetapa</b> y <b>tal como salió de
 * la cámara</b>. Si se enviara sin subetapa, la foto quedaría colgando del
 * contrato y nadie la encontraría en el paso; si se reenvasara en el navegador,
 * perdería la fecha y la ubicación que el teléfono escribe dentro.
 */
vi.mock('@/services/documentoService', () => ({
  subirDocumento: vi.fn(),
}))

const { subirDocumento } = await import('@/services/documentoService')

function fotoDePrueba(nombre = 'entrega.jpg', bytes = 1024) {
  return new File([new Uint8Array(bytes)], nombre, { type: 'image/jpeg' })
}

describe('EvidenciaFotografica', () => {
  beforeEach(() => {
    vi.mocked(subirDocumento).mockReset()
    // jsdom no implementa las URL de objeto, que son las de la vista previa.
    URL.createObjectURL = vi.fn(() => 'blob:vista-previa')
    URL.revokeObjectURL = vi.fn()
  })

  it('ofrece tomar la foto con la cámara y elegir una ya tomada', () => {
    render(
      <EvidenciaFotografica contratoId={1} subetapaApiId={77} codigoSubetapa="3.2" onCargada={() => {}} />,
    )

    expect(screen.getByRole('button', { name: /Tomar foto de la entrega/i })).toBeTruthy()
    expect(screen.getByRole('button', { name: /Elegir una foto/i })).toBeTruthy()
  })

  it('envía la foto sin modificarla y asociada a la subetapa', async () => {
    vi.mocked(subirDocumento).mockResolvedValue({ id: 9, nombre: 'Evidencia fotográfica 3.2 — entrega.jpg' } as never)
    const onCargada = vi.fn()
    const { container } = render(
      <EvidenciaFotografica contratoId={4} subetapaApiId={77} codigoSubetapa="3.2" onCargada={onCargada} />,
    )

    const foto = fotoDePrueba()
    const campos = container.querySelectorAll('input[type="file"]')
    fireEvent.change(campos[0], { target: { files: [foto] } })
    fireEvent.click(screen.getByRole('button', { name: /Cargar evidencia/i }))

    await waitFor(() => expect(subirDocumento).toHaveBeenCalledTimes(1))
    const [contratoId, archivo, opciones] = vi.mocked(subirDocumento).mock.calls[0]
    expect(contratoId).toBe(4)
    // El mismo objeto File que entregó la cámara: ni recomprimido ni recortado.
    expect(archivo).toBe(foto)
    expect(opciones?.subetapaId).toBe(77)
    await waitFor(() => expect(onCargada).toHaveBeenCalled())
  })

  it('el campo de la cámara pide la cámara trasera y solo acepta JPG y PNG', () => {
    const { container } = render(
      <EvidenciaFotografica contratoId={1} subetapaApiId={77} codigoSubetapa="3.1" onCargada={() => {}} />,
    )

    const campoCamara = container.querySelectorAll('input[type="file"]')[0]
    expect(campoCamara.getAttribute('capture')).toBe('environment')
    expect(campoCamara.getAttribute('accept')).toBe('image/jpeg,image/png')
  })

  it('dice qué pasó cuando el servidor rechaza la foto, sin dar por cargada la evidencia', async () => {
    vi.mocked(subirDocumento).mockRejectedValue(new Error('sin conexión'))
    const { container } = render(
      <EvidenciaFotografica contratoId={1} subetapaApiId={77} codigoSubetapa="3.2" onCargada={() => {}} />,
    )

    fireEvent.change(container.querySelectorAll('input[type="file"]')[0], {
      target: { files: [fotoDePrueba()] },
    })
    fireEvent.click(screen.getByRole('button', { name: /Cargar evidencia/i }))

    await screen.findByText(/No se pudo cargar la foto/i)
    expect(screen.queryByText(/Evidencia cargada/i)).toBeNull()
  })

  it('rechaza en el teléfono una foto mayor que el tope del servidor, sin gastar la subida', () => {
    const { container } = render(
      <EvidenciaFotografica contratoId={1} subetapaApiId={77} codigoSubetapa="3.2" onCargada={() => {}} />,
    )

    const enorme = fotoDePrueba('panoramica.jpg', 21 * 1024 * 1024)
    fireEvent.change(container.querySelectorAll('input[type="file"]')[0], { target: { files: [enorme] } })

    expect(screen.getByText(/el máximo son 20 MB/i)).toBeTruthy()
    expect(subirDocumento).not.toHaveBeenCalled()
  })
})
