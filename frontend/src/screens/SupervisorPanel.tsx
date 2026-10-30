// Panel principal del Supervisor.
//
// Estructura: armazón con barra lateral izquierda (AppShell) y cinco vistas —
// Bandeja de entrada (aterrizaje al iniciar sesión), Contrato (recorrido de
// etapas + ficha completa + copiloto + gráficas), Alertas, Documentos y
// Registros.
//
// Toda la información proviene del backend: etapas y subetapas reales del
// contrato asignado, alertas no leídas, documentos realmente generados y la
// firma electrónica de la cuenta. Los estados de carga, de error y de "sin
// contrato" son pantallas distintas a propósito: afirmar "no tiene nada
// pendiente" cuando en realidad falló una consulta sería el peor error de
// esta pantalla.

import { useState, useRef, useEffect } from 'react'

// Referencia estable: pasar `[]` en línea crearía un array nuevo en cada render
// y el hook volvería a fijar el estado sin parar.
const SIN_ALERTAS: never[] = []
const SIN_DOCUMENTOS: never[] = []
import { usePrefs } from '@/prefs'
import AppShell, { type NavGroup } from '@/components/AppShell'
import Registros, { type Registro } from '@/components/Registros'
import Bandeja, { type ItemBandeja } from '@/components/supervisor/Bandeja'
import ContratoInfo from '@/components/supervisor/ContratoInfo'
import ContratoGraficas from '@/components/supervisor/ContratoGraficas'
import VistaDocumentos from '@/components/supervisor/VistaDocumentos'
import VistaAlertas from '@/components/supervisor/VistaAlertas'
import { destinoDeAlerta, type DestinoDeAlerta } from '@/components/supervisor/destinoDeAlerta'
import PanelCopiloto, { type RevisionPaso } from '@/components/supervisor/PanelCopiloto'
import {
  CargandoContratoState,
  EmptyContractState,
  ErrorContratoState,
} from '@/components/supervisor/EstadosSinContrato'
import { SectionHeader, StageJourney, StatCard, type LiveAlert, type Stage } from '@/components/ui'
import {
  AvatarIcon,
  IconBell,
  IconCheck,
  IconClock,
  IconContract,
  IconFileText,
  IconHistory,
  IconInbox,
  IconPlay,
} from '@/components/icons'
import {
  ACTA_FIRMADA_Y_ENVIO,
  AI_GENERATED_DOCS,
  HERRAMIENTA_POR_PASO,
  SUBETAPAS_CON_EVIDENCIA_FOTOGRAFICA,
  TUTORIAL,
  FORMAL_DOCS,
} from '@/data/contractFlow'
import EvidenciaFotografica from '@/components/supervisor/EvidenciaFotografica'
import ActaFirmadaYEnvio from '@/components/supervisor/ActaFirmadaYEnvio'
import VerificacionSecop from '@/components/supervisor/VerificacionSecop'
import BorradorDeCorreo from '@/components/supervisor/BorradorDeCorreo'
import SolicitudDePedido from '@/components/supervisor/SolicitudDePedido'
import AcuerdoDeEntregas from '@/components/supervisor/AcuerdoDeEntregas'
import ContactosSstAmbiental from '@/components/supervisor/ContactosSstAmbiental'
import ContactoDelContratista from '@/components/supervisor/ContactoDelContratista'
import DatosDelDocumento, {
  guardarTablasDelContrato,
  leerTablasDelContrato,
  soloLoDelDocumento,
  tablasDelContrato,
} from '@/components/supervisor/DatosDelDocumento'
import RevisarAntesDeFirmar from '@/components/supervisor/RevisarAntesDeFirmar'
import type { Step, Tab, ChatMsg } from '@/types/domain'
import type {
  AccionCopiloto,
  AuthResponse,
  AlertaResponse,
  ChatResponse,
  ContratoResponse,
  DocumentoResponse,
  CronogramaResponse,
  PlantillaDocumento,
  TablasDelDocumento,
  TipoAlerta,
} from '@/services/api/types'
import { getEtapasContrato, cambiarEstadoSubetapa } from '@/services/etapaService'
import { guiaDelSubPaso } from '@/data/guiaSubPaso'
import { getAlertasContrato, marcarAlertaLeida } from '@/services/alertaService'
import { alEnviarCambios } from '@/services/colaDeCambios'
import { getCronograma } from '@/services/cronogramaService'
import { useRecursoDelContrato } from '@/hooks/useRecursoDelContrato'
import {
  getDocumentosContrato,
  generarDocumento,
  firmarDocumento,
  getPlantillasDocumento,
  precalentarCopiloto,
  preguntarCopilotoEnVivo,
  descargarDocumento,
} from '@/services/documentoService'
import { getMiFirma } from '@/services/firmaService'
import {
  CortadaPorSegundoPlano,
  esperarPrimerPlano,
  idDeSolicitud,
  mantenerPantallaEncendida,
  repetirSiSeCorta,
  vigilarSegundoPlano,
} from '@/services/segundoPlano'
import { intencionEnLaRevision } from '@/services/revisionDelPaso'
import { ApiError } from '@/services/api/client'
import { motivoDelFalloDeDescarga } from '@/services/falloDeDescarga'
import { mapEtapas } from '@/services/mappers'
import { fechaDelCentro, formatFecha } from '@/services/format'

// Chips de preguntas frecuentes — el label es corto para el botón y la
// pregunta va completa. Las cinco tienen respuesta fija y el backend las
// contesta sin modelo, al instante: la primera GuiaDelPasoActual (el paso real
// del contrato) y las otras cuatro FichaDeDocumentoFormal (el catálogo). Hasta
// el 1-10-2026 iban todas a Ollama: hasta ~158 s en CPU. Si se cambia una
// pregunta, hay que cambiarla también en CopilotoChatServiceTest y
// FichaDeDocumentoFormalTest, que comprueban que no lleguen al modelo.
const QUICK_SUGGESTIONS: Array<{ label: string; question: string }> = [
  {
    label: '¿Qué me falta en este paso?',
    question: '¿Qué me falta en el paso en el que estoy y cómo lo registro en SICOT?',
  },
  { label: 'GCCON-F-031', question: '¿Qué es el GCCON-F-031, quién lo firma y en qué sub-paso se genera?' },
  { label: 'GIL-F-010', question: '¿Qué es el GIL-F-010, quién lo firma y en qué sub-paso se genera?' },
  {
    label: 'ESUCON',
    question:
      '¿Qué es "ESUCON"? ¿Tiene código de formato oficial confirmado? ¿En qué sub-paso se genera y quién lo firma?',
  },
  {
    label: 'GCCON-F-030',
    question: '¿Qué es el GCCON-F-030? ¿Es lo mismo que el acta de liquidación? ¿En qué sub-paso se genera?',
  },
]

// Una respuesta del Copiloto tal como se guarda en el chat. Lo que el servidor
// armó sin modelo (fuente SISTEMA) se marca en pantalla y no se le reenvía al
// modelo (ver historialCopiloto.ts); un servidor anterior al 02-10-2026 no
// manda fuente, y todo lo suyo pasaba por el modelo o se trataba como tal.
// La acción solo se guarda: se ejecuta cuando el supervisor pulsa su botón.
/**
 * Por qué no llegó la respuesta del Copiloto, dicho con lo que se sabe. Hasta
 * el 18-10-2026 todo fallo sin respuesta del servidor decía «verifique que
 * Ollama esté disponible», aunque lo que fallara fuera la red o un
 * intermediario y el modelo estuviera respondiendo.
 */
const porQueNoRespondio = (e: unknown): string => {
  if (e instanceof ApiError || e instanceof CortadaPorSegundoPlano) return e.message
  if (e instanceof TypeError)
    return 'No hubo respuesta del servidor de SICOT: revise la conexión del equipo y vuelva a intentarlo.'
  return 'No se pudo completar la consulta al Copiloto. Vuelva a intentarlo en un momento.'
}

/**
 * Cada cuánto se le recuerda al backend que el panel sigue abierto. Un tercio
 * del tiempo que Ollama mantiene el modelo cargado (30 minutos): aunque el
 * navegador retrase un temporizador de una pestaña de fondo, llega antes.
 */
const RENOVAR_COPILOTO_MS = 10 * 60 * 1000

const mensajeDelCopiloto = ({ respuesta, fuente, accion }: ChatResponse): ChatMsg => ({
  role: 'ai',
  text: respuesta,
  origen: fuente === 'SISTEMA' ? 'sistema' : 'modelo',
  ...(accion ? { accion } : {}),
})

// La barra de recorrido tiene una sección por paso REAL del contrato (los
// mismos que trae el backend), no por una agrupación aparte: así el número que
// se ve en la barra es el mismo "Paso N" del que hablan el copiloto, la bandeja
// y el acordeón de etapas.
const claveDePaso = (id: number) => `paso-${id}`
const pasoDeClave = (key: string) => Number(key.replace('paso-', '')) || 1

// Resumen de lo que ocurre en cada paso — se muestra bajo la barra grande.
// Los pasos 1 y 2 siguen el proceso real que documentó el supervisor el
// 15-10-2026 (ver data/contractFlow.ts); los demás, GCCON-P-010.
const DETALLE_PASO: Record<number, string> = {
  1: 'Contrato publicado en SECOP II, designación del supervisor y del apoyo (GCCON-F-032) y registro en SICOT.',
  2: 'Firma y devolución del Acta de Inicio, aviso a SST y ambiental, verificación en SECOP II y acuerdo de entregas con el contratista.',
  3: 'Inspección física en bodega, evidencia fotográfica y firma del Informe de Supervisión GCCON-F-031.',
  4: 'Verificación de PILA y factura electrónica DIAN, y firma del Acta de Recibo a Satisfacción GIL-F-010.',
  5: 'Vigencia de garantías, orden de pago y CRP, y firma de la certificación de cumplimiento que respalda el pago.',
  6: 'Cumplimiento total del objeto, Informe Final GCCON-F-030 y archivo del expediente digital en SIGEP.',
}

/**
 * Lo que el servidor dijo que estaba mal. En un 400 de validación el mensaje
 * general es «Error de validación en los datos enviados.» y lo que hay que
 * corregir viene por campo: unas notas de más de 4000 caracteres fallaban sin
 * que nada dijera que el problema era su largo (auditoría del 02-10-2026).
 */
function motivoDelServidor(e: ApiError): string {
  const porCampo = Object.values(e.detail?.fieldErrors ?? {})
  return porCampo[0] ?? e.message
}

/** "INSPECCIÓN — Monitoreo y Ejecución" -> "Inspección". */
function etiquetaCorta(titulo: string): string {
  const cabeza = titulo.split('—')[0].trim()
  const texto = cabeza || titulo
  return texto.charAt(0).toUpperCase() + texto.slice(1).toLowerCase()
}

/** "INICIO — Acta de Inicio (GCCON-F-018)" -> "Acta de Inicio". */
function etiquetaDeCola(titulo: string): string {
  const cola = titulo.split('—').slice(1).join('—')
  return cola.replace(/\([^)]*\)/g, '').trim()
}

/**
 * Etiquetas cortas y **distinguibles entre sí** para la barra de recorrido.
 *
 * <p>El GCCON-P-010 llama "INICIO" a sus dos primeros pasos: «INICIO — Estudios
 * y Suscripción» e «INICIO — Acta de Inicio». Quedarse con la cabeza, que es lo
 * que se hacía, ponía dos segmentos rotulados igual, uno al lado del otro. En
 * una barra cuyo trabajo es responder "¿en cuál voy?", dos rótulos idénticos
 * son justo el fallo que no puede tener.
 *
 * <p>Cuando la cabeza se repite se usa la cola, que es la parte que de verdad
 * los diferencia. No se concatenan las dos ("Inicio: Estudios y Suscripción")
 * porque el segmento es estrecho y la parte repetida se comería el espacio de
 * la parte que distingue. El nombre completo sigue disponible en el título
 * grande y al pasar el ratón sobre el segmento.
 */
function etiquetasDeBarra(titulos: string[]): string[] {
  const cabezas = titulos.map(etiquetaCorta)
  return titulos.map((titulo, i) => {
    const seRepite = cabezas.filter((c) => c === cabezas[i]).length > 1
    if (!seRepite) return cabezas[i]
    const cola = etiquetaDeCola(titulo)
    return cola || cabezas[i]
  })
}

const TITULO_VISTA: Record<Tab, { titulo: string; sub: string }> = {
  bandeja: { titulo: 'Bandeja de entrada', sub: 'Pendientes y situación actual de su contrato' },
  contrato: { titulo: 'Contrato en supervisión', sub: 'Recorrido del proceso, ficha completa y copiloto' },
  alertas: { titulo: 'Alertas', sub: 'Seguimiento en vivo del contrato' },
  documentos: { titulo: 'Documentos', sub: 'Documentos formales del proceso GCCON-P-010' },
  registros: { titulo: 'Registros', sub: 'Bitácora de acciones ejecutadas sobre el contrato' },
}

// ─── Helper components (privados de este panel) ────────────────────────────────

function StepCircle({ n, status }: { n: number; status: 'completed' | 'active' | 'pending' }) {
  if (status === 'completed')
    return (
      <div
        style={{
          width: 26,
          height: 26,
          borderRadius: '50%',
          flexShrink: 0,
          background: 'var(--accent)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <svg width="13" height="10" viewBox="0 0 13 10" fill="none">
          <path
            d="M1 5L5 9L12 1"
            stroke="var(--on-accent)"
            strokeWidth="2.2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </div>
    )
  if (status === 'active')
    return (
      <div
        style={{
          width: 26,
          height: 26,
          borderRadius: '50%',
          flexShrink: 0,
          border: '2px solid var(--accent)',
          background: 'var(--accent-soft)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: 12,
          fontWeight: 600,
          color: 'var(--accent)',
        }}
      >
        {n}
      </div>
    )
  return (
    <div
      style={{
        width: 26,
        height: 26,
        borderRadius: '50%',
        flexShrink: 0,
        border: '2px solid var(--step-pending)',
        background: 'transparent',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: 12,
        fontWeight: 600,
        color: 'var(--text-muted)',
      }}
    >
      {n}
    </div>
  )
}

function ProgressBar({ pct }: { pct: number }) {
  return (
    <div style={{ height: 4, background: 'var(--bg-elevated)', borderRadius: 2, overflow: 'hidden', flexGrow: 1 }}>
      <div
        style={{
          height: '100%',
          width: `${pct}%`,
          background: pct === 100 ? 'var(--accent)' : 'var(--accent-dim)',
          borderRadius: 2,
          transition: 'width 0.4s',
        }}
      />
    </div>
  )
}

/** Lo que el supervisor dio en el formulario de datos: los datos sueltos y las filas de las tablas. */
type DatosParaGenerar = { campos: Record<string, string>; tablas: TablasDelDocumento }

export default function SupervisorPanel({
  vista,
  onCambiarVista,
  steps,
  setSteps,
  usuario,
  contrato,
  contratos = [],
  onSeleccionarContrato,
  cargandoContrato,
  errorContrato,
  onLogout,
  onOpenSettings,
  onStartTour,
  registros,
  onRefreshRegistros,
  pedidosCopiloto = 0,
  onContratoActualizado,
}: {
  vista: Tab
  onCambiarVista: (t: Tab) => void
  steps: Step[]
  setSteps: (s: Step[]) => void
  usuario: AuthResponse
  contrato: ContratoResponse | null
  /** Todos los contratos del supervisor; con más de uno aparece el selector. */
  contratos?: ContratoResponse[]
  onSeleccionarContrato?: (id: number) => void
  cargandoContrato: boolean
  errorContrato: boolean
  onLogout: () => void
  onOpenSettings: () => void
  onStartTour: () => void
  registros: Registro[]
  onRefreshRegistros: () => Promise<void>
  /** Sube cada vez que el avatar flotante pide abrir el Copiloto. */
  pedidosCopiloto?: number
  /**
   * El contrato cambió desde el panel (hoy, el teléfono del contratista traído
   * del SECOP II en el paso 2.4): quien tiene la lista de contratos la pone al día.
   */
  onContratoActualizado?: (c: ContratoResponse) => void
}) {
  const { prefs } = usePrefs()
  const [dismissed, setDismissed] = useState<Set<string>>(new Set())
  // La vista activa vive en la URL, no en estado local: así el botón atrás
  // funciona, recargar conserva la vista y una vista se puede compartir por
  // enlace. Ver docs/decisiones/ADR-007.
  const tab = vista
  const setTab = onCambiarVista
  const [expandedSteps, setExpandedSteps] = useState<Set<number>>(new Set([1]))
  // Sube cada vez que el 2.2 guarda contactos o el 2.4 guarda fechas. Los dos
  // están montados a la vez y cada uno usa lo del otro en sus correos (el del
  // 2.4 va a los contactos del 2.2; el del 2.2 lista las fechas del 2.4): con
  // esto cada uno vuelve a pedir lo que guardó el otro, en vez de quedarse con
  // lo que leyó al abrirse.
  const [versionDelArranque, setVersionDelArranque] = useState(0)
  const [activeSubStep, setActiveSubStep] = useState<string | null>(null)
  const [tutorialMode, setTutorialMode] = useState(false)
  const [procesandoFirma, setProcesandoFirma] = useState<string | null>(null)
  // Qué se está haciendo con el documento del sub-paso ocupado, para que el
  // botón diga «Generando…» o «Firmando…»: decía «Generando y firmando…»
  // mientras se generaba un borrador que todavía no se iba a firmar.
  const [faseDocumento, setFaseDocumento] = useState<'generando' | 'firmando'>('generando')
  // Revisión del Copiloto antes de cerrar un paso: se activa al accionar el
  // último sub-paso pendiente de la etapa activa. Es consultiva: el supervisor
  // puede confirmar el paso o cancelar en cualquier momento, sin describir
  // nada ni esperar al modelo. listaParaConfirmar pasa a true cuando el
  // Copiloto ya revisó la descripción (o falló al intentarlo).
  const [revisionPaso, setRevisionPaso] = useState<RevisionPaso | null>(null)
  // Datos del documento que el supervisor está por generar y firmar.
  // `revision` es la revisión del Copiloto que llevó hasta aquí (último
  // sub-paso de un paso): si el supervisor cancela el formulario, se devuelve,
  // para no obligarlo a describir el paso otra vez.
  const [datosDocumento, setDatosDocumento] = useState<{
    stepId: number
    subStepId: string
    notas?: string
    plantilla: PlantillaDocumento
    revision?: RevisionPaso
  } | null>(null)
  // El borrador que el supervisor está revisando antes de firmar: ya está
  // generado (sin firmar) y se firma solo si pulsa «Firmar». Vale para los
  // cinco documentos, lleven o no observaciones redactadas por el Copiloto.
  const [revisionBorrador, setRevisionBorrador] = useState<{
    stepId: number
    subStepId: string
    notas: string
    datos: DatosParaGenerar
    revision?: RevisionPaso
    documentoId: number
    /** La huella del borrador que se le muestra: se firma ese y no otro. */
    huella: string
    documento: string
    nombreArchivo: string
    observaciones: string | null
    redactadasConIa: boolean
    motivoNotasTalCual: string | null
  } | null>(null)
  // Lo ya escrito para otro documento de este contrato (su cédula, la fecha de
  // suscripción…), para no pedirlo dos veces. Solo en memoria, a propósito: una
  // cédula no se guarda en el almacenamiento del navegador de un equipo que
  // puede ser compartido.
  const [datosRecordados, setDatosRecordados] = useState<Record<string, string>>({})
  // Se recuerda solo lo que es del contrato. Lo que es de cada documento (el
  // número de informe, el periodo, la factura) no: arrastrarlo al informe
  // siguiente dejaba precargado el del mes pasado, y firmarlo sin notarlo
  // ponía un dato falso en un documento oficial. Lo que el supervisor borró
  // también se olvida, en vez de volver a aparecer.
  const recordarDatos = (plantilla: PlantillaDocumento, valores: Record<string, string>) =>
    setDatosRecordados((prev) => {
      const siguiente = { ...prev }
      for (const campo of plantilla.campos) {
        if (campo.porDocumento) continue
        const valor = valores[campo.clave]?.trim()
        if (valor) siguiente[campo.clave] = valor
        else delete siguiente[campo.clave]
      }
      return siguiente
    })
  // Lo escrito en un formulario que se canceló, por sub-paso, pero SOLO lo que
  // es de ese documento (la factura, el periodo): eso no se recuerda para los
  // demás, pero tampoco debe perderse por un Escape o un Cancelar sin querer.
  // Lo que es del contrato ya está en datosRecordados. Si el borrador lo
  // guardara también, pisaría una corrección hecha después en otro documento:
  // la fecha mal escrita volvía a aparecer (revisión del 29-09-2026).
  const [borradores, setBorradores] = useState<Record<string, Record<string, string>>>({})
  const guardarBorrador = (subStepId: string, plantilla: PlantillaDocumento, valores: Record<string, string>) =>
    setBorradores((prev) => ({ ...prev, [subStepId]: soloLoDelDocumento(plantilla, valores) }))
  // Las tablas (obligaciones, amparos, órdenes de pago) se recuerdan igual que
  // los datos: del contrato, solo las columnas que son del contrato —el texto
  // de cada obligación—, para no volver a copiarlas en cada informe; y por
  // sub-paso, las filas completas de un formulario que se canceló. Ahí sí van
  // completas: la tabla entera queda a la vista en el formulario antes de
  // firmar, a diferencia de un dato suelto que podía pasar inadvertido.
  //
  // A diferencia de la cédula, lo del contrato en las tablas sí se guarda en
  // el equipo: son cláusulas y amparos públicos del contrato en SECOP II, no
  // datos personales, y copiar quince obligaciones en cada informe porque se
  // cerró SICOT era justo lo que la tabla prometía evitar (revisión del
  // 01-10-2026).
  const [tablasRecordadas, setTablasRecordadas] = useState<TablasDelDocumento>({})
  const [borradoresTablas, setBorradoresTablas] = useState<Record<string, TablasDelDocumento>>({})
  const recordarTablas = (plantilla: PlantillaDocumento, tablas: TablasDelDocumento) =>
    setTablasRecordadas((prev) => {
      const siguiente = { ...prev, ...tablasDelContrato(plantilla, tablas) }
      if (contrato) guardarTablasDelContrato(contrato.id, siguiente)
      return siguiente
    })
  const guardarBorradorTablas = (subStepId: string, tablas: TablasDelDocumento) =>
    setBorradoresTablas((prev) => ({ ...prev, [subStepId]: tablas }))
  // Lo recordado es de UN contrato: al cambiar de contrato se olvida, salvo
  // las tablas del contrato guardadas en el equipo, que se cargan de ese.
  useEffect(() => {
    setDatosRecordados({})
    setBorradores({})
    setTablasRecordadas(contrato ? leerTablasDelContrato(contrato.id) : {})
    setBorradoresTablas({})
    setRevisionBorrador(null)
    // eslint-disable-next-line react-hooks/exhaustive-deps -- se recarga al cambiar de contrato, no con cada copia del objeto
  }, [contrato?.id])
  // Firma electrónica real de la cuenta — si el Administrador no la asignó
  // aún, se muestra honestamente en vez de dejar que el intento de firmar falle.
  const [tieneFirma, setTieneFirma] = useState<boolean | null>(null)
  useEffect(() => {
    getMiFirma()
      .then((r) => setTieneFirma(r.tieneFirmaActiva))
      .catch(() => setTieneFirma(null))
  }, [])
  const [chatMsgs, setChatMsgs] = useState<ChatMsg[]>([{ role: 'ai', text: TUTORIAL.welcome }])
  const [chatInput, setChatInput] = useState('')
  // true mientras se espera la respuesta real del Copiloto IA (Ollama) —
  // puede tardar, así que se deshabilita el envío en vez de dejar que se
  // acumulen preguntas superpuestas.
  const [pensando, setPensando] = useState(false)
  // Lo que el Copiloto lleva escrito de la respuesta en curso (respuesta en
  // vivo). Vacío mientras el modelo todavía lee el contrato.
  const [respuestaEnCurso, setRespuestaEnCurso] = useState('')
  // La revisión del paso que espera al modelo (su idSolicitud), para saber al
  // llegar su respuesta si el supervisor la dejó; y lo mismo como estado, para
  // pintar «sin esperar la revisión» en el botón de confirmar.
  const revisionEnCurso = useRef<string | null>(null)
  const [revisando, setRevisando] = useState(false)
  // Mientras SICOT espera a la IA —una pregunta o revisión del Copiloto, o
  // generar o firmar un documento: el sub-paso ocupado sin formulario ni
  // borrador en revisión abiertos—, la pantalla no se apaga sola. Si se
  // apagaba, el teléfono pasaba SICOT a segundo plano y cortaba la petición
  // aunque el supervisor no hubiera salido de la aplicación.
  const esperandoALaIa = pensando || (procesandoFirma !== null && !datosDocumento && !revisionBorrador)
  useEffect(() => {
    if (esperandoALaIa) return mantenerPantallaEncendida()
  }, [esperandoALaIa])
  // El copiloto ocupa una columna alta y ancha a la derecha. Se puede plegar
  // cuando el supervisor quiere leer la ficha o las gráficas a ancho completo;
  // vuelve a abrirse desde el botón de la cabecera.
  const [copilotoAbierto, setCopilotoAbierto] = useState(true)
  const descargandoDesdeElChat = useRef(false)
  // El avatar flotante lo abre aunque esté plegado: App ya llevó a la vista
  // Contrato, que es donde vive el panel.
  useEffect(() => {
    if (pedidosCopiloto > 0) setCopilotoAbierto(true)
  }, [pedidosCopiloto])
  const chatEndRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [chatMsgs])

  // Precalentar el Copiloto en cuanto se abre el contrato.
  //
  // No pinta nada ni se espera: solo le pide al backend que vaya cargando el
  // contexto de este contrato en el modelo. La primera pregunta al Copiloto
  // tardaba ~158 s porque el modelo tenía que leerse el prompt entero; la
  // segunda, 0,8 s, porque ese prefijo ya estaba cacheado. Disparándolo aquí,
  // esa espera ocurre mientras el supervisor lee la ficha.
  //
  // Va en su propio efecto, y no dentro del de las etapas, porque no tiene
  // nada que ver con ellas: si mañana cambia cómo se cargan las etapas, esto
  // no debería verse arrastrado.
  //
  // Y se repite mientras el panel está abierto. Ollama descarga el modelo a
  // los 30 minutos sin uso (sicot.ia.keep-alive), y con él la caché: el
  // 20-10-2026 una pregunta hecha 36 minutos después de abrir el contrato
  // tardó 224 s. Cada 10 minutos —y al volver a la pestaña o a la aplicación,
  // que en el teléfono detiene los temporizadores en segundo plano— el backend
  // mira si el contexto sigue en caché: si sigue, solo renueva la carga del
  // modelo, en menos de un segundo y sin usar la CPU; si no, lo precalienta.
  useEffect(() => {
    if (!contrato) return
    const id = contrato.id
    precalentarCopiloto(id)
    const cadaDiezMinutos = window.setInterval(() => precalentarCopiloto(id), RENOVAR_COPILOTO_MS)
    const alVolver = () => {
      if (document.visibilityState === 'visible') precalentarCopiloto(id)
    }
    document.addEventListener('visibilitychange', alVolver)
    return () => {
      window.clearInterval(cadaDiezMinutos)
      document.removeEventListener('visibilitychange', alVolver)
    }
  }, [contrato])

  // Cuando la cola de cambios deja algo aplicado en el servidor, lo que se ve
  // deja de ser la intención guardada y pasa a ser el estado de verdad: se
  // vuelven a pedir las etapas para traer lo que el servidor calculó con ello
  // (el porcentaje de la etapa, el paso siguiente activado).
  const [vueltaDeLaCola, setVueltaDeLaCola] = useState(0)
  useEffect(
    () =>
      alEnviarCambios(() => {
        setVueltaDeLaCola((n) => n + 1)
        // Los contactos y las fechas de entrega también: si salieron de la
        // copia con la nota «guardados en este equipo», la nota seguiría ahí
        // aunque el servidor ya los tenga.
        setVersionDelArranque((v) => v + 1)
      }),
    [],
  )

  // Cargar etapas/subetapas reales del contrato asignado (autoridad: backend)
  useEffect(() => {
    if (!contrato) return
    let cancelado = false
    getEtapasContrato(contrato.id)
      .then((etapas) => {
        if (!cancelado) setSteps(mapEtapas(etapas))
      })
      .catch((err) => console.error('No se pudieron cargar las etapas del contrato:', err))
    return () => {
      cancelado = true
    }
  }, [contrato, setSteps, vueltaDeLaCola])

  // Alertas reales del contrato (no leídas).
  //
  // `errorAlertas` existe porque un fallo aquí NO puede parecerse a "no hay
  // alertas": la lista vacía se pinta en verde con "Sin pendientes", y decirle
  // eso a un supervisor cuando en realidad no se pudo consultar el servicio es
  // el peor error posible de esta pantalla — le asegura que todo está en orden
  // justo cuando el sistema no lo sabe.
  const { datos: alertasCargadas, error: errorAlertas } = useRecursoDelContrato<AlertaResponse[]>(
    contrato?.id ?? null,
    async (id) => (await getAlertasContrato(id)).filter((a) => !a.leida),
    SIN_ALERTAS,
  )
  // Copia local porque marcar una alerta como leída la retira de la lista sin
  // volver a pedirla al servidor. Se resincroniza cuando el hook trae datos
  // nuevos (otro contrato, o una recarga).
  const [alertasApi, setAlertasApi] = useState<AlertaResponse[]>(SIN_ALERTAS)
  useEffect(() => {
    setAlertasApi(alertasCargadas)
  }, [alertasCargadas])

  // Documentos reales del contrato, con su carga y su error, por la misma razón
  // que las alertas: si fallaba la consulta, la lista quedaba vacía y la vista
  // Documentos decía «Sin generar aún» en los cinco formatos a un supervisor
  // que ya había firmado el Acta de Inicio (auditoría del 02-10-2026).
  const {
    datos: docsContrato,
    cargando: cargandoDocumentos,
    error: errorDocumentos,
    recargar: recargarDocumentos,
  } = useRecursoDelContrato<DocumentoResponse[]>(contrato?.id ?? null, getDocumentosContrato, SIN_DOCUMENTOS)

  const toggleStep = (id: number) => {
    setExpandedSteps((prev) => {
      const n = new Set(prev)
      if (n.has(id)) n.delete(id)
      else n.add(id)
      return n
    })
  }

  const getStats = (step: Step) => {
    const total = step.subSteps.length
    const done = step.subSteps.filter((s) => s.completed).length
    return { total, done, pct: total ? Math.round((done / total) * 100) : 0 }
  }

  // Inicia el modo tutorial en cualquiera de los 6 pasos, desde su primer
  // sub-paso pendiente — generaliza lo que antes solo existía para el Paso 3.
  const handleIniciarPaso = (stepId: number) => {
    const step = steps.find((s) => s.id === stepId)
    const primeraPendiente = step?.subSteps.find((ss) => !ss.completed)
    if (!step || !primeraPendiente) return
    setTab('contrato')
    setTutorialMode(true)
    setActiveSubStep(primeraPendiente.id)
    setExpandedSteps(new Set([stepId]))
    // La guía sale de la plantilla del procedimiento, al instante; antes era
    // una pregunta al modelo por sub-paso (ver data/guiaSubPaso.ts).
    setChatMsgs((prev) => [...prev, { role: 'ai', text: guiaDelSubPaso(step, primeraPendiente), origen: 'guia' }])
  }

  // Ejecuta de verdad la acción de un sub-paso (generar/firmar si aplica y
  // marcar completado en el backend). Separado de handleActionSubStep para
  // que el botón "Confirmar Paso" (después de la revisión de IA) pueda
  // invocarlo directamente, sin volver a pasar por la compuerta de revisión.
  const ejecutarAccionSubPaso = async (
    stepId: number,
    subStepId: string,
    notas?: string,
    datos?: DatosParaGenerar,
    revision?: RevisionPaso,
    /**
     * firmar: el supervisor ya revisó ese borrador y lo firma tal cual, sin
     * regenerarlo; la huella es la del borrador que se le mostró, y va
     * siempre, para que el servidor no firme otra versión. redactarConIa
     * false: «Usar mis notas tal cual», se regenera sin pasar por el Copiloto.
     */
    opciones?: { firmar?: { documentoId: number; huella: string }; redactarConIa?: boolean },
  ) => {
    // Lo que pasa con un documento formal (falta la firma, «estoy armando…»,
    // el fallo al generar o firmar) se dice en el chat del Copiloto: si estaba
    // plegado, se abre, o el botón parecía no hacer nada (punto abierto de la
    // prueba manual del APK, 14-10-2026).
    if (AI_GENERATED_DOCS.has(subStepId)) setCopilotoAbierto(true)
    const sub = steps.flatMap((s) => s.subSteps).find((ss) => ss.id === subStepId)
    if (AI_GENERATED_DOCS.has(subStepId)) {
      const doc = FORMAL_DOCS.find((d) => d.subStepId === subStepId)
      if (!doc || !contrato) return
      if (tieneFirma === false) {
        setChatMsgs((prev) => [
          ...prev,
          {
            role: 'ai',
            text: 'Todavía no se ha obtenido su firma electrónica — solicítela al Administrador desde su panel antes de poder firmar documentos.',
          },
        ])
        return
      }
      // Si el documento de este sub-paso ya está firmado —se firmó y después
      // falló algo al refrescar, o la conexión se cortó justo después de
      // firmar—, no se intenta generar otro: el servidor lo rechaza siempre y
      // el sub-paso quedaba bloqueado para siempre. Se cierra el sub-paso.
      const yaFirmado = docsContrato.some(
        (d) =>
          d.generadoPorIa &&
          d.firmaId &&
          sub?.apiId != null &&
          d.subetapaId === sub.apiId &&
          d.nombre.startsWith(doc.name),
      )
      if (!yaFirmado) {
        // Ocupado desde ya: antes se marcaba después de consultar el catálogo,
        // y en ese rato un doble clic generaba y firmaba dos veces.
        setProcesandoFirma(subStepId)
        const firmar = opciones?.firmar
        setFaseDocumento(firmar ? 'firmando' : 'generando')
        // Antes de generar y firmar se preguntan los datos que el formato pide y
        // el contrato no tiene: firmado, el documento ya no se puede corregir.
        // Si el catálogo no responde NO se genera: seguir sin preguntar era
        // firmar con todo pendiente, justo lo que el formulario evita.
        if (!firmar) {
          let plantilla: PlantillaDocumento | undefined
          try {
            plantilla = (await getPlantillasDocumento()).find((p) => p.tipo === doc.tipo)
          } catch {
            setProcesandoFirma(null)
            if (revision) setRevisionPaso(revision)
            setChatMsgs((prev) => [
              ...prev,
              {
                role: 'ai',
                text: `No pude consultar qué datos pide «${doc.name}», así que no lo generé: firmado sin ellos ya no se podría corregir. Inténtelo de nuevo en un momento.`,
              },
            ])
            return
          }
          if (
            datos === undefined &&
            plantilla &&
            (plantilla.campos.length > 0 || (plantilla.tablas?.length ?? 0) > 0)
          ) {
            // El sub-paso sigue ocupado mientras el formulario esté abierto.
            setDatosDocumento({ stepId, subStepId, notas, plantilla, revision })
            return
          }
        }
        const vigia = vigilarSegundoPlano()
        try {
          if (!firmar) {
            // Redactar las observaciones con el Copiloto tarda minutos en un
            // portátil sin GPU, y el único indicio era el rótulo del botón,
            // que en el teléfono queda fuera de la pantalla. Sin notas, o en
            // un formato sin observaciones, no hay modelo y tarda segundos.
            const redactaElCopiloto = !!notas?.trim() && doc.llevaObservaciones && (opciones?.redactarConIa ?? true)
            if (redactaElCopiloto) {
              setChatMsgs((prev) => [
                ...prev,
                {
                  role: 'ai',
                  text:
                    `Estoy armando «${doc.name}» y redactando sus observaciones: puede tardar varios minutos. ` +
                    'Mantenga SICOT abierto y en pantalla mientras tanto; en el teléfono, si cambia de aplicación, ' +
                    'se corta la conexión. No se firmará nada sin que usted revise el borrador.',
                },
              ])
            }
            const generado = await generarDocumento(contrato.id, {
              tipo: doc.tipo,
              subetapaId: sub?.apiId ?? null,
              notas: notas ?? null,
              datos: datos?.campos ?? {},
              tablas: datos?.tablas ?? {},
              redactarConIa: opciones?.redactarConIa ?? true,
            })
            // Nada se firma sin que el supervisor vea el borrador: queda sin
            // firmar y se le muestra, con sus observaciones si las lleva. Antes
            // solo se detenía cuando el Copiloto había redactado; en los demás
            // casos se firmaba directo, sin que nadie abriera el PDF, y sin la
            // huella de lo generado. El sub-paso sigue ocupado hasta que decida.
            setRevisionBorrador({
              stepId,
              subStepId,
              notas: notas ?? '',
              datos: datos ?? { campos: {}, tablas: {} },
              revision,
              documentoId: generado.id,
              huella: generado.huellaDelBorrador,
              documento: doc.name,
              nombreArchivo: generado.nombre,
              observaciones: generado.observaciones,
              redactadasConIa: generado.observacionesRedactadasConIa,
              motivoNotasTalCual: generado.motivoNotasTalCual,
            })
            return
          }
          await firmarDocumento(contrato.id, firmar.documentoId, firmar.huella)
          setBorradores((prev) => {
            const resto = { ...prev }
            delete resto[subStepId]
            return resto
          })
          setBorradoresTablas((prev) => {
            const resto = { ...prev }
            delete resto[subStepId]
            return resto
          })
          // Lo que lleva, los datos pendientes y por qué las observaciones van
          // tal cual ya se vieron en la revisión: aquí basta con decir dónde
          // quedó.
          setChatMsgs((prev) => [
            ...prev,
            { role: 'ai', text: `Firmé «${doc.name}» tal como lo revisó. Lo encuentra en Documentos.` },
          ])
        } catch (e) {
          setProcesandoFirma(null)
          if (revision) setRevisionPaso(revision)
          // Aquí NO se reintenta, a diferencia del chat del copiloto: si la
          // conexión se cortó, el servidor puede haber hecho su parte igualmente
          // —así pasó al medirlo—. Lo honesto es decir qué se estaba haciendo y
          // qué comprobar. Hasta el 02-10-2026 todo fallo sin respuesta del
          // servidor se atribuía al «Copiloto IA», también al firmar un
          // borrador ya revisado, donde el Copiloto no interviene.
          const motivo = e instanceof ApiError ? motivoDelServidor(e) : null
          const cortadoPorSegundoPlano = motivo === null && vigia.seOculto()
          const porQueSeCorto = cortadoPorSegundoPlano
            ? 'la conexión se cortó porque SICOT pasó a segundo plano, y el teléfono cierra las conexiones de las aplicaciones que no están en pantalla'
            : 'no hubo respuesta del servidor de SICOT'
          const texto = firmar
            ? motivo === null
              ? // La firma pudo registrarse aunque la respuesta se perdiera, y un
                // documento firmado no se vuelve a firmar.
                `No sé si la firma de «${doc.name}» llegó a registrarse: ${porQueSeCorto}. Revise Documentos antes de volver a firmar.`
              : `No pude firmar «${doc.name}»: ${motivo}`
            : motivo === null
              ? // Generar solo arma un borrador, que se reutiliza al repetir: no
                // hay documento repetido que temer, y nada quedó firmado.
                `No pude generar «${doc.name}»: ${porQueSeCorto}. No se firmó nada; vuelva a intentarlo` +
                (cortadoPorSegundoPlano ? ' con SICOT en pantalla mientras se genera.' : ' en un momento.')
              : `No pude generar «${doc.name}»: ${motivo}`
          setChatMsgs((prev) => [...prev, { role: 'ai', text: texto }])
          // La lista de documentos se refresca (al volver, si se cortó por
          // segundo plano): si el documento llegó a firmarse, el siguiente
          // intento lo encuentra y cierra el sub-paso en vez de chocar.
          if (cortadoPorSegundoPlano) void esperarPrimerPlano().then(recargarDocumentos)
          else recargarDocumentos()
          return
        } finally {
          vigia.terminar()
        }
      } else {
        setChatMsgs((prev) => [
          ...prev,
          {
            role: 'ai',
            text: `«${doc.name}» ya estaba firmado en este sub-paso; lo marco como completado.`,
          },
        ])
      }
      setProcesandoFirma(null)
      // Fuera del try de la firma: si refrescar el registro falla, la firma ya
      // se hizo, y decir «no pude completar la firma» era falso y dejaba el
      // sub-paso sin cerrar.
      void onRefreshRegistros().catch(() => {})
      recargarDocumentos()
    }
    const previo = steps
    const updated = steps.map((s) =>
      s.id !== stepId
        ? s
        : { ...s, subSteps: s.subSteps.map((ss) => (ss.id === subStepId ? { ...ss, completed: true } : ss)) },
    )
    setSteps(updated)

    // Persistir en el backend: el servidor recalcula porcentaje y estado de la etapa.
    // Si esta era la última subetapa del paso, además activa la primera subetapa
    // del paso siguiente — sin esto ninguna etapa posterior queda "EN_CURSO" y el
    // tutorial se queda sin poder continuar al terminar un paso.
    if (sub?.apiId && contrato) {
      try {
        // `null` es «guardado en este equipo, sin red»: el cambio está en la
        // cola y la lectura de abajo lo trae encima de la copia, rotulado
        // como pendiente de enviar (ADR-015).
        const enviado = await cambiarEstadoSubetapa(contrato.id, { id: sub.apiId, codigo: sub.id }, 'COMPLETADA')
        const pasoActualizado = updated.find((s) => s.id === stepId)
        const eraUltimaDelPaso = pasoActualizado?.subSteps.every((s) => s.completed)
        if (eraUltimaDelPaso) {
          const siguientePaso = steps.find((s) => s.id === stepId + 1)
          const primeraSubSiguiente = siguientePaso?.subSteps[0]
          if (primeraSubSiguiente?.apiId) {
            await cambiarEstadoSubetapa(
              contrato.id,
              { id: primeraSubSiguiente.apiId, codigo: primeraSubSiguiente.id },
              'EN_CURSO',
            )
          }
        }
        const etapas = await getEtapasContrato(contrato.id)
        setSteps(mapEtapas(etapas))
        if (enviado === null) {
          setChatMsgs((prev) => [
            ...prev,
            {
              role: 'ai',
              text:
                `Sin conexión con el servidor: el cierre de «${sub.label}» quedó guardado en este equipo y se ` +
                'enviará en cuanto vuelva la red. Si el servidor no lo acepta, se lo diré con el motivo.',
            },
          ])
        }
      } catch (e) {
        // No se guardó de verdad en el servidor — revertir el avance optimista
        // en vez de dejar un porcentaje "completado" que un refresco borraría
        // sin explicación.
        setSteps(previo)
        const mensaje = e instanceof ApiError ? e.message : 'No se pudo guardar el avance en el servidor.'
        setChatMsgs((prev) => [
          ...prev,
          {
            role: 'ai',
            text: `No pude registrar "${sub?.label ?? subStepId}" como completado: ${mensaje}. Vuelva a intentarlo.`,
          },
        ])
        return
      }
    }

    // Avanzar dentro del tutorial del paso activo (cualquiera de los 6, no solo el 3)
    if (tutorialMode) {
      const pasoActual = updated.find((s) => s.id === stepId)
      const orden = pasoActual?.subSteps.map((ss) => ss.id) ?? []
      const idx = orden.indexOf(subStepId)
      if (idx !== -1 && idx < orden.length - 1) {
        const next = orden[idx + 1]
        setActiveSubStep(next)
        const nextSub = pasoActual?.subSteps.find((ss) => ss.id === next)
        if (pasoActual && nextSub)
          setChatMsgs((prev) => [...prev, { role: 'ai', text: guiaDelSubPaso(pasoActual, nextSub), origen: 'guia' }])
      } else {
        setActiveSubStep(null)
        setTutorialMode(false)
        // No se pisa `steps` aquí: el fetch de arriba (getEtapasContrato) ya trajo
        // el estado real del servidor, incluida la activación del paso siguiente —
        // sobrescribirlo con el snapshot local `updated` borraba esa activación.
        const doneMsg = TUTORIAL[`step${stepId}done`] ?? `Ha completado el Paso ${stepId}.`
        setChatMsgs((prev) => [...prev, { role: 'ai', text: doneMsg }])
      }
    }
  }

  // Punto de entrada del botón de cada sub-paso. Si es el último sub-paso
  // pendiente del paso activo, en vez de completarlo de inmediato, primero le
  // pide al supervisor que describa qué hizo/verificó en todo el paso y pasa
  // esa descripción por el Copiloto para una revisión — recién con la
  // confirmación explícita del supervisor después de leer la revisión se
  // marca el paso como completado de verdad.
  const handleActionSubStep = (stepId: number, subStepId: string) => {
    const step = steps.find((s) => s.id === stepId)
    const orden = step?.subSteps.map((ss) => ss.id) ?? []
    const esUltimoSubPaso = orden.length > 0 && orden.indexOf(subStepId) === orden.length - 1
    if (esUltimoSubPaso && step) {
      const documento = AI_GENERATED_DOCS.has(subStepId)
        ? FORMAL_DOCS.find((d) => d.subStepId === subStepId)?.name
        : undefined
      setRevisionPaso({ stepId, subStepId, listaParaConfirmar: false, documento })
      // La revisión espera en el chat: con el panel plegado, nada cambiaba en pantalla.
      setCopilotoAbierto(true)
      const resumenSubPasos = step.subSteps.map((ss) => `- ${ss.id} ${ss.label}`).join('\n')
      setChatMsgs((prev) => [
        ...prev,
        {
          role: 'ai',
          text: `Antes de marcar el Paso ${stepId} como completado, cuénteme brevemente qué hizo o verificó en cada uno de estos puntos:\n${resumenSubPasos}\n\nEscriba su respuesta abajo y la reviso con usted. La revisión es de apoyo: si prefiere, confirme el paso sin ella o cancele con los botones de abajo. Si tiene una duda, pregúntela; no la tomo como su descripción.`,
        },
      ])
      return
    }
    ejecutarAccionSubPaso(stepId, subStepId)
  }

  // Revisión de IA de la descripción del supervisor sobre lo que hizo en el
  // paso — es una revisión de apoyo basada solo en lo que el supervisor
  // describe (SICOT no verifica evidencia externa todavía); la decisión
  // final de confirmar sigue siendo del supervisor.
  //
  // Se manda solo la descripción y el número del paso: las instrucciones de
  // la revisión las arma el servidor. Antes viajaban desde aquí dentro de la
  // «pregunta», y el servidor las metía en el bloque de contenido no
  // confiable, bajo la orden de no seguir las instrucciones que trajera: un
  // modelo pequeño a veces contestaba que no podía seguirlas, y cualquiera
  // podía cambiarlas (auditoría del 02-10-2026). Tampoco va el historial: la
  // revisión es de este paso, no de la conversación.
  const revisarPaso = async (descripcion: string) => {
    if (!contrato || !revisionPaso || pensando) return
    const paso = revisionPaso.stepId
    const idSolicitud = idDeSolicitud()
    const sigueEnCurso = () => revisionEnCurso.current === idSolicitud
    revisionEnCurso.current = idSolicitud
    setRevisando(true)
    setPensando(true)
    // Desde ya, y no al llegar la revisión: si el supervisor confirma sin
    // esperarla, su descripción sigue yendo como notas del documento.
    setRevisionPaso((prev) => (prev ? { ...prev, descripcion } : prev))
    try {
      // Igual que una pregunta suelta: la revisión no cambia nada en el
      // servidor, así que si el teléfono la corta se repite al volver, con el
      // mismo idSolicitud para no pagar otra inferencia. Si mientras tanto el
      // supervisor la dejó (confirmó o canceló), no se repite.
      const respuesta = await repetirSiSeCorta(
        () =>
          sigueEnCurso()
            ? preguntarCopilotoEnVivo(contrato.id, descripcion, undefined, { idSolicitud, revisarPaso: paso }, (t) => {
                if (sigueEnCurso()) setRespuestaEnCurso(t)
              })
            : Promise.reject(new Error('Revisión abandonada')),
        {
          alCortarsePorSegundoPlano: () => {
            if (sigueEnCurso()) avisarQueSeRepite()
          },
          alCortarseLaConexion: () => {
            if (sigueEnCurso()) avisarQueSeReintenta()
          },
        },
      )
      if (sigueEnCurso()) setChatMsgs((prev) => [...prev, mensajeDelCopiloto(respuesta)])
    } catch (e) {
      if (!sigueEnCurso()) return
      const mensaje = porQueNoRespondio(e)
      // Unos mensajes traen su punto final y otros no; pegarle siempre uno
      // dejaba «(Ollama).. Puede confirmar».
      const frase = /[.!?]$/.test(mensaje) ? mensaje : `${mensaje}.`
      setChatMsgs((prev) => [
        ...prev,
        {
          role: 'ai',
          text: `No pude revisar su descripción: ${frase} Puede confirmar de todas formas si está seguro, o volver a intentarlo.`,
        },
      ])
    } finally {
      // Una revisión que el supervisor dejó ya no manda: «Pensando…» se apagó
      // al dejarla, y puede haber otra pregunta en curso.
      if (sigueEnCurso()) {
        revisionEnCurso.current = null
        setRevisando(false)
        setPensando(false)
        setRespuestaEnCurso('')
        setRevisionPaso((prev) => (prev ? { ...prev, listaParaConfirmar: true } : prev))
      }
    }
  }

  // El supervisor deja la revisión en curso (confirma o cancela sin
  // esperarla). La inferencia sigue en el servidor, pero su respuesta ya no
  // se pinta: él decidió no esperarla, y una revisión que llega después de
  // cerrar el paso solo confundiría.
  const dejarLaRevisionEnCurso = () => {
    if (!revisionEnCurso.current) return
    revisionEnCurso.current = null
    setRevisando(false)
    setPensando(false)
    setRespuestaEnCurso('')
  }

  const confirmarRevision = () => {
    const r = revisionPaso
    dejarLaRevisionEnCurso()
    setRevisionPaso(null)
    if (r) ejecutarAccionSubPaso(r.stepId, r.subStepId, r.descripcion, undefined, r)
  }

  const cancelarRevision = (pedidoPorEscrito: boolean) => {
    const r = revisionPaso
    dejarLaRevisionEnCurso()
    setRevisionPaso(null)
    if (pedidoPorEscrito && r)
      setChatMsgs((prev) => [
        ...prev,
        {
          role: 'ai',
          text: `De acuerdo, el Paso ${r.stepId} sigue abierto. Cuando quiera cerrarlo, vuelva a pulsar el botón de su último sub-paso.`,
        },
      ])
  }

  // Si el teléfono cortó la pregunta porque SICOT pasó a segundo plano, el
  // motivo no es Ollama y decirlo sería mentir: en el APK de Android el sistema
  // destruye las conexiones de la aplicación a los pocos segundos de salir de
  // ella, y una pregunta al copiloto sobre CPU dura minutos (ver
  // services/segundoPlano.ts). Se avisa y se repite al volver.
  const avisarQueSeRepite = () =>
    setChatMsgs((prev) => [
      ...prev,
      {
        role: 'ai',
        text: 'La respuesta se cortó porque SICOT pasó a segundo plano: el teléfono cierra las conexiones de las aplicaciones que no están en pantalla. La vuelvo a pedir ahora; mantenga SICOT abierto hasta que responda.',
      },
    ])

  // La conexión se cortó por el camino (sin red un momento, o un intermediario
  // que se cansó de esperar) con SICOT en pantalla. El servidor sigue con la
  // respuesta y la guarda para el reintento, que lleva el mismo idSolicitud.
  const avisarQueSeReintenta = () =>
    setChatMsgs((prev) => [
      ...prev,
      {
        role: 'ai',
        text: 'Se cortó la conexión con el servidor antes de que llegara la respuesta. El servidor la sigue preparando; la vuelvo a pedir en unos segundos.',
      },
    ])

  // Pregunta real al Copiloto IA (Ollama, vía CopilotoChatService en el
  // backend) — anclada a los datos reales del contrato y al estado real de
  // sus etapas. Ya no hay coincidencia de palabras clave local.
  const preguntarAlCopiloto = async (texto: string) => {
    if (!contrato || pensando) return
    setPensando(true)
    // Uno por pregunta, creado fuera de la petición para que el reintento
    // lleve el mismo: así el servidor reconoce la pregunta y no lanza otra
    // inferencia mientras la del primer intento sigue en la CPU.
    const idSolicitud = idDeSolicitud()
    try {
      const respuesta = await repetirSiSeCorta(
        () => preguntarCopilotoEnVivo(contrato.id, texto, chatMsgs, { idSolicitud }, setRespuestaEnCurso),
        { alCortarsePorSegundoPlano: avisarQueSeRepite, alCortarseLaConexion: avisarQueSeReintenta },
      )
      setChatMsgs((prev) => [...prev, mensajeDelCopiloto(respuesta)])
    } catch (e) {
      setChatMsgs((prev) => [...prev, { role: 'ai', text: `No pude responder: ${porQueNoRespondio(e)}` }])
    } finally {
      setPensando(false)
      setRespuestaEnCurso('')
    }
  }

  const sendChat = () => {
    const text = chatInput.trim()
    if (!text || pensando) return
    setChatMsgs((prev) => [...prev, { role: 'user', text }])
    setChatInput('')
    // Con una revisión del paso abierta, no todo lo escrito es la descripción:
    // una pregunta va al chat y «cancelar» sale (ver revisionDelPaso.ts).
    const intencion = revisionPaso ? intencionEnLaRevision(text) : 'pregunta'
    if (intencion === 'cancelar') {
      cancelarRevision(true)
    } else if (intencion === 'descripcion' && revisionPaso && !revisionPaso.listaParaConfirmar) {
      revisarPaso(text)
    } else {
      preguntarAlCopiloto(text)
    }
  }

  const quickChat = (q: string) => {
    if (pensando) return
    setChatMsgs((prev) => [...prev, { role: 'user', text: q }])
    preguntarAlCopiloto(q)
  }

  // Navigate to a specific sub-step from another view
  const goToSubStep = (subStepId: string, stepId: number) => {
    setTab('contrato')
    setExpandedSteps((prev) => {
      const n = new Set(prev)
      n.add(stepId)
      return n
    })
    setActiveSubStep(subStepId)
    setTutorialMode(true)
  }

  // Llevar la pantalla hasta el sub-paso que se acaba de abrir.
  //
  // `goToSubStep` cambiaba de pestaña, expandía el paso y lo marcaba como
  // activo, pero dejaba el recorrido donde estuviera — casi siempre arriba del
  // todo. En un monitor el sub-paso suele quedar a la vista igual; en un
  // teléfono no: «Ir a firmar» en Documentos, o «Abrir la carga de fotos» del
  // Copiloto, dejaban al supervisor ante los seis pasos plegados, teniendo que
  // buscar a mano a dónde lo acababan de llevar (visto en el emulador el
  // 08-10-2026). El sub-paso se pinta DESPUÉS de expandir su paso, así que se
  // busca en el siguiente fotograma y no en el mismo.
  useEffect(() => {
    if (!activeSubStep || tab !== 'contrato') return
    const fotograma = requestAnimationFrame(() => {
      document.querySelector('.substep-row.active-tutorial')?.scrollIntoView({ block: 'center' })
    })
    return () => cancelAnimationFrame(fotograma)
  }, [activeSubStep, tab])

  const irAPaso = (stepId: number) => {
    setTab('contrato')
    setExpandedSteps(new Set([stepId]))
  }

  // Lo que una respuesta del Copiloto ofrece abrir. Solo se llama cuando el
  // supervisor pulsa el botón de la acción, nunca al llegar la respuesta, y
  // reutiliza la misma navegación que el resto del panel: ninguna acción
  // firma, marca ni genera nada. Abrir el sub-paso de un documento deja al
  // supervisor ante «Firmar documento», con su revisión y su confirmación.
  const ejecutarAccionDelCopiloto = (accion: AccionCopiloto) => {
    // El paso se deduce del sub-paso («2.7» → 2) si el servidor no lo manda.
    const paso = accion.paso ?? (accion.subpaso ? Number(accion.subpaso.split('.')[0]) : null)
    const existeElSubPaso = steps.some((s) => s.id === paso && s.subSteps.some((ss) => ss.id === accion.subpaso))
    switch (accion.tipo) {
      case 'IR_A_PASO':
        if (paso) irAPaso(paso)
        return
      case 'IR_A_SUBPASO':
      case 'ABRIR_DOCUMENTO':
      case 'ABRIR_EVIDENCIA':
        if (paso && accion.subpaso && existeElSubPaso) goToSubStep(accion.subpaso, paso)
        else if (paso) irAPaso(paso)
        return
      case 'MOSTRAR_ALERTAS':
        setTab('alertas')
        return
      case 'MOSTRAR_DOCUMENTOS':
        setTab('documentos')
        return
      case 'DESCARGAR_DOCUMENTO': {
        const doc = docsContrato.find((d) => d.id === accion.documentoId)
        // Si la lista de este equipo todavía no lo tiene, se abre la vista
        // Documentos en vez de bajar a ciegas un archivo que no se puede nombrar.
        if (!contrato || !doc) {
          setTab('documentos')
          return
        }
        // Un segundo toque mientras baja un PDF de varios MB abría otro «Guardar
        // como»: la vista Documentos ya lo evitaba, el botón del chat no.
        if (descargandoDesdeElChat.current) return
        descargandoDesdeElChat.current = true
        descargarDocumento(contrato.id, doc.id, doc.nombre)
          .then((resultado) => {
            if (resultado === 'guardado')
              setChatMsgs((prev) => [...prev, { role: 'ai', text: `«${doc.nombre}» quedó guardado en el teléfono.` }])
          })
          .catch((e) => {
            // El mismo motivo que en la vista Documentos: «intente de nuevo» solo
            // cuando reintentar puede arreglarlo.
            setChatMsgs((prev) => [...prev, { role: 'ai', text: motivoDelFalloDeDescarga(e, `«${doc.nombre}»`) }])
          })
          .finally(() => {
            descargandoDesdeElChat.current = false
          })
        return
      }
      case 'IR_A_CONFIGURACION':
        onOpenSettings()
        return
    }
  }

  // El paso que el Copiloto debe guiar ahora mismo — cualquiera de los 6, no solo el 3.
  const activeStep = steps.find((s) => s.status === 'active')

  // ── Alerta de cronograma ──
  // El cálculo NO vive aquí. Vivía: este panel repartía el plazo en seis
  // segmentos iguales y comparaba con la etapa activa, mientras el backend
  // calculaba la brecha entre plazo consumido y avance para decidir si
  // persistir una alerta. Dos algoritmos, dos definiciones de "atraso", y SICOT
  // podía decir "el paso 3 va a tiempo" en la pantalla y "brecha de 39 puntos"
  // en la bandeja del mismo contrato el mismo día.
  //
  // Ahora el cálculo está una sola vez, del lado que FR-002 declara autoridad de
  // las reglas de negocio (service/Cronograma.java), y esta pantalla lo pinta.
  const { datos: cronograma } = useRecursoDelContrato<CronogramaResponse | null>(
    contrato?.id ?? null,
    getCronograma,
    null,
  )

  const alertaCronograma: LiveAlert | null =
    cronograma && cronograma.semaforo !== 'SIN_DATOS'
      ? {
          id: 'cronograma-' + (cronograma.etapaActual ?? 0),
          severity: cronograma.semaforo === 'ROJO' ? 'critica' : cronograma.semaforo === 'AMARILLO' ? 'leve' : 'ok',
          text: cronograma.mensaje,
        }
      : null

  const dismiss = (id: string) => {
    setDismissed((prev) => new Set(prev).add(id))
    if (id.startsWith('api-')) {
      const apiId = Number(id.slice(4))
      marcarAlertaLeida(apiId, contrato?.id ?? null).catch((err) =>
        console.error('No se pudo marcar la alerta como leída:', err),
      )
      setAlertasApi((prev) => prev.filter((a) => a.id !== apiId))
    }
  }

  // El botón de una alerta lleva al sitio relacionado con ella, o no se
  // muestra (ver destinoDeAlerta.ts).
  const destinoDe = (tipo: TipoAlerta) => destinoDeAlerta(tipo, activeStep?.id ?? null)
  const irADestino = (destino: DestinoDeAlerta) => {
    if (destino.vista === 'documentos') setTab('documentos')
    else irAPaso(destino.paso)
  }

  // ── Barra de recorrido: una sección por paso real del contrato ──
  //
  // El color de cada sección sale del estado real: verde si el paso está
  // cerrado, el semáforo del cronograma si es el paso en curso, y gris si
  // todavía no se ha tocado.
  const etiquetasBarra = etiquetasDeBarra(steps.map((s) => s.title))
  const stages: Stage[] = steps.map((s, indice) => {
    const total = s.subSteps.length
    const hechos = s.subSteps.filter((ss) => ss.completed).length
    const pct = total ? Math.round((hechos / total) * 100) : 0
    const enCurso = s.status === 'active'
    const state: Stage['state'] =
      pct === 100
        ? 'ok'
        : enCurso
          ? alertaCronograma?.severity === 'critica'
            ? 'critical'
            : alertaCronograma?.severity === 'leve'
              ? 'warning'
              : 'ok'
          : pct === 0
            ? 'idle'
            : 'warning'
    const detalleBase = DETALLE_PASO[s.id] ?? `${hechos} de ${total} sub-pasos cerrados en este paso.`
    return {
      key: claveDePaso(s.id),
      label: etiquetasBarra[indice],
      fullLabel: s.title,
      pct,
      state,
      detail:
        s.id === 6 && contrato?.fechaFin
          ? `${detalleBase} El contrato termina el ${formatFecha(contrato.fechaFin)}.`
          : detalleBase,
    }
  })
  const etapaActualKey = activeStep ? claveDePaso(activeStep.id) : null

  // Cifras del contrato — todas sobre el estado real de las subetapas y las
  // fechas registradas; alimentan los indicadores de la vista Contrato.
  const todosLosSubPasos = steps.flatMap((s) => s.subSteps)
  const avanceGlobal = todosLosSubPasos.length
    ? Math.round((todosLosSubPasos.filter((ss) => ss.completed).length / todosLosSubPasos.length) * 100)
    : 0
  const etapasCerradas = steps.filter((s) => s.subSteps.length > 0 && s.subSteps.every((ss) => ss.completed)).length
  const subPasosPendientesPasoActivo = activeStep ? activeStep.subSteps.filter((ss) => !ss.completed).length : 0
  const diasDeVigencia = contrato?.fechaFin
    ? Math.ceil((new Date(contrato.fechaFin).getTime() - Date.now()) / 86400000)
    : null

  // ── Bandeja de entrada: todo lo que le falta al supervisor ahora mismo ──
  //
  // Cada elemento sale de un dato real: la alerta de cronograma calculada con
  // las fechas del contrato, las alertas no leídas del backend, la firma
  // electrónica de la cuenta, los sub-pasos pendientes de la etapa en curso y
  // los documentos que el Copiloto ya redactó pero siguen sin firma.
  const itemsBandeja: ItemBandeja[] = (() => {
    const items: ItemBandeja[] = []

    if (alertaCronograma && !dismissed.has(alertaCronograma.id)) {
      const destino = destinoDe('CRONOGRAMA')
      items.push({
        id: alertaCronograma.id,
        severidad:
          alertaCronograma.severity === 'critica' ? 'critica' : alertaCronograma.severity === 'leve' ? 'leve' : 'ok',
        categoria: 'Cronograma',
        titulo: alertaCronograma.severity === 'ok' ? 'El paso en curso va a tiempo' : 'El paso en curso está atrasado',
        detalle: alertaCronograma.text,
        accionLabel: destino?.etiqueta,
        onAccion: destino ? () => irADestino(destino) : undefined,
        onDescartar: () => dismiss(alertaCronograma.id),
      })
    }

    for (const a of alertasApi) {
      const id = 'api-' + a.id
      if (dismissed.has(id)) continue
      const destino = destinoDe(a.tipo)
      items.push({
        id,
        severidad: a.prioridad === 'ALTA' ? 'critica' : a.prioridad === 'MEDIA' ? 'leve' : 'info',
        categoria: 'Alerta',
        titulo: a.tipo.charAt(0) + a.tipo.slice(1).toLowerCase().replace(/_/g, ' '),
        detalle: a.mensaje,
        fecha: formatFecha(a.fechaCreacion.slice(0, 10)),
        accionLabel: destino?.etiqueta,
        onAccion: destino ? () => irADestino(destino) : undefined,
        onDescartar: () => dismiss(id),
      })
    }

    if (tieneFirma === false) {
      items.push({
        id: 'firma-pendiente',
        severidad: 'critica',
        categoria: 'Firma',
        titulo: 'Falta su firma electrónica',
        detalle:
          'Todavía no se le ha asignado una firma electrónica activa. Sin ella no puede firmar los documentos formales del contrato: solicítela al Administrador.',
      })
    }

    if (activeStep) {
      for (const ss of activeStep.subSteps.filter((s) => !s.completed)) {
        const esDocIa = AI_GENERATED_DOCS.has(ss.id)
        items.push({
          id: 'sub-' + ss.id,
          severidad: 'leve',
          categoria: 'Tarea',
          titulo: `Falta ${esDocIa ? 'firmar' : 'completar'}: ${ss.id} ${ss.label}`,
          detalle: `Paso ${activeStep.id} — ${activeStep.title}. Responsable: ${ss.responsible} · Documento: ${ss.document}.`,
          accionLabel: esDocIa ? 'Ir a firmar' : 'Abrir sub-paso',
          onAccion: () => goToSubStep(ss.id, activeStep.id),
        })
      }
    }

    for (const doc of docsContrato.filter((d) => d.generadoPorIa && d.estado !== 'APROBADO')) {
      const formal = FORMAL_DOCS.find((f) => doc.nombre.startsWith(f.name))
      items.push({
        id: 'doc-' + doc.id,
        severidad: 'leve',
        categoria: 'Documento',
        titulo: `Documento sin firmar: ${doc.nombre}`,
        detalle: formal
          ? `${formal.code === 'PENDIENTE_DE_DEFINIR' ? 'Código pendiente de definir' : formal.code} · generado por SICOT en el sub-paso ${formal.subStepId}.`
          : 'Documento generado por SICOT que todavía no tiene firma registrada.',
        fecha: fechaDelCentro(doc.fechaSubida),
        accionLabel: formal ? 'Ir a firmar' : undefined,
        onAccion: formal ? () => goToSubStep(formal.subStepId, formal.step) : undefined,
      })
    }
    // Sin la lista, la bandeja no puede decir qué borradores siguen sin firmar:
    // callarlo sería dar a entender que no queda ninguno.
    if (errorDocumentos) {
      items.push({
        id: 'documentos-sin-consultar',
        severidad: 'leve',
        categoria: 'Documento',
        titulo: 'No se pudieron consultar los documentos del contrato',
        detalle: 'Hasta que el servidor responda, esta bandeja no puede mostrar los documentos que siguen sin firmar.',
        accionLabel: 'Reintentar',
        onAccion: recargarDocumentos,
      })
    }

    const orden: Record<ItemBandeja['severidad'], number> = { critica: 0, leve: 1, info: 2, ok: 3 }
    return items.sort((a, b) => orden[a.severidad] - orden[b.severidad])
  })()

  const pendientesCriticos = itemsBandeja.filter((i) => i.severidad === 'critica').length

  // Mientras se consulta el contrato real todavía no sabemos si hay uno o no
  // — mostrar "sin contrato asignado" en ese instante sería falso.
  if (!contrato && cargandoContrato) {
    return <CargandoContratoState usuario={usuario} onLogout={onLogout} onOpenSettings={onOpenSettings} />
  }

  // La consulta falló: no se puede afirmar que no tenga contrato asignado.
  if (!contrato && errorContrato) {
    return <ErrorContratoState usuario={usuario} onLogout={onLogout} onOpenSettings={onOpenSettings} />
  }

  // Ya se consultó correctamente y no hay contrato asignado: estado vacío real.
  if (!contrato) {
    return (
      <EmptyContractState
        usuario={usuario}
        onLogout={onLogout}
        onOpenSettings={onOpenSettings}
        onStartTour={onStartTour}
      />
    )
  }

  const navGroups: NavGroup[] = [
    {
      label: 'Supervisión',
      items: [
        {
          id: 'bandeja',
          label: 'Bandeja de entrada',
          icon: <IconInbox size={17} />,
          count: itemsBandeja.length,
          countTone: pendientesCriticos > 0 ? 'alert' : 'normal',
        },
        { id: 'contrato', label: 'Contrato', icon: <IconContract size={17} /> },
      ],
    },
    {
      label: 'Seguimiento',
      items: [
        {
          id: 'alertas',
          label: 'Alertas',
          icon: <IconBell size={17} />,
          count: alertasApi.length,
          countTone: 'alert',
        },
        { id: 'documentos', label: 'Documentos', icon: <IconFileText size={17} /> },
        { id: 'registros', label: 'Registros', icon: <IconHistory size={17} /> },
      ],
    },
  ]

  return (
    <AppShell
      roleBadge="Panel Supervisor"
      groups={navGroups}
      activeId={tab}
      onNavigate={(id) => setTab(id as Tab)}
      usuario={usuario}
      onLogout={onLogout}
      onOpenSettings={onOpenSettings}
      title={TITULO_VISTA[tab].titulo}
      subtitle={`${contrato.numeroContrato} · ${TITULO_VISTA[tab].sub}`}
      barra={
        contratos.length > 1 && onSeleccionarContrato ? (
          <>
            <label htmlFor="selector-contrato" style={{ fontSize: 12, color: 'var(--text-muted)' }}>
              Contrato en supervisión
            </label>
            <select
              id="selector-contrato"
              aria-label="Contrato en supervisión"
              value={contrato.id}
              onChange={(e) => onSeleccionarContrato(Number(e.target.value))}
              style={{
                fontSize: 12,
                padding: '5px 8px',
                width: 'auto',
                minWidth: 220,
                maxWidth: '100%',
                fontFamily: 'var(--font-mono)',
              }}
            >
              {/* Los activos primero: son los que se supervisan; un borrador o
                  uno cancelado se puede abrir, pero no es lo que se busca. */}
              {[...contratos]
                .sort((a, b) => (a.estado === 'ACTIVO' ? 0 : 1) - (b.estado === 'ACTIVO' ? 0 : 1))
                .map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.numeroContrato}
                    {c.estado !== 'ACTIVO' ? ` (${c.estado.toLowerCase()})` : ''}
                  </option>
                ))}
            </select>
            <span style={{ fontSize: 11.5, color: 'var(--text-muted)' }}>
              {contratos.filter((c) => c.estado === 'ACTIVO').length} activos de {contratos.length} asignados
            </span>
          </>
        ) : undefined
      }
      actions={
        <>
          {tab === 'contrato' && (
            <button
              className="btn-ghost"
              onClick={() => setCopilotoAbierto((v) => !v)}
              title={copilotoAbierto ? 'Ocultar el panel del Copiloto' : 'Mostrar el panel del Copiloto'}
              style={{ padding: '7px 13px', fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 6 }}
            >
              <AvatarIcon id={prefs.avatarId} size={13} />
              {copilotoAbierto ? 'Ocultar copiloto' : 'Mostrar copiloto'}
            </button>
          )}
          <button
            className="btn-ghost"
            onClick={onStartTour}
            style={{ padding: '7px 13px', fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 6 }}
          >
            <IconPlay size={10} /> Tutorial
          </button>
        </>
      }
    >
      {/* ── Bandeja de entrada ── */}
      {tab === 'bandeja' && (
        <Bandeja
          nombre={usuario.nombre}
          contrato={contrato}
          steps={steps}
          // Se pasa calculado, no se recalcula dentro: es la misma cifra que la
          // barra de la vista Contrato. Dos cálculos del "mismo" porcentaje
          // acaban divergiendo, y entonces el supervisor ve dos números para lo
          // que él lee como una sola cosa.
          avanceGlobal={avanceGlobal}
          items={itemsBandeja}
          errorAlertas={errorAlertas}
          onIrAContrato={() => setTab('contrato')}
          onContinuarPaso={handleIniciarPaso}
        />
      )}

      {/* ── Contrato ── */}
      {tab === 'contrato' && (
        <div className="split-panel" style={{ flex: 1, display: 'flex', overflow: 'hidden', minWidth: 0 }}>
          <div style={{ flex: 1, overflowY: 'auto', padding: '16px 22px 26px', minWidth: 0 }}>
            {/* Recorrido del contrato — ocupa todo el ancho de la columna de
                  contenido, con la etapa actual marcada. */}
            <div data-tour="progreso" style={{ marginBottom: 16 }}>
              <StageJourney
                stages={stages}
                currentKey={etapaActualKey}
                overallPct={avanceGlobal}
                onStageClick={(key) => setExpandedSteps(new Set([pasoDeClave(key)]))}
              />
            </div>

            {/* Indicadores del contrato.

                Eran cuatro y la primera era «Avance global», el mismo número que
                la barra de arriba ya muestra en grande. Verlo dos veces seguidas
                no lo refuerza: hace dudar de si son dos cifras distintas. Las
                tres que quedan dicen cada una algo que la barra no dice. */}
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))',
                gap: 12,
                marginBottom: 16,
              }}
            >
              <StatCard
                label="Etapas cerradas"
                value={`${etapasCerradas}/${steps.length}`}
                hint="Pasos del GCCON-P-010 completados en su totalidad"
                icon={<IconCheck size={15} />}
              />
              <StatCard
                label="Sub-pasos por cerrar"
                value={String(subPasosPendientesPasoActivo)}
                hint={activeStep ? `En el Paso ${activeStep.id} — ${activeStep.title}` : 'Sin paso en curso'}
                tone={subPasosPendientesPasoActivo > 0 ? 'warn' : 'accent'}
                icon={<IconInbox size={15} />}
              />
              <StatCard
                label="Vigencia"
                value={diasDeVigencia === null ? '—' : diasDeVigencia < 0 ? 'Vencido' : `${diasDeVigencia} días`}
                hint={
                  diasDeVigencia === null
                    ? 'El contrato no tiene fechas registradas'
                    : diasDeVigencia < 0
                      ? `Terminó el ${formatFecha(contrato.fechaFin)}`
                      : `Termina el ${formatFecha(contrato.fechaFin)}`
                }
                tone={diasDeVigencia !== null && diasDeVigencia < 0 ? 'danger' : 'info'}
                icon={<IconClock size={15} />}
              />
            </div>

            {/* Ficha completa del contrato */}
            <div data-tour="contrato" style={{ marginBottom: 16 }}>
              <ContratoInfo contrato={contrato} />
            </div>

            {/* Etapas y sub-pasos */}
            <SectionHeader
              eyebrow="Proceso GCCON-P-010"
              title="Etapas y sub-pasos"
              desc="Cada paso se abre para ver sus puntos de control. El Copiloto lo guía en el paso activo y SICOT arma los documentos formales con los datos del contrato; usted revisa y firma."
            />
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 22 }}>
              {steps.length === 0 && (
                <div
                  className="card"
                  style={{ padding: '28px 20px', textAlign: 'center', fontSize: 13, color: 'var(--text-muted)' }}
                >
                  Las etapas de este contrato todavía no se han cargado desde el servidor.
                </div>
              )}
              {steps.map((step) => {
                const stats = getStats(step)
                const isOpen = expandedSteps.has(step.id)
                return (
                  <div
                    key={step.id}
                    className="card"
                    style={{
                      overflow: 'hidden',
                      borderColor: step.status === 'active' ? 'var(--accent)' : 'var(--border)',
                    }}
                  >
                    <div className="step-row" onClick={() => toggleStep(step.id)}>
                      <StepCircle n={step.id} status={step.status} />
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div
                          style={{
                            fontSize: 13.5,
                            fontWeight: 500,
                            color: step.status === 'pending' ? 'var(--text-muted)' : 'var(--text-primary)',
                            marginBottom: 5,
                          }}
                        >
                          Paso {step.id} — {step.title}
                          {step.status === 'active' && (
                            <span
                              style={{
                                marginLeft: 8,
                                fontSize: 9.5,
                                fontWeight: 700,
                                letterSpacing: '0.08em',
                                color: 'var(--on-accent)',
                                background: 'var(--accent)',
                                padding: '2px 7px',
                                borderRadius: 3,
                              }}
                            >
                              ACTIVO
                            </span>
                          )}
                        </div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <ProgressBar pct={stats.pct} />
                          <span
                            style={{
                              fontSize: 11,
                              color: 'var(--text-muted)',
                              whiteSpace: 'nowrap',
                              fontFamily: 'var(--font-mono)',
                            }}
                          >
                            {stats.done}/{stats.total} — {stats.pct}%
                          </span>
                        </div>
                      </div>
                      <span
                        style={{
                          color: 'var(--text-muted)',
                          fontSize: 12,
                          transform: isOpen ? 'rotate(180deg)' : 'none',
                          transition: 'transform 0.2s',
                        }}
                      >
                        ▼
                      </span>
                    </div>

                    {isOpen && (
                      <div style={{ borderTop: '1px solid var(--border)', padding: '8px 8px 12px' }}>
                        {step.subSteps.map((ss, idx) => {
                          const isActiveTutorial = activeSubStep === ss.id
                          const isAiDoc = AI_GENERATED_DOCS.has(ss.id)
                          const procesando = procesandoFirma === ss.id
                          const actionLabel = procesando
                            ? datosDocumento?.subStepId === ss.id
                              ? 'Esperando datos…'
                              : revisionBorrador?.subStepId === ss.id
                                ? 'Revisando el borrador…'
                                : faseDocumento === 'firmando'
                                  ? 'Firmando…'
                                  : 'Generando…'
                            : isAiDoc
                              ? 'Firmar documento'
                              : 'Marcar completado'
                          const completedLabel = isAiDoc ? 'Firmado' : 'Completado'
                          return (
                            <div key={ss.id} className={`substep-row${isActiveTutorial ? ' active-tutorial' : ''}`}>
                              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                                <div
                                  style={{
                                    width: 18,
                                    height: 18,
                                    borderRadius: '50%',
                                    flexShrink: 0,
                                    background: ss.completed
                                      ? 'var(--accent)'
                                      : isActiveTutorial
                                        ? 'var(--accent-soft)'
                                        : 'transparent',
                                    border: ss.completed
                                      ? 'none'
                                      : isActiveTutorial
                                        ? '1.5px solid var(--accent)'
                                        : '1.5px solid var(--step-pending)',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    fontSize: 10,
                                  }}
                                >
                                  {ss.completed ? (
                                    <span style={{ color: 'var(--on-accent)', fontWeight: 700 }}>✓</span>
                                  ) : (
                                    <span
                                      style={{
                                        color: isActiveTutorial ? 'var(--accent)' : 'var(--text-muted)',
                                        fontSize: 9,
                                      }}
                                    >
                                      {idx + 1}
                                    </span>
                                  )}
                                </div>
                                <span
                                  style={{
                                    fontSize: 13,
                                    color: ss.completed
                                      ? 'var(--text-muted)'
                                      : isActiveTutorial
                                        ? 'var(--text-primary)'
                                        : 'var(--text-secondary)',
                                    textDecoration: ss.completed ? 'line-through' : 'none',
                                  }}
                                >
                                  <span
                                    style={{
                                      fontFamily: 'var(--font-mono)',
                                      fontSize: 11.5,
                                      color: 'var(--text-muted)',
                                      marginRight: 6,
                                    }}
                                  >
                                    {ss.id}
                                  </span>
                                  {ss.label}
                                  {isAiDoc && !ss.completed && (
                                    <span
                                      style={{
                                        marginLeft: 6,
                                        fontSize: 10,
                                        color: 'var(--accent)',
                                        background: 'var(--accent-soft)',
                                        padding: '1px 5px',
                                        borderRadius: 3,
                                      }}
                                    >
                                      SICOT genera
                                    </span>
                                  )}
                                  {/* El «tercer estado» de ADR-015: hecho en este
                                      equipo, todavía sin aceptar por el servidor.
                                      Sin el rótulo, un cierre guardado sin red se
                                      veía igual que uno que ya está en el
                                      expediente. */}
                                  {ss.pendienteDeEnviar && (
                                    <span
                                      title="Hecho sin conexión. Se envía al servidor cuando vuelva la red."
                                      style={{
                                        marginLeft: 6,
                                        fontSize: 10,
                                        color: 'var(--alert-leve)',
                                        border: '1px solid var(--alert-leve)',
                                        padding: '0 5px',
                                        borderRadius: 3,
                                        textDecoration: 'none',
                                        display: 'inline-block',
                                      }}
                                    >
                                      Pendiente de enviar
                                    </span>
                                  )}
                                </span>
                              </div>
                              {/* Responsable y documento van como texto, no como
                                  etiquetas de color.

                                  Eran tres «chips» por sub-paso y hay 27 sub-pasos:
                                  81 rectángulos de color en una sola pantalla,
                                  todos con el mismo peso visual que las cosas que
                                  sí piden acción. El dato sigue estando; lo que se
                                  quita es el grito.

                                  El estado «Pendiente» desapareció porque ya se lee
                                  dos veces en la misma fila: el círculo va vacío y
                                  el texto va sin tachar. Cuando SÍ está cerrado se
                                  dice, porque «Firmado» y «Completado» no son lo
                                  mismo y esa diferencia sí importa. */}
                              <div
                                style={{
                                  display: 'flex',
                                  gap: 8,
                                  flexWrap: 'wrap',
                                  paddingLeft: 26,
                                  alignItems: 'center',
                                  fontSize: 11.5,
                                  color: 'var(--text-muted)',
                                  lineHeight: 1.5,
                                }}
                              >
                                <span>
                                  {ss.responsible} · {ss.document}
                                </span>
                                {ss.completed && (
                                  <span style={{ color: isAiDoc ? 'var(--info)' : 'var(--accent)', fontWeight: 600 }}>
                                    {completedLabel}
                                  </span>
                                )}
                                {isActiveTutorial && !ss.completed && (
                                  <button
                                    className="btn-green"
                                    onClick={() => handleActionSubStep(step.id, ss.id)}
                                    disabled={procesando}
                                    style={{
                                      padding: '4px 12px',
                                      fontSize: 11,
                                      marginLeft: 4,
                                      opacity: procesando ? 0.6 : 1,
                                      cursor: procesando ? 'default' : 'pointer',
                                    }}
                                  >
                                    {actionLabel}
                                  </button>
                                )}
                              </div>
                              {SUBETAPAS_CON_EVIDENCIA_FOTOGRAFICA.has(ss.id) && contrato && (
                                <EvidenciaFotografica
                                  contratoId={contrato.id}
                                  subetapaApiId={ss.apiId}
                                  codigoSubetapa={ss.id}
                                  onCargada={() => {
                                    void onRefreshRegistros()
                                    recargarDocumentos()
                                  }}
                                />
                              )}
                              {/* Los pasos del proceso real (etapa 2). Solo en el paso
                                  en curso o uno abierto sin cerrar: cerrado, ya no hay
                                  nada que hacer en él. */}
                              {contrato && !ss.completed && ss.documentosRequeridos === ACTA_FIRMADA_Y_ENVIO && (
                                <ActaFirmadaYEnvio
                                  contratoId={contrato.id}
                                  subetapaApiId={ss.apiId}
                                  documentos={docsContrato}
                                  onCargado={() => {
                                    void onRefreshRegistros()
                                    recargarDocumentos()
                                  }}
                                />
                              )}
                              {contrato && !ss.completed && HERRAMIENTA_POR_PASO[ss.label] === 'VERIFICACION_SECOP' && (
                                <VerificacionSecop contratoId={contrato.id} />
                              )}
                              {/* Paso 2.2: los profesionales de SST y ambiental, y el correo
                                  que ya sale dirigido a ellos. A diferencia de las otras
                                  herramientas, sigue con el sub-paso cerrado: el correo de
                                  las fechas del 2.4 sale dirigido a estos contactos. */}
                              {contrato && HERRAMIENTA_POR_PASO[ss.label] === 'CORREO_SST' && (
                                <ContactosSstAmbiental
                                  contrato={contrato}
                                  version={versionDelArranque}
                                  onGuardado={() => {
                                    setVersionDelArranque((v) => v + 1)
                                    // Sin red el guardado va a la cola y el registro no
                                    // se puede refrescar: no es un error de nadie.
                                    void onRefreshRegistros().catch(() => {})
                                  }}
                                />
                              )}
                              {/* Paso 2.4, en el orden en que lo hace el supervisor según el
                                  paso 5 de la hoja de Alex: cómo contactar al contratista, el
                                  correo, el Excel de bienes y cantidades que va adjunto (al de
                                  SST no se le manda el pedido) y las fechas que acuerden.
                                  Las fechas siguen con el 2.4 cerrado: los recordatorios
                                  llegan después y una fecha se puede mover. Van en la misma
                                  posición abierto o cerrado, para que cerrar el paso no las
                                  desmonte y se pierda una edición a medias. */}
                              {contrato && HERRAMIENTA_POR_PASO[ss.label] === 'CORREO_CONTRATISTA' && (
                                <>
                                  {!ss.completed && (
                                    <>
                                      <ContactoDelContratista
                                        contrato={contrato}
                                        onActualizado={(c) => {
                                          onContratoActualizado?.(c)
                                          void onRefreshRegistros()
                                        }}
                                      />
                                      <BorradorDeCorreo tipo="CORREO_CONTRATISTA" contrato={contrato} />
                                      <SolicitudDePedido contrato={contrato} />
                                    </>
                                  )}
                                  <AcuerdoDeEntregas
                                    contrato={contrato}
                                    version={versionDelArranque}
                                    onGuardado={() => {
                                      setVersionDelArranque((v) => v + 1)
                                      void onRefreshRegistros().catch(() => {})
                                    }}
                                  />
                                </>
                              )}
                            </div>
                          )
                        })}
                      </div>
                    )}
                  </div>
                )
              })}
            </div>

            {/* Gráficas del contrato */}
            <SectionHeader
              eyebrow="Indicadores"
              title="Tablero del contrato"
              desc="Cifras calculadas con el estado real del contrato: subetapas cerradas en el servidor, documentos efectivamente generados y las fechas registradas."
            />
            <ContratoGraficas steps={steps} docs={docsContrato} contrato={contrato} />
          </div>

          {/* ── Copiloto ── */}
          {/* Copiloto — columna completa, de la cabecera al pie: es una
                conversación de trabajo, no un widget, y necesita alto para que se
                lean las respuestas largas sin desplazarse a cada rato. */}
          {copilotoAbierto && (
            <PanelCopiloto
              prefs={prefs}
              contrato={contrato}
              chatMsgs={chatMsgs}
              pensando={pensando}
              respuestaEnCurso={respuestaEnCurso}
              tutorialMode={tutorialMode}
              revisionPaso={revisionPaso}
              activeStep={activeStep}
              chatInput={chatInput}
              chatEndRef={chatEndRef}
              sugerencias={QUICK_SUGGESTIONS}
              onCerrar={() => setCopilotoAbierto(false)}
              onCambiarEntrada={setChatInput}
              onEnviar={sendChat}
              onSugerencia={quickChat}
              onIniciarPaso={handleIniciarPaso}
              revisando={revisando}
              onConfirmarRevision={confirmarRevision}
              onCancelarRevision={() => cancelarRevision(false)}
              onAccion={ejecutarAccionDelCopiloto}
            />
          )}
        </div>
      )}

      {/* ── Alertas ── */}
      {tab === 'alertas' && (
        <VistaAlertas
          contrato={contrato}
          alertaCronograma={alertaCronograma}
          alertasApi={alertasApi}
          errorAlertas={errorAlertas}
          destinoDe={destinoDe}
          onIrA={irADestino}
        />
      )}
      {/* ── Documentos ── */}
      {tab === 'documentos' && (
        <VistaDocumentos
          contrato={contrato}
          docsContrato={docsContrato}
          cargando={cargandoDocumentos}
          error={errorDocumentos}
          onReintentar={recargarDocumentos}
          tieneFirma={tieneFirma}
          onIrASubPaso={goToSubStep}
        />
      )}

      {/* ── Registros ── */}
      {tab === 'registros' && <Registros extra={registros} />}

      {datosDocumento && (
        <DatosDelDocumento
          plantilla={datosDocumento.plantilla}
          iniciales={{ ...datosRecordados, ...borradores[datosDocumento.subStepId] }}
          tablasIniciales={{ ...tablasRecordadas, ...borradoresTablas[datosDocumento.subStepId] }}
          onCancelar={(valores, tablas) => {
            const d = datosDocumento
            recordarDatos(d.plantilla, valores)
            guardarBorrador(d.subStepId, d.plantilla, valores)
            recordarTablas(d.plantilla, tablas)
            guardarBorradorTablas(d.subStepId, tablas)
            setDatosDocumento(null)
            setProcesandoFirma(null)
            if (d.revision) setRevisionPaso(d.revision)
          }}
          onConfirmar={(datos, tablas) => {
            const d = datosDocumento
            recordarDatos(d.plantilla, datos)
            recordarTablas(d.plantilla, tablas)
            // Se guarda también aquí: si la generación falla, el siguiente
            // intento vuelve a traer la factura y el periodo. Se borra al firmar.
            guardarBorrador(d.subStepId, d.plantilla, datos)
            guardarBorradorTablas(d.subStepId, tablas)
            setDatosDocumento(null)
            ejecutarAccionSubPaso(d.stepId, d.subStepId, d.notas, { campos: datos, tablas }, d.revision)
          }}
        />
      )}

      {revisionBorrador && contrato && (
        <RevisarAntesDeFirmar
          contratoId={contrato.id}
          documentoId={revisionBorrador.documentoId}
          documento={revisionBorrador.documento}
          nombreArchivo={revisionBorrador.nombreArchivo}
          notas={revisionBorrador.notas}
          observaciones={revisionBorrador.observaciones}
          redactadasConIa={revisionBorrador.redactadasConIa}
          motivoNotasTalCual={revisionBorrador.motivoNotasTalCual}
          onFirmar={() => {
            const r = revisionBorrador
            setRevisionBorrador(null)
            ejecutarAccionSubPaso(r.stepId, r.subStepId, r.notas, r.datos, r.revision, {
              firmar: { documentoId: r.documentoId, huella: r.huella },
            })
          }}
          onUsarNotas={() => {
            const r = revisionBorrador
            setRevisionBorrador(null)
            ejecutarAccionSubPaso(r.stepId, r.subStepId, r.notas, r.datos, r.revision, { redactarConIa: false })
          }}
          onCancelar={() => {
            const r = revisionBorrador
            setRevisionBorrador(null)
            setProcesandoFirma(null)
            if (r.revision) setRevisionPaso(r.revision)
            setChatMsgs((prev) => [
              ...prev,
              {
                role: 'ai',
                text: `No firmé «${r.documento}»: quedó como borrador sin firmar en Documentos. Cuando vuelva a firmar el paso, se genera de nuevo.`,
              },
            ])
            recargarDocumentos()
          }}
        />
      )}
    </AppShell>
  )
}
