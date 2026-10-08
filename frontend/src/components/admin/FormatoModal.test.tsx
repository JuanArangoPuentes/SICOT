import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { FormatoModal } from './FormatoModal'

// El aviso de un campo obligatorio no puede sobrevivir a que lo llenen.
//
// Los tres campos se validan al pulsar «Cargar formato», y el aviso se
// quedaba en pantalla hasta el siguiente intento: en el emulador, el
// 08-10-2026, se veía «El nombre del formato es obligatorio» con el nombre ya
// escrito justo encima, y seguía ahí después de elegir el archivo. Quien lo
// lea no puede saber si el formulario está bien o mal.

function abrir() {
  render(<FormatoModal formatoExistente={null} onClose={vi.fn()} onUploaded={vi.fn()} />)
}

describe('FormatoModal', () => {
  it('quita el aviso de campo obligatorio en cuanto se escribe en el campo', () => {
    abrir()
    fireEvent.change(screen.getByPlaceholderText(/GCCON-F-031/), { target: { value: 'PRUEBA-F-001' } })
    fireEvent.click(screen.getByRole('button', { name: /Cargar formato/ }))
    expect(screen.getByText(/El nombre del formato es obligatorio/)).toBeInTheDocument()

    fireEvent.change(screen.getByPlaceholderText(/Informe de supervisión/), {
      target: { value: 'Formato de prueba' },
    })
    expect(screen.queryByText(/El nombre del formato es obligatorio/)).not.toBeInTheDocument()
  })

  it('quita el aviso de archivo al elegir uno', () => {
    abrir()
    fireEvent.change(screen.getByPlaceholderText(/GCCON-F-031/), { target: { value: 'PRUEBA-F-001' } })
    fireEvent.change(screen.getByPlaceholderText(/Informe de supervisión/), {
      target: { value: 'Formato de prueba' },
    })
    fireEvent.click(screen.getByRole('button', { name: /Cargar formato/ }))
    expect(screen.getByText(/Selecciona un archivo/)).toBeInTheDocument()

    const campo = document.querySelector('input[type="file"]') as HTMLInputElement
    fireEvent.change(campo, { target: { files: [new File(['x'], 'formato.pdf', { type: 'application/pdf' })] } })
    expect(screen.queryByText(/Selecciona un archivo/)).not.toBeInTheDocument()
  })
})
