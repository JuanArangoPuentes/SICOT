// Modal de restablecimiento de contraseña de una cuenta real.
//
// Extraído de AdminPanel.tsx, que reunía las cuatro vistas, sus cuatro modales y
// sus piezas compartidas en un solo archivo de 913 líneas — el mismo patrón que
// ya se aplicó a SupervisorPanel.tsx (ver components/supervisor/).

import { useState } from 'react'
import { Field, Modal } from '@/components/ui'
import { IconCheckCircle } from '@/components/icons'
import { actualizarUsuario, enviarCredenciales } from '@/services/usuarioService'
import { ApiError } from '@/services/api/client'
import { mapUser, randomPassword, type UserRow } from './tipos'

/**
 * Asigna una contraseña temporal nueva a un usuario y se la envía por correo.
 *
 * Es una operación real: `PUT /api/usuarios/{id}` con `password` reemplaza la
 * contraseña codificada en el servidor, y después se reutiliza el mismo envío
 * de credenciales que usa la creación de usuarios. La contraseña se muestra en
 * pantalla porque el correo puede fallar (SMTP sin configurar) y el
 * administrador necesita poder entregarla a mano — el resultado del envío se
 * informa tal como llegó, sin fingir éxito.
 */
export function ResetPasswordModal({ usuario, onClose, onActualizado }: {
  usuario: UserRow
  onClose: () => void
  onActualizado: (u: UserRow) => void
}) {
  const [pw] = useState(randomPassword())
  // El backend exige teléfono al actualizar; si la cuenta todavía no lo tiene,
  // hay que capturarlo aquí en vez de inventar uno.
  const [tel, setTel] = useState(usuario.telefonoApi ?? '')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [listo, setListo] = useState(false)
  const [envioResultado, setEnvioResultado] = useState<{ enviado: boolean; error: string | null } | null>(null)
  const [enviando, setEnviando] = useState(false)

  const restablecer = async () => {
    if (busy) return
    if (!tel.trim()) { setError('El número de teléfono es obligatorio para guardar los datos del usuario.'); return }
    setError('')
    setBusy(true)
    try {
      const actualizado = await actualizarUsuario(Number(usuario.id), {
        nombre: usuario.nombre,
        email: usuario.correo,
        password: pw,
        telefono: tel.trim(),
        rol: usuario.rolApi,
      })
      onActualizado(mapUser(actualizado))
      setListo(true)
      setBusy(false)

      setEnviando(true)
      try {
        setEnvioResultado(await enviarCredenciales(actualizado.id, { password: pw }))
      } catch {
        setEnvioResultado({ enviado: false, error: 'No se pudo contactar al servidor.' })
      }
      setEnviando(false)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se pudo restablecer la contraseña.')
      setBusy(false)
    }
  }

  if (listo) {
    return (
      <Modal title="Contraseña restablecida" onClose={onClose} width={460}>
        <div style={{ textAlign: 'center', padding: '10px 0 4px' }}>
          <IconCheckCircle size={40} style={{ color: 'var(--accent)', margin: '0 auto 8px' }} />
          <p style={{ fontSize: 14, color: 'var(--accent)', fontWeight: 600, margin: '8px 0 4px' }}>
            La contraseña de {usuario.nombre} fue reemplazada
          </p>
          <p style={{ fontSize: 13, color: 'var(--text-secondary)', margin: 0 }}>{usuario.correo}</p>

          <div style={{
            marginTop: 14, padding: '10px 14px', borderRadius: 8, fontSize: 12.5, textAlign: 'left',
            background: enviando ? 'var(--bg-surface)' : envioResultado?.enviado ? 'var(--accent-soft)' : 'var(--chip-red-bg)',
            border: `1px solid ${enviando ? 'var(--border)' : envioResultado?.enviado ? 'var(--accent-line)' : 'var(--chip-red)'}`,
          }}>
            {enviando && 'Enviando correo…'}
            {!enviando && envioResultado?.enviado && `Correo enviado a ${usuario.correo}.`}
            {!enviando && envioResultado && !envioResultado.enviado && (
              <>No fue posible enviar el correo: {envioResultado.error}. Copie la contraseña y entréguela de forma manual.</>
            )}
          </div>

          <div style={{ marginTop: 14, padding: '10px 14px', background: 'var(--bg-surface)', borderRadius: 8, fontSize: 12, color: 'var(--text-secondary)', textAlign: 'left' }}>
            Contraseña temporal: <span style={{ fontFamily: 'var(--font-mono)' }}>{pw}</span>
          </div>

          <button className="btn-green" style={{ width: '100%', padding: '10px 0', fontSize: 13, marginTop: 16 }} onClick={onClose}>
            Aceptar
          </button>
        </div>
      </Modal>
    )
  }

  return (
    <Modal title="Restablecer contraseña" onClose={onClose} width={460}>
      <p style={{ fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.6, margin: '0 0 14px' }}>
        Se le asignará una contraseña temporal nueva a <strong style={{ color: 'var(--text-primary)' }}>{usuario.nombre}</strong>{' '}
        ({usuario.correo}) y se enviará a su correo institucional. La contraseña anterior deja de servir de inmediato.
      </p>

      <Field label="Teléfono de contacto">
        <input type="text" value={tel} onChange={e => setTel(e.target.value)}
          placeholder="Número de contacto del usuario" style={{ width: '100%', padding: '9px 10px' }} />
      </Field>

      <div style={{ padding: '10px 14px', background: 'var(--bg-surface)', borderRadius: 8, fontSize: 12, color: 'var(--text-secondary)' }}>
        Contraseña temporal que se asignará: <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--text-primary)' }}>{pw}</span>
      </div>

      {error && <p style={{ color: 'var(--alert-critica)', fontSize: 12, margin: '10px 0 0' }}>{error}</p>}

      <div style={{ display: 'flex', gap: 10, marginTop: 18 }}>
        <button className="btn-ghost" style={{ flex: 1, padding: '10px 0', fontSize: 13 }} onClick={onClose} disabled={busy}>Cancelar</button>
        <button className="btn-green" style={{ flex: 2, padding: '10px 0', fontSize: 13, opacity: busy ? 0.6 : 1, cursor: busy ? 'default' : 'pointer' }}
          onClick={restablecer} disabled={busy}>
          {busy ? 'Restableciendo…' : 'Restablecer y enviar'}
        </button>
      </div>
    </Modal>
  )
}
