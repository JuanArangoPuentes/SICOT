import { render, screen } from "@testing-library/react"
import { beforeEach, describe, expect, it, vi } from "vitest"
import SupervisorPanel from "./SupervisorPanel"
import { PrefsProvider } from "@/prefs"
import { contrato, sesionSupervisor } from "@/test/dobles"

/**
 * Pruebas de la pantalla que ve un supervisor todos los días.
 *
 * Lo que se comprueba aquí no es que "renderice sin romperse", sino la regla
 * más importante de este panel: <b>no puede afirmar nada que no sepa</b>. Un
 * supervisor que lee "no tiene contrato asignado" cierra la pantalla y se va;
 * si eso se muestra cuando en realidad falló la consulta, el sistema le mintió
 * sobre su trabajo pendiente. Los tres estados —cargando, error, vacío— tienen
 * que ser distinguibles.
 */

// El panel llama al backend en sus efectos. Se interceptan los servicios para
// que la prueba controle el escenario y no dependa de que haya un servidor.
vi.mock("@/services/etapaService", () => ({
  getEtapasContrato: vi.fn(),
  cambiarEstadoSubetapa: vi.fn(),
}))
vi.mock("@/services/alertaService", () => ({
  getAlertasContrato: vi.fn(),
  marcarAlertaLeida: vi.fn(),
}))
vi.mock("@/services/documentoService", () => ({
  getDocumentosContrato: vi.fn(),
  generarDocumento: vi.fn(),
  firmarDocumento: vi.fn(),
  preguntarCopiloto: vi.fn(),
  verificarIntegridad: vi.fn(),
  descargarDocumento: vi.fn(),
}))
vi.mock("@/services/firmaService", () => ({
  getMiFirma: vi.fn(),
}))

function montar(props: Partial<React.ComponentProps<typeof SupervisorPanel>> = {}) {
  return render(
    <PrefsProvider>
      <SupervisorPanel
        vista="bandeja"
        onCambiarVista={vi.fn()}
        steps={[]}
        setSteps={vi.fn()}
        usuario={sesionSupervisor()}
        contrato={null}
        cargandoContrato={false}
        errorContrato={false}
        onLogout={vi.fn()}
        onOpenSettings={vi.fn()}
        onStartTour={vi.fn()}
        registros={[]}
        onRefreshRegistros={vi.fn().mockResolvedValue(undefined)}
        {...props}
      />
    </PrefsProvider>,
  )
}

describe("SupervisorPanel", () => {
  // Los valores de retorno se restablecen en CADA prueba, no una sola vez al
  // definir el mock: `setup.ts` llama a `vi.restoreAllMocks()` en su `afterEach`
  // global, que limpia las implementaciones. Sin esto, solo la primera prueba
  // del archivo encuentra los servicios simulados y el resto recibe `undefined`
  // — un fallo que parece del componente y en realidad es del andamiaje.
  beforeEach(async () => {
    localStorage.clear()
    vi.mocked((await import("@/services/etapaService")).getEtapasContrato).mockResolvedValue([])
    vi.mocked((await import("@/services/alertaService")).getAlertasContrato).mockResolvedValue([])
    vi.mocked((await import("@/services/documentoService")).getDocumentosContrato).mockResolvedValue([])
    vi.mocked((await import("@/services/firmaService")).getMiFirma).mockResolvedValue({
      tieneFirmaActiva: true,
      firmaId: "FIRMA-TEST",
    })
  })

  it("mientras consulta el contrato NO afirma que no hay ninguno", () => {
    montar({ cargandoContrato: true })

    expect(screen.getByText(/consultando su contrato/i)).toBeInTheDocument()
    expect(screen.queryByText(/no tiene un contrato asignado/i)).not.toBeInTheDocument()
  })

  /**
   * El error más grave posible de esta pantalla: decirle a alguien que no tiene
   * trabajo pendiente cuando lo que ocurrió es que el backend no respondió.
   */
  it("si la consulta falla lo dice, en vez de fingir que no hay contrato", () => {
    montar({ errorContrato: true })

    expect(screen.getByText(/no se pudo cargar su contrato/i)).toBeInTheDocument()
    expect(screen.queryByText(/no tiene un contrato asignado/i)).not.toBeInTheDocument()
    expect(screen.getByRole("button", { name: /reintentar/i })).toBeInTheDocument()
  })

  it("sin contrato y sin error sí muestra el estado vacío", () => {
    montar()

    // Por rol y no por texto suelto: el mensaje aparece como encabezado y
    // repetido en el cuerpo, y consultar por texto encontraría los dos.
    expect(
      screen.getByRole("heading", { name: /no tiene un contrato asignado/i }),
    ).toBeInTheDocument()
  })

  it("con un contrato asignado muestra su número", async () => {
    montar({ contrato: contrato() })

    expect(await screen.findAllByText(/CTMA-2026-0184/)).not.toHaveLength(0)
  })

  /**
   * Las secciones del menú se muestran siempre, incluso sin contrato: si al no
   * haberlo el menú se redujera a una entrada, parecería que el sistema perdió
   * funcionalidad. Quedan inactivas, no ocultas.
   */
  it("mantiene visibles todas las secciones aunque no haya contrato", () => {
    montar()

    for (const seccion of [/contrato/i, /alertas/i, /documentos/i, /registros/i]) {
      expect(screen.getAllByText(seccion).length).toBeGreaterThan(0)
    }
  })
})
