// Cambiar la contraseña propia, desde el menú del usuario, en los tres roles.
//
// Antes no existía: el administrador solo podía asignar una contraseña al azar
// a otra cuenta («Resetear clave») y nadie podía elegir la suya. Al montar el
// servidor del Centro (05-11-2026) la guía pedía cambiar la del administrador
// inicial y no había dónde.

import { useState } from 'react'
import { Field, Modal } from '@/components/ui'
import { IconCheckCircle } from '@/components/icons'
import { ApiError } from '@/services/api/client'
import { cambiarMiContrasena } from '@/services/cuentaService'
import { leerSesionGuardada } from '@/services/session'

/** Las mismas reglas que aplica el servidor (CambiarContrasenaRequest), para avisar antes de enviar. */
function problemaDeLaNueva(actual: string, nueva: string, confirmacion: string): string {
  if (!actual) return 'Escriba su contraseña actual.'
  if (!nueva) return 'Escriba la contraseña nueva.'
  if (nueva.length < 8 || nueva.length > 72) return 'La contraseña debe tener entre 8 y 72 caracteres.'
  if (nueva !== nueva.trim()) return 'La contraseña no puede empezar ni terminar con espacios.'
  if (nueva === actual) return 'La contraseña nueva tiene que ser distinta de la actual.'
  if (nueva !== confirmacion) return 'La confirmación no coincide con la contraseña nueva.'
  return ''
}

function mensajeDelServidor(e: unknown): string {
  if (!(e instanceof ApiError)) return 'No se pudo contactar al servidor. Revise la conexión e intente de nuevo.'
  if (e.esDemasiadasSolicitudes) {
    const minutos = e.reintentarEnSegundos ? Math.ceil(e.reintentarEnSegundos / 60) : null
    return minutos
      ? `Demasiados intentos con una contraseña actual equivocada. Vuelva a intentarlo en ${minutos} min.`
      : e.message
  }
  return e.detail?.fieldErrors?.contrasenaNueva ?? e.detail?.fieldErrors?.contrasenaActual ?? e.message
}

export function CambiarContrasenaModal({ correo, onClose }: { correo: string; onClose: () => void }) {
  const [actual, setActual] = useState('')
  const [nueva, setNueva] = useState('')
  const [confirmacion, setConfirmacion] = useState('')
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [listo, setListo] = useState(false)

  // Una sesión abierta sin red (ADR-015) no tiene con qué presentarse al
  // servidor: el cambio no se puede hacer, y decirlo es mejor que un error.
  const sinConexion = Boolean(leerSesionGuardada()?.sinConexion)

  const cambiar = async () => {
    if (enviando) return
    const problema = problemaDeLaNueva(actual, nueva, confirmacion)
    if (problema) {
      setError(problema)
      return
    }
    setError('')
    setEnviando(true)
    try {
      await cambiarMiContrasena(correo, actual, nueva)
      setListo(true)
    } catch (e) {
      setError(mensajeDelServidor(e))
    } finally {
      setEnviando(false)
    }
  }

  // Cualquier cambio en un campo quita el aviso: si no, «la confirmación no
  // coincide» seguía en pantalla con las dos ya iguales.
  const alEscribir = (fijar: (v: string) => void) => (e: React.ChangeEvent<HTMLInputElement>) => {
    fijar(e.target.value)
    setError('')
  }

  if (listo) {
    return (
      <Modal title="Contraseña cambiada" onClose={onClose} width={420}>
        <div style={{ textAlign: 'center', padding: '6px 0 2px' }}>
          <IconCheckCircle size={40} style={{ color: 'var(--accent)', margin: '0 auto 8px' }} />
          <p style={{ fontSize: 14, color: 'var(--accent)', fontWeight: 600, margin: '8px 0 6px' }}>
            Su contraseña nueva ya está activa
          </p>
          <p style={{ fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.6, margin: 0 }}>
            Se cerraron las sesiones que tenía abiertas en otros equipos: allí tendrá que entrar con la nueva. En este
            equipo sigue trabajando sin volver a entrar.
          </p>
          <button
            className="btn-green"
            style={{ width: '100%', padding: '10px 0', fontSize: 13, marginTop: 16 }}
            onClick={onClose}
          >
            Aceptar
          </button>
        </div>
      </Modal>
    )
  }

  return (
    <Modal title="Cambiar contraseña" onClose={onClose} width={420} cerrarAlTocarFuera={false}>
      {sinConexion ? (
        <>
          <p style={{ fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.6, margin: '0 0 14px' }}>
            Entró sin conexión con el servidor, así que la contraseña no se puede cambiar ahora. Cuando vuelva a tener
            red, cierre sesión, entre de nuevo y cámbiela desde este mismo menú.
          </p>
          <button className="btn-green" style={{ width: '100%', padding: '10px 0', fontSize: 13 }} onClick={onClose}>
            Entendido
          </button>
        </>
      ) : (
        <form
          onSubmit={(e) => {
            e.preventDefault()
            void cambiar()
          }}
        >
          <p style={{ fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.6, margin: '0 0 14px' }}>
            Al cambiarla se cierran las sesiones que tenga abiertas en otros equipos.
          </p>
          {/* El correo, oculto, para que el gestor de contraseñas del navegador
              sepa de qué cuenta es la contraseña nueva. */}
          <input type="email" name="username" autoComplete="username" value={correo} readOnly hidden />
          <Field label="Contraseña actual">
            <input
              type="password"
              autoComplete="current-password"
              style={{ width: '100%', padding: '9px 10px' }}
              value={actual}
              onChange={alEscribir(setActual)}
              autoFocus
            />
          </Field>
          <Field label="Contraseña nueva (entre 8 y 72 caracteres)">
            <input
              type="password"
              autoComplete="new-password"
              style={{ width: '100%', padding: '9px 10px' }}
              value={nueva}
              onChange={alEscribir(setNueva)}
            />
          </Field>
          <Field label="Repita la contraseña nueva">
            <input
              type="password"
              autoComplete="new-password"
              style={{ width: '100%', padding: '9px 10px' }}
              value={confirmacion}
              onChange={alEscribir(setConfirmacion)}
            />
          </Field>

          {error && (
            <p role="alert" style={{ color: 'var(--alert-critica)', fontSize: 12, margin: '4px 0 0' }}>
              {error}
            </p>
          )}

          <div style={{ display: 'flex', gap: 10, marginTop: 18 }}>
            <button
              type="button"
              className="btn-ghost"
              style={{ flex: 1, padding: '10px 0', fontSize: 13 }}
              onClick={onClose}
            >
              Cancelar
            </button>
            <button
              type="submit"
              className="btn-green"
              style={{ flex: 1, padding: '10px 0', fontSize: 13 }}
              disabled={enviando}
            >
              {enviando ? 'Cambiando…' : 'Cambiar contraseña'}
            </button>
          </div>
        </form>
      )}
    </Modal>
  )
}
