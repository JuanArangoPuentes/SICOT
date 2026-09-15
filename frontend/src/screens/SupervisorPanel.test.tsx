import { act, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SupervisorPanel from './SupervisorPanel'
import { PrefsProvider } from '@/prefs'
import { contrato, sesionSupervisor } from '@/test/dobles'
import type { Step, SubStep } from '@/types/domain'

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
})
