import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { propsDePulsable } from './ui'

// Hasta el 19-11-2026 dos zonas pulsables de la interfaz eran `<div onClick>`:
// la zona para elegir documentos del alta de contratos y las filas de los pasos
// del expediente del supervisor. Con ratón funcionaban; con teclado no
// existían. Estas pruebas son las que lo habrían detectado.
describe('propsDePulsable', () => {
  function Pulsable({ accion }: { accion: () => void }) {
    return <div {...propsDePulsable(accion)}>Elegir documentos</div>
  }

  it('deja el elemento al alcance del tabulador y lo anuncia como botón', () => {
    render(<Pulsable accion={() => {}} />)
    const zona = screen.getByRole('button', { name: 'Elegir documentos' })
    expect(zona.tabIndex).toBe(0)
  })

  it('se activa con Enter', () => {
    const accion = vi.fn()
    render(<Pulsable accion={accion} />)
    fireEvent.keyDown(screen.getByRole('button'), { key: 'Enter' })
    expect(accion).toHaveBeenCalledTimes(1)
  })

  it('se activa con Espacio, sin desplazar la página', () => {
    const accion = vi.fn()
    render(<Pulsable accion={accion} />)
    const evento = fireEvent.keyDown(screen.getByRole('button'), { key: ' ' })
    expect(accion).toHaveBeenCalledTimes(1)
    // `fireEvent` devuelve false cuando el manejador llamó a preventDefault.
    expect(evento).toBe(false)
  })

  it('no se activa con cualquier otra tecla', () => {
    const accion = vi.fn()
    render(<Pulsable accion={accion} />)
    const zona = screen.getByRole('button')
    fireEvent.keyDown(zona, { key: 'a' })
    fireEvent.keyDown(zona, { key: 'Tab' })
    fireEvent.keyDown(zona, { key: 'Escape' })
    expect(accion).not.toHaveBeenCalled()
  })

  it('sigue respondiendo al ratón', () => {
    const accion = vi.fn()
    render(<Pulsable accion={accion} />)
    fireEvent.click(screen.getByRole('button'))
    expect(accion).toHaveBeenCalledTimes(1)
  })
})
