// Panel de Gestión y Contratación — carga de fichas de contrato, tabla de
// contratos y flujo de asignación al supervisor.
// Extraído 1:1 desde el App.tsx original de Figma Make — sin cambios visuales.

import { useEffect, useRef, useState } from 'react'
import { Chip, Modal, TopBar, type ChipType } from '@/components/ui'
import { IconCheckCircle, IconClipboardList, IconFileText, IconLoader, IconPlay, IconUpload } from '@/components/icons'
import type { UploadState } from '@/types/domain'
import type { AuthResponse, ContratoResponse, EstadoContrato, ExtraccionContratoResponse } from '@/services/api/types'
import { getContratos, crearContrato } from '@/services/contratoService'
import { getUsuarios } from '@/services/usuarioService'
import { extraerDatosContrato, subirDocumento } from '@/services/documentoService'
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

// Acepta tanto un número limpio como texto descriptivo con el valor embebido
// (ej. lo que a veces devuelve la extracción con IA: "DIEZ MILLONES DE PESOS
// ($10.000.000 COP)") — se queda solo con los dígitos.
const parseValor = (texto: string): number | null => {
  if (!texto) return null
  const soloDigitos = texto.replace(/[^\d]/g, '')
  if (!soloDigitos) return null
  const n = Number(soloDigitos)
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

// AAAA-MM-DD (lo que devuelve la IA) -> DD/MM/AAAA (lo que espera el campo de vigencia)
const isoADisplay = (iso: string | null | undefined): string => {
  if (!iso || !/^\d{4}-\d{2}-\d{2}$/.test(iso)) return ''
  const [y, m, d] = iso.split('-')
  return `${d}/${m}/${y}`
}

export default function GestionPanel({ usuario, onLogout, onOpenSettings, onStartTour }: {
  usuario: AuthResponse
  onLogout: () => void
  onOpenSettings: () => void
  onStartTour: () => void
}) {
  const [uploadState, setUploadState] = useState<UploadState>('idle')
  const [showModal, setShowModal] = useState(false)
  const [lastProcessedContract, setLastProcessedContract] = useState<{ id: string; supervisor: string } | null>(null)
  const [progress, setProgress] = useState(0)
  const [extraccion, setExtraccion] = useState<ExtraccionContratoResponse | null>(null)
  const [errorExtraccion, setErrorExtraccion] = useState('')
  const [archivosSeleccionados, setArchivosSeleccionados] = useState<File[]>([])
  const fileInputRef = useRef<HTMLInputElement>(null)
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
      .catch(err => console.error('No se pudieron cargar los contratos:', err))
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
      .catch(err => console.error('No se pudieron cargar los supervisores:', err))
    return () => { cancelado = true }
  }, [])

  const openUpload = () => {
    setShowModal(true); setUploadState('idle'); setProgress(0); setRevisionIA(false)
    setExtraccion(null); setErrorExtraccion(''); setArchivosSeleccionados([])
    setIdContrato(''); setObjeto(''); setProveedor(''); setValor(''); setVigencia('')
    setNit(''); setRepresentanteLegal(''); setLugarEjecucion(''); setRegistroPresupuestal('')
  }

  const handleFileSelect = () => fileInputRef.current?.click()

  const handleFilesChosen = async (archivos: File[]) => {
    if (archivos.length === 0) return
    setArchivosSeleccionados(archivos)
    setErrorExtraccion('')
    setUploadState('uploading')
    setProgress(100)
    setUploadState('analyzing')
    try {
      const resultado = await extraerDatosContrato(archivos)
      setExtraccion(resultado)
      setIdContrato(resultado.idContrato ?? '')
      setObjeto(resultado.objeto ?? '')
      setProveedor(resultado.proveedor ?? '')
      setNit(resultado.nit ?? '')
      setRepresentanteLegal(resultado.representanteLegal ?? '')
      setValor(resultado.valor ?? '')
      const inicio = isoADisplay(resultado.vigenciaInicio)
      const fin = isoADisplay(resultado.vigenciaFin)
      setVigencia(inicio && fin ? `${inicio} – ${fin}` : '')
      setLugarEjecucion(resultado.lugarEjecucion ?? '')
      setRegistroPresupuestal(resultado.registroPresupuestal ?? '')
      if (resultado.tipoContrato && Object.keys(CONTRACT_TYPES).includes(resultado.tipoContrato)) {
        setTipo(resultado.tipoContrato)
      }
      setUploadState('detect')
    } catch (e) {
      setErrorExtraccion(e instanceof ApiError ? e.message : 'No se pudo analizar el documento con el Copiloto IA.')
      setUploadState('review')
    }
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
      if (archivosSeleccionados.length > 0) {
        await Promise.all(archivosSeleccionados.map(archivo =>
          subirDocumento(creado.id, archivo).catch(() => { /* la carga real es secundaria; el contrato ya quedó creado */ })
        ))
      }
      setUploadState('done')
      const sup = supervisores.find(s => String(s.id) === supervisor)
      setLastProcessedContract({ id: creado.numeroContrato, supervisor: sup?.nombre ?? '— Sin asignar —' })
      const lista = await getContratos()
      setContratos(lista.map(mapContratoRow))
      setTimeout(() => {
        setShowModal(false)
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
      <TopBar badge="Panel Gestión" usuario={usuario} onOpenSettings={onOpenSettings} onLogout={onLogout}
        avatarColor="#7c3aed" avatarTextColor="white"
        actions={<button className="btn-ghost" onClick={onStartTour} style={{ padding: '6px 13px', fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 6 }}><IconPlay size={10} /> Tutorial</button>} />

      <div style={{ flex: 1, overflowY: 'auto', padding: '20px 24px' }}>
        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
          <div>
            <div className="eyebrow">Panel de Gestión y Contratación</div>
            <h2 style={{ fontSize: 20 }}>Contratos</h2>
          </div>
          <button data-tour="cargar" className="btn-green" onClick={openUpload} style={{ padding: '9px 18px', fontSize: 13 }}>
            + Cargar nueva ficha
          </button>
        </div>

        {/* Ficha procesada card — solo se muestra tras cargar y procesar una ficha real */}
        {lastProcessedContract && (
          <div data-tour="ficha" className="card" style={{ padding: '14px 16px', marginBottom: 20, borderLeft: '4px solid var(--accent)', display: 'flex', alignItems: 'center', gap: 16 }}>
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
          <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--border)', fontSize: 12, fontWeight: 600, color: 'var(--text-muted)', letterSpacing: '0.06em', background: 'var(--bg-surface)' }}>
            REGISTRO DE CONTRATOS
          </div>
          {/* Header row */}
          <div style={{ display: 'grid', gridTemplateColumns: '180px 1fr 180px 160px 120px 180px', padding: '8px 16px', borderBottom: '1px solid var(--border)', fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', letterSpacing: '0.04em', gap: 12, background: 'var(--bg-surface)' }}>
            <span>CONTRATO</span><span>OBJETO</span><span>SUPERVISOR</span><span>ESTADO</span><span>VALOR</span><span>VIGENCIA</span>
          </div>
          {/* Empty state */}
          {contratos.length === 0 && (
            <div style={{ padding: '40px 16px', textAlign: 'center' }}>
              <IconClipboardList size={26} style={{ opacity: 0.5, margin: '0 auto 8px' }} />
              <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-primary)', marginBottom: 4 }}>Aún no tiene contratos registrados</div>
              <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>Cargue una ficha de contrato para que el Copiloto la procese automáticamente.</div>
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
        <Modal title="Cargar nueva ficha de contrato" onClose={() => setShowModal(false)} width={480} hideClose={uploadState === 'done'}>
            {uploadState === 'idle' && (
              <div>
                <input ref={fileInputRef} type="file" accept=".pdf" multiple style={{ display: 'none' }}
                  onChange={e => { const f = Array.from(e.target.files ?? []); e.target.value = ''; handleFilesChosen(f) }} />
                <div onClick={handleFileSelect}
                  style={{ border: '2px dashed var(--border)', borderRadius: 10, padding: '40px 20px', textAlign: 'center', cursor: 'pointer', transition: 'border-color 0.15s' }}
                  onMouseEnter={e => (e.currentTarget.style.borderColor = 'var(--accent-dim)')}
                  onMouseLeave={e => (e.currentTarget.style.borderColor = 'var(--border)')}>
                  <IconFileText size={34} style={{ color: 'var(--text-muted)', margin: '0 auto 12px' }} />
                  <p style={{ color: 'var(--text-secondary)', fontSize: 14, margin: 0 }}>
                    Haga clic para seleccionar uno o varios documentos<br />
                    <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>PDF — max 20 MB cada uno, hasta 6 archivos</span>
                  </p>
                </div>
                <p style={{ color: 'var(--text-muted)', fontSize: 11, marginTop: 12, textAlign: 'center' }}>
                  El Copiloto IA (Ollama local) lee los PDF que cargue y combina lo que encuentre en todos
                  para proponer los datos del contrato — usted los revisa, corrige y confirma antes de crear
                  el contrato. Los archivos quedan adjuntos al contrato de todas formas, se analicen o no.
                </p>
                <p style={{ color: 'var(--accent)', fontSize: 11, marginTop: 8, textAlign: 'center', fontWeight: 600 }}>
                  Recomendado: cargue solo el Acta de Inicio y/o la notificación del supervisor — son los
                  que realmente traen datos del contrato. El Manual de Supervisión y los formatos en blanco
                  (GCCON-F-031, etc.) no aportan datos y son documentos largos que pueden tardar 10+ minutos
                  cada uno en esta máquina sin encontrar nada útil.
                </p>
                <p style={{ color: 'var(--text-muted)', fontSize: 11, marginTop: 8, textAlign: 'center' }}>
                  El análisis toma 1 a 4 minutos por documento en esta máquina (se procesan uno por uno). La
                  lectura de DOCX estará disponible en una fase posterior; por ahora cargue en PDF.
                </p>
              </div>
            )}

            {uploadState === 'uploading' && (
              <div style={{ textAlign: 'center', padding: '20px 0' }}>
                <IconUpload size={32} style={{ color: 'var(--accent)', margin: '0 auto 16px' }} />
                <p style={{ fontSize: 14, marginBottom: 12 }}>
                  Subiendo {archivosSeleccionados.length} documento{archivosSeleccionados.length === 1 ? '' : 's'}...
                </p>
                <div style={{ height: 4, background: 'var(--border)', borderRadius: 2, overflow: 'hidden' }}>
                  <div style={{ height: '100%', width: `${progress}%`, background: 'var(--accent)', borderRadius: 2, transition: 'width 0.12s' }} />
                </div>
                <p style={{ color: 'var(--text-muted)', fontSize: 12, marginTop: 8 }}>{progress}%</p>
              </div>
            )}

            {uploadState === 'analyzing' && (
              <div style={{ textAlign: 'center', padding: '32px 0' }}>
                <IconLoader size={30} style={{ color: 'var(--accent)', margin: '0 auto 16px' }} />
                <p style={{ color: 'var(--accent)', fontSize: 14, fontWeight: 600 }}>
                  Analizando {archivosSeleccionados.length} documento{archivosSeleccionados.length === 1 ? '' : 's'}...
                </p>
                <p style={{ color: 'var(--text-muted)', fontSize: 12 }}>
                  El Copiloto IA está extrayendo los datos del contrato — esto corre en un modelo local (sin
                  costo por consumo) y puede tardar varios minutos en esta máquina. No cierre esta ventana.
                </p>
                <div style={{ marginTop: 10, fontSize: 11, color: 'var(--text-muted)' }}>
                  {archivosSeleccionados.map(f => <div key={f.name}>{f.name}</div>)}
                </div>
              </div>
            )}

            {uploadState === 'detect' && (
              <div>
                <div className="card" style={{ padding: 0, overflow: 'hidden', borderColor: 'var(--accent-line)' }}>
                  <div style={{ padding: '10px 14px', borderBottom: '1px solid var(--border)', fontSize: 11, fontWeight: 700, letterSpacing: '0.1em', color: 'var(--accent)' }}>
                    ◆ ANÁLISIS DEL COPILOTO IA — DATOS PROPUESTOS
                  </div>
                  <div style={{ padding: '14px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
                      <IconCheckCircle size={16} style={{ color: 'var(--accent)' }} />
                      <strong style={{ fontSize: 14 }}>{tipo}</strong>
                    </div>
                    <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.1em', color: 'var(--text-muted)', marginBottom: 6 }}>CARACTERÍSTICAS DETECTADAS</div>
                    {[
                      ['Contrato', extraccion?.idContrato],
                      ['Proveedor', extraccion?.proveedor],
                      ['NIT', extraccion?.nit],
                      ['Monto', extraccion?.valor],
                      ['Lugar de ejecución', extraccion?.lugarEjecucion],
                    ].map(([k, v]) => (
                      <div key={k} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12.5, padding: '3px 0', color: 'var(--text-secondary)' }}>
                        <span>{k}</span>
                        <span style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-mono)', fontSize: 12 }}>{v || '— no detectado —'}</span>
                      </div>
                    ))}
                    <p style={{ fontSize: 11.5, color: 'var(--text-muted)', margin: '10px 0 0', lineHeight: 1.5 }}>
                      Estos datos vienen del documento cargado, no de un catálogo — revise y corrija lo que
                      haga falta en el siguiente paso.
                    </p>
                    <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.1em', color: 'var(--text-muted)', margin: '14px 0 6px' }}>SECUENCIA RECOMENDADA</div>
                    {CONTRACT_TYPES[tipo].etapas.map((e, i) => (
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
                {errorExtraccion ? (
                  <div style={{ marginBottom: 16, padding: '10px 14px', border: '1px solid var(--chip-red)', background: 'var(--chip-red-bg)', borderRadius: 8, fontSize: 13, color: 'var(--text-primary)' }}>
                    El Copiloto IA no pudo analizar los documentos: {errorExtraccion} Diligencie los datos manualmente.
                  </div>
                ) : (
                  <div style={{ marginBottom: 16, padding: '10px 14px', background: 'var(--accent-soft)', border: '1px solid var(--accent-line)', borderRadius: 8, fontSize: 13, color: 'var(--accent)' }}>
                    ✓ Documento analizado. Revise, corrija y confirme los datos extraídos.
                  </div>
                )}
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
                    ◆ Documentos clave para <strong>{tipo}</strong>: {CONTRACT_TYPES[tipo].documentos.join(', ')}.
                    {' '}La detección automática de inconsistencias entre la propuesta y la ficha técnica está
                    disponible en una fase posterior del Copiloto IA.
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
                <p style={{ color: 'var(--accent)', fontSize: 15, fontWeight: 600, margin: 0 }}>Contrato asignado.</p>
                <p style={{ color: 'var(--text-muted)', fontSize: 13, marginTop: 8 }}>
                  {tipo} · {centro.split(' — ')[0]} — {lastProcessedContract?.supervisor ?? '—'} ha sido notificado.
                </p>
                {archivosSeleccionados.length > 0 && (
                  <p style={{ color: 'var(--text-muted)', fontSize: 12, marginTop: 6 }}>
                    {archivosSeleccionados.length} documento{archivosSeleccionados.length === 1 ? '' : 's'} adjunto{archivosSeleccionados.length === 1 ? '' : 's'} al contrato.
                  </p>
                )}
              </div>
            )}
        </Modal>
      )}
    </div>
  )
}
