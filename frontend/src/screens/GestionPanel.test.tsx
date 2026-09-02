import { render, screen, waitFor } from "@testing-library/react"
import { beforeEach, describe, expect, it, vi } from "vitest"
import GestionPanel from "./GestionPanel"
import { PrefsProvider } from "@/prefs"
import { contrato, sesionGestion } from "@/test/dobles"

/**
 * Pruebas del panel de Gestión Contractual.
 *
 * Esta pantalla es la que carga la ficha de un contrato con ayuda del Copiloto,
 * y por eso concentra el riesgo de "dato inventado": lo que la IA extrae de un
 * PDF es una PROPUESTA que una persona confirma, nunca un hecho. Lo que se fija
 * aquí es que el panel siga presentando su información real y no se caiga
 * cuando el backend o la IA fallan — los dos casos donde sería más tentador
 * mostrar algo aproximado.
 */

vi.mock("@/services/contratoService", () => ({
  getContratos: vi.fn(),
  crearContrato: vi.fn(),
}))
vi.mock("@/services/usuarioService", () => ({
  getUsuarios: vi.fn(),
  crearUsuario: vi.fn(),
  actualizarUsuario: vi.fn(),
  cambiarEstadoUsuario: vi.fn(),
  enviarCredenciales: vi.fn(),
}))
vi.mock("@/services/documentoService", () => ({
  extraerDatosContrato: vi.fn(),
  subirDocumento: vi.fn(),
}))

function montar() {
  return render(
    <PrefsProvider>
      <GestionPanel
        usuario={sesionGestion()}
        onLogout={vi.fn()}
        onOpenSettings={vi.fn()}
        onStartTour={vi.fn()}
      />
    </PrefsProvider>,
  )
}

describe("GestionPanel", () => {
  // Ver la nota de SupervisorPanel.test.tsx sobre el `restoreAllMocks` global.
  beforeEach(async () => {
    localStorage.clear()
    vi.mocked((await import("@/services/contratoService")).getContratos).mockResolvedValue([])
    vi.mocked((await import("@/services/usuarioService")).getUsuarios).mockResolvedValue([])
  })

  it("consulta los contratos reales al montar", async () => {
    const { getContratos } = await import("@/services/contratoService")

    montar()

    await waitFor(() => expect(getContratos).toHaveBeenCalled())
  })

  it("muestra el número de un contrato existente", async () => {
    const { getContratos } = await import("@/services/contratoService")
    vi.mocked(getContratos).mockResolvedValue([contrato()])

    montar()

    expect(await screen.findAllByText(/CTMA-2026-0184/)).not.toHaveLength(0)
  })

  /**
   * Si el backend no responde, la pantalla no puede quedar en blanco ni
   * romperse: quien gestiona contratos debe poder seguir viendo la interfaz y
   * reintentar.
   */
  it("sobrevive a un fallo del backend sin romper el render", async () => {
    const { getContratos } = await import("@/services/contratoService")
    vi.mocked(getContratos).mockRejectedValue(new Error("backend caído"))

    montar()

    await waitFor(() =>
      expect(screen.getAllByText(/contrato/i).length).toBeGreaterThan(0),
    )
  })

  /**
   * Sin contratos cargados, la tabla no debe inventar filas de ejemplo. Es la
   * regla de "no simular" del proyecto aplicada a esta pantalla.
   */
  it("sin contratos no muestra ningún número de contrato inventado", async () => {
    montar()

    await waitFor(() =>
      expect(screen.getAllByText(/contrato/i).length).toBeGreaterThan(0),
    )
    expect(screen.queryByText(/CTMA-2026-0184/)).not.toBeInTheDocument()
  })
})
