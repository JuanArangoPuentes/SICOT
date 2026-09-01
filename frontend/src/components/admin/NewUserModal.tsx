// Modal de creación de una cuenta de usuario.
//
// Extraído de AdminPanel.tsx, que reunía las cuatro vistas, sus cuatro modales y
// sus piezas compartidas en un solo archivo de 913 líneas — el mismo patrón que
// ya se aplicó a SupervisorPanel.tsx (ver components/supervisor/).

import { useState } from 'react'
import { Field, Modal } from '@/components/ui'
import { IconCheckCircle } from '@/components/icons'
import type { Rol, UsuarioResponse } from '@/services/api/types'
import { crearUsuario, enviarCredenciales } from '@/services/usuarioService'
import { ApiError } from '@/services/api/client'
import { mapUser, randomPassword, type UserRow } from './tipos'

export function NewUserModal({ onClose, onCreate }: { onClose: () => void; onCreate: (u: UserRow) => void }) {
  const [nombre, setNombre] = useState('')
  const [correo, setCorreo] = useState('')
  const [tel, setTel] = useState('')
  const [rol, setRol] = useState('Supervisor')
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
    onCreate(mapUser(usuarioCreado))
  }

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
