// Panel principal del Supervisor — las 5 etapas GCCON-P-010, alertas vivas,
// documentos generados por IA, copiloto conversacional y registros.
// Extraído 1:1 desde el App.tsx original de Figma Make — sin cambios visuales.

import { useState, useRef, useEffect } from 'react'
import { usePrefs } from '@/prefs'
import Registros, { type Registro } from '@/components/Registros'
import { Chip, StageProgressBar, TopBar, type LiveAlert, type Stage } from '@/components/ui'
import { AvatarIcon, IconPlay } from '@/components/icons'
import { AI_GENERATED_DOCS, TUTORIAL, FORMAL_DOCS } from '@/data/contractFlow'
import type { Step, Tab, ChatMsg } from '@/types/domain'
import type { AuthResponse, AlertaResponse, ContratoResponse, DocumentoResponse } from '@/services/api/types'
import { getEtapasContrato, cambiarEstadoSubetapa } from '@/services/etapaService'
import { getAlertasContrato, marcarAlertaLeida } from '@/services/alertaService'
import { getDocumentosContrato, generarDocumento, firmarDocumento, descargarDocumento, preguntarCopiloto } from '@/services/documentoService'
import { getMiFirma } from '@/services/firmaService'
import { ApiError } from '@/services/api/client'
import { mapEtapas } from '@/services/mappers'
import { formatCOP, formatFecha } from '@/services/format'

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

// ─── Estado vacío — Supervisor sin contrato asignado ────────────────────────
function EmptyContractState({ usuario, onLogout, onOpenSettings, onStartTour }: {
  usuario: AuthResponse
  onLogout: () => void
  onOpenSettings: () => void
  onStartTour: () => void
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', background: 'var(--bg-base)', overflow: 'hidden' }}>
      {/* Top bar */}
      <TopBar badge="Panel Supervisor" usuario={usuario} onOpenSettings={onOpenSettings} onLogout={onLogout} />

      {/* Content */}
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24 }}>
        <div className="card" style={{ maxWidth: 460, width: '100%', padding: 32, textAlign: 'center' }}>
          <div style={{ width: 40, height: 2, background: 'var(--accent)', borderRadius: 1, margin: '0 auto 20px' }} />
          <h2 style={{ margin: '0 0 12px', fontSize: 18 }}>
            No tiene un contrato asignado
          </h2>
          <p style={{ color: 'var(--text-secondary)', fontSize: 14, lineHeight: 1.6, margin: '0 0 24px' }}>
            Actualmente no tiene un contrato asignado para seguimiento. Cuando Gestión le asigne uno, la información aparecerá aquí.
          </p>
          <div style={{ background: 'var(--accent-soft)', border: '1px solid var(--accent-line)', borderRadius: 8, padding: '12px 16px', marginBottom: 24, fontSize: 13, color: 'var(--text-muted)' }}>
            <div style={{ fontSize: 11, fontWeight: 600, letterSpacing: '0.06em', marginBottom: 6, color: 'var(--accent)' }}>ESTADO</div>
            <div>Esperando asignación de Gestión y Contratación</div>
          </div>
          <button className="btn-ghost" onClick={onStartTour} style={{ width: '100%', padding: '12px 0', fontSize: 13, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}>
            <IconPlay size={10} /> Ver tutorial del proceso
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── Estado de carga — mientras se consulta el contrato real del supervisor ──
function CargandoContratoState({ usuario, onLogout, onOpenSettings }: {
  usuario: AuthResponse
  onLogout: () => void
  onOpenSettings: () => void
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', background: 'var(--bg-base)', overflow: 'hidden' }}>
      <TopBar badge="Panel Supervisor" usuario={usuario} onOpenSettings={onOpenSettings} onLogout={onLogout} />
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24 }}>
        <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>Consultando su contrato asignado…</div>
      </div>
    </div>
  )
}

// ─── Helper components (privados de este panel) ────────────────────────────────

function StepCircle({ n, status }: { n: number; status: 'completed' | 'active' | 'pending' }) {
  if (status === 'completed') return (
    <div style={{ width: 26, height: 26, borderRadius: '50%', flexShrink: 0, background: 'var(--accent)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <svg width="13" height="10" viewBox="0 0 13 10" fill="none">
        <path d="M1 5L5 9L12 1" stroke="#03200D" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
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
    <div style={{ height: 2, background: 'var(--accent-soft)', borderRadius: 1, overflow: 'hidden', flexGrow: 1 }}>
      <div style={{ height: '100%', width: `${pct}%`, background: pct === 100 ? 'var(--accent)' : 'var(--accent-dim)', borderRadius: 1, transition: 'width 0.4s' }} />
    </div>
  )
}

export default function SupervisorPanel({
  steps, setSteps, usuario, contrato, cargandoContrato, onLogout, onOpenSettings, onStartTour, registros, addRegistro,
}: {
  steps: Step[]
  setSteps: (s: Step[]) => void
  usuario: AuthResponse
  contrato: ContratoResponse | null
  cargandoContrato: boolean
  onLogout: () => void
  onOpenSettings: () => void
  onStartTour: () => void
  registros: Registro[]
  addRegistro: (r: Registro) => void
}) {
  const { prefs } = usePrefs()
  const [dismissed, setDismissed] = useState<Set<string>>(new Set())
  const [tab, setTab] = useState<Tab>('panel')
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
  const [confirmed, setConfirmed] = useState(true)
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

  // Alertas reales del contrato (no leídas)
  const [alertasApi, setAlertasApi] = useState<AlertaResponse[]>([])
  useEffect(() => {
    if (!contrato) {
      setAlertasApi([])
      return
    }
    let cancelado = false
    getAlertasContrato(contrato.id)
      .then(lista => {
        if (!cancelado) setAlertasApi(lista.filter(a => !a.leida))
      })
      .catch(err => console.error('No se pudieron cargar las alertas del contrato:', err))
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
        const firmado = await firmarDocumento(contrato.id, generado.id)
        addRegistro({
          id: 'sig-' + subStepId,
          tipo: 'Firma',
          destinatario: `${contrato?.supervisorNombre ?? ''} — Supervisor`,
          fecha: new Date().toLocaleString('es-CO', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }).replace(',', ''),
          asunto: `${doc.name}${doc.code !== 'PENDIENTE_DE_DEFINIR' ? ' ' + doc.code : ''} — firma electrónica ${firmado.firmaId ?? ''}`,
          estado: 'Firmado',
        })
        getDocumentosContrato(contrato.id).then(setDocsContrato)
          .catch(err => console.error('Firma registrada, pero no se pudo refrescar la lista de documentos:', err))
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

  // Navigate to a specific sub-step from another tab
  const goToSubStep = (subStepId: string, stepId: number) => {
    setTab('panel')
    setExpandedSteps(prev => { const n = new Set(prev); n.add(stepId); return n })
    setActiveSubStep(subStepId)
    setTutorialMode(true)
  }

  const step3 = steps.find(s => s.id === 3)
  // El paso que el Copiloto debe guiar ahora mismo — cualquiera de los 6, no solo el 3.
  const activeStep = steps.find(s => s.status === 'active')

  // Alerts: derive from actual step state — Paso 3 = Inspección, Paso 4 = Recepción
  const paso4Active = steps.find(s => s.id === 4)?.status !== 'completed'
  // GCCON-F-031 signed at 3.4; GIL-F-010 signed at 4.3
  const gccon031Signed = step3?.subSteps.find(s => s.id === '3.4')?.completed
  const gil010Signed = steps.find(s => s.id === 4)?.subSteps.find(s => s.id === '4.3')?.completed

  // ── Barra de progreso lineal: 5 etapas GCCON-P-010 ──
  const pctOf = (ids: number[]) => {
    const subs = steps.filter(s => ids.includes(s.id)).flatMap(s => s.subSteps)
    return subs.length ? Math.round((subs.filter(s => s.completed).length / subs.length) * 100) : 0
  }
  const stateOf = (pct: number, critical: boolean, warning: boolean): Stage['state'] =>
    pct === 100 ? 'ok' : critical ? 'critical' : warning ? 'warning' : pct === 0 ? 'idle' : 'warning'

  const stages: Stage[] = [
    { key: 'inicio', label: 'Inicio', pct: pctOf([1, 2]), state: stateOf(pctOf([1, 2]), false, false), detail: 'Estudios previos, suscripción SECOP II y Acta de Inicio GCCON-F-018.' },
    { key: 'inspeccion', label: 'Inspección', pct: pctOf([3]), state: stateOf(pctOf([3]), false, pctOf([3]) < 100 && pctOf([1, 2]) === 100), detail: 'Monitoreo de entrega, inspección física en bodega e Informe GCCON-F-031.' },
    { key: 'recepcion', label: 'Recepción', pct: pctOf([4]), state: stateOf(pctOf([4]), pctOf([4]) === 0 && pctOf([3]) === 100, false), detail: 'Acta de Recibo GIL-F-010, PILA y factura electrónica DIAN.' },
    { key: 'certificacion', label: 'Certificación', pct: pctOf([5]), state: stateOf(pctOf([5]), false, false), detail: 'ESUCON, GRF-F-089 y trámite formal de pago al proveedor.' },
    { key: 'cierre', label: 'Cierre', pct: pctOf([6]), state: stateOf(pctOf([6]), false, false), detail: `Acta de Liquidación GCCON-F-030 y archivo SIGEP. Vence ${formatFecha(contrato?.fechaFin)}.` },
  ]

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

  // ── Alertas vivas (máx. 3, descartables) ──
  // La de cronograma se calcula localmente con datos reales (arriba); el
  // resto viene del backend.
  const alertasReales: LiveAlert[] = alertasApi.map(a => ({
    id: 'api-' + a.id,
    severity: a.prioridad === 'ALTA' ? 'critica' : 'leve',
    text: a.mensaje,
  }))
  const liveAlerts = [
    ...(alertaCronograma ? [alertaCronograma] : []),
    ...alertasReales,
  ].filter(a => !dismissed.has(a.id))

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
        setTab('panel'); setExpandedSteps(new Set([6]))
      } else if (a.tipo === 'FIRMA' || a.tipo === 'DOCUMENTO' || a.tipo === 'IA') {
        setTab('panel'); setExpandedSteps(new Set([3]))
      } else {
        setTab('panel'); setExpandedSteps(new Set([4]))
      }
      return
    }
    if (alertId.startsWith('cronograma-') && activeStep) {
      setTab('panel'); setExpandedSteps(new Set([activeStep.id]))
    }
  }

  // Mientras se consulta el contrato real todavía no sabemos si hay uno o no
  // — mostrar "sin contrato asignado" en ese instante sería falso.
  if (!contrato && cargandoContrato) {
    return <CargandoContratoState usuario={usuario} onLogout={onLogout} onOpenSettings={onOpenSettings} />
  }

  // Protección: si ya se consultó y no hay contrato asignado, mostrar estado vacío
  if (!contrato) {
    return <EmptyContractState usuario={usuario} onLogout={onLogout} onOpenSettings={onOpenSettings} onStartTour={onStartTour} />
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', background: 'var(--bg-base)', overflow: 'hidden' }}>
      {/* Top bar */}
      <TopBar badge="Panel Supervisor" usuario={usuario} onOpenSettings={onOpenSettings} onLogout={onLogout}
        actions={<button className="btn-ghost" onClick={onStartTour} style={{ padding: '6px 13px', fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 6 }}><IconPlay size={10} /> Tutorial</button>} />

      {/* Tabs */}
      <div style={{ display: 'flex', borderBottom: '1px solid var(--border)', padding: '0 20px', flexShrink: 0 }}>
        {(['panel', 'alertas', 'documentos', 'registros'] as Tab[]).map(t => (
          <button key={t} data-tour={`tab-${t}`} className={`tab-btn ${tab === t ? 'active' : ''}`} onClick={() => setTab(t)}>
            {t === 'panel' ? 'Panel Principal'
              : t === 'alertas' ? 'Alertas'
                : t === 'documentos' ? 'Documentos'
                  : 'Registros'}
          </button>
        ))}
      </div>

      {/* Content */}
      <div className="split-panel" style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>

        {/* ── Panel Principal ── */}
        {tab === 'panel' && (
          <>
            {/* Accordion column */}
            <div style={{ flex: 1, overflowY: 'auto', padding: '16px 20px 24px' }}>

              {/* ── Sección superior: Progreso (izq) + Alertas (der) ── */}
              <div data-tour="progreso" style={{ display: 'flex', gap: 16, marginBottom: 20, alignItems: 'flex-start' }}>

                {/* Barra de progreso GCCON-P-010 */}
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ marginBottom: 10 }}>
                    <span style={{ display: 'inline-block', fontSize: 10, fontWeight: 700, letterSpacing: '0.12em', color: 'var(--accent)', background: 'var(--accent-soft)', border: '1px solid var(--accent-line)', borderRadius: 4, padding: '3px 10px' }}>
                      ETAPAS DEL CONTRATO
                    </span>
                  </div>
                  <StageProgressBar
                    stages={stages}
                    onStageClick={key => {
                      const map: Record<string, number> = { inicio: 1, inspeccion: 3, recepcion: 4, certificacion: 5, cierre: 6 }
                      if (map[key]) setExpandedSteps(new Set([map[key]]))
                    }}
                  />
                </div>

                {/* Panel de alertas prominentes */}
                <ProminentAlerts
                  alerts={liveAlerts}
                  blink={prefs.blinkAlerts}
                  onDismiss={dismiss}
                  onResolve={resolveAlert}
                />
              </div>

              {/* Contract card */}
              <div data-tour="contrato" className="card" style={{ marginBottom: 16, padding: '14px 16px', borderLeft: '4px solid var(--accent)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12 }}>
                  <div>
                    <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: '0.06em', color: 'var(--text-muted)', marginBottom: 4, fontFamily: 'var(--font-ui)' }}>
                      CONTRATO ASIGNADO
                    </div>
                    <div style={{ fontFamily: 'var(--font-mono)', fontSize: 13, color: 'var(--accent-tech)', marginBottom: 4, letterSpacing: '0.03em' }}>
                      {contrato?.numeroContrato ?? ''}
                    </div>
                    <div style={{ fontSize: 13, color: 'var(--text-primary)', marginBottom: 4 }}>{contrato?.objeto ?? ''}</div>
                    <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                      Valor: <strong>{formatCOP(contrato?.valor)}</strong> · {formatFecha(contrato?.fechaInicio)} – {formatFecha(contrato?.fechaFin)}
                    </div>
                  </div>
                  {confirmed
                    ? <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: 'var(--accent)', fontSize: 12, fontWeight: 500, flexShrink: 0 }}>✓ Recepción confirmada</div>
                    : <button className="btn-green" onClick={() => setConfirmed(true)} style={{ padding: '6px 14px', fontSize: 12, flexShrink: 0 }}>Confirmar recepción</button>
                  }
                </div>
              </div>

              {/* Steps */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {steps.map(step => {
                  const stats = getStats(step)
                  const isOpen = expandedSteps.has(step.id)
                  return (
                    <div key={step.id} className="card" style={{
                      overflow: 'hidden',
                      borderColor: step.status === 'active' ? 'var(--accent-line)' : step.status === 'completed' ? 'var(--accent-soft)' : 'var(--border)',
                    }}>
                      <div className="step-row" onClick={() => toggleStep(step.id)}>
                        <StepCircle n={step.id} status={step.status} />
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div style={{ fontSize: 13, fontWeight: 500, color: step.status === 'pending' ? 'var(--text-muted)' : 'var(--text-primary)', marginBottom: 4 }}>
                            Paso {step.id} — {step.title}
                            {step.status === 'active' && (
                              <span style={{ marginLeft: 8, fontSize: 10, fontWeight: 600, color: 'var(--accent)', background: 'var(--accent-soft)', padding: '1px 7px', borderRadius: 3 }}>ACTIVO</span>
                            )}
                          </div>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                            <ProgressBar pct={stats.pct} />
                            <span style={{ fontSize: 11, color: 'var(--text-muted)', whiteSpace: 'nowrap' }}>{stats.done}/{stats.total} — {stats.pct}%</span>
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
                                    border: ss.completed ? 'none' : isActiveTutorial ? '1.5px solid var(--accent)' : '1.5px solid var(--text-muted)',
                                    display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 10,
                                  }}>
                                    {ss.completed
                                      ? <span style={{ color: '#03200D', fontWeight: 700 }}>✓</span>
                                      : <span style={{ color: isActiveTutorial ? 'var(--accent)' : 'var(--text-muted)', fontSize: 9 }}>{idx + 1}</span>
                                    }
                                  </div>
                                  <span style={{
                                    fontSize: 13,
                                    color: ss.completed ? 'var(--text-secondary)' : isActiveTutorial ? 'var(--text-primary)' : 'var(--text-muted)',
                                    textDecoration: ss.completed ? 'line-through' : 'none',
                                  }}>
                                    {ss.label}
                                    {isAiDoc && !ss.completed && (
                                      <span style={{ marginLeft: 6, fontSize: 10, color: 'var(--accent)', background: 'var(--accent-soft)', padding: '1px 5px', borderRadius: 3 }}>IA generó</span>
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
                                      style={{ padding: '3px 12px', fontSize: 11, marginLeft: 4, opacity: procesando ? 0.6 : 1, cursor: procesando ? 'default' : 'pointer' }}>
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
            </div>

            {/* Copiloto column */}
            <div data-tour="copiloto" className="split-aside" style={{ width: 380, flexShrink: 0, borderLeft: '1px solid var(--border)', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
              <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--border)', flexShrink: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <div style={{ width: 36, height: 36, borderRadius: '50%', color: 'var(--accent)', background: 'var(--bg-card)', border: '1.5px solid var(--accent)', boxShadow: '0 0 0 3px var(--accent-soft)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><AvatarIcon id={prefs.avatarId} size={18} /></div>
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <span style={{ fontSize: 13, fontWeight: 600 }}>{prefs.avatarName}</span>
                      <span style={{ fontSize: 10, fontWeight: 600, color: '#03200D', background: 'var(--accent)', padding: '1px 6px', borderRadius: 3 }}>Activo</span>
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>Asistente contractual · {contrato?.numeroContrato ?? ''}</div>
                  </div>
                </div>

                {/* data-tour anchor — alertas chip cluster lives inline in the progress bar */}
                <div data-tour="alertas" />
              </div>

              {/* Messages */}
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
                    <button className="btn-green" onClick={() => handleIniciarPaso(activeStep.id)} style={{ padding: '8px 16px', fontSize: 13 }}>
                      Iniciar Paso {activeStep.id}
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

              {/* Quick suggestions */}
              <div style={{ padding: '8px 12px', borderTop: '1px solid var(--border)', display: 'flex', gap: 6, flexWrap: 'wrap', flexShrink: 0 }}>
                {QUICK_SUGGESTIONS.map(({ label, question }) => (
                  <button key={label} onClick={() => quickChat(question)} disabled={pensando || !!revisionPaso}
                    style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 20, padding: '4px 10px', fontSize: 11, color: 'var(--text-secondary)', cursor: (pensando || revisionPaso) ? 'default' : 'pointer', opacity: (pensando || revisionPaso) ? 0.5 : 1, fontFamily: 'Inter, sans-serif', transition: 'border-color 0.15s' }}
                    onMouseEnter={e => (e.currentTarget.style.borderColor = 'var(--accent-dim)')}
                    onMouseLeave={e => (e.currentTarget.style.borderColor = 'var(--border)')}>
                    {label}
                  </button>
                ))}
              </div>

              {/* Input */}
              <div style={{ padding: '8px 12px 12px', borderTop: '1px solid var(--border)', display: 'flex', gap: 8, flexShrink: 0 }}>
                <span style={{ color: 'var(--text-muted)', fontSize: 14, paddingTop: 8, fontFamily: 'var(--font-mono)' }}>+</span>
                <input type="text" value={chatInput} onChange={e => setChatInput(e.target.value)}
                  onKeyDown={e => { if (e.key === 'Enter') sendChat() }}
                  placeholder={
                    pensando ? 'Esperando respuesta del Copiloto…'
                      : revisionPaso && !revisionPaso.listaParaConfirmar ? 'Describa qué hizo o verificó en este paso...'
                        : 'Escriba una orden o pregunta a la IA...'
                  }
                  disabled={pensando}
                  style={{ flex: 1, padding: '8px 12px', opacity: pensando ? 0.6 : 1 }} />
                <button className="btn-green" onClick={sendChat} disabled={pensando} style={{ padding: '8px 14px', fontSize: 13, opacity: pensando ? 0.6 : 1, cursor: pensando ? 'default' : 'pointer' }}>→</button>
              </div>
            </div>
          </>
        )}

        {/* ── Alertas ── */}
        {tab === 'alertas' && (
          <div style={{ flex: 1, overflowY: 'auto', padding: 24 }}>
            <div style={{ marginBottom: 20 }}>
              <div className="eyebrow">Seguimiento en vivo</div>
              <h3 style={{ fontSize: 18 }}>Alertas del contrato {contrato?.numeroContrato ?? ''}</h3>
            </div>

            {alertaCronograma && (
              <AlertCard
                severity={alertaCronograma.severity === 'ok' ? 'ok' : alertaCronograma.severity === 'critica' ? 'urgent' : 'attention'}
                title="Cronograma del paso activo"
                desc={alertaCronograma.text}
                actionLabel="Ver paso"
                onAction={() => resolveAlert(alertaCronograma.id)}
              />
            )}

            {alertasApi.filter(a => !a.leida).map(a => {
              const severity = a.prioridad === 'ALTA' ? 'urgent' as const
                : a.prioridad === 'MEDIA' ? 'attention' as const
                : 'info' as const
              return (
                <AlertCard
                  key={a.id}
                  severity={severity}
                  title={a.tipo.charAt(0) + a.tipo.slice(1).toLowerCase().replace('_', ' ')}
                  desc={a.mensaje}
                  onAction={undefined}
                />
              )
            })}

            {alertasApi.filter(a => !a.leida).length === 0 && !alertaCronograma && (
              <div className="card" style={{ padding: '40px 20px', textAlign: 'center' }}>
                <div style={{ width: 34, height: 34, borderRadius: '50%', background: 'var(--accent)', color: '#03200D', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, fontSize: 16, margin: '0 auto 12px' }}>✓</div>
                <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--accent)', marginBottom: 4 }}>Sin alertas activas</div>
                <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>No hay alertas pendientes para este contrato.</div>
              </div>
            )}
          </div>
        )}

        {/* ── Documentos ── */}
        {tab === 'documentos' && (
          <div style={{ flex: 1, overflowY: 'auto', padding: 24 }}>
            <div style={{ marginBottom: 20 }}>
              <div className="eyebrow">GCCON-P-010</div>
              <h3 style={{ margin: '0 0 4px', fontSize: 18 }}>Documentos formales</h3>
              <p style={{ margin: 0, fontSize: 13, color: 'var(--text-muted)' }}>
                El Copiloto IA redacta cada documento cuando usted llega a su sub-paso — antes de eso, todavía
                no existe. Aquí solo se marca como disponible lo que ya se generó de verdad.
              </p>
              {tieneFirma === false && (
                <p style={{ margin: '8px 0 0', fontSize: 12.5, color: 'var(--alert-critica)', fontWeight: 600 }}>
                  ⚠ Todavía no se ha obtenido su firma electrónica. Solicítela al Administrador antes de poder firmar.
                </p>
              )}
            </div>

            {/* GCCON-P-010 formal docs — Copiloto genera, supervisor firma.
                El estado viene de docsContrato (datos reales), no de los pasos locales:
                si el documento no existe todavía en el backend, se marca "Sin generar",
                nunca se ofrece firmar algo que no fue realmente redactado. */}
            <div className="card" style={{ overflow: 'hidden', marginBottom: 24 }}>
              <div style={{ padding: '10px 16px', borderBottom: '1px solid var(--border)', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', letterSpacing: '0.06em', display: 'grid', gridTemplateColumns: '1fr 130px 120px 80px 150px', gap: 10, background: 'var(--bg-surface)' }}>
                <span>DOCUMENTO</span><span>CÓDIGO</span><span>DESCRIPCIÓN</span><span>ETAPA</span><span>ESTADO</span>
              </div>
              {FORMAL_DOCS.map(doc => {
                const generado = docsContrato.find(d => d.generadoPorIa && d.nombre.startsWith(doc.name))
                const ETAPA_LABEL: Record<number, string> = { 2: 'Inicio', 3: 'Inspección', 4: 'Recepción', 5: 'Certificación', 6: 'Cierre' }
                return (
                  <div key={doc.subStepId}
                    style={{ padding: '12px 16px', borderBottom: '1px solid var(--border)', display: 'grid', gridTemplateColumns: '1fr 130px 120px 80px 150px', gap: 10, alignItems: 'center' }}
                    onMouseEnter={e => (e.currentTarget.style.background = 'var(--accent-soft)')}
                    onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}>
                    <div>
                      <div style={{ fontSize: 13, fontWeight: 500, color: 'var(--text-primary)' }}>{doc.name}</div>
                      <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>IA genera · sub-paso {doc.subStepId}</div>
                    </div>
                    <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--accent-tech)' }}>
                      {doc.code === 'PENDIENTE_DE_DEFINIR' ? 'Código pendiente de definir' : doc.code}
                    </span>
                    <span style={{ fontSize: 11, color: 'var(--text-secondary)', lineHeight: 1.4 }}>{doc.desc}</span>
                    <span style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 500 }}>{ETAPA_LABEL[doc.step]}</span>
                    {!generado ? (
                      <Chip text="Sin generar aún" type="pending" />
                    ) : generado.estado === 'APROBADO' ? (
                      <Chip text="🟢 Firmado" type="signed" />
                    ) : (
                      <button
                        onClick={() => goToSubStep(doc.subStepId, doc.step)}
                        style={{ background: 'var(--accent-soft)', border: '1px solid var(--accent-line)', borderRadius: 6, padding: '5px 10px', fontSize: 11, color: 'var(--accent)', cursor: 'pointer', fontFamily: 'Inter, sans-serif', whiteSpace: 'nowrap' }}>
                        Ir a firmar →
                      </button>
                    )}
                  </div>
                )
              })}
            </div>

            {/* Documentos reales del contrato (backend) */}
            {docsContrato.length > 0 && (
              <div>
                <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', letterSpacing: '0.08em', marginBottom: 12 }}>DOCUMENTOS DEL CONTRATO</div>
                {docsContrato.map(doc => {
                  const estado = doc.estado === 'APROBADO'
                    ? { text: 'Disponible', type: 'done' as const }
                    : doc.estado === 'RECHAZADO'
                      ? { text: 'Rechazado', type: 'conflicto' as const }
                      : { text: 'Pendiente', type: 'pending' as const }
                  return (
                    <div key={doc.id} className="card" style={{ padding: '12px 16px', marginBottom: 8, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
                      <div>
                        <div style={{ fontSize: 13, fontWeight: 500, color: 'var(--accent-tech)', fontFamily: 'var(--font-mono)' }}>{doc.nombre}</div>
                        <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 2 }}>
                          {doc.tipo} · {formatFecha(doc.fechaSubida.slice(0, 10))}{doc.generadoPorIa ? ' · Generado por el Copiloto IA' : ''}
                        </div>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
                        <Chip text={estado.text} type={estado.type} />
                        {contrato && (
                          <button
                            onClick={() => descargarDocumento(contrato.id, doc.id, doc.nombre).catch(err => {
                              console.error('No se pudo descargar el documento:', err)
                              window.alert('No se pudo descargar el documento. Intente de nuevo en un momento.')
                            })}
                            style={{ background: 'var(--accent-soft)', border: '1px solid var(--accent-line)', borderRadius: 6, padding: '5px 10px', fontSize: 11, color: 'var(--accent)', cursor: 'pointer', fontFamily: 'Inter, sans-serif', whiteSpace: 'nowrap' }}>
                            Descargar
                          </button>
                        )}
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        )}

        {/* ── Registros ── */}
        {tab === 'registros' && <Registros extra={registros} />}
      </div>

      {/* Bottom bar */}
      <div style={{ display: 'flex', borderTop: '1px solid var(--border)', flexShrink: 0 }}>
        <button className="btn-ghost" disabled title="Disponible en una fase posterior" style={{ flex: 1, padding: '10px 0', fontSize: 12, borderRadius: 0, borderRight: '1px solid var(--border)', opacity: 0.5, cursor: 'not-allowed' }}>Ayuda SENA</button>
        <button className="btn-ghost" disabled title="Disponible en una fase posterior" style={{ flex: 1, padding: '10px 0', fontSize: 12, borderRadius: 0, opacity: 0.5, cursor: 'not-allowed' }}>Manual GCCON-M-002</button>
      </div>
    </div>
  )
}

// ─── Panel de alertas prominentes (izquierda de la barra de progreso) ──────────

function ProminentAlerts({ alerts, blink, onDismiss, onResolve }: {
  alerts: LiveAlert[]
  blink: boolean
  onDismiss: (id: string) => void
  onResolve: (id: string) => void
}) {
  if (alerts.length === 0) {
    return (
      <div style={{
        width: 200, flexShrink: 0,
        border: '1.5px solid var(--accent-line)',
        borderRadius: 10, padding: '20px 16px',
        background: 'var(--accent-soft)',
        display: 'flex', flexDirection: 'column',
        alignItems: 'center', justifyContent: 'center',
        gap: 8, minHeight: 90,
      }}>
        <div style={{
          width: 34, height: 34, borderRadius: '50%',
          background: 'var(--accent)', color: '#03200D',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontWeight: 800, fontSize: 16,
        }}>✓</div>
        <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--accent)' }}>Sin alertas pendientes</div>
        <div style={{ fontSize: 11, color: 'var(--text-muted)', textAlign: 'center', lineHeight: 1.4 }}>
          Sin alertas activas en este contrato
        </div>
      </div>
    )
  }

  return (
    <div style={{ width: 220, flexShrink: 0, display: 'flex', flexDirection: 'column', gap: 8 }}>
      {alerts.slice(0, 3).map(a => {
        const critical = a.severity === 'critica'
        const ok = a.severity === 'ok'
        const colorVar = critical ? 'var(--alert-critica)' : ok ? 'var(--accent)' : 'var(--alert-leve)'
        const colorHex = critical ? '#C83030' : ok ? '#2E9E5B' : '#B8780A'
        const bgColor = critical ? 'rgba(200,48,48,0.07)' : ok ? 'rgba(46,158,91,0.07)' : 'rgba(184,120,10,0.07)'
        const borderVal = critical ? 'rgba(200,48,48,0.28)' : ok ? 'rgba(46,158,91,0.28)' : 'rgba(184,120,10,0.28)'
        const btnText = critical ? '#FFFFFF' : '#03200D'
        const etiqueta = critical ? '● CRÍTICO' : ok ? '✓ A TIEMPO' : '◉ PENDIENTE'

        return (
          <div key={a.id}
            className={blink && !ok ? (critical ? 'blink-fast' : 'blink-slow') : undefined}
            style={{
              borderRadius: 10, padding: '12px 14px',
              border: `1px solid ${borderVal}`,
              borderLeft: `4px solid ${colorHex}`,
              background: bgColor,
            }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
              <span style={{ fontSize: 9, fontWeight: 800, letterSpacing: '0.12em', color: colorVar }}>
                {etiqueta}
              </span>
              <button onClick={() => onDismiss(a.id)}
                style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: 18, padding: 0, lineHeight: 1 }}>
                ×
              </button>
            </div>
            <p style={{ margin: '0 0 10px', fontSize: 12, lineHeight: 1.5, color: 'var(--text-primary)', fontWeight: 500 }}>
              {a.text}
            </p>
            <button onClick={() => onResolve(a.id)}
              style={{
                width: '100%', padding: '7px 0', fontSize: 11, fontWeight: 700,
                background: colorHex, color: btnText,
                border: 'none', borderRadius: 6, cursor: 'pointer',
                fontFamily: 'var(--font-ui)', letterSpacing: '0.03em',
                transition: 'opacity 0.15s',
              }}
              onMouseEnter={e => (e.currentTarget.style.opacity = '0.85')}
              onMouseLeave={e => (e.currentTarget.style.opacity = '1')}>
              {ok ? 'Ver paso →' : 'Resolver →'}
            </button>
          </div>
        )
      })}
    </div>
  )
}

// Alert card component
function AlertCard({ severity, title, desc, actionLabel, onAction }: {
  severity: 'urgent' | 'attention' | 'info' | 'ok'
  title: string
  desc: string
  actionLabel?: string
  onAction?: () => void
}) {
  const config = {
    urgent: { dot: '#EF4B4B', border: 'rgba(239,75,75,0.35)', bg: 'rgba(239,75,75,0.05)', labelColor: '#f87171', label: 'URGENTE' },
    attention: { dot: '#F2B84B', border: 'rgba(242,184,75,0.35)', bg: 'rgba(242,184,75,0.05)', labelColor: '#F2B84B', label: 'ATENCIÓN' },
    info: { dot: '#55BDD2', border: 'rgba(85,189,210,0.3)', bg: 'rgba(85,189,210,0.04)', labelColor: '#55BDD2', label: 'INFORMATIVO' },
    ok: { dot: '#2E9E5B', border: 'rgba(46,158,91,0.35)', bg: 'rgba(46,158,91,0.05)', labelColor: '#2E9E5B', label: 'A TIEMPO' },
  }[severity]

  return (
    <div className="card" style={{ padding: '14px 16px', marginBottom: 12, borderColor: config.border, background: config.bg }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
        <div style={{ width: 10, height: 10, borderRadius: '50%', background: config.dot, flexShrink: 0, marginTop: 4 }} />
        <div style={{ flex: 1 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
            <span style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.08em', color: config.labelColor }}>{config.label}</span>
            <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-primary)' }}>{title}</span>
          </div>
          <p style={{ margin: 0, fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.5 }}>{desc}</p>
          {actionLabel && onAction && (
            <button onClick={onAction}
              style={{ marginTop: 10, background: 'transparent', border: `1px solid ${config.border}`, borderRadius: 6, padding: '5px 12px', fontSize: 12, color: config.labelColor, cursor: 'pointer', fontFamily: 'Inter, sans-serif', transition: 'opacity 0.15s' }}
              onMouseEnter={e => (e.currentTarget.style.opacity = '0.75')}
              onMouseLeave={e => (e.currentTarget.style.opacity = '1')}>
              {actionLabel} →
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
