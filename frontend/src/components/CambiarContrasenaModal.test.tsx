import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/services/api/client'
import { cambiarMiContrasena } from '@/services/cuentaService'
import { CambiarContrasenaModal } from './CambiarContrasenaModal'

vi.mock('@/services/cuentaService', () => ({ cambiarMiContrasena: vi.fn() }))

const cambiar = vi.mocked(cambiarMiContrasena)

function llenar(actual: string, nueva: string, confirmacion = nueva) {
  fireEvent.change(screen.getByLabelText('Contraseña actual'), { target: { value: actual } })
  fireEvent.change(screen.getByLabelText(/^Contraseña nueva/), { target: { value: nueva } })
  fireEvent.change(screen.getByLabelText('Repita la contraseña nueva'), { target: { value: confirmacion } })
  fireEvent.click(screen.getByRole('button', { name: 'Cambiar contraseña' }))
}

/**
 * El formulario de «Cambiar contraseña». Lo que se vigila: que avise antes de
 * enviar lo que el servidor va a rechazar, que muestre el motivo real cuando el
 * servidor rechaza, y que una sesión sin red no ofrezca algo que no puede hacer.
 */
describe('CambiarContrasenaModal', () => {
  beforeEach(() => {
    cambiar.mockReset()
    localStorage.clear()
  })

  afterEach(() => localStorage.clear())

  it('cambia la contraseña con los datos escritos y lo confirma', async () => {
    cambiar.mockResolvedValue({
      token: 't',
      usuarioId: 1,
      nombre: 'Admin',
      email: 'a@sicot.test',
      rol: 'ADMINISTRADOR',
    })
    render(<CambiarContrasenaModal correo="a@sicot.test" onClose={vi.fn()} />)

    llenar('Admin2026', 'OtraClave456*')

    expect(await screen.findByText('Su contraseña nueva ya está activa')).toBeInTheDocument()
    expect(cambiar).toHaveBeenCalledWith('a@sicot.test', 'Admin2026', 'OtraClave456*')
  })

  it('no envía si la confirmación no coincide, y el aviso se va al corregirla', () => {
    render(<CambiarContrasenaModal correo="a@sicot.test" onClose={vi.fn()} />)

    llenar('Admin2026', 'OtraClave456*', 'OtraClave456')
    expect(screen.getByRole('alert')).toHaveTextContent('La confirmación no coincide')
    expect(cambiar).not.toHaveBeenCalled()

    fireEvent.change(screen.getByLabelText('Repita la contraseña nueva'), { target: { value: 'OtraClave456*' } })
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it.each([
    ['corta1*', 'entre 8 y 72 caracteres'],
    ['ClaveConEspacio ', 'no puede empezar ni terminar con espacios'],
    ['Admin2026', 'distinta de la actual'],
  ])('avisa antes de enviar una nueva que el servidor rechazaría (%s)', (nueva, aviso) => {
    render(<CambiarContrasenaModal correo="a@sicot.test" onClose={vi.fn()} />)

    llenar('Admin2026', nueva)

    expect(screen.getByRole('alert')).toHaveTextContent(aviso)
    expect(cambiar).not.toHaveBeenCalled()
  })

  it('muestra el motivo del servidor y deja intentarlo otra vez', async () => {
    cambiar.mockRejectedValue(new ApiError(400, 'La contraseña actual no es correcta.'))
    render(<CambiarContrasenaModal correo="a@sicot.test" onClose={vi.fn()} />)

    llenar('NoEsLaMia1', 'OtraClave456*')

    expect(await screen.findByRole('alert')).toHaveTextContent('La contraseña actual no es correcta.')
    expect(screen.getByRole('button', { name: 'Cambiar contraseña' })).toBeEnabled()
  })

  it('tras demasiados intentos dice cuánto esperar', async () => {
    cambiar.mockRejectedValue(new ApiError(429, 'Demasiados intentos.', undefined, 600))
    render(<CambiarContrasenaModal correo="a@sicot.test" onClose={vi.fn()} />)

    llenar('NoEsLaMia1', 'OtraClave456*')

    expect(await screen.findByRole('alert')).toHaveTextContent('Vuelva a intentarlo en 10 min')
  })

  it('sin conexión con el servidor no ofrece el formulario y dice por qué', () => {
    localStorage.setItem(
      'sicot.session',
      JSON.stringify({
        token: '',
        usuarioId: 3,
        nombre: 'Sup',
        email: 's@sicot.test',
        rol: 'SUPERVISOR',
        sinConexion: true,
      }),
    )
    render(<CambiarContrasenaModal correo="s@sicot.test" onClose={vi.fn()} />)

    expect(screen.getByText(/Entró sin conexión con el servidor/)).toBeInTheDocument()
    expect(screen.queryByLabelText('Contraseña actual')).not.toBeInTheDocument()
  })
})
