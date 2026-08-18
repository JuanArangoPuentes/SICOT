// Panel de Gestión y Contratación — carga de fichas de contrato, tabla de
// contratos y flujo de asignación al supervisor.
// Extraído 1:1 desde el App.tsx original de Figma Make — sin cambios visuales.

import { useEffect, useState } from 'react'
import { Chip, SicotBadge, UserMenu, type ChipType } from '@/components/ui'
import { IconCheckCircle, IconClipboardList, IconFileText, IconLoader, IconPlay, IconSettings, IconUpload } from '@/components/icons'
import type { UploadState } from '@/types/domain'
import type { AuthResponse, ContratoResponse, EstadoContrato } from '@/services/api/types'
import { getContratos, crearContrato } from '@/services/contratoService'
import { getUsuarios } from '@/services/usuarioService'
import { ApiError } from '@/services/api/client'
import { formatCOP, formatFecha } from '@/services/format'

// Secuencias diferenciadas por tipo de contrato (definición del proceso, no se persiste)
const CONTRACT_TYPES: Record<string, { etapas: string[]; documentos: string[] }> = {
  'Suministro de Bienes': {
    etapas: ['Inicio', 'Inspección de Recepción', 'Recibo a Satisfacción', 'Cierre'],
    documentos: ['Acta de Inicio', 'GIL-F-010', 'ESUCON'],
  },
  Servicios: {
    etapas: ['Inicio', 'Ejecución', 'Inspección de Servicios', 'Cierre'],
    documentos: ['Acta de Inicio', 'GCCON-F-031', 'Certificación'],
  },
  Obras: {
    etapas: ['Inicio', 'Ejecución Parcial', 'Inspección Técnica', 'Cierre'],
    documentos: ['Acta de Inicio', 'Actas de Avance', 'Certificación Final'],
  },
  Arrendamiento: {
    etapas: ['Inicio', 'Ejecución', 'Verificación de Canon', 'Cierre'],
    documentos: ['Acta de Inicio', 'GCCON-F-031', 'Acta de Liquidación'],
  },
}

// Supervisores disponibles para asignación — se cargan desde /api/usuarios
// (solo visible para ADMINISTRADOR; el rol GESTION recibe 403 y crea sin asignar)
interface SupervisorOption { id: number; nombre: string }

const CENTROS_COSTO = ['920510 — CTMA Formación', '920511 — CTMA Ebanistería', '920512 — CTMA Tapicería']

const ESTADO_ROW: Record<EstadoContrato, { label: string; type: ChipType }> = {
  ACTIVO: { label: 'Activo', type: 'vigente' },
  BORRADOR: { label: 'Borrador', type: 'pending' },
  SUSPENDIDO: { label: 'Suspendido', type: 'unassigned' },
  FINALIZADO: { label: 'Finalizado', type: 'finished' },
  CANCELADO: { label: 'Cancelado', type: 'inactive' },
}

const mapContratoRow = (c: ContratoResponse) => ({
  id: c.numeroContrato,
  object: c.objeto,
  supervisor: c.supervisorNombre ?? '— Sin asignar —',
  statusLabel: ESTADO_ROW[c.estado].label,
  statusType: ESTADO_ROW[c.estado].type,
  value: formatCOP(c.valor),
  vigencia: `${formatFecha(c.fechaInicio)} – ${formatFecha(c.fechaFin)}`,
})

const parseValor = (texto: string): number | null => {
  const n = Number(texto.replace(/[$.\s]/g, '').replace(',', '.'))
  return Number.isFinite(n) && n > 0 ? n : null
}

const parseVigencia = (texto: string): { inicio: string | null; fin: string | null } => {
  const fechas = texto.match(/\d{2}\/\d{2}\/\d{4}/g)
  const toIso = (f: string) => {
    const [d, m, y] = f.split('/')
    return `${y}-${m}-${d}`
  }
  if (!fechas || fechas.length < 2) return { inicio: null, fin: null }
  return { inicio: toIso(fechas[0]), fin: toIso(fechas[1]) }
}

export default function GestionPanel({ usuario, onNewContractAssigned, onLogout, onOpenSettings, onStartTour }: {
  usuario: AuthResponse
  onNewContractAssigned: () => void
  onLogout: () => void
  onOpenSettings: () => void
  onStartTour: () => void
}) {
  const [uploadState, setUploadState] = useState<UploadState>('idle')
  const [showModal, setShowModal] = useState(false)
  const [lastProcessedContract, setLastProcessedContract] = useState<{ id: string; supervisor: string } | null>(null)
  const [progress, setProgress] = useState(0)
  const [tipo, setTipo] = useState('Suministro de Bienes')
  const [centro, setCentro] = useState(CENTROS_COSTO[0])
  const [supervisor, setSupervisor] = useState('')
  const [revisionIA, setRevisionIA] = useState(false)

  const [contratos, setContratos] = useState<ReturnType<typeof mapContratoRow>[]>([])
  const [supervisores, setSupervisores] = useState<SupervisorOption[]>([])
  const [busyCrear, setBusyCrear] = useState(false)
  const [errorCrear, setErrorCrear] = useState('')

  // Campos de la ficha (datos que se persisten en el backend)
  const [idContrato, setIdContrato] = useState('')
  const [objeto, setObjeto] = useState('')
  const [proveedor, setProveedor] = useState('')
  const [valor, setValor] = useState('')
  const [vigencia, setVigencia] = useState('')
  const [nit, setNit] = useState('')
  const [representanteLegal, setRepresentanteLegal] = useState('')
  const [lugarEjecucion, setLugarEjecucion] = useState('')
  const [registroPresupuestal, setRegistroPresupuestal] = useState('')

  // Tabla de contratos reales (GET /api/contratos)
  useEffect(() => {
    let cancelado = false
    getContratos()
      .then(lista => {
        if (!cancelado) setContratos(lista.map(mapContratoRow))
      })
      .catch(() => {})
    return () => { cancelado = true }
  }, [])

  // Supervisores para asignación (solo ADMINISTRADOR tiene acceso)
  useEffect(() => {
    let cancelado = false
    getUsuarios()
      .then(lista => {
        if (cancelado) return
        const sups = lista.filter(u => u.rol === 'SUPERVISOR' && u.activo)
          .map(u => ({ id: u.id, nombre: u.nombre }))
        setSupervisores(sups)
        setSupervisor(String(sups[0]?.id ?? ''))
      })
      .catch(() => {})
    return () => { cancelado = true }
  }, [])

  const openUpload = () => { setShowModal(true); setUploadState('idle'); setProgress(0); setRevisionIA(false) }

  const handleFileSelect = () => {
    setUploadState('uploading')
    let p = 0
    const interval = setInterval(() => {
      p += 15
      setProgress(p)
      if (p >= 100) {
        clearInterval(interval)
        setUploadState('analyzing')
        setTimeout(() => setUploadState('detect'), 1800)
      }
    }, 120)
  }

  const handleAsignar = async () => {
    if (busyCrear) return
    const num = idContrato.trim()
    const obj = objeto.trim()
    const val = parseValor(valor)
    if (!num) { setErrorCrear('El ID del contrato es obligatorio.'); return }
    if (!obj) { setErrorCrear('El objeto del contrato es obligatorio.'); return }
    if (val == null) { setErrorCrear('El valor debe ser un número mayor que cero.'); return }
    setBusyCrear(true)
    setErrorCrear('')
    try {
      const vig = parseVigencia(vigencia)
      const creado = await crearContrato({
        numeroContrato: num,
        objeto: obj,
        valor: val,
        fechaInicio: vig.inicio,
        fechaFin: vig.fin,
        supervisorId: supervisor ? Number(supervisor) : null,
        tipoContrato: tipo,
        contratista: proveedor.trim() || null,
        contratistaNit: nit.trim() || null,
        representanteLegal: representanteLegal.trim() || null,
        lugarEjecucion: lugarEjecucion.trim() || null,
        numeroRegistroPresupuestal: registroPresupuestal.trim() || null,
        centroCosto: centro,
      })
      setUploadState('done')
      const sup = supervisores.find(s => String(s.id) === supervisor)
      setLastProcessedContract({ id: creado.numeroContrato, supervisor: sup?.nombre ?? '— Sin asignar —' })
      const lista = await getContratos()
      setContratos(lista.map(mapContratoRow))
      setTimeout(() => {
        setShowModal(false)
        onNewContractAssigned()
      }, 1200)
    } catch (e) {
      setErrorCrear(e instanceof ApiError ? e.message : 'No se pudo crear el contrato.')
      setUploadState('review')
    } finally {
      setBusyCrear(false)
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', background: 'var(--bg-base)', overflow: 'hidden' }}>
      {/* Top bar */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '0 20px', height: 52, borderBottom: '1px solid var(--border)', flexShrink: 0 }}>
        <SicotBadge small />
        <span style={{ fontSize: 11, color: 'var(--text-muted)', padding: '2px 8px', border: '1px solid var(--border)', borderRadius: 4 }}>Panel Gestión</span>
        <div style={{ flex: 1 }} />
        <button className="btn-ghost" onClick={onStartTour} style={{ padding: '5px 12px', fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 6 }}><IconPlay size={10} /> Tutorial</button>
        <button className="btn-ghost" onClick={onOpenSettings} style={{ padding: '5px 12px', fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 6 }}><IconSettings size={13} /> Configuración</button>
        <UserMenu label={usuario.nombre} email={usuario.email} avatarColor="#7c3aed" avatarTextColor="white" onLogout={onLogout} />
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: '20px 24px' }}>
        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
          <div>
            <h2 style={{ margin: 0, fontSize: 18, fontWeight: 700 }}>Contratos</h2>
            <p style={{ margin: '4px 0 0', fontSize: 13, color: 'var(--text-muted)' }}>Panel de Gestión y Contratación</p>
          </div>
          <button data-tour="cargar" className="btn-green" onClick={openUpload} style={{ padding: '9px 18px', fontSize: 13 }}>
            + Cargar nueva ficha
          </button>
        </div>

        {/* Ficha procesada card — solo se muestra tras cargar y procesar una ficha real */}
        {lastProcessedContract && (
          <div data-tour="ficha" className="card" style={{ padding: '14px 16px', marginBottom: 20, borderColor: 'var(--accent-line)', background: 'var(--accent-soft)', display: 'flex', alignItems: 'center', gap: 16 }}>
            <div style={{ width: 40, height: 40, borderRadius: '50%', background: 'var(--chip-green-bg)', border: '1.5px solid var(--chip-green)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 18, flexShrink: 0 }}>✓</div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--accent)', letterSpacing: '0.06em', marginBottom: 2 }}>FICHA PROCESADA</div>
              <div style={{ fontSize: 13, color: 'var(--text-primary)' }}>El Copiloto IA procesó y asignó <strong>{lastProcessedContract.id}</strong> automáticamente a {lastProcessedContract.supervisor}.</div>
            </div>
            <Chip text="Asignado" type="done" />
          </div>
        )}

        {/* Contracts table */}
        <div data-tour="tabla" className="card" style={{ overflow: 'hidden' }}>
          <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--border)', fontSize: 12, fontWeight: 600, color: 'var(--text-muted)', letterSpacing: '0.06em' }}>
            REGISTRO DE CONTRATOS
          </div>
          {/* Header row */}
          <div style={{ display: 'grid', gridTemplateColumns: '180px 1fr 180px 160px 120px 180px', padding: '8px 16px', borderBottom: '1px solid var(--border)', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', letterSpacing: '0.04em', gap: 12 }}>
            <span>CONTRATO</span><span>OBJETO</span><span>SUPERVISOR</span><span>ESTADO</span><span>VALOR</span><span>VIGENCIA</span>
          </div>
          {/* Empty state */}
          {contratos.length === 0 && (
            <div style={{ padding: '40px 16px', textAlign: 'center' }}>
              <IconClipboardList size={26} style={{ opacity: 0.5, margin: '0 auto 8px' }} />
              <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-primary)', marginBottom: 4 }}>Aún no tienes contratos registrados</div>
              <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>Carga una ficha de contrato para que el Copiloto la procese automáticamente.</div>
            </div>
          )}
          {/* Data rows */}
          {contratos.map(c => (
            <div key={c.id}
              style={{ display: 'grid', gridTemplateColumns: '180px 1fr 180px 160px 120px 180px', padding: '12px 16px', borderBottom: '1px solid var(--border)', fontSize: 13, gap: 12, alignItems: 'center', transition: 'background 0.1s', cursor: 'default' }}
              onMouseEnter={e => (e.currentTarget.style.background = 'var(--accent-soft)')}
              onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}>
              <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--accent-tech)' }}>{c.id}</span>
              <span style={{ color: 'var(--text-primary)', fontSize: 12 }}>{c.object}</span>
              <span style={{ color: c.supervisor === '— Sin asignar —' ? 'var(--text-muted)' : 'var(--text-secondary)', fontSize: 12, fontStyle: c.supervisor === '— Sin asignar —' ? 'italic' : 'normal' }}>{c.supervisor}</span>
              <Chip text={c.statusLabel} type={c.statusType} />
              <span style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-mono)', fontSize: 12 }}>{c.value}</span>
              <span style={{ color: 'var(--text-muted)', fontSize: 12 }}>{c.vigencia}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Upload modal */}
      {showModal && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.75)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100, padding: 24 }}>
          <div className="card" style={{ width: '100%', maxWidth: 480, padding: 28 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700 }}>Cargar nueva ficha de contrato</h3>
              {uploadState !== 'done' && (
                <button onClick={() => setShowModal(false)} style={{ background: 'none', border: 'none', color: 'var(--text-muted)', fontSize: 20, cursor: 'pointer', padding: 0 }}>×</button>
              )}
            </div>

            {uploadState === 'idle' && (
              <div>
                <div onClick={handleFileSelect}
                  style={{ border: '2px dashed var(--border)', borderRadius: 10, padding: '40px 20px', textAlign: 'center', cursor: 'pointer', transition: 'border-color 0.15s' }}
                  onMouseEnter={e => (e.currentTarget.style.borderColor = 'var(--accent-dim)')}
                  onMouseLeave={e => (e.currentTarget.style.borderColor = 'var(--border)')}>
                  <IconFileText size={34} style={{ color: 'var(--text-muted)', margin: '0 auto 12px' }} />
                  <p style={{ color: 'var(--text-secondary)', fontSize: 14, margin: 0 }}>
                    Haz clic para seleccionar el documento<br />
                    <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>PDF, DOCX — max 20 MB</span>
                  </p>
                </div>
                <p style={{ color: 'var(--text-muted)', fontSize: 11, marginTop: 12, textAlign: 'center' }}>
                  Nota: en el prototipo la lectura se simula con datos de ejemplo precargados.
                </p>
              </div>
            )}

            {uploadState === 'uploading' && (
              <div style={{ textAlign: 'center', padding: '20px 0' }}>
                <IconUpload size={32} style={{ color: 'var(--accent)', margin: '0 auto 16px' }} />
                <p style={{ fontSize: 14, marginBottom: 12 }}>Subiendo documento...</p>
                <div style={{ height: 4, background: 'var(--border)', borderRadius: 2, overflow: 'hidden' }}>
                  <div style={{ height: '100%', width: `${progress}%`, background: 'var(--accent)', borderRadius: 2, transition: 'width 0.12s' }} />
                </div>
                <p style={{ color: 'var(--text-muted)', fontSize: 12, marginTop: 8 }}>{progress}%</p>
              </div>
            )}

            {uploadState === 'analyzing' && (
              <div style={{ textAlign: 'center', padding: '32px 0' }}>
                <IconLoader size={30} style={{ color: 'var(--accent)', margin: '0 auto 16px' }} />
                <p style={{ color: 'var(--accent)', fontSize: 14, fontWeight: 600 }}>Analizando documento...</p>
                <p style={{ color: 'var(--text-muted)', fontSize: 12 }}>El Copiloto IA está extrayendo los datos del contrato</p>
              </div>
            )}

            {uploadState === 'detect' && (
              <div>
                <div className="card" style={{ padding: 0, overflow: 'hidden', borderColor: 'var(--accent-line)' }}>
                  <div style={{ padding: '10px 14px', borderBottom: '1px solid var(--border)', fontSize: 11, fontWeight: 700, letterSpacing: '0.1em', color: 'var(--accent)' }}>
                    ◆ ANÁLISIS DEL CONTRATO — TIPO IDENTIFICADO
                  </div>
                  <div style={{ padding: '14px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
                      <IconCheckCircle size={16} style={{ color: 'var(--accent)' }} />
                      <strong style={{ fontSize: 14 }}>Suministro de Bienes</strong>
                      <Chip text="Menor cuantía" type="document" />
                    </div>
                    <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.1em', color: 'var(--text-muted)', marginBottom: 6 }}>CARACTERÍSTICAS</div>
                    {[
                      ['Proveedor', '—'],
                      ['NIT', '—'],
                      ['Monto', '—'],
                      ['Acto administrativo', '—'],
                    ].map(([k, v]) => (
                      <div key={k} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12.5, padding: '3px 0', color: 'var(--text-secondary)' }}>
                        <span>{k}</span>
                        <span style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-mono)', fontSize: 12 }}>{v}</span>
                      </div>
                    ))}
                    <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.1em', color: 'var(--text-muted)', margin: '14px 0 6px' }}>SECUENCIA RECOMENDADA</div>
                    {CONTRACT_TYPES['Suministro de Bienes'].etapas.map((e, i) => (
                      <div key={e} style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 12.5, padding: '3px 0' }}>
                        <span style={{ width: 18, height: 18, borderRadius: '50%', background: 'var(--accent-soft)', color: 'var(--accent)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 10, fontWeight: 700 }}>{i + 1}</span>
                        {e}
                      </div>
                    ))}
                  </div>
                </div>
                <button className="btn-green" onClick={() => setUploadState('review')} style={{ width: '100%', padding: '10px 0', fontSize: 13, marginTop: 16 }}>
                  Continuar a confirmación →
                </button>
              </div>
            )}

            {uploadState === 'review' && (
              <div>
                <div style={{ marginBottom: 16, padding: '10px 14px', background: 'var(--accent-soft)', border: '1px solid var(--accent-line)', borderRadius: 8, fontSize: 13, color: 'var(--accent)' }}>
                  ✓ Documento analizado. Revisa, corrige y confirma los datos extraídos.
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {[
                    { label: 'ID Contrato', value: idContrato, onChange: setIdContrato, mono: true },
                    { label: 'Objeto', value: objeto, onChange: setObjeto, mono: false },
                    { label: 'Proveedor / Contratista', value: proveedor, onChange: setProveedor, mono: false },
                    { label: 'NIT o CC del contratista', value: nit, onChange: setNit, mono: true },
                    { label: 'Representante legal', value: representanteLegal, onChange: setRepresentanteLegal, mono: false },
                    { label: 'Valor', value: valor, onChange: setValor, mono: true },
                    { label: 'Vigencia (dd/mm/aaaa – dd/mm/aaaa)', value: vigencia, onChange: setVigencia, mono: false },
                    { label: 'Lugar de ejecución', value: lugarEjecucion, onChange: setLugarEjecucion, mono: false },
                    { label: 'Número de registro presupuestal', value: registroPresupuestal, onChange: setRegistroPresupuestal, mono: true },
                  ].map(f => (
                    <div key={f.label}>
                      <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 4 }}>{f.label}</div>
                      <input type="text" value={f.value} onChange={e => f.onChange(e.target.value)}
                        style={{ width: '100%', padding: '8px 12px', fontFamily: f.mono ? 'var(--font-mono)' : 'inherit', fontSize: 13 }} />
                    </div>
                  ))}
                  <div>
                    <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 4 }}>Tipo de contrato</div>
                    <select value={tipo} onChange={e => setTipo(e.target.value)}>
                      {Object.keys(CONTRACT_TYPES).map(t => <option key={t}>{t}</option>)}
                    </select>
                    <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 5 }}>
                      Secuencia: {CONTRACT_TYPES[tipo].etapas.join(' → ')}
                    </div>
                  </div>
                  <div>
                    <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 4 }}>Centro de costo</div>
                    <select value={centro} onChange={e => setCentro(e.target.value)}>
                      {CENTROS_COSTO.map(c => <option key={c}>{c}</option>)}
                    </select>
                  </div>
                  <div>
                    <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 4 }}>Supervisor asignado</div>
                    <select value={supervisor} onChange={e => setSupervisor(e.target.value)}>
                      {supervisores.length === 0 && <option value="">— Sin asignar —</option>}
                      {supervisores.map(s => <option key={s.id} value={String(s.id)}>{s.nombre}</option>)}
                    </select>
                  </div>
                </div>

                {revisionIA && (
                  <div style={{ marginTop: 14, padding: '10px 14px', border: '1px solid var(--accent-line)', borderRadius: 8, fontSize: 12.5, color: 'var(--text-secondary)', lineHeight: 1.55 }}>
                    ◆ Revisión adicional: los documentos clave para <strong>{tipo}</strong> son{' '}
                    {CONTRACT_TYPES[tipo].documentos.join(', ')}. No se detectaron inconsistencias entre el objeto contractual y la modalidad seleccionada.
                  </div>
                )}

                {errorCrear && (
                  <div style={{ marginTop: 12, padding: '8px 12px', border: '1px solid var(--chip-red)', background: 'var(--chip-red-bg)', borderRadius: 8, fontSize: 12.5, color: 'var(--text-primary)' }}>
                    {errorCrear}
                  </div>
                )}

                <div style={{ display: 'flex', gap: 10, marginTop: 20, flexWrap: 'wrap' }}>
                  <button className="btn-ghost" onClick={() => setUploadState('idle')} style={{ flex: 1, padding: '10px 0', fontSize: 13, minWidth: 110 }}>✗ Rechazar</button>
                  <button className="btn-ghost" onClick={() => setRevisionIA(true)} style={{ flex: 1, padding: '10px 0', fontSize: 13, minWidth: 140 }}>? Revisión IA</button>
                  <button className="btn-green" onClick={handleAsignar} disabled={busyCrear} style={{ flex: 2, padding: '10px 0', fontSize: 13, minWidth: 170, opacity: busyCrear ? 0.6 : 1, cursor: busyCrear ? 'default' : 'pointer' }}>
                    {busyCrear ? 'Guardando…' : '✓ Confirmar y cargar'}
                  </button>
                </div>
              </div>
            )}

            {uploadState === 'done' && (
              <div style={{ textAlign: 'center', padding: '24px 0' }}>
                <IconCheckCircle size={44} style={{ color: 'var(--accent)', margin: '0 auto 12px' }} />
                <p style={{ color: 'var(--accent)', fontSize: 15, fontWeight: 600, margin: 0 }}>¡Contrato asignado!</p>
                <p style={{ color: 'var(--text-muted)', fontSize: 13, marginTop: 8 }}>
                  {tipo} · {centro.split(' — ')[0]} — {lastProcessedContract?.supervisor ?? '—'} ha sido notificado.
                </p>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
