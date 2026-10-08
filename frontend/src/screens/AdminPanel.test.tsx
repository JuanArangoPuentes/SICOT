import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminPanel from './AdminPanel'
import { PrefsProvider } from '@/prefs'
import { sesionAdministrador } from '@/test/dobles'
import { ApiError } from '@/services/api/client'
import type { FormatoDocumentalResponse } from '@/services/api/types'

/**
 * Pruebas del panel de administración, después de partirlo de 913 a 370 líneas.
 *
 * La descomposición se había validado solo con el chequeo de tipos y clics
 * manuales: TypeScript confirma que las piezas encajan, no que sigan mostrando
 * lo mismo. Estas pruebas fijan lo que la pantalla debe seguir haciendo para
 * que una segunda ronda de refactor no la vacíe en silencio.
 */

vi.mock('@/services/usuarioService', () => ({
  getUsuarios: vi.fn(),
  crearUsuario: vi.fn(),
  actualizarUsuario: vi.fn(),
  cambiarEstadoUsuario: vi.fn(),
  enviarCredenciales: vi.fn(),
}))
vi.mock('@/services/formatoService', () => ({
  getFormatos: vi.fn(),
  subirFormato: vi.fn(),
  eliminarFormato: vi.fn(),
  descargarFormato: vi.fn(),
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

function montar(props: Partial<React.ComponentProps<typeof AdminPanel>> = {}) {
  return render(
    <PrefsProvider>
      <AdminPanel
        vista="usuarios"
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
    vi.mocked((await import('@/services/usuarioService')).getUsuarios).mockResolvedValue([])
    vi.mocked((await import('@/services/formatoService')).getFormatos).mockResolvedValue([])
    vi.mocked((await import('@/services/firmaService')).getFirmas).mockResolvedValue([])
    vi.mocked((await import('@/services/seguimientoService')).getSeguimiento).mockResolvedValue({
      supervisores: [],
      contratosSinSupervisor: [],
      generadoEn: '2026-09-24T20:00:00Z',
    })
  })

  it('muestra la vista de usuarios cuando la URL la selecciona', async () => {
    montar({ vista: 'usuarios' })

    await waitFor(() => expect(screen.getAllByText(/usuarios/i).length).toBeGreaterThan(0))
  })

  /**
   * La vista activa viene de la URL (ADR-007). Si el panel dejara de respetar
   * esa prop, los enlaces profundos volverían a llevar siempre a la misma
   * pantalla — el defecto que ese ADR corrigió.
   */
  it('respeta la vista que le llega por props, no una interna', async () => {
    montar({ vista: 'documentos' })

    await waitFor(() => expect(screen.getAllByText(/documentos/i).length).toBeGreaterThan(0))
  })

  it('pide al backend los datos de las tres secciones al montar', async () => {
    const { getUsuarios } = await import('@/services/usuarioService')
    const { getFormatos } = await import('@/services/formatoService')
    const { getFirmas } = await import('@/services/firmaService')

    montar()

    await waitFor(() => {
      expect(getUsuarios).toHaveBeenCalled()
      expect(getFormatos).toHaveBeenCalled()
      expect(getFirmas).toHaveBeenCalled()
    })
  })

  /**
   * Un fallo del backend no debe dejar la pantalla en blanco ni romper el
   * render: el administrador tiene que poder seguir navegando.
   */
  it('sobrevive a que el backend falle al cargar', async () => {
    const { getUsuarios } = await import('@/services/usuarioService')
    vi.mocked(getUsuarios).mockRejectedValue(new Error('backend caído'))

    montar()

    await waitFor(() => expect(screen.getAllByText(/usuarios/i).length).toBeGreaterThan(0))
  })

  describe('descargar un formato', () => {
    const formato: FormatoDocumentalResponse = {
      id: 4,
      codigo: 'GCCON-F-018',
      nombre: 'Acta de Inicio',
      version: '03',
      tipoArchivo: 'DOCX',
      nombreArchivo: 'GCCON-F-018.docx',
      tamanioBytes: 20480,
      estado: 'VIGENTE',
      subidoPorNombre: null,
      fechaActualizacion: '2026-09-01T08:00:00Z',
    }

    async function descargar(resultado: () => Promise<unknown>) {
      const servicio = await import('@/services/formatoService')
      vi.mocked(servicio.getFormatos).mockResolvedValue([formato])
      vi.mocked(servicio.descargarFormato).mockImplementation(resultado as typeof servicio.descargarFormato)
      montar({ vista: 'documentos' })
      fireEvent.click(await screen.findByRole('button', { name: /descargar/i }))
    }

    // Antes: `.catch(() => {})`. Con la sesión caducada o el formato borrado,
    // el botón no hacía nada visible.
    it('si falla, dice por qué', async () => {
      await descargar(() => Promise.reject(new ApiError(404, 'El formato ya no existe.')))

      expect(await screen.findByRole('alert')).toHaveTextContent('El formato ya no existe.')
    })

    // En el APK el «Guardar como» se cierra sin más señal.
    it('en el teléfono confirma que quedó guardado', async () => {
      await descargar(() => Promise.resolve('guardado'))

      expect(await screen.findByRole('status')).toHaveTextContent('«GCCON-F-018.docx» quedó guardado en el teléfono.')
    })
  })
})
