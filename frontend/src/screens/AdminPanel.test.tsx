import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminPanel from './AdminPanel'
import { PrefsProvider } from '@/prefs'
import { sesionAdministrador } from '@/test/dobles'
import type { SeguimientoResponse, UsuarioResponse } from '@/services/api/types'

/**
 * Pruebas del panel del Administrador.
 *
 * Desde el 06-11-2026 el panel es solo para supervisar a los supervisores: el
 * avance de cada uno y las cuentas y firmas con que trabajan. Estas pruebas
 * fijan eso, para que una vista de cifras generales o el catálogo de formatos
 * no vuelvan a colarse en la pantalla de la Subdirección.
 */

vi.mock('@/services/usuarioService', () => ({
  getUsuarios: vi.fn(),
  crearUsuario: vi.fn(),
  actualizarUsuario: vi.fn(),
  cambiarEstadoUsuario: vi.fn(),
  enviarCredenciales: vi.fn(),
}))
vi.mock('@/services/seguimientoService', () => ({
  getSeguimiento: vi.fn(),
}))
vi.mock('@/services/firmaService', () => ({
  getFirmas: vi.fn(),
  crearFirma: vi.fn(),
  cambiarEstadoFirma: vi.fn(),
  getMiFirma: vi.fn(),
}))

const ALEX: UsuarioResponse = {
  id: 7,
  nombre: 'Alex Fernando Zapata Ríos',
  email: 'afzapatar@sena.edu.co',
  telefono: '3001234567',
  rol: 'SUPERVISOR',
  activo: true,
  fechaCreacion: '2026-10-15T14:00:00Z',
}

function seguimientoCon(firmaVigente: boolean): SeguimientoResponse {
  return {
    supervisores: [
      {
        id: ALEX.id,
        nombre: ALEX.nombre,
        email: ALEX.email,
        activo: true,
        firmaVigente,
        contratosFinalizados: 0,
        contratos: [],
      },
    ],
    contratosSinSupervisor: [],
    generadoEn: '2026-11-06T20:00:00Z',
  }
}

function montar(props: Partial<React.ComponentProps<typeof AdminPanel>> = {}) {
  return render(
    <PrefsProvider>
      <AdminPanel
        vista="seguimiento"
        onCambiarVista={vi.fn()}
        usuario={sesionAdministrador()}
        onLogout={vi.fn()}
        onOpenSettings={vi.fn()}
        {...props}
      />
    </PrefsProvider>,
  )
}

describe('AdminPanel', () => {
  // Ver la nota de SupervisorPanel.test.tsx: el `restoreAllMocks` global obliga
  // a fijar los valores de retorno en cada prueba, no al declarar el mock.
  beforeEach(async () => {
    localStorage.clear()
    vi.mocked((await import('@/services/usuarioService')).getUsuarios).mockResolvedValue([ALEX])
    vi.mocked((await import('@/services/firmaService')).getFirmas).mockResolvedValue([])
    vi.mocked((await import('@/services/seguimientoService')).getSeguimiento).mockResolvedValue(seguimientoCon(true))
  })

  it('el avance de los supervisores encabeza el panel con el semáforo de los contratos', async () => {
    montar()

    expect(await screen.findByText('Cómo van los contratos activos')).toBeInTheDocument()
    expect(screen.getByText(ALEX.nombre)).toBeInTheDocument()
  })

  it('solo ofrece supervisar y gestionar cuentas: sin panel de cifras ni catálogo de formatos', async () => {
    montar()
    await screen.findByText(ALEX.nombre)

    expect(screen.getAllByText('Avance de los supervisores').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Usuarios').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Firmas electrónicas').length).toBeGreaterThan(0)
    expect(screen.queryByText('Panel de Control')).not.toBeInTheDocument()
    expect(screen.queryByText('Formatos documentales')).not.toBeInTheDocument()
  })

  /**
   * La vista activa viene de la URL (ADR-007). Si el panel dejara de respetar
   * esa prop, los enlaces profundos volverían a llevar siempre a la misma
   * pantalla — el defecto que ese ADR corrigió.
   */
  it('respeta la vista que le llega por props, no una interna', async () => {
    montar({ vista: 'usuarios' })

    expect(await screen.findByText('Gestión de Usuarios')).toBeInTheDocument()
    expect(screen.queryByText('Cómo van los contratos activos')).not.toBeInTheDocument()
  })

  it('pide al servidor las cuentas, las firmas y el avance al montar', async () => {
    const { getUsuarios } = await import('@/services/usuarioService')
    const { getFirmas } = await import('@/services/firmaService')
    const { getSeguimiento } = await import('@/services/seguimientoService')

    montar()

    await waitFor(() => {
      expect(getUsuarios).toHaveBeenCalled()
      expect(getFirmas).toHaveBeenCalled()
      expect(getSeguimiento).toHaveBeenCalled()
    })
  })

  /**
   * Un fallo del servidor no debe dejar la pantalla en blanco ni hacerse pasar
   * por una lista vacía: el administrador tiene que saber que no se consultó.
   */
  it('si no se pudieron consultar las cuentas, lo dice en vez de mostrar una tabla vacía', async () => {
    const { getUsuarios } = await import('@/services/usuarioService')
    vi.mocked(getUsuarios).mockRejectedValue(new Error('servidor caído'))

    montar({ vista: 'usuarios' })

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudieron consultar las cuentas')
  })

  describe('la firma del supervisor desde el avance', () => {
    it('al supervisor sin firma se le asigna desde su propia tarjeta', async () => {
      const { getSeguimiento } = await import('@/services/seguimientoService')
      const { crearFirma } = await import('@/services/firmaService')
      vi.mocked(getSeguimiento).mockResolvedValue(seguimientoCon(false))
      vi.mocked(crearFirma).mockResolvedValue({
        id: 1,
        usuarioId: ALEX.id,
        usuarioNombre: ALEX.nombre,
        usuarioEmail: ALEX.email,
        firmaId: 'FE-2026-0007',
        activa: true,
        asignadoPorId: 1,
        asignadoPorNombre: 'Administrador SICOT',
        fechaAsignacion: '2026-11-06T20:05:00Z',
      })

      montar()
      const tarjeta = await screen.findByRole('region', { name: `Supervisor ${ALEX.nombre}` })
      fireEvent.click(within(tarjeta).getByRole('button', { name: 'Asignar firma' }))

      // El diálogo abre con SU cuenta elegida, no con la primera de la lista.
      const dialogo = await screen.findByRole('dialog')
      expect(within(dialogo).getByRole('combobox')).toHaveValue(String(ALEX.id))
      fireEvent.click(within(dialogo).getByRole('button', { name: 'Asignar firma' }))

      await waitFor(() => expect(crearFirma).toHaveBeenCalledWith({ usuarioId: ALEX.id }))
    })

    it('no ofrece el botón a quien ya tiene firma vigente', async () => {
      montar()
      const tarjeta = await screen.findByRole('region', { name: `Supervisor ${ALEX.nombre}` })

      expect(within(tarjeta).queryByRole('button', { name: 'Asignar firma' })).not.toBeInTheDocument()
    })
  })
})
