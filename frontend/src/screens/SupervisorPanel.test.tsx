import { act, fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SupervisorPanel from './SupervisorPanel'
import { PrefsProvider } from '@/prefs'
import { contrato, documento, sesionSupervisor } from '@/test/dobles'
import type { Step, SubStep } from '@/types/domain'
import type { DocumentoGeneradoResponse, PlantillaDocumento } from '@/services/api/types'

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
  // Se simula aunque ninguna prueba lo compruebe: el panel lo dispara al abrir
  // el contrato y, sin simular, la prueba saldría a la red de verdad a un
  // backend que aquí no existe.
  precalentarCopiloto: vi.fn(),
  preguntarCopiloto: vi.fn(),
  verificarIntegridad: vi.fn(),
  descargarDocumento: vi.fn(),
  verBorrador: vi.fn(),
  getPlantillasDocumento: vi.fn(),
}))
vi.mock('@/services/firmaService', () => ({
  getMiFirma: vi.fn(),
}))

/**
 * Monta el panel y espera a que sus efectos terminen.
 *
 * El `await act` del final no es decorativo: el panel pide etapas, alertas,
 * documentos y firma en sus efectos, y esas promesas simuladas se resuelven
 * DESPUÉS de que `render` haya vuelto. Sin esperar aquí, cada prueba dejaba
 * actualizaciones de estado cayendo fuera de act(...) y React lo avisaba por
 * consola en cada ejecución. Un aviso que sale siempre entrena al equipo a no
 * leer la salida de las pruebas, y entonces el aviso que sí importa tampoco se
 * lee. Además, esperar aquí es lo correcto: las aserciones miran el panel ya
 * asentado, no a medio cargar.
 */
async function montar(props: Partial<React.ComponentProps<typeof SupervisorPanel>> = {}) {
  const resultado = render(
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
  await act(async () => {})
  return resultado
}

describe('SupervisorPanel', () => {
  // Los valores de retorno se restablecen en CADA prueba, no una sola vez al
  // definir el mock: `setup.ts` llama a `vi.restoreAllMocks()` en su `afterEach`
  // global, que limpia las implementaciones. Sin esto, solo la primera prueba
  // del archivo encuentra los servicios simulados y el resto recibe `undefined`
  // — un fallo que parece del componente y en realidad es del andamiaje.
  beforeEach(async () => {
    // El historial de llamadas también: sin esto, «firmar no se llamó» veía
    // las firmas de la prueba anterior.
    vi.clearAllMocks()
    localStorage.clear()
    vi.mocked((await import('@/services/etapaService')).getEtapasContrato).mockResolvedValue([])
    vi.mocked((await import('@/services/alertaService')).getAlertasContrato).mockResolvedValue([])
    vi.mocked((await import('@/services/documentoService')).getDocumentosContrato).mockResolvedValue([])
    vi.mocked((await import('@/services/firmaService')).getMiFirma).mockResolvedValue({
      tieneFirmaActiva: true,
      firmaId: 'FIRMA-TEST',
    })
  })

  it('mientras consulta el contrato NO afirma que no hay ninguno', async () => {
    await montar({ cargandoContrato: true })

    expect(screen.getByText(/consultando su contrato/i)).toBeInTheDocument()
    expect(screen.queryByText(/no tiene un contrato asignado/i)).not.toBeInTheDocument()
  })

  /**
   * El error más grave posible de esta pantalla: decirle a alguien que no tiene
   * trabajo pendiente cuando lo que ocurrió es que el backend no respondió.
   */
  it('si la consulta falla lo dice, en vez de fingir que no hay contrato', async () => {
    await montar({ errorContrato: true })

    expect(screen.getByText(/no se pudo cargar su contrato/i)).toBeInTheDocument()
    expect(screen.queryByText(/no tiene un contrato asignado/i)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /reintentar/i })).toBeInTheDocument()
  })

  it('sin contrato y sin error sí muestra el estado vacío', async () => {
    await montar()

    // Por rol y no por texto suelto: el mensaje aparece como encabezado y
    // repetido en el cuerpo, y consultar por texto encontraría los dos.
    expect(screen.getByRole('heading', { name: /no tiene un contrato asignado/i })).toBeInTheDocument()
  })

  it('con un contrato asignado muestra su número', async () => {
    await montar({ contrato: contrato() })

    expect(await screen.findAllByText(/CTMA-2026-0184/)).not.toHaveLength(0)
  })

  /**
   * Las secciones del menú se muestran siempre, incluso sin contrato: si al no
   * haberlo el menú se redujera a una entrada, parecería que el sistema perdió
   * funcionalidad. Quedan inactivas, no ocultas.
   */
  it('mantiene visibles todas las secciones aunque no haya contrato', async () => {
    await montar()

    for (const seccion of [/contrato/i, /alertas/i, /documentos/i, /registros/i]) {
      expect(screen.getAllByText(seccion).length).toBeGreaterThan(0)
    }
  })

  // ──────────────────────────────────────────────────────────────────────────
  // Barras y porcentajes
  //
  // El jefe del área pidió el panel más simple pero conservando «las barras y
  // el porcentaje de los procesos». Lo que se simplificó fue la repetición: el
  // mismo porcentaje llegaba a salir tres veces en la misma pantalla con tres
  // formas distintas. Estas pruebas fijan que el dato siga estando y que siga
  // saliendo UNA vez.
  // ──────────────────────────────────────────────────────────────────────────

  /** Etapas del GCCON-P-010 con avance real: 1 y 2 cerradas, la 3 a medias. */
  function etapasConAvance(): Step[] {
    const sub = (id: string, completed: boolean): SubStep => ({
      id,
      label: `Sub-paso ${id}`,
      responsible: 'Área requirente',
      document: 'Ficha',
      completed,
    })
    return [
      {
        id: 1,
        title: 'INICIO — Estudios y Suscripción',
        status: 'completed',
        subSteps: [sub('1.1', true), sub('1.2', true)],
      },
      {
        id: 2,
        title: 'INICIO — Acta de Inicio (GCCON-F-018)',
        status: 'completed',
        subSteps: [sub('2.1', true), sub('2.2', true)],
      },
      {
        id: 3,
        title: 'INSPECCIÓN — Monitoreo y Ejecución',
        status: 'active',
        subSteps: [sub('3.1', true), sub('3.2', false)],
      },
      {
        id: 4,
        title: 'CIERRE — Informe Final y Archivo',
        status: 'pending',
        subSteps: [sub('4.1', false), sub('4.2', false)],
      },
    ]
  }

  it('la bandeja muestra la barra de avance con el porcentaje real', async () => {
    // 5 de 8 sub-pasos cerrados = 63 %.
    await montar({ contrato: contrato(), steps: etapasConAvance() })

    const barra = await screen.findByRole('progressbar', { name: /avance del contrato/i })
    expect(barra).toHaveAttribute('aria-valuenow', '63')
    expect(screen.getByText(/5 de 8 sub-pasos cerrados/i)).toBeInTheDocument()
  })

  /**
   * El GCCON-P-010 llama «INICIO» a sus dos primeros pasos. La barra se
   * quedaba con lo anterior al guion largo, así que pintaba dos segmentos
   * seguidos rotulados igual. En una barra cuyo único trabajo es responder
   * «¿en cuál voy?», dos rótulos idénticos son el peor fallo posible.
   */
  it('en la barra, ningún segmento queda rotulado igual que otro', async () => {
    await montar({ vista: 'contrato', contrato: contrato(), steps: etapasConAvance() })
    await screen.findByText(/recorrido del contrato/i)

    // Los segmentos de la barra son los únicos botones cuyo título termina en
    // "% completado". Se consultan por ahí para no confundirlos con el resto de
    // la pantalla, donde el nombre completo de la etapa aparece otras veces.
    const segmentos = screen.getAllByRole('button').filter((b) => /% completado$/.test(b.getAttribute('title') ?? ''))

    expect(segmentos).toHaveLength(4)
    const rotulos = segmentos.map((b) =>
      b.textContent
        ?.replace(/[✓\d%]/g, '')
        .trim()
        .toLowerCase(),
    )
    expect(new Set(rotulos).size).toBe(rotulos.length)
    // Y en concreto: las dos etapas que el GCCON-P-010 llama "INICIO".
    expect(rotulos[0]).toContain('estudios y suscripción')
    expect(rotulos[1]).toContain('acta de inicio')
  })

  /**
   * «Avance global» salía dos veces seguidas con el mismo número: en la
   * cabecera de la barra y otra vez como tarjeta justo debajo. Ver dos veces
   * la misma cifra no la refuerza — hace dudar de si son dos cifras distintas.
   */
  it('el avance global aparece una sola vez en la vista de contrato', async () => {
    await montar({ vista: 'contrato', contrato: contrato(), steps: etapasConAvance() })

    await screen.findByText(/recorrido del contrato/i)
    expect(screen.getAllByText(/avance global/i)).toHaveLength(1)
  })

  /**
   * Simplificar no puede significar perder el dato. Las tres tarjetas que
   * quedan dicen cada una algo que la barra de arriba no dice.
   */
  it('conserva los indicadores que no duplican la barra', async () => {
    await montar({ vista: 'contrato', contrato: contrato(), steps: etapasConAvance() })

    await screen.findByText(/recorrido del contrato/i)

    // getAllBy y no getBy: "Vigencia" también es el nombre de un campo de la
    // ficha del contrato, más abajo en la misma pantalla. Lo que se afirma es
    // que el indicador sigue estando, no que el texto sea único.
    for (const indicador of [/etapas cerradas/i, /sub-pasos por cerrar/i, /vigencia/i]) {
      expect(screen.getAllByText(indicador).length).toBeGreaterThan(0)
    }
  })

  // Con la lista de documentos vacía por un fallo, Documentos decía «Sin
  // generar aún» en los cinco formatos y la bandeja callaba los borradores sin
  // firmar (auditoría del 02-10-2026).
  it('si no puede consultar los documentos, lo dice en vez de darlos por no generados', async () => {
    vi.mocked((await import('@/services/documentoService')).getDocumentosContrato).mockRejectedValue(
      new TypeError('Failed to fetch'),
    )
    await montar({ vista: 'documentos', contrato: contrato() })

    expect(screen.queryByText('Sin generar aún')).not.toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent(/no se pudieron consultar los documentos del contrato/i)
  })

  it('si no puede consultar los documentos, la bandeja lo avisa en vez de callar los borradores', async () => {
    vi.mocked((await import('@/services/documentoService')).getDocumentosContrato).mockRejectedValue(
      new TypeError('Failed to fetch'),
    )
    await montar({ vista: 'bandeja', contrato: contrato() })

    expect(screen.getByText('No se pudieron consultar los documentos del contrato')).toBeInTheDocument()
  })

  // ──────────────────────────────────────────────────────────────────────────
  // Generar y firmar un documento formal
  //
  // Un documento firmado no se puede corregir. Cada prueba fija un camino en el
  // que, hasta el 29-09-2026, el panel firmaba algo que no debía, dejaba el
  // sub-paso bloqueado o decía que la firma había fallado cuando no.
  // ──────────────────────────────────────────────────────────────────────────

  /** El sub-paso 2.7 (Acta de Inicio) en un paso que sigue abierto después de él. */
  function pasoConActa(): Step[] {
    return [
      {
        id: 1,
        title: 'INICIO — Acta de Inicio (GCCON-F-018)',
        status: 'active',
        subSteps: [
          {
            id: '2.7',
            label: 'Firmar el Acta de Inicio',
            responsible: 'Supervisor',
            document: 'Acta',
            completed: false,
            apiId: 27,
          },
          { id: '2.8', label: 'Archivar', responsible: 'Supervisor', document: 'Acta', completed: false, apiId: 28 },
        ],
      },
    ]
  }

  const plantillaActa: PlantillaDocumento = {
    tipo: 'ACTA_INICIO',
    codigo: 'GCCON-F-018',
    nombre: 'Acta de Inicio',
    llevaObservaciones: false,
    campos: [
      {
        clave: 'cedulaSupervisor',
        etiqueta: 'Cédula del supervisor',
        ejemplo: '1',
        opcional: false,
        dependeDe: null,
        porDocumento: false,
      },
    ],
  }

  /** Inicia el paso desde el copiloto (el botón de firmar solo sale en el sub-paso activo) y pulsa firmar. */
  async function firmarActa() {
    await act(async () => fireEvent.click(screen.getByRole('button', { name: /iniciar paso 1/i })))
    await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Firmar documento' })))
  }

  async function servicios() {
    return {
      documentos: vi.mocked(await import('@/services/documentoService')),
      etapas: vi.mocked(await import('@/services/etapaService')),
    }
  }

  it('si no puede consultar qué datos pide el formato, no genera ni firma nada', async () => {
    const { documentos } = await servicios()
    documentos.getPlantillasDocumento.mockRejectedValue(new Error('sin red'))
    await montar({ vista: 'contrato', contrato: contrato(), steps: pasoConActa() })

    await firmarActa()

    expect(documentos.generarDocumento).not.toHaveBeenCalled()
    expect(await screen.findByText(/no pude consultar qué datos pide «Acta de Inicio»/i)).toBeInTheDocument()
    // Y el sub-paso queda libre para intentarlo otra vez.
    expect(screen.getByRole('button', { name: 'Firmar documento' })).toBeEnabled()
  })

  it('si el documento del sub-paso ya está firmado, lo cierra en vez de generar otro', async () => {
    const { documentos, etapas } = await servicios()
    documentos.getDocumentosContrato.mockResolvedValue([
      documento({
        nombre: 'Acta de Inicio — CTMA-2026-0184',
        subetapaId: 27,
        generadoPorIa: true,
        firmaId: 'FIRMA-TEST',
      }),
    ])
    etapas.cambiarEstadoSubetapa.mockResolvedValue(undefined as never)
    etapas.getEtapasContrato.mockResolvedValue([])
    await montar({ vista: 'contrato', contrato: contrato(), steps: pasoConActa() })

    await firmarActa()

    expect(documentos.generarDocumento).not.toHaveBeenCalled()
    expect(etapas.cambiarEstadoSubetapa).toHaveBeenCalledWith(27, 'COMPLETADA')
  })

  it('pide los datos antes de firmar y, si luego falla el registro, no dice que la firma falló', async () => {
    const { documentos, etapas } = await servicios()
    documentos.getPlantillasDocumento.mockResolvedValue([plantillaActa])
    documentos.generarDocumento.mockResolvedValue(generado())
    documentos.firmarDocumento.mockResolvedValue(documento({ id: 9, generadoPorIa: true, firmaId: 'FIRMA-TEST' }))
    etapas.cambiarEstadoSubetapa.mockResolvedValue(undefined as never)
    etapas.getEtapasContrato.mockResolvedValue([])
    const onRefreshRegistros = vi.fn().mockRejectedValue(new Error('registro caído'))
    await montar({ vista: 'contrato', contrato: contrato(), steps: pasoConActa(), onRefreshRegistros })

    await firmarActa()
    expect(documentos.generarDocumento).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText('Cédula del supervisor'), { target: { value: '98.587.121' } })
    await act(async () => fireEvent.click(screen.getByText('Generar y revisar')))

    expect(documentos.generarDocumento).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ tipo: 'ACTA_INICIO', datos: { cedulaSupervisor: '98.587.121' } }),
    )
    await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Firmar' })))
    expect(documentos.firmarDocumento).toHaveBeenCalledWith(1, 9, 'huella-del-borrador')
    expect(etapas.cambiarEstadoSubetapa).toHaveBeenCalledWith(27, 'COMPLETADA')
    expect(screen.queryByText(/no pude completar la firma/i)).not.toBeInTheDocument()
  })

  // Un formato cuyos datos son solo tablas (las obligaciones) también se
  // pregunta antes de firmar, y las filas llegan a la generación.
  it('pide las tablas del formato aunque no tenga datos sueltos y las envía al generar', async () => {
    const { documentos, etapas } = await servicios()
    documentos.getPlantillasDocumento.mockResolvedValue([
      {
        ...plantillaActa,
        campos: [],
        tablas: [
          {
            clave: 'obligacionesEspecificas',
            etiqueta: 'Obligaciones específicas del contratista',
            ayuda: 'Una fila por obligación.',
            columnas: [
              { etiqueta: 'Obligación', ejemplo: 'Proveer los bienes', delContrato: true },
              { etiqueta: 'Actividades realizadas', ejemplo: 'Se verificó', delContrato: false },
            ],
          },
        ],
      },
    ])
    documentos.generarDocumento.mockResolvedValue(generado())
    documentos.firmarDocumento.mockResolvedValue(documento({ id: 9, generadoPorIa: true, firmaId: 'FIRMA-TEST' }))
    etapas.cambiarEstadoSubetapa.mockResolvedValue(undefined as never)
    etapas.getEtapasContrato.mockResolvedValue([])
    await montar({ vista: 'contrato', contrato: contrato(), steps: pasoConActa() })

    await firmarActa()
    expect(documentos.generarDocumento).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText('Obligación (fila 1) de Obligaciones específicas del contratista'), {
      target: { value: 'Proveer los bienes nuevos' },
    })
    fireEvent.change(
      screen.getByLabelText('Actividades realizadas (fila 1) de Obligaciones específicas del contratista'),
      { target: { value: 'Se verificó' } },
    )
    await act(async () => fireEvent.click(screen.getByText('Generar y revisar')))

    expect(documentos.generarDocumento).toHaveBeenCalledWith(
      1,
      expect.objectContaining({
        tipo: 'ACTA_INICIO',
        datos: {},
        tablas: { obligacionesEspecificas: [['Proveer los bienes nuevos', 'Se verificó']] },
      }),
    )
  })

  // ── La redacción del Copiloto se lee antes de firmar ────────────────────
  //
  // Hasta el 29-09-2026 el panel generaba y firmaba de un golpe: en la prueba
  // en vivo, «se devolvieron al contratista 3 monitores» salió como «el
  // contratista ha devuelto tres monitores» y se habría firmado sin leerlo.

  /** El documento recién generado, con cómo quedaron sus observaciones. */
  function generado(parcial: Partial<DocumentoGeneradoResponse> = {}): DocumentoGeneradoResponse {
    return {
      ...documento({ id: 9, generadoPorIa: true }),
      observaciones: null,
      observacionesRedactadasConIa: false,
      motivoNotasTalCual: null,
      huellaDelBorrador: 'huella-del-borrador',
      ...parcial,
    }
  }

  async function prepararRedaccion() {
    const { documentos, etapas } = await servicios()
    documentos.getPlantillasDocumento.mockResolvedValue([{ ...plantillaActa, campos: [] }])
    documentos.firmarDocumento.mockResolvedValue(documento({ id: 9, generadoPorIa: true, firmaId: 'FIRMA-TEST' }))
    etapas.cambiarEstadoSubetapa.mockResolvedValue(undefined as never)
    etapas.getEtapasContrato.mockResolvedValue([])
    return { documentos, etapas }
  }

  it('si el Copiloto redactó las observaciones, las muestra y no firma hasta que el supervisor las acepte', async () => {
    const { documentos, etapas } = await prepararRedaccion()
    documentos.generarDocumento.mockResolvedValue(
      generado({ observaciones: 'El contratista ha devuelto tres monitores.', observacionesRedactadasConIa: true }),
    )
    await montar({ vista: 'contrato', contrato: contrato(), steps: pasoConActa() })

    await firmarActa()

    // La redacción va completa; partida en tramos porque se marcan las
    // palabras que no estaban en las notas.
    expect(screen.getByTestId('redaccion-revisada')).toHaveTextContent('El contratista ha devuelto tres monitores.')
    expect(screen.getByTestId('redaccion-revisada').querySelectorAll('mark').length).toBeGreaterThan(0)
    expect(documentos.firmarDocumento).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Revisando el borrador…' })).toBeDisabled()

    await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Firmar' })))

    // Se firma el mismo borrador que se mostró, sin regenerarlo, y con su
    // huella: si otra pestaña lo regeneró entre medias, el servidor no firma.
    expect(documentos.generarDocumento).toHaveBeenCalledTimes(1)
    expect(documentos.firmarDocumento).toHaveBeenCalledWith(1, 9, 'huella-del-borrador')
    expect(etapas.cambiarEstadoSubetapa).toHaveBeenCalledWith(27, 'COMPLETADA')
  })

  it('«Usar mis notas tal cual» regenera sin el Copiloto y, tras revisarlo, firma ese borrador', async () => {
    const { documentos } = await prepararRedaccion()
    documentos.generarDocumento
      .mockResolvedValueOnce(
        generado({ observaciones: 'El contratista ha devuelto tres monitores.', observacionesRedactadasConIa: true }),
      )
      .mockResolvedValueOnce(
        generado({
          observaciones: 'se devolvieron al contratista 3 monitores',
          huellaDelBorrador: 'huella-de-las-notas',
        }),
      )
    await montar({ vista: 'contrato', contrato: contrato(), steps: pasoConActa() })

    await firmarActa()
    await act(async () => fireEvent.click(screen.getByText('Usar mis notas tal cual')))

    expect(documentos.generarDocumento).toHaveBeenCalledTimes(2)
    expect(documentos.generarDocumento).toHaveBeenLastCalledWith(1, expect.objectContaining({ redactarConIa: false }))
    // El borrador nuevo también se revisa: hasta el 02-10-2026 se firmaba
    // directo y sin su huella, y otra pestaña que regenerara el mismo borrador
    // con el Copiloto entre medias hacía firmar una redacción que nadie leyó.
    expect(documentos.firmarDocumento).not.toHaveBeenCalled()
    expect(screen.getByTestId('observaciones-tal-cual')).toHaveTextContent('se devolvieron al contratista 3 monitores')
    await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Firmar' })))
    expect(documentos.firmarDocumento).toHaveBeenCalledWith(1, 9, 'huella-de-las-notas')
  })

  it('cancelar deja el borrador sin firmar y el sub-paso libre', async () => {
    const { documentos, etapas } = await prepararRedaccion()
    documentos.generarDocumento.mockResolvedValue(
      generado({ observaciones: 'Texto redactado.', observacionesRedactadasConIa: true }),
    )
    await montar({ vista: 'contrato', contrato: contrato(), steps: pasoConActa() })

    await firmarActa()
    await act(async () => fireEvent.click(screen.getByText('Cancelar')))

    expect(documentos.firmarDocumento).not.toHaveBeenCalled()
    expect(etapas.cambiarEstadoSubetapa).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Firmar documento' })).toBeEnabled()
    expect(screen.getByText(/quedó como borrador sin firmar/i)).toBeInTheDocument()
  })

  it('si van las notas tal cual, las muestra con el motivo y no firma hasta que pulse «Firmar»', async () => {
    const { documentos } = await prepararRedaccion()
    documentos.generarDocumento.mockResolvedValue(
      generado({
        observaciones: 'mis notas',
        motivoNotasTalCual: 'la redacción del Copiloto perdía cifras de sus notas',
      }),
    )
    await montar({ vista: 'contrato', contrato: contrato(), steps: pasoConActa() })

    await firmarActa()

    expect(screen.getByTestId('observaciones-tal-cual')).toHaveTextContent('mis notas')
    expect(screen.getByText(/porque la redacción del Copiloto perdía cifras de sus notas/)).toBeInTheDocument()
    expect(screen.queryByText('Usar mis notas tal cual')).not.toBeInTheDocument()
    expect(documentos.firmarDocumento).not.toHaveBeenCalled()

    await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Firmar' })))
    expect(documentos.firmarDocumento).toHaveBeenCalledWith(1, 9, 'huella-del-borrador')
  })

  // Hasta el 02-10-2026 un documento sin observaciones redactadas se firmaba
  // en cuanto se generaba: un número de contrato mal escrito o una celda vacía
  // de la tabla quedaban firmados sin que nadie abriera el PDF.
  it('un documento sin observaciones también se revisa, con su borrador completo, y no se firma sin «Firmar»', async () => {
    const { documentos } = await prepararRedaccion()
    documentos.generarDocumento.mockResolvedValue(generado({ nombre: 'Acta de Inicio — CTMA-2026-0184' }))
    documentos.verBorrador.mockResolvedValue('abierto')
    await montar({ vista: 'contrato', contrato: contrato(), steps: pasoConActa() })

    await firmarActa()

    expect(documentos.firmarDocumento).not.toHaveBeenCalled()
    expect(screen.getByRole('dialog')).toHaveTextContent(/revisar antes de firmar/i)
    await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Ver borrador completo (PDF)' })))
    expect(documentos.verBorrador).toHaveBeenCalledWith(1, 9, 'Acta de Inicio — CTMA-2026-0184')
    expect(documentos.firmarDocumento).not.toHaveBeenCalled()

    await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Firmar' })))
    expect(documentos.firmarDocumento).toHaveBeenCalledWith(1, 9, 'huella-del-borrador')
    expect(screen.getByText(/firmé «Acta de Inicio» tal como lo revisó/i)).toBeInTheDocument()
  })
})
