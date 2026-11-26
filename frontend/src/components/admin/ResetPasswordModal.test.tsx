import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ResetPasswordModal } from './ResetPasswordModal'
import { LARGO_DE_CLAVE_TEMPORAL, type UserRow } from './tipos'
import { ApiError } from '@/services/api/client'
import type { UsuarioResponse } from '@/services/api/types'

/**
 * Restablecer una contraseña es lo que se hace ante una cuenta que se sospecha
 * comprometida, y este archivo estaba al **0 % de cobertura**.
 *
 * <h2>Qué podía romperse sin que nada lo dijera</h2>
 * <ul>
 *   <li><b>Que se mande el rol y el teléfono actuales.</b> El endpoint es
 *       `PUT /api/usuarios/{id}`, que reemplaza la fila entera: un rol mal
 *       enviado DEGRADA O ASCIENDE a la persona de paso, al restablecerle la
 *       clave. Es el defecto más caro que puede tener esta pantalla y no había
 *       nada que lo impidiera.</li>
 *   <li><b>Que la contraseña se vea cuando el correo falla.</b> Si la pantalla
 *       fingiera éxito, la cuenta quedaría con una clave que nadie conoce: su
 *       dueño fuera, y sin forma de volver a entrar.</li>
 *   <li><b>Que se exija el teléfono.</b> El backend lo pide al actualizar, así
 *       que sin él el restablecimiento falla con un 400 después de haber
 *       anunciado que iba a cambiarla.</li>
 * </ul>
 */

const actualizarUsuario = vi.fn()
const enviarCredenciales = vi.fn()

vi.mock('@/services/usuarioService', () => ({
  actualizarUsuario: (...args: unknown[]) => actualizarUsuario(...args),
  enviarCredenciales: (...args: unknown[]) => enviarCredenciales(...args),
}))

const USUARIO: UserRow = {
  id: '7',
  nombre: 'Ana Zapata',
  correo: 'azapatar@sena.edu.co',
  telefono: '3001234567',
  rol: 'Supervisor',
  activo: true,
  rolApi: 'SUPERVISOR',
  telefonoApi: '3001234567',
}

const ACTUALIZADO: UsuarioResponse = {
  id: 7,
  nombre: 'Ana Zapata',
  email: 'azapatar@sena.edu.co',
  telefono: '3001234567',
  rol: 'SUPERVISOR',
  activo: true,
  fechaCreacion: '2026-11-20T10:00:00Z',
}

describe('ResetPasswordModal', () => {
  beforeEach(() => {
    actualizarUsuario.mockReset()
    enviarCredenciales.mockReset()
    actualizarUsuario.mockResolvedValue(ACTUALIZADO)
    enviarCredenciales.mockResolvedValue({ enviado: true, error: null })
  })

  it('conserva el rol y los datos de la cuenta: restablecer no puede cambiar de rol a nadie', async () => {
    render(<ResetPasswordModal usuario={USUARIO} onClose={vi.fn()} onActualizado={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: 'Restablecer y enviar' }))

    await waitFor(() => expect(actualizarUsuario).toHaveBeenCalledTimes(1))
    const [id, cuerpo] = actualizarUsuario.mock.calls[0]
    expect(id).toBe(7)
    expect(cuerpo).toMatchObject({
      nombre: 'Ana Zapata',
      email: 'azapatar@sena.edu.co',
      telefono: '3001234567',
      // PUT reemplaza la fila entera: si esto se perdiera, restablecer la clave
      // de un supervisor lo convertiría en otra cosa sin que nadie lo pidiera.
      rol: 'SUPERVISOR',
    })
    expect(cuerpo.password).toHaveLength(LARGO_DE_CLAVE_TEMPORAL)
  })

  it('avisa al panel con la fila actualizada y manda la credencial a su correo', async () => {
    const onActualizado = vi.fn()
    render(<ResetPasswordModal usuario={USUARIO} onClose={vi.fn()} onActualizado={onActualizado} />)

    fireEvent.click(screen.getByRole('button', { name: 'Restablecer y enviar' }))

    await waitFor(() => expect(onActualizado).toHaveBeenCalledWith(expect.objectContaining({ id: '7' })))
    await waitFor(() => expect(enviarCredenciales).toHaveBeenCalledWith(7, expect.any(Object)))
    expect(await screen.findByText(/Correo enviado a azapatar@sena.edu.co/)).toBeInTheDocument()
  })

  it('una cuenta sin teléfono no se restablece a ciegas: lo pide', async () => {
    // El backend lo exige al actualizar. Sin esta comprobación, la pantalla
    // anunciaba el cambio y el servidor lo rechazaba con un 400.
    render(<ResetPasswordModal usuario={{ ...USUARIO, telefonoApi: null }} onClose={vi.fn()} onActualizado={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: 'Restablecer y enviar' }))

    expect(screen.getByText(/teléfono es obligatorio/)).toBeInTheDocument()
    expect(actualizarUsuario).not.toHaveBeenCalled()

    fireEvent.change(screen.getByPlaceholderText('Número de contacto del usuario'), {
      target: { value: '3009998888' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Restablecer y enviar' }))
    await waitFor(() => expect(actualizarUsuario).toHaveBeenCalled())
    expect(actualizarUsuario.mock.calls[0][1]).toMatchObject({ telefono: '3009998888' })
  })

  it('si el correo no sale, la contraseña queda a la vista para entregarla a mano', async () => {
    enviarCredenciales.mockResolvedValue({ enviado: false, error: 'SMTP no configurado' })
    render(<ResetPasswordModal usuario={USUARIO} onClose={vi.fn()} onActualizado={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: 'Restablecer y enviar' }))

    expect(await screen.findByText(/No fue posible enviar el correo/)).toBeInTheDocument()
    // La contraseña anterior YA dejó de servir: sin esta a la vista, su dueño
    // se queda fuera del sistema.
    const temporal = screen.getByText(/Contraseña temporal:/)
    expect(temporal.textContent?.replace('Contraseña temporal: ', '')).toHaveLength(LARGO_DE_CLAVE_TEMPORAL)
  })

  it('un rechazo del servidor se muestra con su motivo y no dice que se cambió', async () => {
    actualizarUsuario.mockRejectedValue(new ApiError(400, 'No se puede cambiar el rol del único administrador.'))
    const onActualizado = vi.fn()
    render(<ResetPasswordModal usuario={USUARIO} onClose={vi.fn()} onActualizado={onActualizado} />)

    fireEvent.click(screen.getByRole('button', { name: 'Restablecer y enviar' }))

    expect(await screen.findByText(/único administrador/)).toBeInTheDocument()
    expect(onActualizado).not.toHaveBeenCalled()
    expect(screen.queryByText(/fue reemplazada/)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Restablecer y enviar' })).toBeEnabled()
  })

  it('no restablece dos veces si se pulsa dos veces', async () => {
    let resolver: ((u: UsuarioResponse) => void) | undefined
    actualizarUsuario.mockImplementation(() => new Promise<UsuarioResponse>((r) => (resolver = r)))
    render(<ResetPasswordModal usuario={USUARIO} onClose={vi.fn()} onActualizado={vi.fn()} />)

    const boton = screen.getByRole('button', { name: 'Restablecer y enviar' })
    fireEvent.click(boton)
    fireEvent.click(boton)

    expect(actualizarUsuario).toHaveBeenCalledTimes(1)
    resolver?.(ACTUALIZADO)
    await waitFor(() => expect(screen.getByText(/fue reemplazada/)).toBeInTheDocument())
  })
})
