import { act, fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SupervisorPanel from './SupervisorPanel'
import { PrefsProvider } from '@/prefs'
import { getEtapasContrato } from '@/services/etapaService'
import { getAlertasContrato } from '@/services/alertaService'
import { getDocumentosContrato } from '@/services/documentoService'
import { getMiFirma } from '@/services/firmaService'
import { alerta, contrato, sesionSupervisor } from '@/test/dobles'
import type { TipoAlerta } from '@/services/api/types'
import type { Step } from '@/types/domain'

/**
 * A dónde llevan los atajos que llegan desde fuera de la vista abierta.
 *
 * Va aparte de SupervisorPanel.test.tsx porque aquí no importa qué pinta cada
 * vista, sino que el botón que promete llevar a un sitio lleve a ese sitio.
 */

vi.mock('@/services/etapaService', () => ({
  getEtapasContrato: vi.fn(),
  cambiarEstadoSubetapa: vi.fn(),
}))
vi.mock('@/services/alertaService', () => ({
  getAlertasContrato: vi.fn(),
  marcarAlertaLeida: vi.fn(),
}))
vi.mock('@/services/documentoService', () => ({
  getDocumentosContrato: vi.fn(),
  generarDocumento: vi.fn(),
  firmarDocumento: vi.fn(),
  precalentarCopiloto: vi.fn(),
  preguntarCopiloto: vi.fn(),
  verificarIntegridad: vi.fn(),
  descargarDocumento: vi.fn(),
  getPlantillasDocumento: vi.fn(),
}))
vi.mock('@/services/firmaService', () => ({
  getMiFirma: vi.fn(),
}))
// Sin cronograma: así la única alerta en pantalla es la que pone cada prueba.
vi.mock('@/services/cronogramaService', () => ({
  getCronograma: vi.fn(() => new Promise(() => {})),
}))

/** El contrato va por el paso 2. */
function pasos(): Step[] {
  const sub = (id: string, completed: boolean) => ({
    id,
    label: `Sub-paso ${id}`,
    responsible: 'Supervisor',
    document: 'Ficha',
    completed,
  })
  return [
    { id: 1, title: 'INICIO — Estudios y Suscripción', status: 'completed', subSteps: [sub('1.1', true)] },
    { id: 2, title: 'INICIO — Acta de Inicio (GCCON-F-018)', status: 'active', subSteps: [sub('2.1', false)] },
    { id: 3, title: 'INSPECCIÓN — Monitoreo y Ejecución', status: 'pending', subSteps: [sub('3.1', false)] },
  ]
}

function panel(props: Partial<React.ComponentProps<typeof SupervisorPanel>>) {
  return (
    <PrefsProvider>
      <SupervisorPanel
        vista="contrato"
        onCambiarVista={vi.fn()}
        steps={pasos()}
        setSteps={vi.fn()}
        usuario={sesionSupervisor()}
        contrato={contrato()}
        cargandoContrato={false}
        errorContrato={false}
        onLogout={vi.fn()}
        onOpenSettings={vi.fn()}
        onStartTour={vi.fn()}
        registros={[]}
        onRefreshRegistros={vi.fn().mockResolvedValue(undefined)}
        {...props}
      />
    </PrefsProvider>
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(getEtapasContrato).mockResolvedValue([])
  vi.mocked(getAlertasContrato).mockResolvedValue([])
  vi.mocked(getDocumentosContrato).mockResolvedValue([])
  vi.mocked(getMiFirma).mockResolvedValue({ tieneFirmaActiva: true, firmaId: 'FIRMA-TEST' })
})

describe('el avatar flotante', () => {
  it('vuelve a abrir el Copiloto aunque el supervisor lo haya plegado', async () => {
    const { rerender } = render(panel({ pedidosCopiloto: 0 }))
    await act(async () => {})

    fireEvent.click(screen.getByRole('button', { name: /ocultar copiloto/i }))
    expect(screen.getByRole('button', { name: /mostrar copiloto/i })).toBeInTheDocument()

    rerender(panel({ pedidosCopiloto: 1 }))

    expect(screen.getByRole('button', { name: /ocultar copiloto/i })).toBeInTheDocument()
  })
})

describe('el botón de una alerta', () => {
  async function conAlerta(tipo: TipoAlerta, vista: 'alertas' | 'bandeja') {
    vi.mocked(getAlertasContrato).mockResolvedValue([alerta({ tipo })])
    const onCambiarVista = vi.fn()
    render(panel({ vista, onCambiarVista }))
    await act(async () => {})
    return onCambiarVista
  }

  it('el cronograma atrasado lleva al paso en curso, no al Cierre', async () => {
    const onCambiarVista = await conAlerta('CRONOGRAMA', 'alertas')

    fireEvent.click(screen.getByRole('button', { name: /ir al paso 2/i }))

    expect(onCambiarVista).toHaveBeenCalledWith('contrato')
  })

  it('desde la bandeja, la asignación de supervisión lleva al paso en curso', async () => {
    const onCambiarVista = await conAlerta('SOLICITUD', 'bandeja')

    fireEvent.click(screen.getByRole('button', { name: /ir al paso 2/i }))

    expect(onCambiarVista).toHaveBeenCalledWith('contrato')
  })

  it('la integridad de un documento lleva a Documentos', async () => {
    const onCambiarVista = await conAlerta('DOCUMENTO', 'alertas')

    fireEvent.click(screen.getByRole('button', { name: /ver los documentos/i }))

    expect(onCambiarVista).toHaveBeenCalledWith('documentos')
  })

  it('una alerta sin sitio relacionado se muestra sin botón', async () => {
    await conAlerta('FACTURA', 'alertas')

    expect(screen.getByText('El paso en curso está atrasado.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /ir al paso|ver los documentos/i })).not.toBeInTheDocument()
  })
})
