import { useEffect, useState } from 'react'
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Chip, Field, Modal, SicotBadge, UserMenu, type ChipType } from './ui'
import type { AuthResponse, Rol, UsuarioResponse } from '@/services/api/types'
import { getUsuarios, crearUsuario, cambiarEstadoUsuario } from '@/services/usuarioService'
import { getContratos } from '@/services/contratoService'
import { ApiError } from '@/services/api/client'

// ─── Datos base ───────────────────────────────────────────────────────────────

type DocEstado = 'vigente' | 'sugerido' | 'conflicto'

interface DocRow {
  code: string
  name: string
  version: string
  updated: string
  estado: DocEstado
}

// Catálogo de formatos oficiales — vacío por defecto (mock: no expuesto por la API).
// Se puebla al cargar los formatos GCCON/GIL/ESUCON/GRF reales del sistema.
const DOCS_INICIAL: DocRow[] = []

interface UserRow {
  id: string
  nombre: string
  correo: string
  cargo: string
  rol: string
  activo: boolean
  centros: string
}

interface FirmaRow { id: string; usuario: string; firmaId: string; fecha: string; activa: boolean }

// Firmas electrónicas emitidas — vacío por defecto (mock: módulo no expuesto por la API)
const FIRMAS_INICIAL: FirmaRow[] = []

// Actividad de los últimos 30 días — vacío hasta tener datos reales de uso (mock)
const ACTIVIDAD: { dia: string; creados: number; supervisados: number; cerrados: number }[] = []

const ROL_LABEL: Record<Rol, string> = {
  ADMINISTRADOR: 'Administrador',
  GESTION: 'Gestor de Contratación',
  SUPERVISOR: 'Supervisor',
}

const ROL_CARGO: Record<Rol, string> = {
  ADMINISTRADOR: 'Administrador del sistema',
  GESTION: 'Profesional de Gestión',
  SUPERVISOR: 'Instructor',
}

const mapUser = (u: UsuarioResponse): UserRow => ({
  id: String(u.id),
  nombre: u.nombre,
  correo: u.email,
  cargo: ROL_CARGO[u.rol],
  rol: ROL_LABEL[u.rol],
  activo: u.activo,
  centros: '—',
})

const DOC_CHIP: Record<DocEstado, { label: string; type: ChipType }> = {
  vigente: { label: 'Vigente', type: 'vigente' },
  sugerido: { label: 'Cambios sugeridos (IA)', type: 'sugerido' },
  conflicto: { label: 'Conflicto detectado', type: 'conflicto' },
}

const randomPassword = () => {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789#$%&'
  return Array.from({ length: 12 }, () => chars[Math.floor(Math.random() * chars.length)]).join('')
}

type AdminTab = 'dashboard' | 'documentos' | 'usuarios' | 'firmas'

export default function AdminPanel({ usuario, onLogout, onOpenSettings }: { usuario: AuthResponse; onLogout: () => void; onOpenSettings: () => void }) {
  const [tab, setTab] = useState<AdminTab>('dashboard')
  const [docs, setDocs] = useState(DOCS_INICIAL)
  const [users, setUsers] = useState<UserRow[]>([])
  const [firmas, setFirmas] = useState(FIRMAS_INICIAL)

  const [uploadDoc, setUploadDoc] = useState<DocRow | null>(null)
  const [previewDoc, setPreviewDoc] = useState<DocRow | null>(null)
  const [historyDoc, setHistoryDoc] = useState<DocRow | null>(null)
  const [newUser, setNewUser] = useState(false)
  const [newFirma, setNewFirma] = useState<string | null>(null)

  const [contratosActivos, setContratosActivos] = useState(0)
  const [totalContratos, setTotalContratos] = useState(0)

  // Datos reales: usuarios y contratos (GET /api/usuarios, GET /api/contratos)
  useEffect(() => {
    let cancelado = false
    getUsuarios()
      .then(lista => { if (!cancelado) setUsers(lista.map(mapUser)) })
      .catch(() => {})
    getContratos()
      .then(lista => {
        if (cancelado) return
        setTotalContratos(lista.length)
        setContratosActivos(lista.filter(c => c.estado === 'ACTIVO').length)
      })
      .catch(() => {})
    return () => { cancelado = true }
  }, [])

  const toggleActivo = async (u: UserRow) => {
    try {
      const actualizado = await cambiarEstadoUsuario(Number(u.id), { activo: !u.activo })
      setUsers(us => us.map(x => x.id === u.id ? { ...x, activo: actualizado.activo } : x))
    } catch { /* el chip conserva el estado real si el backend rechaza */ }
  }

  const alertasSistema = docs.filter(d => d.estado !== 'vigente').length

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', background: 'var(--bg-base)', overflow: 'hidden' }}>
      {/* Barra superior */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '0 20px', height: 52, borderBottom: '1px solid var(--border)', flexShrink: 0 }}>
        <SicotBadge small />
        <span style={{ fontSize: 11, color: 'var(--text-muted)', padding: '2px 8px', border: '1px solid var(--border)', borderRadius: 4 }}>Interfaz de Administrador</span>
        <div style={{ flex: 1 }} />
        <button className="btn-ghost" onClick={onOpenSettings} style={{ padding: '5px 12px', fontSize: 12 }}>⚙ Configuración</button>
        <UserMenu label={usuario.nombre} email={usuario.email} avatarColor="var(--alert-leve)" avatarTextColor="#1a1400" onLogout={onLogout} />
      </div>

      {/* Pestañas */}
      <div style={{ display: 'flex', borderBottom: '1px solid var(--border)', padding: '0 20px', flexShrink: 0, overflowX: 'auto' }}>
        {([['dashboard', 'Panel de Control'], ['documentos', 'Gestión de Documentos'], ['usuarios', 'Gestión de Usuarios'], ['firmas', 'Firmas Electrónicas']] as const).map(([id, label]) => (
          <button key={id} className={`tab-btn ${tab === id ? 'active' : ''}`} onClick={() => setTab(id)}>{label}</button>
        ))}
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: 24 }}>

        {/* ── Panel de control ── */}
        {tab === 'dashboard' && (
          <>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: 12, marginBottom: 20 }}>
              <Widget label="Contratos activos" value={String(contratosActivos)} hint={`${totalContratos} registrados en total`} />
              <Widget label="Usuarios activos" value={String(users.filter(u => u.activo).length)} hint={`${users.length} registrados en total`} />
              <Widget label="Documentos vigentes" value={`${docs.filter(d => d.estado === 'vigente').length}/${docs.length}`} hint="Formatos oficiales cargados" />
              <Widget label="Alertas del sistema" value={String(alertasSistema)} hint="Requieren revisión documental" tone={alertasSistema ? 'warn' : 'ok'} />
            </div>

            <div className="card" style={{ padding: '16px 18px' }}>
              <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 2 }}>Actividad de los últimos 30 días</div>
              <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 16 }}>Contratos creados, supervisados y cerrados</div>
              <div style={{ height: 260 }}>
                {ACTIVIDAD.length === 0 ? (
                  <div style={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 13, color: 'var(--text-muted)', textAlign: 'center', padding: '0 20px' }}>
                    Aquí verás tus estadísticas cuando tengas contratos activos.
                  </div>
                ) : (
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={ACTIVIDAD} margin={{ top: 4, right: 8, left: -18, bottom: 0 }}>
                    <CartesianGrid stroke="var(--border)" vertical={false} />
                    <XAxis dataKey="dia" tick={{ fill: 'var(--text-muted)', fontSize: 11 }} axisLine={{ stroke: 'var(--border)' }} tickLine={false} />
                    <YAxis tick={{ fill: 'var(--text-muted)', fontSize: 11 }} axisLine={false} tickLine={false} />
                    <Tooltip
                      cursor={{ fill: 'var(--accent-soft)' }}
                      contentStyle={{ background: 'var(--bg-surface)', border: '1px solid var(--accent-line)', borderRadius: 8, fontSize: 12 }}
                      labelStyle={{ color: 'var(--text-primary)' }} />
                    <Legend wrapperStyle={{ fontSize: 12, color: 'var(--text-secondary)' }} />
                    <Bar dataKey="creados" name="Creados" fill="var(--accent)" radius={[3, 3, 0, 0]} />
                    <Bar dataKey="supervisados" name="Supervisados" fill="var(--chip-blue)" radius={[3, 3, 0, 0]} />
                    <Bar dataKey="cerrados" name="Cerrados" fill="var(--chip-purple)" radius={[3, 3, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
                )}
              </div>
            </div>
          </>
        )}

        {/* ── Documentos ── */}
        {tab === 'documentos' && (
          <>
            <SectionHead title="Gestión de Documentos"
              desc="Centro de control para actualizar los formatos y archivos oficiales del sistema." />
            <div className="card" style={{ overflow: 'hidden' }}>
              <GridRow header cols="150px 1fr 100px 140px 190px 250px">
                <span>TIPO</span><span>NOMBRE</span><span>VERSIÓN</span><span>ACTUALIZACIÓN</span><span>ESTADO</span><span>ACCIONES</span>
              </GridRow>
              {docs.map(d => (
                <GridRow key={d.code} cols="150px 1fr 100px 140px 190px 250px">
                  <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--accent)' }}>{d.code}</span>
                  <span style={{ fontSize: 12 }}>{d.name}</span>
                  <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12 }}>{d.version}</span>
                  <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{d.updated}</span>
                  <Chip text={DOC_CHIP[d.estado].label} type={DOC_CHIP[d.estado].type} />
                  <span style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                    <MiniBtn onClick={() => setPreviewDoc(d)}>Ver versión</MiniBtn>
                    <MiniBtn onClick={() => setUploadDoc(d)} accent>Cargar nueva</MiniBtn>
                    <MiniBtn onClick={() => setHistoryDoc(d)}>Historial</MiniBtn>
                  </span>
                </GridRow>
              ))}
            </div>
          </>
        )}

        {/* ── Usuarios ── */}
        {tab === 'usuarios' && (
          <>
            <SectionHead title="Gestión de Usuarios"
              desc="Crear, editar y desactivar supervisores y personal de gestión."
              action={<button className="btn-green" onClick={() => setNewUser(true)} style={{ padding: '8px 14px', fontSize: 12 }}>+ Nuevo usuario</button>} />
            <div className="card" style={{ overflow: 'hidden' }}>
              <GridRow header cols="1fr 260px 150px 190px 110px 220px">
                <span>NOMBRE</span><span>CORREO</span><span>CARGO</span><span>ROL</span><span>ESTADO</span><span>ACCIONES</span>
              </GridRow>
              {users.map(u => (
                <GridRow key={u.id} cols="1fr 260px 150px 190px 110px 220px">
                  <span style={{ fontSize: 12 }}>{u.nombre}<div style={{ fontSize: 11, color: 'var(--text-muted)' }}>Centros: {u.centros}</div></span>
                  <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{u.correo}</span>
                  <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{u.cargo}</span>
                  <span style={{ fontSize: 12 }}>{u.rol}</span>
                  <Chip text={u.activo ? 'Activo' : 'Inactivo'} type={u.activo ? 'vigente' : 'inactive'} />
                  <span style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                    <MiniBtn onClick={() => setNewFirma(u.nombre)}>Resetear clave</MiniBtn>
                    <MiniBtn onClick={() => toggleActivo(u)}>
                      {u.activo ? 'Desactivar' : 'Reactivar'}
                    </MiniBtn>
                  </span>
                </GridRow>
              ))}
            </div>
          </>
        )}

        {/* ── Firmas ── */}
        {tab === 'firmas' && (
          <>
            <SectionHead title="Firmas Electrónicas"
              desc="Crear, asignar y gestionar las firmas digitales del sistema. Los datos se almacenan cifrados."
              action={<button className="btn-green" onClick={() => setNewFirma('')} style={{ padding: '8px 14px', fontSize: 12 }}>+ Generar firma</button>} />
            <div className="card" style={{ overflow: 'hidden' }}>
              <GridRow header cols="1fr 190px 190px 120px 200px">
                <span>USUARIO</span><span>FIRMA ID</span><span>FECHA CREACIÓN</span><span>ESTADO</span><span>ACCIONES</span>
              </GridRow>
              {firmas.map(f => (
                <GridRow key={f.id} cols="1fr 190px 190px 120px 200px">
                  <span style={{ fontSize: 12 }}>{f.usuario}</span>
                  <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--accent)' }}>{f.firmaId}</span>
                  <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{f.fecha}</span>
                  <Chip text={f.activa ? 'Vigente' : 'Revocada'} type={f.activa ? 'vigente' : 'inactive'} />
                  <span style={{ display: 'flex', gap: 6 }}>
                    <MiniBtn onClick={() => undefined}>Descargar .p7s</MiniBtn>
                    <MiniBtn onClick={() => setFirmas(fs => fs.map(x => x.id === f.id ? { ...x, activa: !x.activa } : x))}>
                      {f.activa ? 'Revocar' : 'Restaurar'}
                    </MiniBtn>
                  </span>
                </GridRow>
              ))}
            </div>
          </>
        )}
      </div>

      {previewDoc && (
        <Modal title={`${previewDoc.code} — versión actual`} onClose={() => setPreviewDoc(null)}>
          <div style={{ border: '1px solid var(--border)', borderRadius: 10, background: 'var(--bg-input)', padding: 22, fontFamily: 'var(--font-mono)', fontSize: 12, lineHeight: 1.9, color: 'var(--text-secondary)' }}>
            <div style={{ color: 'var(--accent)', fontWeight: 500 }}>SERVICIO NACIONAL DE APRENDIZAJE — SENA</div>
            <div>{previewDoc.name}</div>
            <div>Código: {previewDoc.code} · Versión: {previewDoc.version}</div>
            <div>Última actualización: {previewDoc.updated}</div>
            <div style={{ marginTop: 14, color: 'var(--text-muted)' }}>
              [Vista previa del formato oficial. En el prototipo se muestra la cabecera normalizada del documento.]
            </div>
          </div>
        </Modal>
      )}

      {historyDoc && (
        <Modal title={`Historial de versiones — ${historyDoc.code}`} onClose={() => setHistoryDoc(null)}>
          {[
            { v: historyDoc.version, f: historyDoc.updated, nota: 'Versión vigente en el sistema' },
            { v: 'v' + (Number(historyDoc.version.slice(1)) - 1), f: '11/09/2024', nota: 'Ajuste de campos de identificación del proveedor' },
            { v: 'v' + (Number(historyDoc.version.slice(1)) - 2), f: '02/02/2024', nota: 'Actualización del encabezado institucional' },
          ].map(h => (
            <div key={h.v} style={{ display: 'flex', gap: 12, padding: '10px 0', borderBottom: '1px solid var(--border)' }}>
              <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--accent)', fontSize: 12, width: 40 }}>{h.v}</span>
              <span style={{ fontSize: 12, color: 'var(--text-secondary)', width: 96 }}>{h.f}</span>
              <span style={{ fontSize: 12, flex: 1 }}>{h.nota}</span>
            </div>
          ))}
        </Modal>
      )}

      {uploadDoc && (
        <UploadDocModal doc={uploadDoc} onClose={() => setUploadDoc(null)}
          onConfirm={estado => {
            setDocs(ds => ds.map(d => d.code === uploadDoc.code
              ? { ...d, estado, version: 'v' + (Number(d.version.slice(1)) + 1), updated: new Date().toLocaleDateString('es-CO') }
              : d))
            setUploadDoc(null)
          }} />
      )}

      {newUser && (
        <NewUserModal onClose={() => setNewUser(false)}
          onCreate={u => { setUsers(us => [...us, u]); setNewUser(false) }} />
      )}

      {newFirma !== null && (
        <NewFirmaModal nombre={newFirma} onClose={() => setNewFirma(null)}
          onCreate={f => { setFirmas(fs => [...fs, f]); setNewFirma(null) }} />
      )}
    </div>
  )
}

// ─── Piezas ───────────────────────────────────────────────────────────────────

function Widget({ label, value, hint, tone = 'ok' }: { label: string; value: string; hint: string; tone?: 'ok' | 'warn' }) {
  return (
    <div className="card" style={{ padding: '14px 16px', borderColor: tone === 'warn' ? 'rgba(255,215,0,0.3)' : 'var(--border)' }}>
      <div style={{ fontSize: 11, letterSpacing: '0.06em', color: 'var(--text-muted)', textTransform: 'uppercase' }}>{label}</div>
      <div style={{ fontSize: 30, fontWeight: 700, color: tone === 'warn' ? 'var(--alert-leve)' : 'var(--accent)', lineHeight: 1.25, fontFamily: 'var(--font-mono)' }}>{value}</div>
      <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{hint}</div>
    </div>
  )
}

function SectionHead({ title, desc, action }: { title: string; desc: string; action?: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16, marginBottom: 16, flexWrap: 'wrap' }}>
      <div>
        <h3 style={{ margin: '0 0 4px', fontSize: 15, fontWeight: 700 }}>{title}</h3>
        <p style={{ margin: 0, fontSize: 13, color: 'var(--text-muted)', maxWidth: 620 }}>{desc}</p>
      </div>
      {action}
    </div>
  )
}

function GridRow({ cols, header, children }: { cols: string; header?: boolean; children: React.ReactNode }) {
  return (
    <div className="data-grid" style={{
      display: 'grid', gridTemplateColumns: cols, gap: 12, alignItems: 'center',
      padding: header ? '10px 16px' : '12px 16px',
      borderBottom: '1px solid var(--border)',
      fontSize: header ? 11 : 13,
      fontWeight: header ? 600 : 400,
      letterSpacing: header ? '0.06em' : undefined,
      color: header ? 'var(--text-muted)' : 'var(--text-primary)',
    }}>{children}</div>
  )
}

function MiniBtn({ children, onClick, accent }: { children: React.ReactNode; onClick: () => void; accent?: boolean }) {
  return (
    <button onClick={onClick} style={{
      background: accent ? 'var(--accent-soft)' : 'transparent',
      border: `1px solid ${accent ? 'var(--accent-line)' : 'var(--border)'}`,
      color: accent ? 'var(--accent)' : 'var(--text-secondary)',
      borderRadius: 6, padding: '4px 9px', fontSize: 11, cursor: 'pointer',
      fontFamily: 'var(--font-ui)', whiteSpace: 'nowrap',
    }}>{children}</button>
  )
}

// ─── Modal: cargar nueva versión con análisis IA ──────────────────────────────

function UploadDocModal({ doc, onClose, onConfirm }: { doc: DocRow; onClose: () => void; onConfirm: (estado: DocEstado) => void }) {
  const [phase, setPhase] = useState<'select' | 'analyzing' | 'result'>('select')

  const start = () => {
    setPhase('analyzing')
    setTimeout(() => setPhase('result'), 1800)
  }

  return (
    <Modal title={`Cargar nueva versión — ${doc.code}`} onClose={onClose} width={560}>
      {phase === 'select' && (
        <div onClick={start}
          style={{ border: '2px dashed var(--border)', borderRadius: 10, padding: '38px 20px', textAlign: 'center', cursor: 'pointer' }}>
          <div style={{ fontSize: 32, marginBottom: 10 }}>📄</div>
          <p style={{ margin: 0, fontSize: 14, color: 'var(--text-secondary)' }}>
            Haz clic para seleccionar el archivo<br />
            <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>PDF o DOCX — máx 20 MB</span>
          </p>
        </div>
      )}

      {phase === 'analyzing' && (
        <div style={{ textAlign: 'center', padding: '34px 0' }}>
          <div style={{ fontSize: 32, marginBottom: 14, display: 'inline-block', animation: 'spin 1s linear infinite' }}>🔄</div>
          <p style={{ color: 'var(--accent)', fontSize: 14, fontWeight: 600, margin: 0 }}>Analizando cambios...</p>
          <p style={{ color: 'var(--text-muted)', fontSize: 12 }}>Comparando la estructura contra la versión {doc.version}</p>
        </div>
      )}

      {phase === 'result' && (
        <>
          <div className="card" style={{ padding: 0, overflow: 'hidden', borderColor: 'var(--accent-line)' }}>
            <div style={{ padding: '10px 14px', borderBottom: '1px solid var(--border)', fontSize: 11, fontWeight: 700, letterSpacing: '0.1em', color: 'var(--accent)' }}>
              🤖 ANÁLISIS IA — CAMBIOS DETECTADOS
            </div>
            {[
              { icon: '✅', color: 'var(--accent)', title: 'Campo "PROVEEDOR" — formato correcto', body: 'Coincide con la estructura del expediente digital.' },
              { icon: '⚠️', color: 'var(--alert-leve)', title: 'Campo "FECHA" — recomendación', body: 'Usar formato DD/MM/AAAA para mantener consistencia con el expediente.' },
              { icon: '🔴', color: 'var(--alert-critica)', title: 'Conflicto detectado', body: `El campo "VALOR EJECUTADO" no existe en GCCON-F-031 y puede romper la consistencia entre formatos. Recomendación: revisar con el área jurídica antes de publicar.` },
            ].map(r => (
              <div key={r.title} style={{ display: 'flex', gap: 10, padding: '12px 14px', borderBottom: '1px solid var(--border)' }}>
                <span style={{ fontSize: 14 }}>{r.icon}</span>
                <div>
                  <div style={{ fontSize: 13, fontWeight: 600, color: r.color }}>{r.title}</div>
                  <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 2, lineHeight: 1.5 }}>{r.body}</div>
                </div>
              </div>
            ))}
          </div>
          <div style={{ display: 'flex', gap: 10, marginTop: 18 }}>
            <button className="btn-ghost" style={{ flex: 1, padding: '10px 0', fontSize: 13 }} onClick={() => onConfirm('conflicto')}>Revisar cambios</button>
            <button className="btn-green" style={{ flex: 1, padding: '10px 0', fontSize: 13 }} onClick={() => onConfirm('sugerido')}>Cargar igualmente</button>
          </div>
        </>
      )}
    </Modal>
  )
}

// ─── Modal: crear usuario ─────────────────────────────────────────────────────

function NewUserModal({ onClose, onCreate }: { onClose: () => void; onCreate: (u: UserRow) => void }) {
  const [nombre, setNombre] = useState('')
  const [correo, setCorreo] = useState('')
  const [tel, setTel] = useState('')
  const [rol, setRol] = useState('Supervisor')
  const [centros, setCentros] = useState<string[]>(['920510'])
  const [entrega, setEntrega] = useState<'correo' | 'whatsapp' | 'qr'>('correo')
  const [pw] = useState(randomPassword())
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)

  const ROL_MAP: Record<string, Rol> = { Supervisor: 'SUPERVISOR', 'Gestor de Contratación': 'GESTION', Administrador: 'ADMINISTRADOR' }

  const crear = async () => {
    if (busy) return
    if (!nombre.trim()) { setError('El nombre completo es obligatorio.'); return }
    if (!correo.endsWith('@soy.sena.edu.co')) { setError('El correo debe pertenecer al dominio @soy.sena.edu.co'); return }
    setError('')
    setBusy(true)
    try {
      const creado = await crearUsuario({ nombre: nombre.trim(), email: correo.trim(), password: pw, rol: ROL_MAP[rol] })
      setDone(true)
      setTimeout(() => onCreate({
        id: String(creado.id),
        nombre: creado.nombre,
        correo: creado.email,
        cargo: ROL_CARGO[creado.rol],
        rol: ROL_LABEL[creado.rol],
        activo: creado.activo,
        centros: centros.join(', ') || '—',
      }), 1600)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se pudo crear el usuario.')
      setBusy(false)
    }
  }

  const CENTROS = ['920510', '920511', '920512', '920520']

  if (done) {
    return (
      <Modal title="Usuario creado" onClose={onClose} width={460}>
        <div style={{ textAlign: 'center', padding: '10px 0 4px' }}>
          <div style={{ fontSize: 42 }}>✅</div>
          <p style={{ fontSize: 14, color: 'var(--accent)', fontWeight: 600, margin: '8px 0 4px' }}>Usuario creado exitosamente</p>
          <p style={{ fontSize: 13, color: 'var(--text-secondary)', margin: 0 }}>
            Notificación enviada a {correo}
            {entrega === 'whatsapp' && tel ? ` y por WhatsApp/SMS a ${tel}` : ''}
            {entrega === 'qr' ? ' — código QR generado para descarga de credenciales' : ''}.
          </p>
        </div>
      </Modal>
    )
  }

  return (
    <Modal title="Crear nuevo supervisor / gestor" onClose={onClose} width={520}>
      <Field label="Nombre completo (requerido)">
        <input type="text" value={nombre} onChange={e => setNombre(e.target.value)} placeholder="Nombres y apellidos" style={{ width: '100%', padding: '9px 10px' }} />
      </Field>
      <Field label="Correo institucional (@soy.sena.edu.co)">
        <input type="email" value={correo} onChange={e => setCorreo(e.target.value)} placeholder="usuario@soy.sena.edu.co" style={{ width: '100%', padding: '9px 10px' }} />
      </Field>
      <Field label="Número de teléfono (opcional)">
        <input type="text" value={tel} onChange={e => setTel(e.target.value)} placeholder="300 000 0000" style={{ width: '100%', padding: '9px 10px' }} />
      </Field>
      <Field label="Cargo">
        <select value={rol} onChange={e => setRol(e.target.value)}>
          <option>Supervisor</option><option>Gestor de Contratación</option><option>Administrador</option>
        </select>
      </Field>
      <Field label="Centros de costo asignados">
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', fontSize: 13, color: 'var(--text-secondary)' }}>
          {CENTROS.map(c => (
            <label key={c} style={{ display: 'flex', gap: 6, alignItems: 'center', cursor: 'pointer' }}>
              <input type="checkbox" checked={centros.includes(c)}
                onChange={() => setCentros(v => v.includes(c) ? v.filter(x => x !== c) : [...v, c])} />
              {c}
            </label>
          ))}
        </div>
      </Field>

      <div className="card" style={{ padding: '12px 14px', margin: '4px 0 12px', borderColor: 'var(--accent-line)' }}>
        <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: '0.08em', color: 'var(--accent)', marginBottom: 6 }}>CONTRASEÑA TEMPORAL (se muestra una sola vez)</div>
        <div style={{ fontFamily: 'var(--font-mono)', fontSize: 15, letterSpacing: '0.06em' }}>{pw}</div>
        <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>Generada automáticamente · 12 caracteres · almacenada (cifrada)</div>
      </div>

      <Field label="Método de entrega de credenciales">
        <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap', fontSize: 13, color: 'var(--text-secondary)' }}>
          {([['correo', 'Correo institucional'], ['whatsapp', 'WhatsApp / SMS'], ['qr', 'Mostrar QR']] as const).map(([v, l]) => (
            <label key={v} style={{ display: 'flex', gap: 6, alignItems: 'center', cursor: 'pointer' }}>
              <input type="radio" name="entrega" checked={entrega === v} onChange={() => setEntrega(v)} />{l}
            </label>
          ))}
        </div>
      </Field>

      {error && <p style={{ color: 'var(--alert-critica)', fontSize: 12, margin: '0 0 10px' }}>{error}</p>}

      <div style={{ display: 'flex', gap: 10 }}>
        <button className="btn-ghost" style={{ flex: 1, padding: '10px 0', fontSize: 13 }} onClick={onClose}>Cancelar</button>
        <button className="btn-green" style={{ flex: 2, padding: '10px 0', fontSize: 13, opacity: busy ? 0.6 : 1, cursor: busy ? 'default' : 'pointer' }} onClick={crear} disabled={busy}>
          {busy ? 'Creando usuario…' : 'Crear usuario'}
        </button>
      </div>
    </Modal>
  )
}

// ─── Modal: generar firma electrónica ─────────────────────────────────────────

function NewFirmaModal({ nombre, onClose, onCreate }: { nombre: string; onClose: () => void; onCreate: (f: FirmaRow) => void }) {
  const [n, setN] = useState(nombre)
  const [correo, setCorreo] = useState(nombre ? '' : '')
  const [tel, setTel] = useState('')
  const [cargo, setCargo] = useState('Supervisor')
  const [doc, setDoc] = useState('')
  const [phase, setPhase] = useState<'form' | 'gen' | 'done'>('form')
  const [firmaId] = useState('FIRMA-' + Math.random().toString(16).slice(2, 8).toUpperCase())

  const generar = () => {
    setPhase('gen')
    setTimeout(() => setPhase('done'), 1500)
  }

  return (
    <Modal title="Generar firma electrónica" onClose={onClose} width={520}>
      {phase === 'form' && (
        <>
          <Field label="Nombre completo">
            <input type="text" value={n} onChange={e => setN(e.target.value)} style={{ width: '100%', padding: '9px 10px' }} />
          </Field>
          <Field label="Correo institucional">
            <input type="email" value={correo} onChange={e => setCorreo(e.target.value)} placeholder="usuario@soy.sena.edu.co" style={{ width: '100%', padding: '9px 10px' }} />
          </Field>
          <Field label="Número de teléfono (opcional)">
            <input type="text" value={tel} onChange={e => setTel(e.target.value)} style={{ width: '100%', padding: '9px 10px' }} />
          </Field>
          <Field label="Cargo">
            <select value={cargo} onChange={e => setCargo(e.target.value)}>
              <option>Supervisor</option><option>Gestor de Contratación</option><option>Administrador</option><option>Otro</option>
            </select>
          </Field>
          <Field label="Datos adicionales — Cédula / NIT (opcional, cifrado)">
            <input type="text" value={doc} onChange={e => setDoc(e.target.value)} style={{ width: '100%', padding: '9px 10px' }} />
          </Field>
          <button className="btn-green" style={{ width: '100%', padding: '10px 0', fontSize: 13 }} onClick={generar}>Generar firma</button>
        </>
      )}

      {phase === 'gen' && (
        <div style={{ textAlign: 'center', padding: '34px 0' }}>
          <div style={{ fontSize: 32, marginBottom: 14, display: 'inline-block', animation: 'spin 1s linear infinite' }}>🔐</div>
          <p style={{ color: 'var(--accent)', fontSize: 14, fontWeight: 600, margin: 0 }}>Generando firma única...</p>
          <p style={{ color: 'var(--text-muted)', fontSize: 12 }}>Cifrando datos con el certificado institucional</p>
        </div>
      )}

      {phase === 'done' && (
        <div style={{ textAlign: 'center' }}>
          <div style={{ fontSize: 40 }}>✍</div>
          <p style={{ fontSize: 14, fontWeight: 600, color: 'var(--accent)', margin: '8px 0 2px' }}>Firma electrónica generada: {firmaId}</p>
          <p style={{ fontSize: 13, color: 'var(--text-secondary)', margin: 0 }}>
            Archivo <span style={{ fontFamily: 'var(--font-mono)' }}>{firmaId}.p7s</span> listo para entrega.
          </p>
          <div style={{ display: 'flex', gap: 10, marginTop: 18 }}>
            <button className="btn-ghost" style={{ flex: 1, padding: '10px 0', fontSize: 13 }}
              onClick={() => onCreate({ id: 'f' + Date.now(), usuario: n || 'Sin nombre', firmaId, fecha: new Date().toLocaleString('es-CO'), activa: true })}>
              ⭳ Descargar firma
            </button>
            <button className="btn-green" style={{ flex: 1, padding: '10px 0', fontSize: 13 }}
              onClick={() => onCreate({ id: 'f' + Date.now(), usuario: n || 'Sin nombre', firmaId, fecha: new Date().toLocaleString('es-CO'), activa: true })}>
              Enviar al usuario
            </button>
          </div>
          <p style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 10 }}>
            Se enviará a {correo || 'el correo institucional registrado'}{tel ? ` y por SMS a ${tel}` : ''}.
          </p>
        </div>
      )}
    </Modal>
  )
}
