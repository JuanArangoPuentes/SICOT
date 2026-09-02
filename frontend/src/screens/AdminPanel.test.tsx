import { render, screen, waitFor } from "@testing-library/react"
import { beforeEach, describe, expect, it, vi } from "vitest"
import AdminPanel from "./AdminPanel"
import { PrefsProvider } from "@/prefs"
import { sesionAdministrador } from "@/test/dobles"

/**
 * Pruebas del panel de administración, después de partirlo de 913 a 370 líneas.
 *
 * La descomposición se había validado solo con el chequeo de tipos y clics
 * manuales: TypeScript confirma que las piezas encajan, no que sigan mostrando
 * lo mismo. Estas pruebas fijan lo que la pantalla debe seguir haciendo para
 * que una segunda ronda de refactor no la vacíe en silencio.
 */

vi.mock("@/services/usuarioService", () => ({
  getUsuarios: vi.fn(),
  crearUsuario: vi.fn(),
  actualizarUsuario: vi.fn(),
  cambiarEstadoUsuario: vi.fn(),
  enviarCredenciales: vi.fn(),
}))
vi.mock("@/services/formatoService", () => ({
  getFormatos: vi.fn(),
  subirFormato: vi.fn(),
  eliminarFormato: vi.fn(),
  descargarFormato: vi.fn(),
}))
vi.mock("@/services/firmaService", () => ({
  getFirmas: vi.fn(),
  crearFirma: vi.fn(),
  cambiarEstadoFirma: vi.fn(),
  getMiFirma: vi.fn(),
}))

function montar(
  props: Partial<React.ComponentProps<typeof AdminPanel>> = {},
) {
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

describe("AdminPanel", () => {
  // Ver la nota de SupervisorPanel.test.tsx: el `restoreAllMocks` global obliga
  // a fijar los valores de retorno en cada prueba, no al declarar el mock.
  beforeEach(async () => {
    localStorage.clear()
    vi.mocked((await import("@/services/usuarioService")).getUsuarios).mockResolvedValue([])
    vi.mocked((await import("@/services/formatoService")).getFormatos).mockResolvedValue([])
    vi.mocked((await import("@/services/firmaService")).getFirmas).mockResolvedValue([])
  })

  it("muestra la vista de usuarios cuando la URL la selecciona", async () => {
    montar({ vista: "usuarios" })

    await waitFor(() =>
      expect(screen.getAllByText(/usuarios/i).length).toBeGreaterThan(0),
    )
  })

  /**
   * La vista activa viene de la URL (ADR-007). Si el panel dejara de respetar
   * esa prop, los enlaces profundos volverían a llevar siempre a la misma
   * pantalla — el defecto que ese ADR corrigió.
   */
  it("respeta la vista que le llega por props, no una interna", async () => {
    montar({ vista: "documentos" })

    await waitFor(() =>
      expect(screen.getAllByText(/documentos/i).length).toBeGreaterThan(0),
    )
  })

  it("pide al backend los datos de las tres secciones al montar", async () => {
    const { getUsuarios } = await import("@/services/usuarioService")
    const { getFormatos } = await import("@/services/formatoService")
    const { getFirmas } = await import("@/services/firmaService")

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
  it("sobrevive a que el backend falle al cargar", async () => {
    const { getUsuarios } = await import("@/services/usuarioService")
    vi.mocked(getUsuarios).mockRejectedValue(new Error("backend caído"))

    montar()

    await waitFor(() =>
      expect(screen.getAllByText(/usuarios/i).length).toBeGreaterThan(0),
    )
  })
})
