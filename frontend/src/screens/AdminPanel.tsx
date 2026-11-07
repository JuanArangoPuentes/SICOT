// Panel del Administrador: supervisar a los supervisores y nada más.
//
// Quien entra con este rol es la Subdirección del Centro (o quien haga sus
// veces), que no lleva contratos: lo suyo es ver en qué va cada supervisor y
// administrar las cuentas con que trabajan. Juan lo dejó dicho el 06-11-2026:
// «únicamente le va a aparecer el progreso que llevan los otros usuarios
// supervisores, para poder gestionar a los otros».
//
// Por eso el panel tiene tres vistas y la primera es el avance:
//   - Avance de los supervisores: en qué paso va cada uno y cómo va cada
//     contrato, en vivo, con el semáforo del cronograma arriba.
//   - Usuarios: crear cuentas, restablecer su contraseña, desactivarlas.
//   - Firmas electrónicas: la firma con que cada supervisor firma sus actas.
//
// Lo que salió y por qué:
//   - «Panel de control» (contratos activos, usuarios activos, formatos
//     cargados): eran cifras del sistema, no del avance de nadie. El semáforo
//     de los contratos, que era lo único de supervisión que tenía, encabeza
//     ahora la vista del avance.
//   - «Formatos documentales»: el catálogo de formatos no lo consulta ningún
//     otro rol ni lo usa la generación de documentos, que reproduce los
//     formatos oficiales desde el propio código (docs/formatos/README.md). El
//     servidor conserva el catálogo y su API (/api/formatos) con lo que ya se
//     hubiera cargado: no se borró nada, solo dejó de mostrarse aquí.

import { useEffect, useState } from 'react'
import AppShell, { type NavGroup } from '@/components/AppShell'
import { Chip } from '@/components/ui'
import { IconClipboardList, IconSignature, IconUsers } from '@/components/icons'
import SeguimientoSupervisores, { ResumenSeguimiento } from '@/components/admin/SeguimientoSupervisores'
import { getSeguimiento } from '@/services/seguimientoService'
import { useRefrescoEnVivo } from '@/hooks/useRefrescoEnVivo'
import type { AuthResponse, SeguimientoResponse, SupervisorSeguimiento } from '@/services/api/types'
import type { AdminTab } from '@/types/domain'
import { getUsuarios, cambiarEstadoUsuario } from '@/services/usuarioService'
import { getFirmas, cambiarEstadoFirma } from '@/services/firmaService'
import { ApiError } from '@/services/api/client'
import { GridRow, MiniBtn, SectionHead } from '@/components/admin/piezas'
import { ResetPasswordModal } from '@/components/admin/ResetPasswordModal'
import { NewUserModal } from '@/components/admin/NewUserModal'
import { NewFirmaModal } from '@/components/admin/NewFirmaModal'
import { mapFirma, mapUser, type FirmaRow, type UserRow } from '@/components/admin/tipos'

function AvisoDeError({ texto, onCerrar }: { texto: string; onCerrar?: () => void }) {
  return (
    <div
      role="alert"
      style={{
        marginBottom: 14,
        padding: '10px 14px',
        border: '1px solid var(--chip-red)',
        background: 'var(--chip-red-bg)',
        borderRadius: 8,
        fontSize: 13,
        color: 'var(--text-primary)',
        display: 'flex',
        justifyContent: 'space-between',
        gap: 12,
        alignItems: 'center',
      }}
    >
      <span>{texto}</span>
      {onCerrar && (
        <button className="btn-ghost" onClick={onCerrar} style={{ padding: '4px 10px', fontSize: 12 }}>
          Cerrar
        </button>
      )}
    </div>
  )
}

export default function AdminPanel({
  vista,
  onCambiarVista,
  usuario,
  onLogout,
  onOpenSettings,
}: {
  vista: AdminTab
  onCambiarVista: (t: AdminTab) => void
  usuario: AuthResponse
  onLogout: () => void
  onOpenSettings: () => void
}) {
  // Vista activa desde la URL — ver docs/decisiones/ADR-007.
  const tab = vista
  const setTab = onCambiarVista
  const [users, setUsers] = useState<UserRow[]>([])
  const [firmas, setFirmas] = useState<FirmaRow[]>([])

  const [newUser, setNewUser] = useState(false)
  const [firmaModalOpen, setFirmaModalOpen] = useState(false)
  const [firmaUsuarioPreseleccionado, setFirmaUsuarioPreseleccionado] = useState<UserRow | null>(null)
  const [usuarioAResetear, setUsuarioAResetear] = useState<UserRow | null>(null)

  // Una lista que no se pudo consultar no se pinta como una lista vacía: el
  // administrador creería que no hay cuentas o firmas, cuando lo que pasó es
  // que el servidor no respondió.
  const [errorUsuarios, setErrorUsuarios] = useState(false)
  const [errorFirmas, setErrorFirmas] = useState(false)
  // Errores de las acciones sobre filas (desactivar una cuenta, revocar una
  // firma). Antes se tragaban en silencio: se hacía clic y no pasaba nada.
  const [errorAccion, setErrorAccion] = useState('')

  const [seguimiento, setSeguimiento] = useState<SeguimientoResponse | null>(null)
  const [errorSeguimiento, setErrorSeguimiento] = useState('')
  const [cargandoSeguimiento, setCargandoSeguimiento] = useState(false)

  const cargarSeguimiento = () => {
    setCargandoSeguimiento(true)
    setErrorSeguimiento('')
    getSeguimiento()
      .then(setSeguimiento)
      .catch((e) =>
        setErrorSeguimiento(
          e instanceof ApiError
            ? `No se pudo consultar el avance de los supervisores: ${e.message}`
            : 'No se pudo consultar el avance de los supervisores. Revise la conexión y use «Actualizar».',
        ),
      )
      .finally(() => setCargandoSeguimiento(false))
  }

  // En vivo mientras la vista está abierta: lo que un supervisor cierra en su
  // equipo aparece aquí en segundos, sin «Actualizar» (useRefrescoEnVivo). Sin
  // el indicador de carga ni borrar lo que se ve: si una vuelta falla, la vista
  // se queda como estaba y el siguiente intento la pone al día.
  useRefrescoEnVivo(tab === 'seguimiento', () =>
    getSeguimiento().then((datos) => {
      setSeguimiento(datos)
      setErrorSeguimiento('')
    }),
  )

  useEffect(() => {
    let cancelado = false
    getUsuarios()
      .then((lista) => {
        if (!cancelado) setUsers(lista.map(mapUser))
      })
      .catch(() => {
        if (!cancelado) setErrorUsuarios(true)
      })
    getFirmas()
      .then((lista) => {
        if (!cancelado) setFirmas(lista.map(mapFirma))
      })
      .catch(() => {
        if (!cancelado) setErrorFirmas(true)
      })
    cargarSeguimiento()
    return () => {
      cancelado = true
    }
  }, [])

  const toggleActivo = async (u: UserRow) => {
    setErrorAccion('')
    try {
      const actualizado = await cambiarEstadoUsuario(Number(u.id), { activo: !u.activo })
      setUsers((us) => us.map((x) => (x.id === u.id ? { ...x, activo: actualizado.activo } : x)))
      cargarSeguimiento()
    } catch (e) {
      // El motivo del servidor dice qué hacer («tiene asignados 2 contratos en curso: reasígnelos…»).
      setErrorAccion(e instanceof ApiError ? e.message : `No se pudo cambiar el estado de ${u.nombre}.`)
    }
  }

  const toggleFirma = async (f: FirmaRow) => {
    setErrorAccion('')
    try {
      const actualizada = await cambiarEstadoFirma(Number(f.id), { activa: !f.activa })
      setFirmas((fs) => fs.map((x) => (x.id === f.id ? { ...x, activa: actualizada.activa } : x)))
      // El seguimiento dice qué supervisores tienen firma: sin recargarlo,
      // seguía diciendo «Sin firma electrónica» después de asignarla.
      cargarSeguimiento()
    } catch (e) {
      setErrorAccion(e instanceof ApiError ? e.message : `No se pudo cambiar el estado de la firma de ${f.usuario}.`)
    }
  }

  const abrirFirma = (cuenta: UserRow | null) => {
    setFirmaUsuarioPreseleccionado(cuenta)
    setFirmaModalOpen(true)
  }

  // Desde el avance: el supervisor que no puede firmar sus actas se arregla en
  // el mismo sitio donde se ve el aviso, sin ir a buscarlo a otra vista. Se
  // ofrece solo si su cuenta está en la lista de usuarios: abrir el diálogo sin
  // ella dejaría preseleccionada otra cuenta.
  const asignarFirmaDesdeElAvance = (s: SupervisorSeguimiento) => {
    const cuenta = users.find((u) => u.id === String(s.id))
    if (cuenta) abrirFirma(cuenta)
  }
  const puedeAsignarDesdeElAvance = (s: SupervisorSeguimiento) =>
    s.activo && users.some((u) => u.id === String(s.id) && u.activo)

  const navGroups: NavGroup[] = [
    {
      label: 'Supervisión',
      items: [
        {
          id: 'seguimiento',
          label: 'Avance de los supervisores',
          icon: <IconClipboardList size={17} />,
          count: seguimiento?.supervisores.length,
        },
      ],
    },
    {
      label: 'Gestionar cuentas',
      items: [
        { id: 'usuarios', label: 'Usuarios', icon: <IconUsers size={17} />, count: users.length },
        { id: 'firmas', label: 'Firmas electrónicas', icon: <IconSignature size={17} />, count: firmas.length },
      ],
    },
  ]

  const TITULO: Record<AdminTab, { titulo: string; sub: string }> = {
    seguimiento: {
      titulo: 'Avance de los supervisores',
      sub: 'En qué paso va cada supervisor y cómo va cada contrato',
    },
    usuarios: { titulo: 'Usuarios', sub: 'Las cuentas de los supervisores y del personal de gestión' },
    firmas: { titulo: 'Firmas electrónicas', sub: 'La firma con que cada supervisor firma sus documentos' },
  }

  return (
    <AppShell
      roleBadge="Interfaz de Administrador"
      groups={navGroups}
      activeId={tab}
      onNavigate={(id) => setTab(id as AdminTab)}
      usuario={usuario}
      avatarColor="var(--alert-leve)"
      avatarTextColor="#1a1400"
      onLogout={onLogout}
      onOpenSettings={onOpenSettings}
      title={TITULO[tab].titulo}
      subtitle={TITULO[tab].sub}
    >
      <div style={{ flex: 1, overflowY: 'auto', padding: 24, minWidth: 0 }}>
        {errorAccion && <AvisoDeError texto={errorAccion} onCerrar={() => setErrorAccion('')} />}

        {/* ── Avance de los supervisores ── */}
        {tab === 'seguimiento' && (
          <>
            {seguimiento && (
              <div className="card" style={{ padding: '16px 18px', marginBottom: 14 }}>
                <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 2 }}>Cómo van los contratos activos</div>
                <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 12 }}>
                  Semáforo del cronograma de cada contrato, el mismo que ve su supervisor
                </div>
                <ResumenSeguimiento datos={seguimiento} />
              </div>
            )}
            <SeguimientoSupervisores
              datos={seguimiento}
              error={errorSeguimiento}
              cargando={cargandoSeguimiento}
              onActualizar={cargarSeguimiento}
              onAsignarFirma={asignarFirmaDesdeElAvance}
              puedeAsignarFirma={puedeAsignarDesdeElAvance}
            />
          </>
        )}

        {/* ── Usuarios ── */}
        {tab === 'usuarios' && (
          <>
            <SectionHead
              icon={<IconUsers size={16} />}
              title="Gestión de Usuarios"
              desc="Crear las cuentas de los supervisores y del personal de gestión, restablecer su contraseña y desactivarlas."
              action={
                <button
                  className="btn-green"
                  onClick={() => setNewUser(true)}
                  style={{ padding: '8px 14px', fontSize: 12 }}
                >
                  + Nuevo usuario
                </button>
              }
            />
            {errorUsuarios && (
              <AvisoDeError texto="No se pudieron consultar las cuentas. Recargue la página para reintentar." />
            )}
            {/* Pasa a una columna cuando no cabe: a 1024 px con la barra lateral
                abierta, ACCIONES quedaba fuera de la tarjeta (index.css). */}
            <div className="card tabla-adaptable tabla-usuarios" style={{ overflow: 'hidden' }}>
              {/* Sin columna «Cargo»: SICOT no guarda el cargo de nadie, y la que
                  había lo deducía del rol («Instructor» para todo supervisor),
                  o sea, repetía el rol con un dato inventado. */}
              <GridRow header cols="1fr 260px 190px 110px 220px">
                <span>NOMBRE</span>
                <span>CORREO</span>
                <span>ROL</span>
                <span>ESTADO</span>
                <span>ACCIONES</span>
              </GridRow>
              {users.map((u) => (
                <GridRow key={u.id} cols="1fr 260px 190px 110px 220px">
                  <span style={{ fontSize: 12 }}>{u.nombre}</span>
                  <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                    {u.correo}
                    <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>{u.telefono}</div>
                  </span>
                  <span style={{ fontSize: 12 }}>{u.rol}</span>
                  <Chip text={u.activo ? 'Activo' : 'Inactivo'} type={u.activo ? 'vigente' : 'inactive'} />
                  <span style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                    {u.activo && (
                      <MiniBtn onClick={() => abrirFirma(u)}>
                        <IconSignature size={11} /> Asignar firma
                      </MiniBtn>
                    )}
                    <MiniBtn
                      onClick={() => setUsuarioAResetear(u)}
                      title="Asignar una contraseña temporal nueva y enviarla al correo del usuario"
                    >
                      Resetear clave
                    </MiniBtn>
                    <MiniBtn onClick={() => toggleActivo(u)}>{u.activo ? 'Desactivar' : 'Reactivar'}</MiniBtn>
                  </span>
                </GridRow>
              ))}
            </div>
          </>
        )}

        {/* ── Firmas ── */}
        {tab === 'firmas' && (
          <>
            <SectionHead
              icon={<IconSignature size={16} />}
              title="Firmas Electrónicas"
              desc="Asigne una firma electrónica a las cuentas que administra (Administrador, Gestión, Supervisor). Es una firma de referencia interna: la integración con un proveedor de firma electrónica (PKI) queda para una fase posterior."
              action={
                <button
                  className="btn-green"
                  onClick={() => abrirFirma(null)}
                  style={{ padding: '8px 14px', fontSize: 12 }}
                >
                  + Asignar firma
                </button>
              }
            />
            {errorFirmas && (
              <AvisoDeError texto="No se pudieron consultar las firmas. Recargue la página para reintentar." />
            )}
            <div className="card tabla-adaptable tabla-firmas" style={{ overflow: 'hidden' }}>
              <GridRow header cols="1fr 260px 190px 190px 120px 160px">
                <span>USUARIO</span>
                <span>CORREO</span>
                <span>FIRMA ID</span>
                <span>FECHA ASIGNACIÓN</span>
                <span>ESTADO</span>
                <span>ACCIONES</span>
              </GridRow>
              {firmas.length === 0 && !errorFirmas && (
                <div style={{ padding: '40px 16px', textAlign: 'center' }}>
                  <IconSignature
                    size={26}
                    style={{ opacity: 0.4, margin: '0 auto 10px', color: 'var(--text-muted)' }}
                  />
                  <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-primary)', marginBottom: 4 }}>
                    Aún no hay firmas asignadas
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                    Utilice "+ Asignar firma" o el botón "Asignar firma" en Gestión de Usuarios.
                  </div>
                </div>
              )}
              {firmas.map((f) => (
                <GridRow key={f.id} cols="1fr 260px 190px 190px 120px 160px">
                  <span style={{ fontSize: 12 }}>{f.usuario}</span>
                  <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{f.correo}</span>
                  <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--accent)' }}>
                    {f.firmaId}
                  </span>
                  <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{f.fecha}</span>
                  <Chip text={f.activa ? 'Vigente' : 'Revocada'} type={f.activa ? 'vigente' : 'inactive'} />
                  <span style={{ display: 'flex', gap: 6 }}>
                    <MiniBtn onClick={() => toggleFirma(f)}>{f.activa ? 'Revocar' : 'Restaurar'}</MiniBtn>
                  </span>
                </GridRow>
              ))}
            </div>
          </>
        )}
      </div>

      {newUser && (
        <NewUserModal
          onClose={() => setNewUser(false)}
          onCreate={(u) => {
            setUsers((us) => [...us, u])
            setNewUser(false)
            cargarSeguimiento()
          }}
        />
      )}

      {usuarioAResetear && (
        <ResetPasswordModal
          usuario={usuarioAResetear}
          onClose={() => setUsuarioAResetear(null)}
          onActualizado={(u) => setUsers((us) => us.map((x) => (x.id === u.id ? u : x)))}
        />
      )}

      {firmaModalOpen && (
        <NewFirmaModal
          usuarios={users}
          usuarioPreseleccionado={firmaUsuarioPreseleccionado}
          onClose={() => setFirmaModalOpen(false)}
          onCreate={(f) => {
            setFirmas((fs) => [...fs, f])
            setFirmaModalOpen(false)
            cargarSeguimiento()
          }}
        />
      )}
    </AppShell>
  )
}
