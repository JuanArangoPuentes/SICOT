import { useEffect, useState } from 'react'
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import AppShell, { type NavGroup } from '@/components/AppShell'
import { Chip, Field, Modal, type ChipType } from '@/components/ui'
import { IconAlertTriangle, IconCheckCircle, IconClipboardList, IconDownload, IconFileText, IconGrid, IconLock, IconSignature, IconTrash, IconUpload, IconUsers } from '@/components/icons'
import type { AuthResponse, EstadoFormato, FirmaResponse, FormatoDocumentalResponse, Rol, UsuarioResponse } from '@/services/api/types'
import type { AdminTab } from '@/types/domain'
import { getUsuarios, crearUsuario, actualizarUsuario, cambiarEstadoUsuario, enviarCredenciales } from '@/services/usuarioService'
import { getContratos } from '@/services/contratoService'
import { getFormatos, subirFormato, eliminarFormato, descargarFormato } from '@/services/formatoService'
import { getFirmas, crearFirma, cambiarEstadoFirma } from '@/services/firmaService'
import { ApiError } from '@/services/api/client'
import { formatBytes, formatFecha } from '@/services/format'
import { GridRow, MiniBtn, SectionHead, Widget } from '@/components/admin/piezas'
import { FormatoModal } from '@/components/admin/FormatoModal'
import { ResetPasswordModal } from '@/components/admin/ResetPasswordModal'
import { NewUserModal } from '@/components/admin/NewUserModal'
import { NewFirmaModal } from '@/components/admin/NewFirmaModal'
import {
  ACTIVIDAD,
  FORMATO_CHIP,
  ROL_CARGO,
  ROL_LABEL,
  mapFirma,
  mapUser,
  randomPassword,
  type FirmaRow,
  type UserRow,
} from '@/components/admin/tipos'


export default function AdminPanel({ vista, onCambiarVista, usuario, onLogout, onOpenSettings }: {
  vista: AdminTab
  onCambiarVista: (t: AdminTab) => void
  usuario: AuthResponse
  onLogout: () => void
  onOpenSettings: () => void
}) {
  // Vista activa desde la URL — ver docs/decisiones/ADR-007.
  const tab = vista
  const setTab = onCambiarVista
  const [formatos, setFormatos] = useState<FormatoDocumentalResponse[]>([])
  const [users, setUsers] = useState<UserRow[]>([])
  const [firmas, setFirmas] = useState<FirmaRow[]>([])

  const [formatoModalOpen, setFormatoModalOpen] = useState(false)
  const [formatoAReemplazar, setFormatoAReemplazar] = useState<FormatoDocumentalResponse | null>(null)
  const [newUser, setNewUser] = useState(false)
  const [firmaModalOpen, setFirmaModalOpen] = useState(false)
  const [firmaUsuarioPreseleccionado, setFirmaUsuarioPreseleccionado] = useState<UserRow | null>(null)
  const [usuarioAResetear, setUsuarioAResetear] = useState<UserRow | null>(null)

  const [contratosActivos, setContratosActivos] = useState(0)
  const [totalContratos, setTotalContratos] = useState(0)

  // Las consultas que fallan NO pueden pintarse como un cero medido: un
  // dashboard que muestra "0 contratos activos" porque la API no respondió es
  // indistinguible de uno que muestra un cero real, y lleva a decisiones
  // equivocadas. Cuando falla se muestra un guion y un aviso.
  const [errorDatos, setErrorDatos] = useState(false)
  // Errores de las acciones sobre filas (activar usuario, revocar firma,
  // eliminar formato). Antes se tragaban en silencio: se hacía clic y no pasaba
  // absolutamente nada, sin explicación.
  const [errorAccion, setErrorAccion] = useState('')

  const cargarFormatos = () => {
    getFormatos().then(setFormatos).catch(() => setErrorDatos(true))
  }

  const cargarFirmas = () => {
    getFirmas().then(lista => setFirmas(lista.map(mapFirma))).catch(() => setErrorDatos(true))
  }

  // Datos reales: usuarios, contratos, catálogo de formatos y firmas asignadas
  useEffect(() => {
    let cancelado = false
    const fallo = () => { if (!cancelado) setErrorDatos(true) }
    getUsuarios()
      .then(lista => { if (!cancelado) setUsers(lista.map(mapUser)) })
      .catch(fallo)
    getContratos()
      .then(lista => {
        if (cancelado) return
        setTotalContratos(lista.length)
        setContratosActivos(lista.filter(c => c.estado === 'ACTIVO').length)
      })
      .catch(fallo)
    getFormatos()
      .then(lista => { if (!cancelado) setFormatos(lista) })
      .catch(fallo)
    getFirmas()
      .then(lista => { if (!cancelado) setFirmas(lista.map(mapFirma)) })
      .catch(fallo)
    return () => { cancelado = true }
  }, [])

  const toggleActivo = async (u: UserRow) => {
    setErrorAccion('')
    try {
      const actualizado = await cambiarEstadoUsuario(Number(u.id), { activo: !u.activo })
      setUsers(us => us.map(x => x.id === u.id ? { ...x, activo: actualizado.activo } : x))
    } catch {
      setErrorAccion(`No se pudo cambiar el estado de ${u.nombre}.`)
    }
  }

  const toggleFirma = async (f: FirmaRow) => {
    setErrorAccion('')
    try {
      const actualizada = await cambiarEstadoFirma(Number(f.id), { activa: !f.activa })
      setFirmas(fs => fs.map(x => x.id === f.id ? { ...x, activa: actualizada.activa } : x))
    } catch {
      setErrorAccion(`No se pudo cambiar el estado de la firma de ${f.usuario}.`)
    }
  }

  const eliminarFormatoRow = async (f: FormatoDocumentalResponse) => {
    if (!window.confirm(`¿Eliminar "${f.codigo} — ${f.nombre}" del catálogo? Esta acción no se puede deshacer.`)) return
    setErrorAccion('')
    try {
      await eliminarFormato(f.id)
      setFormatos(fs => fs.filter(x => x.id !== f.id))
    } catch {
      setErrorAccion(`No se pudo eliminar el formato ${f.codigo}.`)
    }
  }

  const formatosObsoletos = formatos.filter(f => f.estado === 'OBSOLETO').length

  const navGroups: NavGroup[] = [
    {
      label: 'Administración',
      items: [
        { id: 'dashboard', label: 'Panel de Control', icon: <IconGrid size={17} /> },
        { id: 'documentos', label: 'Formatos documentales', icon: <IconFileText size={17} />, count: formatos.length },
      ],
    },
    {
      label: 'Cuentas',
      items: [
        { id: 'usuarios', label: 'Usuarios', icon: <IconUsers size={17} />, count: users.length },
        { id: 'firmas', label: 'Firmas electrónicas', icon: <IconSignature size={17} />, count: firmas.length },
      ],
    },
  ]

  const TITULO: Record<AdminTab, { titulo: string; sub: string }> = {
    dashboard: { titulo: 'Panel de Control', sub: 'Cifras generales del sistema' },
    documentos: { titulo: 'Formatos documentales', sub: 'Catálogo oficial de formatos institucionales' },
    usuarios: { titulo: 'Usuarios', sub: 'Cuentas y roles del sistema' },
    firmas: { titulo: 'Firmas electrónicas', sub: 'Asignación y vigencia de firmas' },
  }

  return (
    <AppShell
      roleBadge="Interfaz de Administrador"
      groups={navGroups}
      activeId={tab}
      onNavigate={id => setTab(id as AdminTab)}
      usuario={usuario}
      avatarColor="var(--alert-leve)"
      avatarTextColor="#1a1400"
      onLogout={onLogout}
      onOpenSettings={onOpenSettings}
      title={TITULO[tab].titulo}
      subtitle={TITULO[tab].sub}
    >
      <div style={{ flex: 1, overflowY: 'auto', padding: 24, minWidth: 0 }}>

        {errorAccion && (
          <div style={{ marginBottom: 14, padding: '10px 14px', border: '1px solid var(--chip-red)', background: 'var(--chip-red-bg)', borderRadius: 8, fontSize: 13, color: 'var(--text-primary)', display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center' }}>
            <span>{errorAccion}</span>
            <button className="btn-ghost" onClick={() => setErrorAccion('')} style={{ padding: '4px 10px', fontSize: 12 }}>Cerrar</button>
          </div>
        )}

        {/* ── Panel de control ── */}
        {tab === 'dashboard' && (
          <>
            {errorDatos && (
              <div style={{ marginBottom: 14, padding: '10px 14px', border: '1px solid var(--chip-red)', background: 'var(--chip-red-bg)', borderRadius: 8, fontSize: 13, color: 'var(--text-primary)' }}>
                No se pudieron consultar algunos datos del servidor. Las cifras de abajo pueden estar
                incompletas — recargue la página para reintentar.
              </div>
            )}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: 12, marginBottom: 20 }}>
              <Widget icon={<IconClipboardList size={17} />} label="Contratos activos" value={errorDatos ? '—' : String(contratosActivos)} hint={errorDatos ? 'Dato no disponible' : `${totalContratos} registrados en total`} />
              <Widget icon={<IconUsers size={17} />} label="Usuarios activos" value={errorDatos ? '—' : String(users.filter(u => u.activo).length)} hint={errorDatos ? 'Dato no disponible' : `${users.length} registrados en total`} />
              <Widget icon={<IconFileText size={17} />} label="Formatos vigentes" value={errorDatos ? '—' : `${formatos.filter(f => f.estado === 'VIGENTE').length}/${formatos.length}`} hint={errorDatos ? 'Dato no disponible' : 'Formatos oficiales cargados'} />
              <Widget icon={<IconAlertTriangle size={17} />} label="Formatos obsoletos" value={errorDatos ? '—' : String(formatosObsoletos)} hint={errorDatos ? 'Dato no disponible' : 'Requieren reemplazo por una versión vigente'} tone={errorDatos ? undefined : (formatosObsoletos ? 'warn' : 'ok')} />
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
                    <MiniBtn onClick={() => setUsuarioAResetear(u)} title="Asignar una contraseña temporal nueva y enviarla al correo del usuario">
                      Resetear clave
                    </MiniBtn>
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

      {usuarioAResetear && (
        <ResetPasswordModal
          usuario={usuarioAResetear}
          onClose={() => setUsuarioAResetear(null)}
          onActualizado={u => setUsers(us => us.map(x => x.id === u.id ? u : x))}
        />
      )}

      {firmaModalOpen && (
        <NewFirmaModal
          usuarios={users}
          usuarioPreseleccionado={firmaUsuarioPreseleccionado}
          onClose={() => setFirmaModalOpen(false)}
          onCreate={f => { setFirmas(fs => [...fs, f]); setFirmaModalOpen(false) }}
        />
      )}
    </AppShell>
  )
}
