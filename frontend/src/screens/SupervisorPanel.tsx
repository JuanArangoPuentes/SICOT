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
import { usePrefs } from '@/prefs'
import AppShell, { type NavGroup } from '@/components/AppShell'
import Registros, { type Registro } from '@/components/Registros'
import Bandeja, { type ItemBandeja } from '@/components/supervisor/Bandeja'
import ContratoInfo from '@/components/supervisor/ContratoInfo'
import ContratoGraficas from '@/components/supervisor/ContratoGraficas'
import VistaDocumentos from '@/components/supervisor/VistaDocumentos'
import VistaAlertas from '@/components/supervisor/VistaAlertas'
import {
  CargandoContratoState,
  EmptyContractState,
  ErrorContratoState,
} from '@/components/supervisor/EstadosSinContrato'
import { Chip, SectionHeader, StageJourney, StatCard, type LiveAlert, type Stage } from '@/components/ui'
import {
  AvatarIcon, IconArrowRight, IconBell, IconChart, IconCheck, IconChevron, IconClock,
  IconContract, IconFileText, IconHistory, IconInbox, IconPlay,
} from '@/components/icons'
import { AI_GENERATED_DOCS, TUTORIAL, FORMAL_DOCS } from '@/data/contractFlow'
import type { Step, Tab, ChatMsg } from '@/types/domain'
import type { AuthResponse, AlertaResponse, ContratoResponse, DocumentoResponse } from '@/services/api/types'
import { getEtapasContrato, cambiarEstadoSubetapa } from '@/services/etapaService'
import { getAlertasContrato, marcarAlertaLeida } from '@/services/alertaService'
import { getDocumentosContrato, generarDocumento, firmarDocumento, preguntarCopiloto } from '@/services/documentoService'
import { getMiFirma } from '@/services/firmaService'
import { ApiError } from '@/services/api/client'
import { mapEtapas } from '@/services/mappers'
import { formatFecha } from '@/services/format'

// Pregunta que se envía al Copiloto real para guiar cada sub-paso del
// tutorial — reemplaza el texto estático que antes vivía en TUTORIAL. Al
// pasar por Ollama con los datos reales del contrato, la respuesta es
// específica a ESTE contrato (contratista, valor, fechas reales), no un
// texto genérico repetido igual para cualquier contrato.
const preguntaGuiaSubPaso = (step: Step, sub: { id: string; label: string }) =>
  `Explíqueme en detalle y de forma extremadamente específica qué debo hacer exactamente en el ` +
  `sub-paso ${sub.id} — "${sub.label}" — del Paso ${step.id} (${step.title}). Deme el paso a paso ` +
  `completo y concreto: qué debo revisar o conseguir, de dónde exactamente lo consigo, y qué debo ` +
  `hacer en SICOT al terminar.`

// Chips de preguntas frecuentes — el label es corto para el botón, la
// pregunta real que se envía al Copiloto va completa para que la respuesta
// de Ollama sea específica y no un genérico "¿en qué te ayudo?".
const QUICK_SUGGESTIONS: Array<{ label: string; question: string }> = [
  { label: '¿Qué documentos necesito?', question: '¿Qué necesito hacer en el paso en el que estoy ahora mismo? Deme el paso a paso completo: de dónde consigo cada insumo y cómo lo registro en SICOT.' },
  { label: 'GCCON-F-031', question: '¿Qué es el GCCON-F-031, quién lo firma y en qué sub-paso se genera?' },
  { label: 'GIL-F-010', question: '¿Qué es el GIL-F-010, quién lo firma y en qué sub-paso se genera?' },
  { label: 'ESUCON', question: '¿Qué es "ESUCON"? ¿Tiene código de formato oficial confirmado? ¿En qué sub-paso se genera y quién lo firma?' },
  { label: 'GCCON-F-030', question: '¿Qué es el GCCON-F-030? ¿Es lo mismo que el acta de liquidación? ¿En qué sub-paso se genera?' },
]

// La barra de recorrido tiene una sección por paso REAL del contrato (los
// mismos que trae el backend), no por una agrupación aparte: así el número que
// se ve en la barra es el mismo "Paso N" del que hablan el copiloto, la bandeja
// y el acordeón de etapas.
const claveDePaso = (id: number) => `paso-${id}`
const pasoDeClave = (key: string) => Number(key.replace('paso-', '')) || 1

// Resumen de lo que ocurre en cada paso — se muestra bajo la barra grande.
const DETALLE_PASO: Record<number, string> = {
  1: 'Estudios previos (GCCON-F-046), CDP y garantías, suscripción y publicación en SECOP II, y designación del supervisor.',
  2: 'Revisión del contratista, cronograma y seguridad social, y firma del Acta de Inicio GCCON-F-018.',
  3: 'Inspección física en bodega, evidencia fotográfica y firma del Informe de Supervisión GCCON-F-031.',
  4: 'Verificación de PILA y factura electrónica DIAN, y firma del Acta de Recibo a Satisfacción GIL-F-010.',
  5: 'Vigencia de garantías, orden de pago y CRP, y firma de la certificación de cumplimiento que respalda el pago.',
  6: 'Cumplimiento total del objeto, Informe Final GCCON-F-030 y archivo del expediente digital en SIGEP.',
}

/** "INSPECCIÓN — Monitoreo y Ejecución" -> "Inspección". */
function etiquetaCorta(titulo: string): string {
  const cabeza = titulo.split('—')[0].trim()
  const texto = cabeza || titulo
  return texto.charAt(0).toUpperCase() + texto.slice(1).toLowerCase()
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
  if (status === 'completed') return (
    <div style={{ width: 26, height: 26, borderRadius: '50%', flexShrink: 0, background: 'var(--accent)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <svg width="13" height="10" viewBox="0 0 13 10" fill="none">
        <path d="M1 5L5 9L12 1" stroke="var(--on-accent)" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </div>
  )
  if (status === 'active') return (
    <div style={{ width: 26, height: 26, borderRadius: '50%', flexShrink: 0, border: '2px solid var(--accent)', background: 'var(--accent-soft)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 600, color: 'var(--accent)' }}>{n}</div>
  )
  return (
    <div style={{ width: 26, height: 26, borderRadius: '50%', flexShrink: 0, border: '2px solid var(--step-pending)', background: 'transparent', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 600, color: 'var(--text-muted)' }}>{n}</div>
  )
}

function ProgressBar({ pct }: { pct: number }) {
  return (
    <div style={{ height: 4, background: 'var(--bg-elevated)', borderRadius: 2, overflow: 'hidden', flexGrow: 1 }}>
      <div style={{ height: '100%', width: `${pct}%`, background: pct === 100 ? 'var(--accent)' : 'var(--accent-dim)', borderRadius: 2, transition: 'width 0.4s' }} />
    </div>
  )
}

export default function SupervisorPanel({
  steps,
  setSteps,
  usuario,
  contrato,
  cargandoContrato,
  errorContrato,
  onLogout,
  onOpenSettings,
  onStartTour,
  registros,
  onRefreshRegistros,
}: {
  steps: Step[]
  setSteps: (s: Step[]) => void
  usuario: AuthResponse
  contrato: ContratoResponse | null
  cargandoContrato: boolean
  errorContrato: boolean
  onLogout: () => void
  onOpenSettings: () => void
  onStartTour: () => void
  registros: Registro[]
  onRefreshRegistros: () => Promise<void>
}) {
  const { prefs } = usePrefs()
  const [dismissed, setDismissed] = useState<Set<string>>(new Set())
  const [tab, setTab] = useState<Tab>('bandeja')
  const [expandedSteps, setExpandedSteps] = useState<Set<number>>(new Set([1]))
  const [activeSubStep, setActiveSubStep] = useState<string | null>(null)
  const [tutorialMode, setTutorialMode] = useState(false)
  const [procesandoFirma, setProcesandoFirma] = useState<string | null>(null)
  // Revisión de IA obligatoria antes de cerrar un paso: se activa al accionar
  // el último sub-paso pendiente de la etapa activa. listaParaConfirmar pasa
  // a true solo después de que el Copiloto ya revisó la descripción del
  // supervisor (o falló al intentarlo) — antes de eso no se puede confirmar.
  const [revisionPaso, setRevisionPaso] = useState<{ stepId: number; subStepId: string; listaParaConfirmar: boolean } | null>(null)
  // Firma electrónica real de la cuenta — si el Administrador no la asignó
  // aún, se muestra honestamente en vez de dejar que el intento de firmar falle.
  const [tieneFirma, setTieneFirma] = useState<boolean | null>(null)
  useEffect(() => {
    getMiFirma().then(r => setTieneFirma(r.tieneFirmaActiva)).catch(() => setTieneFirma(null))
  }, [])
  const [chatMsgs, setChatMsgs] = useState<ChatMsg[]>([{ role: 'ai', text: TUTORIAL.welcome }])
  const [chatInput, setChatInput] = useState('')
  // true mientras se espera la respuesta real del Copiloto IA (Ollama) —
  // puede tardar, así que se deshabilita el envío en vez de dejar que se
  // acumulen preguntas superpuestas.
  const [pensando, setPensando] = useState(false)
  // El copiloto ocupa una columna alta y ancha a la derecha. Se puede plegar
  // cuando el supervisor quiere leer la ficha o las gráficas a ancho completo;
  // vuelve a abrirse desde el botón de la cabecera.
  const [copilotoAbierto, setCopilotoAbierto] = useState(true)
  const chatEndRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [chatMsgs])

  // Cargar etapas/subetapas reales del contrato asignado (autoridad: backend)
  useEffect(() => {
    if (!contrato) return
    let cancelado = false
    getEtapasContrato(contrato.id)
      .then(etapas => {
        if (!cancelado) setSteps(mapEtapas(etapas))
      })
      .catch(err => console.error('No se pudieron cargar las etapas del contrato:', err))
    return () => { cancelado = true }
  }, [contrato, setSteps])

  // Alertas reales del contrato (no leídas).
  //
  // `errorAlertas` existe porque un fallo aquí NO puede parecerse a "no hay
  // alertas": la lista vacía se pinta en verde con "Sin pendientes", y decirle
  // eso a un supervisor cuando en realidad no se pudo consultar el servicio es
  // el peor error posible de esta pantalla — le asegura que todo está en orden
  // justo cuando el sistema no lo sabe.
  const [alertasApi, setAlertasApi] = useState<AlertaResponse[]>([])
  const [errorAlertas, setErrorAlertas] = useState(false)
  useEffect(() => {
    if (!contrato) {
      setAlertasApi([])
      setErrorAlertas(false)
      return
    }
    let cancelado = false
    setErrorAlertas(false)
    getAlertasContrato(contrato.id)
      .then(lista => {
        if (!cancelado) setAlertasApi(lista.filter(a => !a.leida))
      })
      .catch(err => {
        console.error('No se pudieron cargar las alertas del contrato:', err)
        if (!cancelado) {
          setAlertasApi([])
          setErrorAlertas(true)
        }
      })
    return () => { cancelado = true }
  }, [contrato])

  // Documentos reales del contrato
  const [docsContrato, setDocsContrato] = useState<DocumentoResponse[]>([])
  useEffect(() => {
    if (!contrato) {
      setDocsContrato([])
      return
    }
    let cancelado = false
    getDocumentosContrato(contrato.id)
      .then(lista => {
        if (!cancelado) setDocsContrato(lista)
      })
      .catch(err => console.error('No se pudieron cargar los documentos del contrato:', err))
    return () => { cancelado = true }
  }, [contrato])

  const toggleStep = (id: number) => {
    setExpandedSteps(prev => { const n = new Set(prev); n.has(id) ? n.delete(id) : n.add(id); return n })
  }

  const getStats = (step: Step) => {
    const total = step.subSteps.length
    const done = step.subSteps.filter(s => s.completed).length
    return { total, done, pct: total ? Math.round((done / total) * 100) : 0 }
  }

  // Inicia el modo tutorial en cualquiera de los 6 pasos, desde su primer
  // sub-paso pendiente — generaliza lo que antes solo existía para el Paso 3.
  const handleIniciarPaso = (stepId: number) => {
    const step = steps.find(s => s.id === stepId)
    const primeraPendiente = step?.subSteps.find(ss => !ss.completed)
    if (!step || !primeraPendiente) return
    setTab('contrato')
    setTutorialMode(true)
    setActiveSubStep(primeraPendiente.id)
    setExpandedSteps(new Set([stepId]))
    preguntarAlCopiloto(preguntaGuiaSubPaso(step, primeraPendiente))
  }

  // Ejecuta de verdad la acción de un sub-paso (generar/firmar si aplica y
  // marcar completado en el backend). Separado de handleActionSubStep para
  // que el botón "Confirmar Paso" (después de la revisión de IA) pueda
  // invocarlo directamente, sin volver a pasar por la compuerta de revisión.
  const ejecutarAccionSubPaso = async (stepId: number, subStepId: string) => {
    const sub = steps.flatMap(s => s.subSteps).find(ss => ss.id === subStepId)
    if (AI_GENERATED_DOCS.has(subStepId)) {
      const doc = FORMAL_DOCS.find(d => d.subStepId === subStepId)
      if (!doc || !contrato) return
      if (tieneFirma === false) {
        setChatMsgs(prev => [...prev, { role: 'ai', text: 'Todavía no se ha obtenido su firma electrónica — solicítela al Administrador desde su panel antes de poder firmar documentos.' }])
        return
      }
      setProcesandoFirma(subStepId)
      try {
        const generado = await generarDocumento(contrato.id, { tipo: doc.tipo, subetapaId: sub?.apiId ?? null })
        await firmarDocumento(contrato.id, generado.id)
        await onRefreshRegistros()
        getDocumentosContrato(contrato.id).then(setDocsContrato).catch(() => { })
      } catch (e) {
        setProcesandoFirma(null)
        const mensaje = e instanceof ApiError ? e.message : 'No se pudo generar o firmar el documento con el Copiloto IA.'
        setChatMsgs(prev => [...prev, { role: 'ai', text: `No pude completar la firma: ${mensaje}` }])
        return
      }
      setProcesandoFirma(null)
    }
    const previo = steps
    const updated = steps.map(s =>
      s.id !== stepId ? s : { ...s, subSteps: s.subSteps.map(ss => ss.id === subStepId ? { ...ss, completed: true } : ss) }
    )
    setSteps(updated)

    // Persistir en el backend: el servidor recalcula porcentaje y estado de la etapa.
    // Si esta era la última subetapa del paso, además activa la primera subetapa
    // del paso siguiente — sin esto ninguna etapa posterior queda "EN_CURSO" y el
    // tutorial se queda sin poder continuar al terminar un paso.
    if (sub?.apiId && contrato) {
      try {
        await cambiarEstadoSubetapa(sub.apiId, 'COMPLETADA')
        const pasoActualizado = updated.find(s => s.id === stepId)
        const eraUltimaDelPaso = pasoActualizado?.subSteps.every(s => s.completed)
        if (eraUltimaDelPaso) {
          const siguientePaso = steps.find(s => s.id === stepId + 1)
          const primeraSubSiguiente = siguientePaso?.subSteps[0]
          if (primeraSubSiguiente?.apiId) {
            await cambiarEstadoSubetapa(primeraSubSiguiente.apiId, 'EN_CURSO')
          }
        }
        const etapas = await getEtapasContrato(contrato.id)
        setSteps(mapEtapas(etapas))
      } catch (e) {
        // No se guardó de verdad en el servidor — revertir el avance optimista
        // en vez de dejar un porcentaje "completado" que un refresco borraría
        // sin explicación.
        setSteps(previo)
        const mensaje = e instanceof ApiError ? e.message : 'No se pudo guardar el avance en el servidor.'
        setChatMsgs(prev => [...prev, { role: 'ai', text: `No pude registrar "${sub?.label ?? subStepId}" como completado: ${mensaje}. Vuelva a intentarlo.` }])
        return
      }
    }

    // Avanzar dentro del tutorial del paso activo (cualquiera de los 6, no solo el 3)
    if (tutorialMode) {
      const pasoActual = updated.find(s => s.id === stepId)
      const orden = pasoActual?.subSteps.map(ss => ss.id) ?? []
      const idx = orden.indexOf(subStepId)
      if (idx !== -1 && idx < orden.length - 1) {
        const next = orden[idx + 1]
        setActiveSubStep(next)
        const nextSub = pasoActual?.subSteps.find(ss => ss.id === next)
        if (pasoActual && nextSub) preguntarAlCopiloto(preguntaGuiaSubPaso(pasoActual, nextSub))
      } else {
        setActiveSubStep(null)
        setTutorialMode(false)
        // No se pisa `steps` aquí: el fetch de arriba (getEtapasContrato) ya trajo
        // el estado real del servidor, incluida la activación del paso siguiente —
        // sobrescribirlo con el snapshot local `updated` borraba esa activación.
        const doneMsg = TUTORIAL[`step${stepId}done`] ?? `Ha completado el Paso ${stepId}.`
        setChatMsgs(prev => [...prev, { role: 'ai', text: doneMsg }])
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
    const step = steps.find(s => s.id === stepId)
    const orden = step?.subSteps.map(ss => ss.id) ?? []
    const esUltimoSubPaso = orden.length > 0 && orden.indexOf(subStepId) === orden.length - 1
    if (esUltimoSubPaso && step) {
      setRevisionPaso({ stepId, subStepId, listaParaConfirmar: false })
      const resumenSubPasos = step.subSteps.map(ss => `- ${ss.id} ${ss.label}`).join('\n')
      setChatMsgs(prev => [...prev, {
        role: 'ai',
        text: `Antes de marcar el Paso ${stepId} como completado, cuénteme brevemente qué hizo o verificó en cada uno de estos puntos:\n${resumenSubPasos}\n\nEscriba su respuesta abajo y la reviso con usted.`,
      }])
      return
    }
    ejecutarAccionSubPaso(stepId, subStepId)
  }

  // Revisión de IA de la descripción del supervisor sobre lo que hizo en el
  // paso — es una revisión de apoyo basada solo en lo que el supervisor
  // describe (SICOT no verifica evidencia externa todavía); la decisión
  // final de confirmar sigue siendo del supervisor.
  const revisarPaso = async (descripcion: string) => {
    if (!contrato || !revisionPaso || pensando) return
    const step = steps.find(s => s.id === revisionPaso.stepId)
    if (!step) { setRevisionPaso(null); return }
    setPensando(true)
    try {
      const pregunta =
        `Voy a describirle lo que hice para completar el Paso ${step.id} (${step.title}), que tiene estos ` +
        `sub-pasos: ${step.subSteps.map(ss => `${ss.id} ${ss.label}`).join('; ')}. Mi descripción: "${descripcion}". ` +
        `Como Copiloto, evalúe honestamente, basándose SOLO en lo que describí (usted no tiene forma de ` +
        `verificar evidencia externa todavía), si esto parece razonablemente completo y coherente con lo que ` +
        `se esperaba en cada punto, o si detecta algo que probablemente falte o sea insuficiente. Sea claro y ` +
        `directo: diga si le parece que se puede marcar el paso como completado o si recomienda revisar algo ` +
        `antes. Aclare que esta es una revisión de apoyo, no una aprobación oficial — la decisión final es del supervisor.`
      const { respuesta } = await preguntarCopiloto(contrato.id, pregunta, chatMsgs)
      setChatMsgs(prev => [...prev, { role: 'ai', text: respuesta }])
    } catch (e) {
      const mensaje = e instanceof ApiError ? e.message : 'No se pudo conectar con el Copiloto IA (Ollama).'
      setChatMsgs(prev => [...prev, { role: 'ai', text: `No pude revisar su descripción: ${mensaje}. Puede confirmar de todas formas si está seguro, o volver a intentarlo.` }])
    } finally {
      setPensando(false)
      setRevisionPaso(prev => prev ? { ...prev, listaParaConfirmar: true } : prev)
    }
  }

  // Pregunta real al Copiloto IA (Ollama, vía CopilotoChatService en el
  // backend) — anclada a los datos reales del contrato y al estado real de
  // sus etapas. Ya no hay coincidencia de palabras clave local.
  const preguntarAlCopiloto = async (texto: string) => {
    if (!contrato || pensando) return
    setPensando(true)
    try {
      const { respuesta } = await preguntarCopiloto(contrato.id, texto, chatMsgs)
      setChatMsgs(prev => [...prev, { role: 'ai', text: respuesta }])
    } catch (e) {
      const mensaje = e instanceof ApiError ? e.message : 'No se pudo conectar con el Copiloto IA (Ollama). Verifique que esté disponible e inténtelo de nuevo.'
      setChatMsgs(prev => [...prev, { role: 'ai', text: `No pude responder: ${mensaje}` }])
    } finally {
      setPensando(false)
    }
  }

  const sendChat = () => {
    const text = chatInput.trim()
    if (!text || pensando) return
    setChatMsgs(prev => [...prev, { role: 'user', text }])
    setChatInput('')
    if (revisionPaso && !revisionPaso.listaParaConfirmar) {
      revisarPaso(text)
    } else {
      preguntarAlCopiloto(text)
    }
  }

  const quickChat = (q: string) => {
    if (pensando) return
    setChatMsgs(prev => [...prev, { role: 'user', text: q }])
    preguntarAlCopiloto(q)
  }

  // Navigate to a specific sub-step from another view
  const goToSubStep = (subStepId: string, stepId: number) => {
    setTab('contrato')
    setExpandedSteps(prev => { const n = new Set(prev); n.add(stepId); return n })
    setActiveSubStep(subStepId)
    setTutorialMode(true)
  }

  const irAPaso = (stepId: number) => {
    setTab('contrato')
    setExpandedSteps(new Set([stepId]))
  }

  // El paso que el Copiloto debe guiar ahora mismo — cualquiera de los 6, no solo el 3.
  const activeStep = steps.find(s => s.status === 'active')

  // ── Alerta de cronograma (semáforo verde/amarillo/rojo) ──
  // Estimación calculada con las fechas REALES del contrato (fechaInicio/
  // fechaFin) y el paso activo real — reparte el tiempo total del contrato
  // en 6 partes iguales, una por paso, y compara con hoy. NO es un plazo
  // oficial de SENA (no hay uno confirmado por paso) — por eso el mensaje
  // aclara que es una estimación de cronograma, para no aparentar ser una
  // regla institucional inventada.
  const alertaCronograma: LiveAlert | null = (() => {
    if (!contrato?.fechaInicio || !contrato?.fechaFin || !activeStep) return null
    const inicio = new Date(contrato.fechaInicio)
    const fin = new Date(contrato.fechaFin)
    const totalDias = (fin.getTime() - inicio.getTime()) / 86400000
    if (!(totalDias > 0)) return null
    const segmentoDias = totalDias / 6
    const finEsperado = new Date(inicio.getTime() + segmentoDias * activeStep.id * 86400000)
    const hoy = new Date()
    const diasDeAtraso = Math.round((hoy.getTime() - finEsperado.getTime()) / 86400000)
    const fechaEsperadaTexto = finEsperado.toLocaleDateString('es-CO', { day: '2-digit', month: '2-digit', year: 'numeric' })

    let severity: LiveAlert['severity']
    let texto: string
    if (diasDeAtraso <= 0) {
      severity = 'ok'
      texto = `Paso ${activeStep.id} (${activeStep.title}) va a tiempo según el cronograma estimado del contrato — debería cerrarse hacia el ${fechaEsperadaTexto}.`
    } else if (diasDeAtraso <= segmentoDias * 0.5) {
      severity = 'leve'
      texto = `Paso ${activeStep.id} (${activeStep.title}) está atrasado: según el cronograma estimado debía cerrarse hacia el ${fechaEsperadaTexto}, hace ${diasDeAtraso} día(s).`
    } else {
      severity = 'critica'
      texto = `Paso ${activeStep.id} (${activeStep.title}) está muy atrasado: según el cronograma estimado debía cerrarse hacia el ${fechaEsperadaTexto}, hace ${diasDeAtraso} día(s).`
    }
    return { id: 'cronograma-' + activeStep.id, severity, text: texto }
  })()

  const dismiss = (id: string) => {
    setDismissed(prev => new Set(prev).add(id))
    if (id.startsWith('api-')) {
      const apiId = Number(id.slice(4))
      marcarAlertaLeida(apiId).catch(err => console.error('No se pudo marcar la alerta como leída:', err))
      setAlertasApi(prev => prev.filter(a => a.id !== apiId))
    }
  }

  const resolveAlert = (alertId: string) => {
    if (alertId.startsWith('api-')) {
      const a = alertasApi.find(x => 'api-' + x.id === alertId)
      if (!a) return
      if (a.tipo === 'VENCIMIENTO' || a.tipo === 'SECOP' || a.tipo === 'CRONOGRAMA') {
        irAPaso(6)
      } else if (a.tipo === 'FIRMA' || a.tipo === 'DOCUMENTO' || a.tipo === 'IA') {
        irAPaso(3)
      } else {
        irAPaso(4)
      }
      return
    }
    if (alertId.startsWith('cronograma-') && activeStep) {
      irAPaso(activeStep.id)
    }
  }


  // ── Barra de recorrido: una sección por paso real del contrato ──
  //
  // El color de cada sección sale del estado real: verde si el paso está
  // cerrado, el semáforo del cronograma si es el paso en curso, y gris si
  // todavía no se ha tocado.
  const stages: Stage[] = steps.map(s => {
    const total = s.subSteps.length
    const hechos = s.subSteps.filter(ss => ss.completed).length
    const pct = total ? Math.round((hechos / total) * 100) : 0
    const enCurso = s.status === 'active'
    const state: Stage['state'] =
      pct === 100 ? 'ok'
        : enCurso
          ? (alertaCronograma?.severity === 'critica' ? 'critical'
            : alertaCronograma?.severity === 'leve' ? 'warning'
              : 'ok')
          : pct === 0 ? 'idle' : 'warning'
    const detalleBase = DETALLE_PASO[s.id] ?? `${hechos} de ${total} sub-pasos cerrados en este paso.`
    return {
      key: claveDePaso(s.id),
      label: etiquetaCorta(s.title),
      fullLabel: s.title,
      pct,
      state,
      detail: s.id === 6 && contrato?.fechaFin
        ? `${detalleBase} El contrato termina el ${formatFecha(contrato.fechaFin)}.`
        : detalleBase,
    }
  })
  const etapaActualKey = activeStep ? claveDePaso(activeStep.id) : null

  // Cifras del contrato — todas sobre el estado real de las subetapas y las
  // fechas registradas; alimentan los indicadores de la vista Contrato.
  const todosLosSubPasos = steps.flatMap(s => s.subSteps)
  const avanceGlobal = todosLosSubPasos.length
    ? Math.round((todosLosSubPasos.filter(ss => ss.completed).length / todosLosSubPasos.length) * 100)
    : 0
  const etapasCerradas = steps.filter(s => s.subSteps.length > 0 && s.subSteps.every(ss => ss.completed)).length
  const subPasosPendientesPasoActivo = activeStep ? activeStep.subSteps.filter(ss => !ss.completed).length : 0
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
      items.push({
        id: alertaCronograma.id,
        severidad: alertaCronograma.severity === 'critica' ? 'critica' : alertaCronograma.severity === 'leve' ? 'leve' : 'ok',
        categoria: 'Cronograma',
        titulo: alertaCronograma.severity === 'ok' ? 'El paso en curso va a tiempo' : 'El paso en curso está atrasado',
        detalle: alertaCronograma.text,
        accionLabel: 'Ver el paso',
        onAccion: () => resolveAlert(alertaCronograma.id),
        onDescartar: () => dismiss(alertaCronograma.id),
      })
    }

    for (const a of alertasApi) {
      const id = 'api-' + a.id
      if (dismissed.has(id)) continue
      items.push({
        id,
        severidad: a.prioridad === 'ALTA' ? 'critica' : a.prioridad === 'MEDIA' ? 'leve' : 'info',
        categoria: 'Alerta',
        titulo: a.tipo.charAt(0) + a.tipo.slice(1).toLowerCase().replace(/_/g, ' '),
        detalle: a.mensaje,
        fecha: formatFecha(a.fechaCreacion.slice(0, 10)),
        accionLabel: 'Ir al paso',
        onAccion: () => resolveAlert(id),
        onDescartar: () => dismiss(id),
      })
    }

    if (tieneFirma === false) {
      items.push({
        id: 'firma-pendiente',
        severidad: 'critica',
        categoria: 'Firma',
        titulo: 'Falta su firma electrónica',
        detalle: 'Todavía no se le ha asignado una firma electrónica activa. Sin ella no puede firmar los documentos formales del contrato: solicítela al Administrador.',
      })
    }

    if (activeStep) {
      for (const ss of activeStep.subSteps.filter(s => !s.completed)) {
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

    for (const doc of docsContrato.filter(d => d.generadoPorIa && d.estado !== 'APROBADO')) {
      const formal = FORMAL_DOCS.find(f => doc.nombre.startsWith(f.name))
      items.push({
        id: 'doc-' + doc.id,
        severidad: 'leve',
        categoria: 'Documento',
        titulo: `Documento sin firmar: ${doc.nombre}`,
        detalle: formal
          ? `${formal.code === 'PENDIENTE_DE_DEFINIR' ? 'Código pendiente de definir' : formal.code} · generado por el Copiloto en el sub-paso ${formal.subStepId}.`
          : 'Documento generado por el Copiloto IA que todavía no tiene firma registrada.',
        fecha: formatFecha(doc.fechaSubida.slice(0, 10)),
        accionLabel: formal ? 'Ir a firmar' : undefined,
        onAccion: formal ? () => goToSubStep(formal.subStepId, formal.step) : undefined,
      })
    }

    const orden: Record<ItemBandeja['severidad'], number> = { critica: 0, leve: 1, info: 2, ok: 3 }
    return items.sort((a, b) => orden[a.severidad] - orden[b.severidad])
  })()

  const pendientesCriticos = itemsBandeja.filter(i => i.severidad === 'critica').length

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
    return <EmptyContractState usuario={usuario} onLogout={onLogout} onOpenSettings={onOpenSettings} onStartTour={onStartTour} />
  }

  const navGroups: NavGroup[] = [
    {
      label: 'Supervisión',
      items: [
        {
          id: 'bandeja', label: 'Bandeja de entrada', icon: <IconInbox size={17} />,
          count: itemsBandeja.length, countTone: pendientesCriticos > 0 ? 'alert' : 'normal',
        },
        { id: 'contrato', label: 'Contrato', icon: <IconContract size={17} /> },
      ],
    },
    {
      label: 'Seguimiento',
      items: [
        {
          id: 'alertas', label: 'Alertas', icon: <IconBell size={17} />,
          count: alertasApi.length, countTone: 'alert',
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
      onNavigate={id => setTab(id as Tab)}
      usuario={usuario}
      onLogout={onLogout}
      onOpenSettings={onOpenSettings}
      title={TITULO_VISTA[tab].titulo}
      subtitle={`${contrato.numeroContrato} · ${TITULO_VISTA[tab].sub}`}
      actions={
        <>
          {tab === 'contrato' && (
            <button className="btn-ghost" onClick={() => setCopilotoAbierto(v => !v)}
              title={copilotoAbierto ? 'Ocultar el panel del Copiloto' : 'Mostrar el panel del Copiloto'}
              style={{ padding: '7px 13px', fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 6 }}>
              <AvatarIcon id={prefs.avatarId} size={13} />
              {copilotoAbierto ? 'Ocultar copiloto' : 'Mostrar copiloto'}
            </button>
          )}
          <button className="btn-ghost" onClick={onStartTour} style={{ padding: '7px 13px', fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 6 }}>
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
                  onStageClick={key => setExpandedSteps(new Set([pasoDeClave(key)]))}
                />
              </div>

              {/* Indicadores del contrato */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: 12, marginBottom: 16 }}>
                <StatCard
                  label="Avance global"
                  value={`${avanceGlobal}%`}
                  hint={`${todosLosSubPasos.filter(ss => ss.completed).length} de ${todosLosSubPasos.length} sub-pasos cerrados`}
                  icon={<IconChart size={15} />}
                />
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
                    diasDeVigencia === null ? 'El contrato no tiene fechas registradas'
                      : diasDeVigencia < 0 ? `Terminó el ${formatFecha(contrato.fechaFin)}`
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
                desc="Cada paso se abre para ver sus puntos de control. El Copiloto lo guía en el paso activo y redacta los documentos formales; usted revisa y firma."
              />
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 22 }}>
                {steps.length === 0 && (
                  <div className="card" style={{ padding: '28px 20px', textAlign: 'center', fontSize: 13, color: 'var(--text-muted)' }}>
                    Las etapas de este contrato todavía no se han cargado desde el servidor.
                  </div>
                )}
                {steps.map(step => {
                  const stats = getStats(step)
                  const isOpen = expandedSteps.has(step.id)
                  return (
                    <div key={step.id} className="card" style={{
                      overflow: 'hidden',
                      borderColor: step.status === 'active' ? 'var(--accent)' : 'var(--border)',
                    }}>
                      <div className="step-row" onClick={() => toggleStep(step.id)}>
                        <StepCircle n={step.id} status={step.status} />
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div style={{ fontSize: 13.5, fontWeight: 500, color: step.status === 'pending' ? 'var(--text-muted)' : 'var(--text-primary)', marginBottom: 5 }}>
                            Paso {step.id} — {step.title}
                            {step.status === 'active' && (
                              <span style={{ marginLeft: 8, fontSize: 9.5, fontWeight: 700, letterSpacing: '0.08em', color: 'var(--on-accent)', background: 'var(--accent)', padding: '2px 7px', borderRadius: 3 }}>ACTIVO</span>
                            )}
                          </div>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                            <ProgressBar pct={stats.pct} />
                            <span style={{ fontSize: 11, color: 'var(--text-muted)', whiteSpace: 'nowrap', fontFamily: 'var(--font-mono)' }}>{stats.done}/{stats.total} — {stats.pct}%</span>
                          </div>
                        </div>
                        <span style={{ color: 'var(--text-muted)', fontSize: 12, transform: isOpen ? 'rotate(180deg)' : 'none', transition: 'transform 0.2s' }}>▼</span>
                      </div>

                      {isOpen && (
                        <div style={{ borderTop: '1px solid var(--border)', padding: '8px 8px 12px' }}>
                          {step.subSteps.map((ss, idx) => {
                            const isActiveTutorial = activeSubStep === ss.id
                            const isAiDoc = AI_GENERATED_DOCS.has(ss.id)
                            const procesando = procesandoFirma === ss.id
                            const actionLabel = procesando ? 'Generando y firmando…' : isAiDoc ? 'Firmar documento' : 'Marcar completado'
                            const completedLabel = isAiDoc ? 'Firmado' : 'Completado'
                            return (
                              <div key={ss.id} className={`substep-row${isActiveTutorial ? ' active-tutorial' : ''}`}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                                  <div style={{
                                    width: 18, height: 18, borderRadius: '50%', flexShrink: 0,
                                    background: ss.completed ? 'var(--accent)' : isActiveTutorial ? 'var(--accent-soft)' : 'transparent',
                                    border: ss.completed ? 'none' : isActiveTutorial ? '1.5px solid var(--accent)' : '1.5px solid var(--step-pending)',
                                    display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 10,
                                  }}>
                                    {ss.completed
                                      ? <span style={{ color: 'var(--on-accent)', fontWeight: 700 }}>✓</span>
                                      : <span style={{ color: isActiveTutorial ? 'var(--accent)' : 'var(--text-muted)', fontSize: 9 }}>{idx + 1}</span>
                                    }
                                  </div>
                                  <span style={{
                                    fontSize: 13,
                                    color: ss.completed ? 'var(--text-muted)' : isActiveTutorial ? 'var(--text-primary)' : 'var(--text-secondary)',
                                    textDecoration: ss.completed ? 'line-through' : 'none',
                                  }}>
                                    <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11.5, color: 'var(--text-muted)', marginRight: 6 }}>{ss.id}</span>
                                    {ss.label}
                                    {isAiDoc && !ss.completed && (
                                      <span style={{ marginLeft: 6, fontSize: 10, color: 'var(--accent)', background: 'var(--accent-soft)', padding: '1px 5px', borderRadius: 3 }}>IA genera</span>
                                    )}
                                  </span>
                                </div>
                                <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', paddingLeft: 26, alignItems: 'center' }}>
                                  <Chip text={ss.responsible} type="responsible" />
                                  <Chip text={ss.document} type="document" />
                                  {ss.completed
                                    ? <Chip text={completedLabel} type={isAiDoc ? 'signed' : 'done'} />
                                    : <Chip text="Pendiente" type="pending" />
                                  }
                                  {isActiveTutorial && !ss.completed && (
                                    <button className="btn-green"
                                      onClick={() => handleActionSubStep(step.id, ss.id)}
                                      disabled={procesando}
                                      style={{ padding: '4px 12px', fontSize: 11, marginLeft: 4, opacity: procesando ? 0.6 : 1, cursor: procesando ? 'default' : 'pointer' }}>
                                      {actionLabel}
                                    </button>
                                  )}
                                </div>
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
            <div data-tour="copiloto" className="split-aside" style={{ width: 420, minWidth: 340, flexShrink: 0, borderLeft: '1px solid var(--border)', display: 'flex', flexDirection: 'column', overflow: 'hidden', background: 'var(--bg-rail)' }}>
              <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--border)', flexShrink: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <div style={{ width: 36, height: 36, borderRadius: '50%', color: 'var(--accent)', background: 'var(--bg-card)', border: '1.5px solid var(--accent)', boxShadow: '0 0 0 3px var(--accent-soft)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><AvatarIcon id={prefs.avatarId} size={18} /></div>
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <span style={{ fontSize: 13, fontWeight: 600 }}>{prefs.avatarName}</span>
                      <span style={{ fontSize: 10, fontWeight: 600, color: 'var(--on-accent)', background: 'var(--accent)', padding: '1px 6px', borderRadius: 3 }}>Activo</span>
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>Asistente contractual · {contrato.numeroContrato}</div>
                  </div>
                  <div style={{ flex: 1 }} />
                  <button type="button" onClick={() => setCopilotoAbierto(false)}
                    title="Ocultar el panel del Copiloto" aria-label="Ocultar el panel del Copiloto"
                    style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', padding: 4, display: 'flex' }}>
                    <IconChevron size={15} />
                  </button>
                </div>

              </div>

              {/* Mensajes */}
              <div style={{ flex: 1, overflowY: 'auto', padding: '14px 14px 8px', display: 'flex', flexDirection: 'column', gap: 10 }}>
                {chatMsgs.map((m, i) => (
                  <div key={i} style={{ display: 'flex', flexDirection: 'column', alignItems: m.role === 'user' ? 'flex-end' : 'flex-start' }}>
                    {m.role === 'ai' ? (
                      <div style={{ display: 'flex', gap: 6, alignItems: 'flex-start' }}>
                        <div style={{ width: 24, height: 24, borderRadius: '50%', background: 'var(--accent-soft)', border: '1px solid var(--accent-line)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, marginTop: 2, color: 'var(--accent)' }}><AvatarIcon id={prefs.avatarId} size={13} /></div>
                        <div className="copiloto-msg" style={{ maxWidth: '92%' }}>{m.text}</div>
                      </div>
                    ) : (
                      <div className="user-msg" style={{ maxWidth: '88%' }}>{m.text}</div>
                    )}
                  </div>
                ))}
                {pensando && (
                  <div style={{ display: 'flex', gap: 6, alignItems: 'flex-start' }}>
                    <div style={{ width: 24, height: 24, borderRadius: '50%', background: 'var(--accent-soft)', border: '1px solid var(--accent-line)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, flexShrink: 0, marginTop: 2, color: 'var(--accent)' }}>★</div>
                    <div className="copiloto-msg" style={{ maxWidth: '92%', fontStyle: 'italic', color: 'var(--text-muted)' }}>Pensando… puede tardar uno o varios minutos según la carga del servidor. No cierre esta ventana.</div>
                  </div>
                )}
                {!tutorialMode && !pensando && !revisionPaso && activeStep && (
                  <div style={{ paddingLeft: 30 }}>
                    <button className="btn-green" onClick={() => handleIniciarPaso(activeStep.id)} style={{ padding: '8px 16px', fontSize: 13, display: 'inline-flex', alignItems: 'center', gap: 7 }}>
                      Iniciar Paso {activeStep.id} <IconArrowRight size={12} />
                    </button>
                  </div>
                )}
                {revisionPaso?.listaParaConfirmar && !pensando && (
                  <div style={{ paddingLeft: 30, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                    <button className="btn-green"
                      onClick={() => { const r = revisionPaso; setRevisionPaso(null); if (r) ejecutarAccionSubPaso(r.stepId, r.subStepId) }}
                      style={{ padding: '8px 16px', fontSize: 13 }}>
                      Confirmar Paso {revisionPaso.stepId} como completado
                    </button>
                    <button className="btn-ghost" onClick={() => setRevisionPaso(null)} style={{ padding: '8px 16px', fontSize: 13 }}>
                      Cancelar, quiero revisar algo antes
                    </button>
                  </div>
                )}
                <div ref={chatEndRef} />
              </div>

              {/* Sugerencias rápidas */}
              <div style={{ padding: '8px 12px', borderTop: '1px solid var(--border)', display: 'flex', gap: 6, flexWrap: 'wrap', flexShrink: 0 }}>
                {QUICK_SUGGESTIONS.map(({ label, question }) => (
                  <button key={label} onClick={() => quickChat(question)} disabled={pensando || !!revisionPaso}
                    style={{ background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: 20, padding: '4px 10px', fontSize: 11, color: 'var(--text-secondary)', cursor: (pensando || revisionPaso) ? 'default' : 'pointer', opacity: (pensando || revisionPaso) ? 0.5 : 1, fontFamily: 'var(--font-ui)', transition: 'border-color 0.15s' }}
                    onMouseEnter={e => (e.currentTarget.style.borderColor = 'var(--accent-dim)')}
                    onMouseLeave={e => (e.currentTarget.style.borderColor = 'var(--border)')}>
                    {label}
                  </button>
                ))}
              </div>

              {/* Entrada */}
              <div style={{ padding: '8px 12px 12px', borderTop: '1px solid var(--border)', display: 'flex', gap: 8, flexShrink: 0 }}>
                <input type="text" value={chatInput} onChange={e => setChatInput(e.target.value)}
                  onKeyDown={e => { if (e.key === 'Enter') sendChat() }}
                  placeholder={
                    pensando ? 'Esperando respuesta del Copiloto…'
                      : revisionPaso && !revisionPaso.listaParaConfirmar ? 'Describa qué hizo o verificó en este paso...'
                        : 'Escriba una orden o pregunta a la IA...'
                  }
                  disabled={pensando}
                  style={{ flex: 1, padding: '9px 12px', opacity: pensando ? 0.6 : 1 }} />
                <button className="btn-green" onClick={sendChat} disabled={pensando} style={{ padding: '8px 14px', fontSize: 13, opacity: pensando ? 0.6 : 1, cursor: pensando ? 'default' : 'pointer', display: 'inline-flex', alignItems: 'center' }}>
                  <IconArrowRight size={14} />
                </button>
              </div>
            </div>
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
          onResolver={resolveAlert}
        />
      )}
      {/* ── Documentos ── */}
      {tab === 'documentos' && (
        <VistaDocumentos
          contrato={contrato}
          docsContrato={docsContrato}
          tieneFirma={tieneFirma}
          onIrASubPaso={goToSubStep}
        />
      )}

      {/* ── Registros ── */}
      {tab === 'registros' && <Registros extra={registros} />}
    </AppShell>
  )
}
