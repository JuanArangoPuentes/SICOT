import { useEffect, useState } from 'react'
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Chip, Field, Modal, TopBar, type ChipType } from './ui'
import { IconAlertTriangle, IconCheckCircle, IconClipboardList, IconDownload, IconFileText, IconLock, IconSignature, IconTrash, IconUpload, IconUsers } from './icons'
import type { AuthResponse, EstadoFormato, FirmaResponse, FormatoDocumentalResponse, Rol, UsuarioResponse } from '@/services/api/types'
import { getUsuarios, crearUsuario, cambiarEstadoUsuario, enviarCredenciales } from '@/services/usuarioService'
import { getContratos } from '@/services/contratoService'
import { getFormatos, subirFormato, eliminarFormato, descargarFormato } from '@/services/formatoService'
import { getFirmas, crearFirma, cambiarEstadoFirma } from '@/services/firmaService'
import { ApiError } from '@/services/api/client'
import { formatBytes, formatFecha } from '@/services/format'

// ─── Datos base ───────────────────────────────────────────────────────────────

interface UserRow {
  id: string
  nombre: string
  correo: string
  telefono: string
  cargo: string
  rol: string
  activo: boolean
  centros: string
}

interface FirmaRow { id: string; usuarioId: string; usuario: string; correo: string; firmaId: string; fecha: string; activa: boolean }

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
  telefono: u.telefono ?? '—',
  cargo: ROL_CARGO[u.rol],
  rol: ROL_LABEL[u.rol],
  activo: u.activo,
  centros: '—',
})

const mapFirma = (f: FirmaResponse): FirmaRow => ({
  id: String(f.id),
  usuarioId: String(f.usuarioId),
  usuario: f.usuarioNombre,
  correo: f.usuarioEmail,
  firmaId: f.firmaId,
  fecha: formatFecha(f.fechaAsignacion),
  activa: f.activa,
})

const FORMATO_CHIP: Record<EstadoFormato, { label: string; type: ChipType }> = {
  VIGENTE: { label: 'Vigente', type: 'vigente' },
  OBSOLETO: { label: 'Obsoleto', type: 'inactive' },
}

const randomPassword = () => {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789#$%&'
  return Array.from({ length: 12 }, () => chars[Math.floor(Math.random() * chars.length)]).join('')
}

type AdminTab = 'dashboard' | 'documentos' | 'usuarios' | 'firmas'

export default function AdminPanel({ usuario, onLogout, onOpenSettings }: { usuario: AuthResponse; onLogout: () => void; onOpenSettings: () => void }) {
  const [tab, setTab] = useState<AdminTab>('dashboard')
  const [formatos, setFormatos] = useState<FormatoDocumentalResponse[]>([])
  const [users, setUsers] = useState<UserRow[]>([])
  const [firmas, setFirmas] = useState<FirmaRow[]>([])

  const [formatoModalOpen, setFormatoModalOpen] = useState(false)
  const [formatoAReemplazar, setFormatoAReemplazar] = useState<FormatoDocumentalResponse | null>(null)
  const [newUser, setNewUser] = useState(false)
  const [firmaModalOpen, setFirmaModalOpen] = useState(false)
  const [firmaUsuarioPreseleccionado, setFirmaUsuarioPreseleccionado] = useState<UserRow | null>(null)

  const [contratosActivos, setContratosActivos] = useState(0)
  const [totalContratos, setTotalContratos] = useState(0)

  const cargarFormatos = () => {
    getFormatos().then(setFormatos).catch(() => {})
  }

  const cargarFirmas = () => {
    getFirmas().then(lista => setFirmas(lista.map(mapFirma))).catch(() => {})
  }

  // Datos reales: usuarios, contratos, catálogo de formatos y firmas asignadas
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
    getFormatos()
      .then(lista => { if (!cancelado) setFormatos(lista) })
      .catch(() => {})
    getFirmas()
      .then(lista => { if (!cancelado) setFirmas(lista.map(mapFirma)) })
      .catch(() => {})
    return () => { cancelado = true }
  }, [])

  const toggleActivo = async (u: UserRow) => {
    try {
      const actualizado = await cambiarEstadoUsuario(Number(u.id), { activo: !u.activo })
      setUsers(us => us.map(x => x.id === u.id ? { ...x, activo: actualizado.activo } : x))
    } catch { /* el chip conserva el estado real si el backend rechaza */ }
  }

  const toggleFirma = async (f: FirmaRow) => {
    try {
      const actualizada = await cambiarEstadoFirma(Number(f.id), { activa: !f.activa })
      setFirmas(fs => fs.map(x => x.id === f.id ? { ...x, activa: actualizada.activa } : x))
    } catch { /* la fila conserva el estado real si el backend rechaza */ }
  }

  const eliminarFormatoRow = async (f: FormatoDocumentalResponse) => {
    if (!window.confirm(`¿Eliminar "${f.codigo} — ${f.nombre}" del catálogo? Esta acción no se puede deshacer.`)) return
    try {
      await eliminarFormato(f.id)
      setFormatos(fs => fs.filter(x => x.id !== f.id))
    } catch { /* si el backend rechaza, la fila se conserva */ }
  }

  const formatosObsoletos = formatos.filter(f => f.estado === 'OBSOLETO').length

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', background: 'var(--bg-base)', overflow: 'hidden' }}>
      {/* Barra superior */}
      <TopBar badge="Interfaz de Administrador" usuario={usuario} onOpenSettings={onOpenSettings} onLogout={onLogout}
        avatarColor="var(--alert-leve)" avatarTextColor="#1a1400" />

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
            <div style={{ marginBottom: 18 }}>
              <div className="eyebrow">Vista general</div>
              <h3 style={{ fontSize: 18 }}>Panel de Control</h3>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: 12, marginBottom: 20 }}>
              <Widget icon={<IconClipboardList size={17} />} label="Contratos activos" value={String(contratosActivos)} hint={`${totalContratos} registrados en total`} />
              <Widget icon={<IconUsers size={17} />} label="Usuarios activos" value={String(users.filter(u => u.activo).length)} hint={`${users.length} registrados en total`} />
              <Widget icon={<IconFileText size={17} />} label="Formatos vigentes" value={`${formatos.filter(f => f.estado === 'VIGENTE').length}/${formatos.length}`} hint="Formatos oficiales cargados" />
              <Widget icon={<IconAlertTriangle size={17} />} label="Formatos obsoletos" value={String(formatosObsoletos)} hint="Requieren reemplazo por una versión vigente" tone={formatosObsoletos ? 'warn' : 'ok'} />
            </div>

            <div className="card" style={{ padding: '16px 18px' }}>
              <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 2 }}>Actividad de los últimos 30 días</div>
              <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 16 }}>Contratos creados, supervisados y cerrados</div>
              <div style={{ height: 260 }}>
                {ACTIVIDAD.length === 0 ? (
                  <div style={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 13, color: 'var(--text-muted)', textAlign: 'center', padding: '0 20px' }}>
                    Las estadísticas se mostrarán en este panel cuando existan contratos activos.
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
            <SectionHead icon={<IconClipboardList size={16} />} title="Gestión de Documentos"
              desc="Catálogo real de formatos oficiales (GCCON, GIL, ESUCON y demás formatos institucionales). Los archivos que cargues aquí quedan disponibles para descarga real."
              action={<button className="btn-green" onClick={() => { setFormatoAReemplazar(null); setFormatoModalOpen(true) }} style={{ padding: '8px 14px', fontSize: 12 }}>+ Cargar formato</button>} />
            <div className="card" style={{ overflow: 'hidden' }}>
              <GridRow header cols="140px 1fr 80px 150px 150px 250px">
                <span>CÓDIGO</span><span>NOMBRE</span><span>VERSIÓN</span><span>ACTUALIZADO</span><span>ESTADO</span><span>ACCIONES</span>
              </GridRow>
              {formatos.length === 0 && (
                <div style={{ padding: '40px 16px', textAlign: 'center' }}>
                  <IconClipboardList size={28} style={{ opacity: 0.4, margin: '0 auto 10px' }} />
                  <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-primary)', marginBottom: 4 }}>Aún no hay formatos cargados</div>
                  <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>Utilice "+ Cargar formato" para cargar los formatos oficiales (PDF, DOCX o XLSX).</div>
                </div>
              )}
              {formatos.map(f => (
                <GridRow key={f.id} cols="140px 1fr 80px 150px 150px 250px">
                  <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--accent)' }}>{f.codigo}</span>
                  <span style={{ fontSize: 12 }}>
                    {f.nombre}
                    <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>
                      {f.tipoArchivo} · {formatBytes(f.tamanioBytes)}{f.subidoPorNombre ? ` · ${f.subidoPorNombre}` : ''}
                    </div>
                  </span>
                  <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12 }}>{f.version}</span>
                  <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{formatFecha(f.fechaActualizacion)}</span>
                  <Chip text={FORMATO_CHIP[f.estado].label} type={FORMATO_CHIP[f.estado].type} />
                  <span style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                    <MiniBtn onClick={() => descargarFormato(f.id, f.nombreArchivo).catch(() => {})}>
                      <IconDownload size={11} /> Descargar
                    </MiniBtn>
                    <MiniBtn onClick={() => { setFormatoAReemplazar(f); setFormatoModalOpen(true) }} accent>
                      <IconUpload size={11} /> Nueva versión
                    </MiniBtn>
                    <MiniBtn onClick={() => eliminarFormatoRow(f)}>
                      <IconTrash size={11} /> Eliminar
                    </MiniBtn>
                  </span>
                </GridRow>
              ))}
            </div>
          </>
        )}

        {/* ── Usuarios ── */}
        {tab === 'usuarios' && (
          <>
            <SectionHead icon={<IconUsers size={16} />} title="Gestión de Usuarios"
              desc="Crear, editar y desactivar supervisores y personal de gestión."
              action={<button className="btn-green" onClick={() => setNewUser(true)} style={{ padding: '8px 14px', fontSize: 12 }}>+ Nuevo usuario</button>} />
            <div className="card" style={{ overflow: 'hidden' }}>
              <GridRow header cols="1fr 260px 150px 190px 110px 220px">
                <span>NOMBRE</span><span>CORREO</span><span>CARGO</span><span>ROL</span><span>ESTADO</span><span>ACCIONES</span>
              </GridRow>
              {users.map(u => (
                <GridRow key={u.id} cols="1fr 260px 150px 190px 110px 220px">
                  <span style={{ fontSize: 12 }}>{u.nombre}<div style={{ fontSize: 11, color: 'var(--text-muted)' }}>Centros: {u.centros}</div></span>
                  <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{u.correo}<div style={{ fontSize: 11, color: 'var(--text-muted)' }}>{u.telefono}</div></span>
                  <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{u.cargo}</span>
                  <span style={{ fontSize: 12 }}>{u.rol}</span>
                  <Chip text={u.activo ? 'Activo' : 'Inactivo'} type={u.activo ? 'vigente' : 'inactive'} />
                  <span style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                    <MiniBtn onClick={() => { setFirmaUsuarioPreseleccionado(u); setFirmaModalOpen(true) }}>
                      <IconSignature size={11} /> Asignar firma
                    </MiniBtn>
                    <MiniBtn onClick={() => {}} disabled title="El restablecimiento de contraseña se habilita en una fase posterior">Resetear clave</MiniBtn>
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
            <SectionHead icon={<IconSignature size={16} />} title="Firmas Electrónicas"
              desc="Asigna una firma electrónica a las cuentas que administras (Administrador, Gestión, Supervisor). Referencia interna por ahora — la integración con un proveedor de firma electrónica (PKI) real queda para una fase posterior."
              action={<button className="btn-green" onClick={() => { setFirmaUsuarioPreseleccionado(null); setFirmaModalOpen(true) }} style={{ padding: '8px 14px', fontSize: 12 }}>+ Asignar firma</button>} />
            <div className="card" style={{ overflow: 'hidden' }}>
              <GridRow header cols="1fr 260px 190px 190px 120px 160px">
                <span>USUARIO</span><span>CORREO</span><span>FIRMA ID</span><span>FECHA ASIGNACIÓN</span><span>ESTADO</span><span>ACCIONES</span>
              </GridRow>
              {firmas.length === 0 && (
                <div style={{ padding: '40px 16px', textAlign: 'center' }}>
                  <IconSignature size={26} style={{ opacity: 0.4, margin: '0 auto 10px', color: 'var(--text-muted)' }} />
                  <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-primary)', marginBottom: 4 }}>Aún no hay firmas asignadas</div>
                  <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>Utilice "+ Asignar firma" o el botón "Asignar firma" en Gestión de Usuarios.</div>
                </div>
              )}
              {firmas.map(f => (
                <GridRow key={f.id} cols="1fr 260px 190px 190px 120px 160px">
                  <span style={{ fontSize: 12 }}>{f.usuario}</span>
                  <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{f.correo}</span>
                  <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--accent)' }}>{f.firmaId}</span>
                  <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{f.fecha}</span>
                  <Chip text={f.activa ? 'Vigente' : 'Revocada'} type={f.activa ? 'vigente' : 'inactive'} />
                  <span style={{ display: 'flex', gap: 6 }}>
                    <MiniBtn onClick={() => toggleFirma(f)}>
                      {f.activa ? 'Revocar' : 'Restaurar'}
                    </MiniBtn>
                  </span>
                </GridRow>
              ))}
            </div>
          </>
        )}
      </div>

      {formatoModalOpen && (
        <FormatoModal
          formatoExistente={formatoAReemplazar}
          onClose={() => setFormatoModalOpen(false)}
          onUploaded={() => { setFormatoModalOpen(false); cargarFormatos() }}
        />
      )}

      {newUser && (
        <NewUserModal onClose={() => setNewUser(false)}
          onCreate={u => { setUsers(us => [...us, u]); setNewUser(false) }} />
      )}

      {firmaModalOpen && (
        <NewFirmaModal
          usuarios={users}
          usuarioPreseleccionado={firmaUsuarioPreseleccionado}
          onClose={() => setFirmaModalOpen(false)}
          onCreate={f => { setFirmas(fs => [...fs, f]); setFirmaModalOpen(false) }}
        />
      )}
    </div>
  )
}

// ─── Piezas ───────────────────────────────────────────────────────────────────

function Widget({ icon, label, value, hint, tone = 'ok' }: { icon?: React.ReactNode; label: string; value: string; hint: string; tone?: 'ok' | 'warn' }) {
  const tint = tone === 'warn' ? 'var(--alert-leve)' : 'var(--accent)'
  return (
    <div className={`stat-card${tone === 'warn' ? ' warn' : ''}`}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
        {icon && (
          <div style={{
            width: 30, height: 30, borderRadius: 8, flexShrink: 0, color: tint,
            background: tone === 'warn' ? 'rgba(184,120,10,0.12)' : 'var(--accent-soft)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>{icon}</div>
        )}
        <div style={{ fontSize: 11, letterSpacing: '0.06em', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>{label}</div>
      </div>
      <div style={{ fontSize: 32, fontWeight: 700, color: tint, lineHeight: 1.3, fontFamily: 'var(--font-display)', margin: '2px 0' }}>{value}</div>
      <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{hint}</div>
    </div>
  )
}

function SectionHead({ icon, title, desc, action }: { icon?: React.ReactNode; title: string; desc: string; action?: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16, marginBottom: 18, flexWrap: 'wrap' }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
        {icon && (
          <div style={{
            width: 34, height: 34, borderRadius: 9, flexShrink: 0, marginTop: 1, color: 'var(--accent)',
            background: 'var(--accent-soft)', border: '1px solid var(--accent-line)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>{icon}</div>
        )}
        <div>
          <h3 style={{ margin: '0 0 4px', fontSize: 16 }}>{title}</h3>
          <p style={{ margin: 0, fontSize: 13, color: 'var(--text-muted)', maxWidth: 620 }}>{desc}</p>
        </div>
      </div>
      {action}
    </div>
  )
}

function GridRow({ cols, header, children }: { cols: string; header?: boolean; children: React.ReactNode }) {
  return (
    <div className={header ? 'data-grid' : 'data-grid data-grid-row'} style={{
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

function MiniBtn({ children, onClick, accent, disabled, title }: {
  children: React.ReactNode; onClick: () => void; accent?: boolean; disabled?: boolean; title?: string
}) {
  return (
    <button onClick={onClick} disabled={disabled} title={title} style={{
      display: 'inline-flex', alignItems: 'center', gap: 5,
      background: accent ? 'var(--accent-soft)' : 'transparent',
      border: `1px solid ${accent ? 'var(--accent-line)' : 'var(--border)'}`,
      color: disabled ? 'var(--text-muted)' : accent ? 'var(--accent)' : 'var(--text-secondary)',
      borderRadius: 6, padding: '4px 9px', fontSize: 11, cursor: disabled ? 'not-allowed' : 'pointer',
      opacity: disabled ? 0.55 : 1,
      fontFamily: 'var(--font-ui)', whiteSpace: 'nowrap',
    }}>{children}</button>
  )
}

// ─── Modal: cargar formato documental (real — sin simulaciones) ───────────────

function FormatoModal({ formatoExistente, onClose, onUploaded }: {
  formatoExistente: FormatoDocumentalResponse | null
  onClose: () => void
  onUploaded: () => void
}) {
  const [codigo, setCodigo] = useState(formatoExistente?.codigo ?? '')
  const [nombre, setNombre] = useState(formatoExistente?.nombre ?? '')
  const [archivo, setArchivo] = useState<File | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [done, setDone] = useState(false)

  const esNuevoVersion = formatoExistente !== null

  const subir = async () => {
    if (busy) return
    if (!codigo.trim()) { setError('El código del formato es obligatorio (ej. GCCON-F-031).'); return }
    if (!nombre.trim()) { setError('El nombre del formato es obligatorio.'); return }
    if (!archivo) { setError('Selecciona un archivo PDF, DOCX o XLSX.'); return }
    setError('')
    setBusy(true)
    try {
      await subirFormato(codigo.trim(), nombre.trim(), archivo)
      setDone(true)
      setTimeout(onUploaded, 900)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se pudo cargar el archivo.')
      setBusy(false)
    }
  }

  if (done) {
    return (
      <Modal title="Formato cargado" onClose={onClose} width={460}>
        <div style={{ textAlign: 'center', padding: '10px 0 4px' }}>
          <IconUpload size={40} style={{ color: 'var(--accent)', margin: '0 auto 10px' }} />
          <p style={{ fontSize: 14, color: 'var(--accent)', fontWeight: 600, margin: '0 0 4px' }}>
            {esNuevoVersion ? 'Nueva versión cargada correctamente' : 'Formato agregado al catálogo'}
          </p>
          <p style={{ fontSize: 13, color: 'var(--text-secondary)', margin: 0 }}>{codigo} — {nombre}</p>
        </div>
      </Modal>
    )
  }

  return (
    <Modal title={esNuevoVersion ? `Cargar nueva versión — ${formatoExistente!.codigo}` : 'Cargar nuevo formato'} onClose={onClose} width={520}>
      <Field label="Código del formato (ej. GCCON-F-031)">
        <input type="text" value={codigo} disabled={esNuevoVersion}
          onChange={e => setCodigo(e.target.value)} placeholder="GCCON-F-031"
          style={{ width: '100%', padding: '9px 10px', fontFamily: 'var(--font-mono)', opacity: esNuevoVersion ? 0.65 : 1 }} />
      </Field>
      <Field label="Nombre del formato">
        <input type="text" value={nombre} onChange={e => setNombre(e.target.value)}
          placeholder="Informe de supervisión" style={{ width: '100%', padding: '9px 10px' }} />
      </Field>
      <Field label="Archivo (PDF, DOCX o XLSX — máx 20 MB)">
        <input type="file" accept=".pdf,.docx,.xlsx"
          onChange={e => setArchivo(e.target.files?.[0] ?? null)}
          style={{ width: '100%', fontSize: 13, color: 'var(--text-secondary)' }} />
      </Field>

      {error && <p style={{ color: 'var(--alert-critica)', fontSize: 12, margin: '4px 0 10px' }}>{error}</p>}

      <div style={{ display: 'flex', gap: 10, marginTop: 8 }}>
        <button className="btn-ghost" style={{ flex: 1, padding: '10px 0', fontSize: 13 }} onClick={onClose} disabled={busy}>Cancelar</button>
        <button className="btn-green" style={{ flex: 2, padding: '10px 0', fontSize: 13, opacity: busy ? 0.6 : 1, cursor: busy ? 'default' : 'pointer' }} onClick={subir} disabled={busy}>
          {busy ? 'Cargando…' : esNuevoVersion ? 'Cargar nueva versión' : 'Cargar formato'}
        </button>
      </div>
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
  const [pw] = useState(randomPassword())
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)
  const [usuarioCreado, setUsuarioCreado] = useState<UsuarioResponse | null>(null)
  const [envioResultado, setEnvioResultado] = useState<{ enviado: boolean; error: string | null } | null>(null)
  const [enviandoCredenciales, setEnviandoCredenciales] = useState(false)

  const ROL_MAP: Record<string, Rol> = { Supervisor: 'SUPERVISOR', 'Gestor de Contratación': 'GESTION', Administrador: 'ADMINISTRADOR' }

  const crear = async () => {
    if (busy) return
    if (!nombre.trim()) { setError('El nombre completo es obligatorio.'); return }
    if (!correo.endsWith('@soy.sena.edu.co')) { setError('El correo debe pertenecer al dominio @soy.sena.edu.co'); return }
    if (!tel.trim()) { setError('El número de teléfono es obligatorio.'); return }
    setError('')
    setBusy(true)
    try {
      const creado = await crearUsuario({ nombre: nombre.trim(), email: correo.trim(), password: pw, telefono: tel.trim(), rol: ROL_MAP[rol] })
      setUsuarioCreado(creado)
      setDone(true)
      setBusy(false)

      setEnviandoCredenciales(true)
      try {
        setEnvioResultado(await enviarCredenciales(creado.id, { password: pw }))
      } catch {
        setEnvioResultado({ enviado: false, error: 'No se pudo contactar al servidor.' })
      }
      setEnviandoCredenciales(false)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se pudo crear el usuario.')
      setBusy(false)
    }
  }

  const cerrar = () => {
    if (!usuarioCreado) return
    onCreate({
      id: String(usuarioCreado.id),
      nombre: usuarioCreado.nombre,
      correo: usuarioCreado.email,
      telefono: usuarioCreado.telefono ?? '—',
      cargo: ROL_CARGO[usuarioCreado.rol],
      rol: ROL_LABEL[usuarioCreado.rol],
      activo: usuarioCreado.activo,
      centros: centros.join(', ') || '—',
    })
  }

  const CENTROS = ['920510', '920511', '920512', '920520']

  if (done && usuarioCreado) {
    return (
      <Modal title="Usuario creado" onClose={cerrar} width={460}>
        <div style={{ textAlign: 'center', padding: '10px 0 4px' }}>
          <IconCheckCircle size={40} style={{ color: 'var(--accent)', margin: '0 auto 8px' }} />
          <p style={{ fontSize: 14, color: 'var(--accent)', fontWeight: 600, margin: '8px 0 4px' }}>Usuario creado exitosamente</p>
          <p style={{ fontSize: 13, color: 'var(--text-secondary)', margin: 0 }}>{usuarioCreado.nombre} — {usuarioCreado.email}</p>

          <div style={{
            marginTop: 14, padding: '10px 14px', borderRadius: 8, fontSize: 12.5, textAlign: 'left',
            background: enviandoCredenciales ? 'var(--bg-surface)' : envioResultado?.enviado ? 'var(--accent-soft)' : 'var(--chip-red-bg)',
            border: `1px solid ${enviandoCredenciales ? 'var(--border)' : envioResultado?.enviado ? 'var(--accent-line)' : 'var(--chip-red)'}`,
          }}>
            {enviandoCredenciales && 'Enviando correo…'}
            {!enviandoCredenciales && envioResultado?.enviado && `Correo enviado a ${usuarioCreado.email}.`}
            {!enviandoCredenciales && envioResultado && !envioResultado.enviado && (
              <>No fue posible enviar el correo: {envioResultado.error}. Copie la contraseña que aparece a continuación y compártala de forma manual.</>
            )}
          </div>

          <div style={{ marginTop: 14, padding: '10px 14px', background: 'var(--bg-surface)', borderRadius: 8, fontSize: 12, color: 'var(--text-secondary)', textAlign: 'left' }}>
            Contraseña temporal: <span style={{ fontFamily: 'var(--font-mono)' }}>{pw}</span>
          </div>

          <button className="btn-green" style={{ width: '100%', padding: '10px 0', fontSize: 13, marginTop: 16 }} onClick={cerrar}>
            Aceptar
          </button>
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
      <Field label="Número de teléfono (requerido)">
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
        <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4 }}>Generada automáticamente · 12 caracteres · almacenada (cifrada) · se envía por correo institucional al crear</div>
      </div>

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

function NewFirmaModal({ usuarios, usuarioPreseleccionado, onClose, onCreate }: {
  usuarios: UserRow[]
  usuarioPreseleccionado: UserRow | null
  onClose: () => void
  onCreate: (f: FirmaRow) => void
}) {
  const activos = usuarios.filter(u => u.activo)
  const [usuarioId, setUsuarioId] = useState(usuarioPreseleccionado?.id ?? String(activos[0]?.id ?? ''))
  const [phase, setPhase] = useState<'form' | 'gen' | 'done'>('form')
  const [creada, setCreada] = useState<FirmaResponse | null>(null)
  const [error, setError] = useState('')

  const seleccionado = activos.find(u => u.id === usuarioId) ?? null

  const generar = async () => {
    if (!seleccionado) return
    setError('')
    setPhase('gen')
    try {
      const resultado = await crearFirma({ usuarioId: Number(seleccionado.id) })
      setCreada(resultado)
      setPhase('done')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se pudo asignar la firma.')
      setPhase('form')
    }
  }

  const confirmar = () => {
    if (!creada) return
    onCreate(mapFirma(creada))
  }

  return (
    <Modal title="Asignar firma electrónica" onClose={onClose} width={480}>
      {phase === 'form' && (
        <>
          {activos.length === 0 ? (
            <p style={{ fontSize: 13, color: 'var(--text-muted)' }}>No hay cuentas activas disponibles para asignar una firma.</p>
          ) : (
            <Field label="Cuenta">
              <select value={usuarioId} onChange={e => setUsuarioId(e.target.value)}>
                {activos.map(u => <option key={u.id} value={u.id}>{u.nombre} — {u.rol}</option>)}
              </select>
            </Field>
          )}

          {seleccionado && (
            <div className="card" style={{ padding: '10px 14px', marginBottom: 4, fontSize: 12, color: 'var(--text-secondary)' }}>
              {seleccionado.correo} · {seleccionado.cargo}
            </div>
          )}

          <p style={{ fontSize: 11.5, color: 'var(--text-muted)', margin: '10px 0 14px', lineHeight: 1.5 }}>
            Esto asigna un identificador de firma de referencia a la cuenta y queda guardado en
            el sistema. La integración con un proveedor real de firma electrónica (PKI) se
            implementa en una fase posterior.
          </p>

          {error && <p style={{ color: 'var(--alert-critica)', fontSize: 12, margin: '0 0 10px' }}>{error}</p>}

          <button className="btn-green" style={{ width: '100%', padding: '10px 0', fontSize: 13, opacity: seleccionado ? 1 : 0.6 }}
            onClick={generar} disabled={!seleccionado}>
            Asignar firma
          </button>
        </>
      )}

      {phase === 'gen' && (
        <div style={{ textAlign: 'center', padding: '34px 0' }}>
          <IconLock size={30} style={{ color: 'var(--accent)', margin: '0 auto 12px' }} />
          <p style={{ color: 'var(--accent)', fontSize: 14, fontWeight: 600, margin: 0 }}>Asignando firma...</p>
        </div>
      )}

      {phase === 'done' && creada && (
        <div style={{ textAlign: 'center' }}>
          <IconSignature size={34} style={{ color: 'var(--accent)', margin: '0 auto 6px' }} />
          <p style={{ fontSize: 14, fontWeight: 600, color: 'var(--accent)', margin: '8px 0 2px' }}>Firma {creada.firmaId} asignada</p>
          <p style={{ fontSize: 13, color: 'var(--text-secondary)', margin: 0 }}>
            Cuenta: {creada.usuarioNombre} ({creada.usuarioEmail})
          </p>
          <button className="btn-green" style={{ width: '100%', padding: '10px 0', fontSize: 13, marginTop: 18 }} onClick={confirmar}>
            Aceptar
          </button>
        </div>
      )}
    </Modal>
  )
}
