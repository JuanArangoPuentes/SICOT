import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import SupervisorPanel from './SupervisorPanel'
import { PrefsProvider } from '@/prefs'
import { preguntarCopiloto } from '@/services/documentoService'
import type { ChatResponse } from '@/services/api/types'
import { contrato, sesionSupervisor } from '@/test/dobles'
import type { Step } from '@/types/domain'

/**
 * El chat del Copiloto en el panel del Supervisor cuando la petición se corta
 * a medias.
 *
 * Va aparte de SupervisorPanel.test.tsx porque aquí todo gira alrededor de las
 * preguntas al Copiloto: cada prueba decide cuándo responde el modelo y, en las
 * de corte, maneja a mano la visibilidad de la página, cosa que el resto de
 * pruebas del panel no necesita.
 *
 * El escenario de los cortes es el del APK de Android: una pregunta al Copiloto
 * sobre CPU tarda minutos, el supervisor cambia de aplicación y el sistema le
 * cierra la conexión a SICOT a los pocos segundos (ver services/segundoPlano.ts).
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
}))
vi.mock('@/services/firmaService', () => ({
  getMiFirma: vi.fn(),
}))
// La vista Contrato pide el cronograma al abrirse; sin simularlo, la petición
// sale a un backend que aquí no existe y ensucia la salida con un 401.
vi.mock('@/services/cronogramaService', () => ({
  getCronograma: vi.fn(() => new Promise(() => {})),
}))

function ponerVisibilidad(estado: 'visible' | 'hidden') {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => estado })
  document.dispatchEvent(new Event('visibilitychange'))
}

/** Una respuesta del Copiloto que la prueba resuelve o rechaza cuando quiere. */
function respuestaPendiente() {
  let resolver!: (r: ChatResponse) => void
  let rechazar!: (e: unknown) => void
  const promesa = new Promise<ChatResponse>((res, rej) => {
    resolver = res
    rechazar = rej
  })
  return { promesa, resolver, rechazar }
}

/** Lo que llega cuando Android cierra la conexión: no hay respuesta del servidor. */
const conexionCortada = () => new TypeError('Failed to fetch')

/** Un paso activo con un solo sub-paso, sin documento ni fotos: cerrarlo pide la revisión. */
function pasoConUnSubPaso(): Step[] {
  return [
    {
      id: 1,
      title: 'INICIO — Estudios y Suscripción',
      status: 'active',
      subSteps: [
        {
          id: '1.1',
          label: 'Revisar estudios previos',
          responsible: 'Supervisor',
          document: 'Ficha',
          completed: false,
        },
      ],
    },
  ]
}

async function montar() {
  render(
    <PrefsProvider>
      <SupervisorPanel
        vista="contrato"
        onCambiarVista={vi.fn()}
        steps={pasoConUnSubPaso()}
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
      />
    </PrefsProvider>,
  )
  await act(async () => {})
}

async function escribirAlCopiloto(texto: string) {
  fireEvent.change(screen.getByRole('textbox', { name: /mensaje para el copiloto/i }), { target: { value: texto } })
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /enviar al copiloto/i }))
  })
}

/** El supervisor se va a otra aplicación y, mientras está fuera, el sistema corta la petición. */
async function cortarAlSalir(respuesta: ReturnType<typeof respuestaPendiente>) {
  await act(async () => {
    ponerVisibilidad('hidden')
    respuesta.rechazar(conexionCortada())
  })
}

async function volverASicot() {
  await act(async () => ponerVisibilidad('visible'))
}

/** El chat mientras espera al modelo: «Pensando…» a la vista y nada con qué lanzar otra pregunta. */
function expectEsperandoAlCopiloto() {
  expect(screen.getByText(/pensando…/i)).toBeInTheDocument()
  expect(screen.getByRole('textbox', { name: /mensaje para el copiloto/i })).toBeDisabled()
  expect(screen.getByRole('button', { name: /enviar al copiloto/i })).toBeDisabled()
}

beforeEach(async () => {
  // Las pruebas cuentan cuántas veces se le preguntó al Copiloto. El
  // `vi.restoreAllMocks()` global de setup.ts no pone a cero ese contador en
  // los vi.fn() de un vi.mock, así que sin esto se arrastran las llamadas de
  // la prueba anterior.
  vi.mocked(preguntarCopiloto).mockReset()
  vi.mocked((await import('@/services/etapaService')).getEtapasContrato).mockResolvedValue([])
  vi.mocked((await import('@/services/alertaService')).getAlertasContrato).mockResolvedValue([])
  vi.mocked((await import('@/services/documentoService')).getDocumentosContrato).mockResolvedValue([])
  vi.mocked((await import('@/services/firmaService')).getMiFirma).mockResolvedValue({
    tieneFirmaActiva: true,
    firmaId: 'FIRMA-TEST',
  })
})

afterEach(() => ponerVisibilidad('visible'))

describe('SupervisorPanel — Copiloto con la conexión cortada', () => {
  /**
   * El reintento se lanzaba sin esperarlo: el `finally` del primer intento
   * apagaba «Pensando…» en cuanto arrancaba el segundo. Durante los minutos que
   * dura, la entrada y el botón de enviar volvían a estar activos, y una
   * segunda pregunta competía con el reintento por los dos cupos del
   * LimitadorDeUsoIa del backend.
   */
  it('mientras repite una pregunta cortada sigue «Pensando…» y no deja lanzar otra', async () => {
    const primera = respuestaPendiente()
    const reintento = respuestaPendiente()
    vi.mocked(preguntarCopiloto).mockReturnValueOnce(primera.promesa).mockReturnValueOnce(reintento.promesa)
    await montar()

    await escribirAlCopiloto('¿Qué sigue en este paso?')
    await cortarAlSalir(primera)
    expect(screen.getByText(/pasó a segundo plano/i)).toBeInTheDocument()

    await volverASicot()
    expect(preguntarCopiloto).toHaveBeenCalledTimes(2)
    expectEsperandoAlCopiloto()

    await act(async () => reintento.resolver({ respuesta: 'Siga con la revisión de los estudios.' }))
    expect(screen.getByText('Siga con la revisión de los estudios.')).toBeInTheDocument()
    expect(screen.queryByText(/pensando…/i)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /enviar al copiloto/i })).toBeEnabled()
  })

  /**
   * Si el reintento también se corta porque el supervisor volvió a salir, el
   * motivo sigue sin ser Ollama: decir «verifique que Ollama esté disponible»
   * lo manda a buscar un fallo que no existe.
   */
  it('si el reintento también se corta, no culpa a Ollama', async () => {
    const primera = respuestaPendiente()
    const reintento = respuestaPendiente()
    vi.mocked(preguntarCopiloto).mockReturnValueOnce(primera.promesa).mockReturnValueOnce(reintento.promesa)
    await montar()

    await escribirAlCopiloto('¿Qué sigue en este paso?')
    await cortarAlSalir(primera)
    await volverASicot()
    await cortarAlSalir(reintento)

    expect(preguntarCopiloto).toHaveBeenCalledTimes(2)
    expect(screen.queryByText(/ollama/i)).not.toBeInTheDocument()
    expect(screen.getByText(/no pude responder: .*segundo plano/i)).toBeInTheDocument()
  })

  /**
   * La revisión del paso no vigilaba el segundo plano: un corte del teléfono
   * salía como «No se pudo conectar con el Copiloto IA (Ollama)» —el mensaje
   * falso que segundoPlano.ts se escribió para evitar— y el `finally` dejaba
   * el paso listo para confirmar sin que nadie lo hubiera revisado.
   */
  it('si la revisión del paso se corta, no culpa a Ollama y la repite antes de dejar confirmar', async () => {
    const primera = respuestaPendiente()
    const reintento = respuestaPendiente()
    vi.mocked(preguntarCopiloto).mockReturnValueOnce(primera.promesa).mockReturnValueOnce(reintento.promesa)
    await montar()

    fireEvent.click(screen.getByRole('button', { name: /iniciar paso 1/i }))
    fireEvent.click(screen.getByRole('button', { name: /marcar completado/i }))
    await escribirAlCopiloto('Revisé los estudios previos y están completos.')
    await cortarAlSalir(primera)

    expect(screen.queryByText(/ollama/i)).not.toBeInTheDocument()
    expect(screen.getByText(/pasó a segundo plano/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /confirmar paso 1/i })).not.toBeInTheDocument()

    await volverASicot()
    expect(preguntarCopiloto).toHaveBeenCalledTimes(2)
    // Se repite la misma revisión, no una pregunta suelta con la descripción.
    expect(vi.mocked(preguntarCopiloto).mock.calls[1][1]).toBe(vi.mocked(preguntarCopiloto).mock.calls[0][1])
    expectEsperandoAlCopiloto()
    expect(screen.queryByRole('button', { name: /confirmar paso 1/i })).not.toBeInTheDocument()

    await act(async () => reintento.resolver({ respuesta: 'Parece completo; puede cerrar el paso.' }))
    expect(screen.getByText('Parece completo; puede cerrar el paso.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /confirmar paso 1/i })).toBeInTheDocument()
  })
})
