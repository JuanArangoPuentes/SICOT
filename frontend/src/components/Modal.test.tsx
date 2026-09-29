import { fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { Modal } from './ui'

// Hasta el 29-09-2026 el diálogo no manejaba el teclado: el foco se quedaba en
// la página tapada, Escape no cerraba y, al cerrar, el foco se perdía.
function ConBoton({ onClose }: { onClose?: () => void }) {
  const [abierto, setAbierto] = useState(false)
  return (
    <>
      <button onClick={() => setAbierto(true)}>Abrir</button>
      {abierto && (
        <Modal
          title="Prueba"
          onClose={() => {
            onClose?.()
            setAbierto(false)
          }}
        >
          <input aria-label="Campo" />
          <button>Aceptar</button>
        </Modal>
      )}
    </>
  )
}

describe('Modal', () => {
  it('al abrirse lleva el foco al diálogo y al cerrarse lo devuelve a quien lo abrió', () => {
    render(<ConBoton />)
    const abrir = screen.getByText('Abrir')
    abrir.focus()
    fireEvent.click(abrir)

    expect(document.activeElement).toBe(screen.getByRole('dialog'))

    fireEvent.keyDown(document, { key: 'Escape' })
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(document.activeElement).toBe(abrir)
  })

  it('Tab no sale del diálogo', () => {
    render(<ConBoton />)
    fireEvent.click(screen.getByText('Abrir'))
    const cerrar = screen.getByLabelText('Cerrar ventana')
    const aceptar = screen.getByText('Aceptar')

    aceptar.focus()
    fireEvent.keyDown(document, { key: 'Tab' })
    expect(document.activeElement).toBe(cerrar)

    fireEvent.keyDown(document, { key: 'Tab', shiftKey: true })
    expect(document.activeElement).toBe(aceptar)
  })

  /**
   * La revisión de la redacción se abre sola cuando el Copiloto termina, y
   * puede aparecer con Configuración abierta: Escape cierra solo el de
   * arriba, y el de arriba va por encima.
   */
  it('con dos diálogos abiertos, Escape cierra solo el de arriba', () => {
    const abajo = vi.fn()
    const arriba = vi.fn()
    render(
      <>
        <Modal title="Configuración" onClose={abajo}>
          <button>Tema</button>
        </Modal>
        <Modal title="Revise las observaciones" onClose={arriba}>
          <button>Firmar</button>
        </Modal>
      </>,
    )

    fireEvent.keyDown(document, { key: 'Escape' })

    expect(arriba).toHaveBeenCalledTimes(1)
    expect(abajo).not.toHaveBeenCalled()
    const [telonAbajo, telonArriba] = screen.getAllByRole('presentation')
    expect(Number(telonArriba.style.zIndex)).toBeGreaterThan(Number(telonAbajo.style.zIndex))
  })

  it('por defecto un toque fuera lo cierra', () => {
    const onClose = vi.fn()
    render(<ConBoton onClose={onClose} />)
    fireEvent.click(screen.getByText('Abrir'))

    fireEvent.click(screen.getByRole('presentation'))
    expect(onClose).toHaveBeenCalled()
  })
})
