// Panel principal del Supervisor — las 5 etapas GCCON-P-010, alertas vivas,
// documentos generados por IA, copiloto conversacional y registros.
// Extraído 1:1 desde el App.tsx original de Figma Make — sin cambios visuales.

import { useState, useRef, useEffect } from 'react'
import { avatarGlyph, usePrefs } from '@/prefs'
import Registros, { type Registro } from '@/components/Registros'
import { Chip, SicotBadge, StageProgressBar, UserMenu, type LiveAlert, type Stage } from '@/components/ui'
import { AI_GENERATED_DOCS, TUTORIAL, CHAT_RESPONSES, FORMAL_DOCS } from '@/data/contractFlow'
import type { Step, Tab, ChatMsg } from '@/types/domain'
import type { AuthResponse, AlertaResponse, ContratoResponse, DocumentoResponse } from '@/services/api/types'
import { getEtapasContrato, cambiarEstadoSubetapa } from '@/services/etapaService'
import { getAlertasContrato, marcarAlertaLeida } from '@/services/alertaService'
import { getDocumentosContrato } from '@/services/documentoService'
import { mapEtapas } from '@/services/mappers'
import { formatCOP, formatFecha } from '@/services/format'

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
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '0 20px', height: 52, borderBottom: '1px solid var(--border)', flexShrink: 0 }}>
        <SicotBadge small />
        <span style={{ fontSize: 11, color: 'var(--text-muted)', padding: '2px 8px', border: '1px solid var(--border)', borderRadius: 4 }}>Panel Supervisor</span>
        <div style={{ flex: 1 }} />
        <button className="btn-ghost" onClick={onOpenSettings} style={{ padding: '5px 12px', fontSize: 12 }}>⚙ Configuración</button>
        <UserMenu label={usuario.nombre} email={usuario.email} onLogout={onLogout} />
      </div>

      {/* Content */}
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24 }}>
        <div className="card" style={{ maxWidth: 460, width: '100%', padding: 32, textAlign: 'center' }}>
          <div style={{ width: 40, height: 2, background: 'var(--accent)', borderRadius: 1, margin: '0 auto 20px' }} />
          <h2 style={{ margin: '0 0 12px', fontSize: 18, fontWeight: 700, fontFamily: 'var(--font-display, var(--font-ui))', letterSpacing: '-0.01em' }}>
            No tienes un contrato asignado
          </h2>
          <p style={{ color: 'var(--text-secondary)', fontSize: 14, lineHeight: 1.6, margin: '0 0 24px' }}>
            Actualmente no tienes un contrato asignado para seguimiento. Cuando Gestión te asigne uno, la información aparecerá aquí.
          </p>
          <div style={{ background: 'var(--accent-soft)', border: '1px solid var(--accent-line)', borderRadius: 8, padding: '12px 16px', marginBottom: 24, fontSize: 13, color: 'var(--text-muted)' }}>
            <div style={{ fontSize: 11, fontWeight: 600, letterSpacing: '0.06em', marginBottom: 6, color: 'var(--accent)' }}>ESTADO</div>
            <div>Esperando asignación de Gestión y Contratación</div>
          </div>
          <button className="btn-ghost" onClick={onStartTour} style={{ width: '100%', padding: '12px 0', fontSize: 13 }}>
            ▶ Ver tutorial del proceso
          </button>
        </div>
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
  steps, setSteps, newContractNotif, usuario, contrato, onLogout, onOpenSettings, onStartTour, registros, addRegistro,
}: {
  steps: Step[]
  setSteps: (s: Step[]) => void
  newContractNotif: boolean
  usuario: AuthResponse
  contrato: ContratoResponse | null
  onLogout: () => void
  onOpenSettings: () => void
  onStartTour: () => void
  registros: Registro[]
  addRegistro: (r: Registro) => void
}) {
  const { prefs } = usePrefs()
  const [dismissed, setDismissed] = useState<Set<string>>(new Set())
  const [tab, setTab] = useState<Tab>('panel')
  const [expandedSteps, setExpandedSteps] = useState<Set<number>>(new Set([3]))
  const [activeSubStep, setActiveSubStep] = useState<string | null>(null)
  const [tutorialMode, setTutorialMode] = useState(false)
  const [chatMsgs, setChatMsgs] = useState<ChatMsg[]>([{ role: 'ai', text: TUTORIAL.welcome }])
  const [chatInput, setChatInput] = useState('')
  const [confirmed, setConfirmed] = useState(true)
  const chatEndRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [chatMsgs])

  useEffect(() => {
    if (newContractNotif) {
      setChatMsgs(prev => [...prev, { role: 'ai', text: 'Gestión acaba de asignar un nuevo contrato. Revisa la pestaña Alertas para ver los detalles.' }])
    }
  }, [newContractNotif])

  // Cargar etapas/subetapas reales del contrato asignado (autoridad: backend)
  useEffect(() => {
    if (!contrato) return
    let cancelado = false
    getEtapasContrato(contrato.id)
      .then(etapas => {
        if (!cancelado) setSteps(mapEtapas(etapas))
      })
      .catch(() => { /* se conserva el estado actual */ })
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
      .catch(() => { })
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
      .catch(() => { })
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

  // Tutorial order for step 3
  const STEP3_ORDER = ['3.1', '3.2', '3.3', '3.4']

  const handleIniciarPaso3 = () => {
    setTutorialMode(true)
    setActiveSubStep('3.1')
    setExpandedSteps(new Set([3]))
    setChatMsgs(prev => [...prev, { role: 'ai', text: TUTORIAL['3.1'] }])
  }

  const handleActionSubStep = async (stepId: number, subStepId: string) => {
    const sub = steps.flatMap(s => s.subSteps).find(ss => ss.id === subStepId)
    if (AI_GENERATED_DOCS.has(subStepId)) {
      const doc = FORMAL_DOCS.find(d => d.subStepId === subStepId)
      addRegistro({
        id: 'sig-' + subStepId,
        tipo: 'Firma',
        destinatario: `${contrato?.supervisorNombre ?? ''} — Supervisor`,
        fecha: new Date().toLocaleString('es-CO', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }).replace(',', ''),
        asunto: `${doc?.name ?? 'Documento'} ${doc?.code ?? ''} — firma electrónica FIRMA-${Math.random().toString(16).slice(2, 8).toUpperCase()}`,
        estado: 'Firmado',
      })
    }
    const updated = steps.map(s =>
      s.id !== stepId ? s : { ...s, subSteps: s.subSteps.map(ss => ss.id === subStepId ? { ...ss, completed: true } : ss) }
    )
    setSteps(updated)

    // Persistir en el backend: el servidor recalcula porcentaje y estado de la etapa.
    if (sub?.apiId && contrato) {
      try {
        await cambiarEstadoSubetapa(sub.apiId, 'COMPLETADA')
        const etapas = await getEtapasContrato(contrato.id)
        setSteps(mapEtapas(etapas))
      } catch { /* el estado local se corrige en la siguiente carga */ }
    }

    // Advance within step 3 tutorial
    if (stepId === 3) {
      const idx = STEP3_ORDER.indexOf(subStepId)
      if (idx < STEP3_ORDER.length - 1) {
        const next = STEP3_ORDER[idx + 1]
        setActiveSubStep(next)
        setChatMsgs(prev => [...prev, { role: 'ai', text: TUTORIAL[next] }])
      } else {
        setActiveSubStep(null)
        setTutorialMode(false)
        setSteps(updated.map(s => s.id === 3 ? { ...s, status: 'completed' as const } : s))
        setChatMsgs(prev => [...prev, { role: 'ai', text: TUTORIAL.step3done }])
      }
    }
  }

  const sendChat = () => {
    const text = chatInput.trim()
    if (!text) return
    setChatMsgs(prev => [...prev, { role: 'user', text }])
    setChatInput('')
    const lower = text.toLowerCase()
    const match = CHAT_RESPONSES.find(([k]) => lower.includes(k))
    setTimeout(() => {
      setChatMsgs(prev => [...prev, { role: 'ai', text: match ? match[1] : `Entendido. Si tienes preguntas sobre el contrato ${contrato?.numeroContrato ?? ''}, estoy aquí para ayudarte.` }])
    }, 600)
  }

  const quickChat = (q: string) => {
    setChatMsgs(prev => [...prev, { role: 'user', text: q }])
    const lower = q.toLowerCase()
    const match = CHAT_RESPONSES.find(([k]) => lower.includes(k))
    setTimeout(() => {
      setChatMsgs(prev => [...prev, { role: 'ai', text: match ? match[1] : `Estoy aquí para ayudarte con el contrato ${contrato?.numeroContrato ?? ''}.` }])
    }, 500)
  }

  // Navigate to a specific sub-step from another tab
  const goToSubStep = (subStepId: string, stepId: number) => {
    setTab('panel')
    setExpandedSteps(prev => { const n = new Set(prev); n.add(stepId); return n })
    setActiveSubStep(subStepId)
    setTutorialMode(true)
  }

  const step3 = steps.find(s => s.id === 3)

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

  // ── Alertas vivas (máx. 3, descartables) ──
  // Fuente real: backend. No se generan alertas localmente.
  const alertasReales: LiveAlert[] = alertasApi.map(a => ({
    id: 'api-' + a.id,
    severity: a.prioridad === 'ALTA' ? 'critica' : 'leve',
    text: a.mensaje,
  }))
  const liveAlerts = alertasReales.filter(a => !dismissed.has(a.id))

  const dismiss = (id: string) => {
    setDismissed(prev => new Set(prev).add(id))
    if (id.startsWith('api-')) {
      const apiId = Number(id.slice(4))
      marcarAlertaLeida(apiId).catch(() => { })
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
    const actions: Record<string, () => void> = {
      'a-031': () => { setTab('panel'); setExpandedSteps(new Set([3])); setActiveSubStep('3.4'); setTutorialMode(true) },
      'a-gil': () => { setTab('panel'); setExpandedSteps(new Set([4])) },
      'a-p4': () => { setTab('panel'); setExpandedSteps(new Set([4])) },
      'a-venc': () => { setTab('panel'); setExpandedSteps(new Set([6])) },
      'a-new': () => setTab('alertas'),
    }
    actions[alertId]?.()
  }

  // Protección: si no hay contrato asignado, mostrar estado vacío
  if (!contrato) {
    return <EmptyContractState usuario={usuario} onLogout={onLogout} onOpenSettings={onOpenSettings} onStartTour={onStartTour} />
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', background: 'var(--bg-base)', overflow: 'hidden' }}>
      {/* Top bar */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '0 20px', height: 52, borderBottom: '1px solid var(--border)', flexShrink: 0 }}>
        <SicotBadge small />
        <span style={{ fontSize: 11, color: 'var(--text-muted)', padding: '2px 8px', border: '1px solid var(--border)', borderRadius: 4 }}>Panel Supervisor</span>
        <div style={{ flex: 1 }} />
        <button className="btn-ghost" style={{ padding: '5px 12px', fontSize: 12, opacity: 0.45, cursor: 'not-allowed' }}
          title="La publicación en SECOP II corresponde a la Unidad de Contratación">
          Conectar SECOP II
        </button>
        <button className="btn-ghost" onClick={onStartTour} style={{ padding: '5px 12px', fontSize: 12 }}>▶ Tutorial</button>
        <button className="btn-ghost" onClick={onOpenSettings} style={{ padding: '5px 12px', fontSize: 12 }}>⚙ Configuración</button>
        <UserMenu label={usuario.nombre} email={usuario.email} onLogout={onLogout} />
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', borderBottom: '1px solid var(--border)', padding: '0 20px', flexShrink: 0 }}>
        {(['panel', 'alertas', 'documentos', 'registros'] as Tab[]).map(t => (
          <button key={t} data-tour={`tab-${t}`} className={`tab-btn ${tab === t ? 'active' : ''}`} onClick={() => setTab(t)}>
            {t === 'panel' ? 'Panel Principal'
              : t === 'alertas' ? (<span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>Alertas{newContractNotif && <span style={{ width: 7, height: 7, borderRadius: '50%', background: 'var(--alert-critica)', display: 'inline-block' }} />}</span>)
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
              <div data-tour="contrato" className="card" style={{ marginBottom: 16, padding: '14px 16px', borderColor: 'var(--accent-line)', background: 'var(--accent-soft)' }}>
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
                            const actionLabel = isAiDoc ? 'Firmar documento' : 'Marcar completado'
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
                                      style={{ padding: '3px 12px', fontSize: 11, marginLeft: 4 }}>
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
                  <div style={{ width: 36, height: 36, borderRadius: '50%', background: 'var(--bg-card)', border: '1.5px solid var(--accent)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 18 }}>{avatarGlyph(prefs.avatarId)}</div>
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
                        <div style={{ width: 24, height: 24, borderRadius: '50%', background: 'var(--accent-soft)', border: '1px solid var(--accent-line)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, flexShrink: 0, marginTop: 2, color: 'var(--accent)' }}>★</div>
                        <div className="copiloto-msg" style={{ maxWidth: '92%' }}>{m.text}</div>
                      </div>
                    ) : (
                      <div className="user-msg" style={{ maxWidth: '88%' }}>{m.text}</div>
                    )}
                  </div>
                ))}
                {!tutorialMode && step3?.status === 'active' && chatMsgs.length === 1 && (
                  <div style={{ paddingLeft: 30 }}>
                    <button className="btn-green" onClick={handleIniciarPaso3} style={{ padding: '8px 16px', fontSize: 13 }}>Iniciar Paso 3</button>
                  </div>
                )}
                <div ref={chatEndRef} />
              </div>

              {/* Quick suggestions */}
              <div style={{ padding: '8px 12px', borderTop: '1px solid var(--border)', display: 'flex', gap: 6, flexWrap: 'wrap', flexShrink: 0 }}>
                {['¿Qué documentos necesito?', 'GCCON-F-031', 'GIL-F-010', 'ESUCON', 'GCCON-F-030'].map(q => (
                  <button key={q} onClick={() => quickChat(q)}
                    style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', borderRadius: 20, padding: '4px 10px', fontSize: 11, color: 'var(--text-secondary)', cursor: 'pointer', fontFamily: 'Inter, sans-serif', transition: 'border-color 0.15s' }}
                    onMouseEnter={e => (e.currentTarget.style.borderColor = 'var(--accent-dim)')}
                    onMouseLeave={e => (e.currentTarget.style.borderColor = 'var(--border)')}>
                    {q}
                  </button>
                ))}
              </div>

              {/* Input */}
              <div style={{ padding: '8px 12px 12px', borderTop: '1px solid var(--border)', display: 'flex', gap: 8, flexShrink: 0 }}>
                <span style={{ color: 'var(--text-muted)', fontSize: 14, paddingTop: 8, fontFamily: 'var(--font-mono)' }}>+</span>
                <input type="text" value={chatInput} onChange={e => setChatInput(e.target.value)}
                  onKeyDown={e => { if (e.key === 'Enter') sendChat() }}
                  placeholder="Escribe una orden o pregunta a la IA..."
                  style={{ flex: 1, padding: '8px 12px' }} />
                <button className="btn-green" onClick={sendChat} style={{ padding: '8px 14px', fontSize: 13 }}>→</button>
              </div>
            </div>
          </>
        )}

        {/* ── Alertas ── */}
        {tab === 'alertas' && (
          <div style={{ flex: 1, overflowY: 'auto', padding: 24 }}>
            <h3 style={{ margin: '0 0 20px', fontSize: 15, fontWeight: 700 }}>Alertas del contrato {contrato?.numeroContrato ?? ''}</h3>

            {newContractNotif && (
              <AlertCard
                severity="info"
                title="Nuevo contrato asignado"
                desc="Gestión asignó un nuevo contrato. Revisa el Panel Principal para ver los detalles actualizados."
                onAction={undefined}
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

            {alertasApi.filter(a => !a.leida).length === 0 && !newContractNotif && (
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
              <h3 style={{ margin: '0 0 4px', fontSize: 15, fontWeight: 700 }}>Documentos formales</h3>
              <p style={{ margin: 0, fontSize: 13, color: 'var(--text-muted)' }}>
                Documentos generados automáticamente por el Copiloto IA — el supervisor solo firma.
              </p>
            </div>

            {/* GCCON-P-010 formal docs — Copiloto genera, supervisor firma */}
            <div className="card" style={{ overflow: 'hidden', marginBottom: 24 }}>
              <div style={{ padding: '10px 16px', borderBottom: '1px solid var(--border)', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', letterSpacing: '0.06em', display: 'grid', gridTemplateColumns: '1fr 130px 120px 80px 130px', gap: 10 }}>
                <span>DOCUMENTO</span><span>CÓDIGO</span><span>DESCRIPCIÓN</span><span>ETAPA</span><span>ACCIÓN</span>
              </div>
              {FORMAL_DOCS.map(doc => {
                const subStep = steps.flatMap(s => s.subSteps).find(ss => ss.id === doc.subStepId)
                const signed = subStep?.completed ?? false
                const ETAPA_LABEL: Record<number, string> = { 2: 'Inicio', 3: 'Inspección', 4: 'Recepción', 5: 'Certificación', 6: 'Cierre' }
                return (
                  <div key={doc.subStepId}
                    style={{ padding: '12px 16px', borderBottom: '1px solid var(--border)', display: 'grid', gridTemplateColumns: '1fr 130px 120px 80px 130px', gap: 10, alignItems: 'center' }}
                    onMouseEnter={e => (e.currentTarget.style.background = 'var(--accent-soft)')}
                    onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}>
                    <div>
                      <div style={{ fontSize: 13, fontWeight: 500, color: 'var(--text-primary)' }}>{doc.name}</div>
                      <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>IA genera · sub-paso {doc.subStepId}</div>
                    </div>
                    <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--accent-tech)' }}>{doc.code}</span>
                    <span style={{ fontSize: 11, color: 'var(--text-secondary)', lineHeight: 1.4 }}>{doc.desc}</span>
                    <span style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 500 }}>{ETAPA_LABEL[doc.step]}</span>
                    {signed
                      ? <Chip text="🟢 Firmado" type="signed" />
                      : (
                        <button
                          onClick={() => goToSubStep(doc.subStepId, doc.step)}
                          style={{ background: 'var(--accent-soft)', border: '1px solid var(--accent-line)', borderRadius: 6, padding: '5px 10px', fontSize: 11, color: 'var(--accent)', cursor: 'pointer', fontFamily: 'Inter, sans-serif', whiteSpace: 'nowrap', transition: 'background 0.15s' }}
                          onMouseEnter={e => (e.currentTarget.style.background = 'var(--accent-soft)')}
                          onMouseLeave={e => (e.currentTarget.style.background = 'var(--accent-soft)')}>
                          Firmar →
                        </button>
                      )
                    }
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
                    <div key={doc.id} className="card" style={{ padding: '12px 16px', marginBottom: 8, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                      <div>
                        <div style={{ fontSize: 13, fontWeight: 500, color: 'var(--accent-tech)', fontFamily: 'var(--font-mono)' }}>{doc.nombre}</div>
                        <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 2 }}>
                          {doc.tipo} · {formatFecha(doc.fechaSubida.slice(0, 10))}
                        </div>
                      </div>
                      <Chip text={estado.text} type={estado.type} />
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
        <button className="btn-ghost" style={{ flex: 1, padding: '10px 0', fontSize: 12, borderRadius: 0, borderRight: '1px solid var(--border)' }}>Ayuda SENA</button>
        <button className="btn-ghost" style={{ flex: 1, padding: '10px 0', fontSize: 12, borderRadius: 0 }}>Manual GCCON-M-002</button>
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
        <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--accent)' }}>Al día</div>
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
        const colorVar = critical ? 'var(--alert-critica)' : 'var(--alert-leve)'
        const colorHex = critical ? '#C83030' : '#B8780A'
        const bgColor = critical ? 'rgba(200,48,48,0.07)' : 'rgba(184,120,10,0.07)'
        const borderVal = critical ? 'rgba(200,48,48,0.28)' : 'rgba(184,120,10,0.28)'
        const btnText = critical ? '#FFFFFF' : '#03200D'

        return (
          <div key={a.id}
            className={blink ? (critical ? 'blink-fast' : 'blink-slow') : undefined}
            style={{
              borderRadius: 10, padding: '12px 14px',
              border: `1px solid ${borderVal}`,
              borderLeft: `4px solid ${colorHex}`,
              background: bgColor,
            }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
              <span style={{ fontSize: 9, fontWeight: 800, letterSpacing: '0.12em', color: colorVar }}>
                {critical ? '● CRÍTICO' : '◉ PENDIENTE'}
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
              Resolver →
            </button>
          </div>
        )
      })}
    </div>
  )
}

// Alert card component
function AlertCard({ severity, title, desc, actionLabel, onAction }: {
  severity: 'urgent' | 'attention' | 'info'
  title: string
  desc: string
  actionLabel?: string
  onAction?: () => void
}) {
  const config = {
    urgent: { dot: '#EF4B4B', border: 'rgba(239,75,75,0.35)', bg: 'rgba(239,75,75,0.05)', labelColor: '#f87171', label: 'URGENTE' },
    attention: { dot: '#F2B84B', border: 'rgba(242,184,75,0.35)', bg: 'rgba(242,184,75,0.05)', labelColor: '#F2B84B', label: 'ATENCIÓN' },
    info: { dot: '#55BDD2', border: 'rgba(85,189,210,0.3)', bg: 'rgba(85,189,210,0.04)', labelColor: '#55BDD2', label: 'INFORMATIVO' },
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
