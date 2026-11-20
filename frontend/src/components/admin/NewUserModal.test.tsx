import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { NewUserModal } from './NewUserModal'
import { LARGO_DE_CLAVE_TEMPORAL } from './tipos'
import { ApiError } from '@/services/api/client'
import type { UsuarioResponse } from '@/services/api/types'

/**
 * Crear una cuenta es la operación que decide **quién entra al sistema**, y
 * este archivo estaba al **0 % de cobertura**: ninguna prueba lo tocaba.
 *
 * <h2>Qué podía romperse sin que nada lo dijera</h2>
 * Las tres cosas que se fijan aquí:
 * <ul>
 *   <li><b>La validación del correo institucional.</b> Ya falló una vez de
 *       verdad: hasta el 18-10-2026 solo se admitía `@soy.sena.edu.co`, el
 *       dominio de los aprendices, así que <i>no se podía dar de alta a ningún
 *       supervisor real</i> —que usan `@sena.edu.co`—. Se corrigió a mano y sin
 *       prueba, de modo que nada impedía que volviera.</li>
 *   <li><b>Que la contraseña temporal se muestre cuando el correo falla.</b> Es
 *       el único camino que tiene el administrador para entregarla si el SMTP
 *       no está configurado. Si la pantalla fingiera éxito, la cuenta quedaría
 *       creada y nadie podría entrar con ella.</li>
 *   <li><b>Que un rechazo del servidor no pierda lo escrito.</b></li>
 * </ul>
 */

const crearUsuario = vi.fn()
const enviarCredenciales = vi.fn()

vi.mock('@/services/usuarioService', () => ({
  crearUsuario: (...args: unknown[]) => crearUsuario(...args),
  enviarCredenciales: (...args: unknown[]) => enviarCredenciales(...args),
}))

const CREADO: UsuarioResponse = {
  id: 7,
  nombre: 'Ana Zapata',
  email: 'azapatar@sena.edu.co',
  telefono: '3001234567',
  rol: 'SUPERVISOR',
  activo: true,
  fechaCreacion: '2026-11-20T10:00:00Z',
}

function llenarFormulario({
  nombre = 'Ana Zapata',
  correo = 'azapatar@sena.edu.co',
  telefono = '3001234567',
}: { nombre?: string; correo?: string; telefono?: string } = {}) {
  fireEvent.change(screen.getByPlaceholderText('Nombres y apellidos'), { target: { value: nombre } })
  fireEvent.change(screen.getByPlaceholderText('usuario@sena.edu.co'), { target: { value: correo } })
  fireEvent.change(screen.getByPlaceholderText('300 000 0000'), { target: { value: telefono } })
}

describe('NewUserModal', () => {
  beforeEach(() => {
    crearUsuario.mockReset()
    enviarCredenciales.mockReset()
    crearUsuario.mockResolvedValue(CREADO)
    enviarCredenciales.mockResolvedValue({ enviado: true, error: null })
  })

  it('crea la cuenta con el rol elegido y la contraseña generada', async () => {
    const onCreate = vi.fn()
    render(<NewUserModal onClose={vi.fn()} onCreate={onCreate} />)

    llenarFormulario()
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'Gestor de Contratación' } })
    fireEvent.click(screen.getByRole('button', { name: 'Crear usuario' }))

    await waitFor(() => expect(crearUsuario).toHaveBeenCalledTimes(1))
    const enviado = crearUsuario.mock.calls[0][0]
    expect(enviado).toMatchObject({
      nombre: 'Ana Zapata',
      email: 'azapatar@sena.edu.co',
      telefono: '3001234567',
      // La etiqueta de la pantalla está en español y la API espera el enum:
      // si esa traducción se rompe, se crean cuentas con el rol equivocado.
      rol: 'GESTION',
    })
    expect(enviado.password).toHaveLength(LARGO_DE_CLAVE_TEMPORAL)

    // Y la credencial sale hacia el correo de la persona, no hacia otro sitio.
    await waitFor(() => expect(enviarCredenciales).toHaveBeenCalledWith(7, { password: enviado.password }))
    expect(await screen.findByText(/Correo enviado a azapatar@sena.edu.co/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Aceptar' }))
    expect(onCreate).toHaveBeenCalledWith(expect.objectContaining({ correo: 'azapatar@sena.edu.co' }))
  })

  it.each([
    ['azapatar@sena.edu.co', true],
    ['aprendiz@soy.sena.edu.co', true],
    // El caso que falló de verdad: si la expresión vuelve a exigir «soy.», el
    // Centro no puede dar de alta a ninguno de sus supervisores.
    ['AZAPATAR@SENA.EDU.CO', true],
    ['alguien@gmail.com', false],
    ['alguien@sena.edu.co.falso.com', false],
    ['sin-arroba', false],
  ])('el correo %s se acepta: %s', async (correo, valido) => {
    render(<NewUserModal onClose={vi.fn()} onCreate={vi.fn()} />)

    llenarFormulario({ correo })
    fireEvent.click(screen.getByRole('button', { name: 'Crear usuario' }))

    if (valido) {
      await waitFor(() => expect(crearUsuario).toHaveBeenCalled())
    } else {
      expect(crearUsuario).not.toHaveBeenCalled()
      expect(screen.getByText(/correo debe ser institucional del SENA/)).toBeInTheDocument()
    }
  })

  it('exige nombre y teléfono antes de llamar al servidor', () => {
    render(<NewUserModal onClose={vi.fn()} onCreate={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: 'Crear usuario' }))
    expect(screen.getByText('El nombre completo es obligatorio.')).toBeInTheDocument()

    llenarFormulario({ telefono: '' })
    fireEvent.click(screen.getByRole('button', { name: 'Crear usuario' }))
    expect(screen.getByText('El número de teléfono es obligatorio.')).toBeInTheDocument()

    expect(crearUsuario).not.toHaveBeenCalled()
  })

  it('si el correo no sale, muestra la contraseña para entregarla a mano en vez de fingir que se envió', async () => {
    enviarCredenciales.mockResolvedValue({ enviado: false, error: 'SMTP no configurado' })
    render(<NewUserModal onClose={vi.fn()} onCreate={vi.fn()} />)

    llenarFormulario()
    fireEvent.click(screen.getByRole('button', { name: 'Crear usuario' }))

    expect(await screen.findByText(/No fue posible enviar el correo/)).toBeInTheDocument()
    expect(screen.getByText(/SMTP no configurado/)).toBeInTheDocument()
    // La cuenta EXISTE: sin esta contraseña a la vista, nadie podría entrar con ella.
    const temporal = screen.getByText(/Contraseña temporal:/)
    expect(temporal.textContent?.replace('Contraseña temporal: ', '')).toHaveLength(LARGO_DE_CLAVE_TEMPORAL)
  })

  it('si el envío del correo revienta, tampoco se pierde la contraseña', async () => {
    enviarCredenciales.mockRejectedValue(new Error('la red se cayó'))
    render(<NewUserModal onClose={vi.fn()} onCreate={vi.fn()} />)

    llenarFormulario()
    fireEvent.click(screen.getByRole('button', { name: 'Crear usuario' }))

    expect(await screen.findByText(/No se pudo contactar al servidor/)).toBeInTheDocument()
    expect(screen.getByText(/Contraseña temporal:/)).toBeInTheDocument()
  })

  it('un rechazo del servidor se muestra con su motivo y deja reintentar sin volver a escribir', async () => {
    crearUsuario.mockRejectedValue(new ApiError(400, 'Ya existe un usuario con el email azapatar@sena.edu.co.'))
    render(<NewUserModal onClose={vi.fn()} onCreate={vi.fn()} />)

    llenarFormulario()
    fireEvent.click(screen.getByRole('button', { name: 'Crear usuario' }))

    expect(await screen.findByText(/Ya existe un usuario con el email/)).toBeInTheDocument()
    // Lo escrito sigue ahí y el botón vuelve a estar disponible.
    expect(screen.getByPlaceholderText('Nombres y apellidos')).toHaveValue('Ana Zapata')
    expect(screen.getByRole('button', { name: 'Crear usuario' })).toBeEnabled()
  })

  it('no crea dos cuentas si se pulsa dos veces', async () => {
    let resolver: ((u: UsuarioResponse) => void) | undefined
    crearUsuario.mockImplementation(() => new Promise<UsuarioResponse>((r) => (resolver = r)))
    render(<NewUserModal onClose={vi.fn()} onCreate={vi.fn()} />)

    llenarFormulario()
    const boton = screen.getByRole('button', { name: 'Crear usuario' })
    fireEvent.click(boton)
    fireEvent.click(boton)

    expect(crearUsuario).toHaveBeenCalledTimes(1)
    resolver?.(CREADO)
    await waitFor(() => expect(screen.getByText('Usuario creado exitosamente')).toBeInTheDocument())
  })
})
