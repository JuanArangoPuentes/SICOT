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

    // El campo sigue siendo un <input type="file">, solo que oculto: por eso se
    // localiza igual que antes, con querySelector y no por su control visible.
    const campo = document.querySelector('input[type="file"]') as HTMLInputElement
    fireEvent.change(campo, { target: { files: [new File(['x'], 'formato.pdf', { type: 'application/pdf' })] } })
    expect(screen.queryByText(/Selecciona un archivo/)).not.toBeInTheDocument()
  })

  // El selector de archivos nativo no puede quedar a la vista.
  //
  // En la app de Android el navegador lo pintaba como «Choose File / No file
  // chosen»: en inglés, en una interfaz que está toda en español, y sin el
  // estilo del resto de la aplicación. Observado en el emulador el 08-10-2026.
  // Ese texto lo escribe el navegador y no se puede traducir desde aquí, así
  // que la comprobación es que el campo no se muestre y que lo sustituya un
  // control del proyecto.
  it('oculta el control nativo y lo dispara con un botón en español', () => {
    abrir()
    const campo = document.querySelector('input[type="file"]') as HTMLInputElement
    expect(campo).not.toBeNull()
    expect(campo.style.display).toBe('none')
    expect(campo.accept).toBe('.pdf,.docx,.xlsx')
    // Nada visible que el navegador rotule por su cuenta.
    expect(screen.getByText('Sin archivo seleccionado')).toBeInTheDocument()

    const boton = screen.getByRole('button', { name: /Elegir archivo/ })
    const abrirDialogo = vi.spyOn(campo, 'click')
    fireEvent.click(boton)
    expect(abrirDialogo).toHaveBeenCalled()
    abrirDialogo.mockRestore()
  })

  it('muestra el nombre del archivo elegido', () => {
    abrir()
    const campo = document.querySelector('input[type="file"]') as HTMLInputElement
    fireEvent.change(campo, {
      target: { files: [new File(['x'], 'GCCON-F-031 Informe de supervisión.pdf', { type: 'application/pdf' })] },
    })

    expect(screen.getByText(/GCCON-F-031 Informe de supervisión\.pdf/)).toBeInTheDocument()
    expect(screen.queryByText('Sin archivo seleccionado')).not.toBeInTheDocument()
    // Con un archivo ya elegido, el botón ofrece cambiarlo.
    expect(screen.getByRole('button', { name: /Cambiar archivo/ })).toBeInTheDocument()
  })
})
