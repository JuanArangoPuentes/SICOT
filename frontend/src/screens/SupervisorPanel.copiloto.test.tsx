import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import SupervisorPanel from './SupervisorPanel'
import { PrefsProvider } from '@/prefs'
import { firmarDocumento, generarDocumento, preguntarCopiloto } from '@/services/documentoService'
import { historialParaElCopiloto } from '@/services/historialCopiloto'
import type { ChatResponse } from '@/services/api/types'
import { contrato, sesionSupervisor } from '@/test/dobles'
import type { Step } from '@/types/domain'

/**
 * El chat del Copiloto en el panel del Supervisor: qué pasa cuando la petición
 * se corta a medias y qué parte de la conversación le llega al modelo.
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
  const onCambiarVista = vi.fn()
  const setSteps = vi.fn()
  render(
    <PrefsProvider>
      <SupervisorPanel
        vista="contrato"
        onCambiarVista={onCambiarVista}
        steps={pasoConUnSubPaso()}
        setSteps={setSteps}
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
  return { onCambiarVista, setSteps }
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
   * El reintento repetía la pregunta sin nada que la identificara, y el
   * servidor lanzaba otra inferencia mientras la del primer intento seguía
   * ocupando la CPU y un cupo del limitador de IA.
   */
  it('el reintento lleva el mismo idSolicitud que la pregunta cortada, y otra pregunta lleva otro', async () => {
    const primera = respuestaPendiente()
    const reintento = respuestaPendiente()
    vi.mocked(preguntarCopiloto)
      .mockReturnValueOnce(primera.promesa)
      .mockReturnValueOnce(reintento.promesa)
      .mockResolvedValueOnce({ respuesta: 'Después, el cronograma.' })
    await montar()

    await escribirAlCopiloto('¿Qué sigue en este paso?')
    await cortarAlSalir(primera)
    await volverASicot()
    await act(async () => reintento.resolver({ respuesta: 'Siga con la revisión de los estudios.' }))
    await escribirAlCopiloto('¿Y después?')

    const [cortada, repetida, otra] = vi.mocked(preguntarCopiloto).mock.calls.map((c) => c[3]?.idSolicitud)
    expect(cortada).toBeTruthy()
    expect(repetida).toBe(cortada)
    expect(otra).toBeTruthy()
    expect(otra).not.toBe(cortada)
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
   * falso que segundoPlano.ts se escribió para evitar— y el `finally` daba
   * el paso por revisado sin que nadie lo hubiera revisado. Confirmar sin
   * esperar sí se puede (la revisión es consultiva), y el botón lo dice.
   */
  it('si la revisión del paso se corta, no culpa a Ollama y la repite antes de darla por hecha', async () => {
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
    expect(screen.getByRole('button', { name: /confirmar paso 1 .*sin esperar la revisión/i })).toBeInTheDocument()

    await volverASicot()
    expect(preguntarCopiloto).toHaveBeenCalledTimes(2)
    // Se repite la misma revisión, con el mismo idSolicitud, y no una pregunta
    // suelta con la descripción.
    const [cortada, repetida] = vi.mocked(preguntarCopiloto).mock.calls
    expect(repetida).toEqual(cortada)
    expect(repetida[3]).toMatchObject({ revisarPaso: 1, idSolicitud: expect.any(String) })
    expectEsperandoAlCopiloto()
    expect(screen.getByRole('button', { name: /confirmar paso 1 .*sin esperar la revisión/i })).toBeInTheDocument()

    await act(async () => reintento.resolver({ respuesta: 'Parece completo; puede cerrar el paso.' }))
    expect(screen.getByText('Parece completo; puede cerrar el paso.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirmar Paso 1 como completado' })).toBeInTheDocument()
  })
})

describe('SupervisorPanel — lo que le llega al Copiloto', () => {
  /**
   * El historial se filtra por el origen de cada mensaje: lo que no venga
   * marcado como respuesta del modelo o como guía se queda fuera. Si el panel
   * dejara de marcarlos, el Copiloto perdería la memoria sin que nada fallara a
   * la vista; esta prueba es la que lo notaría.
   */
  it('recibe la guía del sub-paso y la pregunta anterior con su respuesta, no la bienvenida', async () => {
    vi.mocked(preguntarCopiloto)
      .mockResolvedValueOnce({ respuesta: 'Los estudios previos los elabora el área requirente.' })
      .mockResolvedValueOnce({ respuesta: 'Sí, revíselos antes de marcar el sub-paso.' })
    await montar()

    fireEvent.click(screen.getByRole('button', { name: /iniciar paso 1/i }))
    await escribirAlCopiloto('¿Quién hace los estudios previos?')
    await escribirAlCopiloto('¿Los tengo que revisar yo?')

    const historial = historialParaElCopiloto(vi.mocked(preguntarCopiloto).mock.calls[1][2] ?? [])
    expect(historial.map((m) => m.text)).toEqual([
      expect.stringMatching(/^Sub-paso 1\.1 — Revisar estudios previos/),
      '¿Quién hace los estudios previos?',
      'Los estudios previos los elabora el área requirente.',
    ])
  })
})

describe('SupervisorPanel — las acciones que ofrece el Copiloto', () => {
  /**
   * Firmar es un acto legal: que el Copiloto entienda «firma el documento» no
   * puede firmar, generar ni abrir nada por sí solo. La respuesta trae a dónde
   * llevar; el supervisor decide pulsando el botón, y lo que abre es el
   * sub-paso, donde la firma sigue pasando por «Firmar documento».
   */
  it('recibir una acción no navega ni genera nada; pulsarla abre el sub-paso y nada más', async () => {
    vi.mocked(preguntarCopiloto).mockResolvedValueOnce({
      respuesta: 'Los estudios previos se revisan en el sub-paso 1.1. Le abro ese sub-paso.',
      fuente: 'SISTEMA',
      accion: {
        tipo: 'ABRIR_DOCUMENTO',
        paso: 1,
        subpaso: '1.1',
        documentoTipo: null,
        documentoId: null,
        etiqueta: 'Abrir el sub-paso 1.1',
      },
    })
    const { onCambiarVista } = await montar()

    await escribirAlCopiloto('firma el documento')
    expect(screen.getByText(/le abro ese sub-paso/i)).toBeInTheDocument()
    expect(onCambiarVista).not.toHaveBeenCalled()
    expect(screen.queryByRole('button', { name: /marcar completado/i })).not.toBeInTheDocument()

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /abrir el sub-paso 1\.1/i }))
    })
    expect(onCambiarVista).toHaveBeenCalledExactlyOnceWith('contrato')
    expect(screen.getByRole('button', { name: /marcar completado/i })).toBeInTheDocument()
    expect(generarDocumento).not.toHaveBeenCalled()
    expect(firmarDocumento).not.toHaveBeenCalled()
  })

  it('«ver alertas» lleva a la pestaña de alertas solo al pulsar el botón', async () => {
    vi.mocked(preguntarCopiloto).mockResolvedValueOnce({
      respuesta: 'Este contrato tiene una alerta de cronograma.',
      fuente: 'SISTEMA',
      accion: {
        tipo: 'MOSTRAR_ALERTAS',
        paso: null,
        subpaso: null,
        documentoTipo: null,
        documentoId: null,
        etiqueta: 'Ver las alertas',
      },
    })
    const { onCambiarVista } = await montar()

    await escribirAlCopiloto('muéstrame las alertas')
    expect(onCambiarVista).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: /ver las alertas/i }))
    expect(onCambiarVista).toHaveBeenCalledExactlyOnceWith('alertas')
  })

  /** Lo que armó el servidor sin modelo no le vuelve al modelo en la pregunta siguiente. */
  it('no le reenvía al modelo una respuesta del sistema ni la pregunta que la pidió', async () => {
    vi.mocked(preguntarCopiloto)
      .mockResolvedValueOnce({ respuesta: 'Acta de Inicio (GCCON-F-018): sub-paso 2.7…', fuente: 'SISTEMA' })
      .mockResolvedValueOnce({ respuesta: 'Sí, cuando el contratista la firme.', fuente: 'MODELO' })
    await montar()

    await escribirAlCopiloto('¿Quién firma el acta de inicio?')
    expect(screen.getByText('Respuesta del sistema')).toBeInTheDocument()
    await escribirAlCopiloto('¿Hay que esperar al contratista?')

    const historial = historialParaElCopiloto(vi.mocked(preguntarCopiloto).mock.calls[1][2] ?? [])
    expect(historial.map((m) => m.text)).toEqual([])
  })
})

describe('SupervisorPanel — la revisión del paso es consultiva', () => {
  /** Abre la revisión del Paso 1: el supervisor pulsa el botón de su último sub-paso. */
  async function pedirCerrarElPaso() {
    fireEvent.click(screen.getByRole('button', { name: /iniciar paso 1/i }))
    fireEvent.click(screen.getByRole('button', { name: /marcar completado/i }))
    expect(screen.getByText(/antes de marcar el paso 1 como completado/i)).toBeInTheDocument()
  }

  /**
   * Las instrucciones de la revisión viajaban desde aquí dentro de la
   * pregunta, y el servidor las metía en el bloque de contenido no confiable
   * con la orden de no seguirlas. Ahora solo va lo que describió el
   * supervisor, el número del paso y nada del historial.
   */
  it('manda solo la descripción y el paso a revisar', async () => {
    vi.mocked(preguntarCopiloto).mockResolvedValueOnce({ respuesta: 'Parece completo.', fuente: 'MODELO' })
    await montar()
    await pedirCerrarElPaso()

    await escribirAlCopiloto('Revisé los estudios previos y están completos.')

    expect(preguntarCopiloto).toHaveBeenCalledExactlyOnceWith(
      expect.any(Number),
      'Revisé los estudios previos y están completos.',
      undefined,
      { idSolicitud: expect.any(String), revisarPaso: 1 },
    )
  })

  /**
   * «cancelar» se tomaba como la descripción del paso: iba a una revisión de
   * minutos y después se ofrecía firmar con «cancelar» como notas.
   */
  it('«cancelar» sale de la revisión sin preguntarle nada al Copiloto', async () => {
    await montar()
    await pedirCerrarElPaso()

    await escribirAlCopiloto('cancelar')

    expect(preguntarCopiloto).not.toHaveBeenCalled()
    expect(screen.getByText(/el paso 1 sigue abierto/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /confirmar paso 1/i })).not.toBeInTheDocument()
  })

  it('una pregunta va al chat y el paso sigue esperando su descripción', async () => {
    vi.mocked(preguntarCopiloto).mockResolvedValueOnce({ respuesta: 'Es el informe de supervisión.', fuente: 'MODELO' })
    await montar()
    await pedirCerrarElPaso()

    await escribirAlCopiloto('que es el f-031?')

    expect(preguntarCopiloto).toHaveBeenCalledOnce()
    expect(vi.mocked(preguntarCopiloto).mock.calls[0][3]?.revisarPaso).toBeUndefined()
    expect(screen.getByText('Es el informe de supervisión.')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: /mensaje para el copiloto/i })).toHaveAttribute(
      'placeholder',
      expect.stringMatching(/describa qué hizo/i),
    )
    expect(screen.getByRole('button', { name: /confirmar paso 1 .*sin revisión/i })).toBeInTheDocument()
  })

  /**
   * Cerrar cada paso obligaba a esperar una inferencia de hasta minutos,
   * aunque la revisión no decide nada. Ahora se confirma sin esperarla, la
   * descripción sigue sirviendo y la respuesta que llegue tarde no se pinta.
   */
  it('se puede confirmar el paso sin esperar la revisión', async () => {
    const revision = respuestaPendiente()
    vi.mocked(preguntarCopiloto).mockReturnValueOnce(revision.promesa)
    const { setSteps } = await montar()
    await pedirCerrarElPaso()
    await escribirAlCopiloto('Revisé los estudios previos.')
    expectEsperandoAlCopiloto()

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /confirmar paso 1 .*sin esperar la revisión/i }))
    })

    expect(setSteps).toHaveBeenCalledWith([
      expect.objectContaining({ id: 1, subSteps: [expect.objectContaining({ id: '1.1', completed: true })] }),
    ])
    expect(screen.queryByText(/pensando…/i)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /enviar al copiloto/i })).toBeEnabled()

    await act(async () => revision.resolver({ respuesta: 'Le falta la póliza.', fuente: 'MODELO' }))
    expect(screen.queryByText('Le falta la póliza.')).not.toBeInTheDocument()
  })

  it('se puede cancelar mientras el Copiloto revisa, sin quedarse «Pensando…»', async () => {
    const revision = respuestaPendiente()
    vi.mocked(preguntarCopiloto).mockReturnValueOnce(revision.promesa)
    const { setSteps } = await montar()
    await pedirCerrarElPaso()
    await escribirAlCopiloto('Revisé los estudios previos.')

    fireEvent.click(screen.getByRole('button', { name: /cancelar, quiero revisar algo antes/i }))

    expect(screen.queryByText(/pensando…/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /confirmar paso 1/i })).not.toBeInTheDocument()
    await act(async () => revision.resolver({ respuesta: 'Parece completo.', fuente: 'MODELO' }))
    expect(screen.queryByText('Parece completo.')).not.toBeInTheDocument()
    // El panel recarga las etapas al abrirse; lo que no hace es marcar el 1.1.
    expect(setSteps).not.toHaveBeenCalledWith([
      expect.objectContaining({ subSteps: [expect.objectContaining({ completed: true })] }),
    ])
  })
})
