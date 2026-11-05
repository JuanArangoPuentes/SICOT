// La franja de lo que el supervisor hizo sin red y todavía no llegó al
// servidor (ADR-015, puntos 3 y 5).
//
// <h2>Por qué está siempre a la vista mientras haya algo</h2>
// Con la cola aparece un estado que antes no existía: «hecho, pero todavía no
// aceptado». Si no se ve, el supervisor cierra el portátil creyendo que su
// trabajo está en el expediente. La franja dice cuántos cambios esperan, deja
// enviarlos a mano y, sobre todo, enseña los que el servidor **no** aceptó con
// el motivo, que es la parte que ADR-015 llama «la que todo el mundo
// subestima»: el trabajo sigue ahí hasta que el supervisor decide.

import { useEffect, useState } from 'react'
import { cuandoSeGuardo } from './AvisoDeCopiaLocal'
import {
  alCambiarLaCola,
  cambiosDeLaCola,
  describirCambio,
  descartarCambio,
  reenviarLaCola,
  reintentarCambio,
  type Cambio,
} from '@/services/colaDeCambios'
import { hayTokenDeSesion } from '@/services/api/client'
import type { AcuerdoDeEntregasResponse, ContactosDelContratoResponse } from '@/services/api/types'

const BOTON = {
  fontSize: 12,
  padding: '3px 10px',
  borderRadius: 4,
  border: '1px solid var(--border)',
  background: 'var(--bg-input)',
  color: 'var(--text-primary)',
  cursor: 'pointer',
} as const

/** Lo que tiene el servidor ahora, en una línea, para ponerlo al lado de lo del supervisor. */
function resumenDelServidor(cambio: Cambio): string | null {
  const otro = cambio.enElServidor
  if (!otro) return null
  if (cambio.intencion.tipo === 'entregas-acordadas') {
    const entregas = (otro as AcuerdoDeEntregasResponse).entregas ?? []
    return entregas.length === 0
      ? 'En el servidor ahora no hay fechas de entrega.'
      : `En el servidor ahora: ${entregas.map((e) => `${e.fecha} (${e.descripcion})`).join('; ')}.`
  }
  if (cambio.intencion.tipo === 'contactos-sst-ambiental') {
    const contactos = (otro as ContactosDelContratoResponse).contactos ?? []
    return contactos.length === 0
      ? 'En el servidor ahora no hay contactos.'
      : `En el servidor ahora: ${contactos.map((c) => `${c.correo} (${c.rol})`).join('; ')}.`
  }
  return null
}

function loQueQuiereElSupervisor(cambio: Cambio): string | null {
  const i = cambio.intencion
  if (i.tipo === 'entregas-acordadas') {
    return i.entregas.length === 0
      ? 'Usted las borró.'
      : `Lo suyo: ${i.entregas.map((e) => `${e.fecha} (${e.descripcion})`).join('; ')}.`
  }
  if (i.tipo === 'contactos-sst-ambiental') {
    return i.contactos.length === 0
      ? 'Usted los borró.'
      : `Lo suyo: ${i.contactos.map((c) => `${c.correo} (${c.rol})`).join('; ')}.`
  }
  return null
}

export function CambiosSinEnviar() {
  const [cambios, setCambios] = useState<readonly Cambio[]>(cambiosDeLaCola)
  const [abierta, setAbierta] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [aviso, setAviso] = useState<string | null>(null)

  useEffect(() => alCambiarLaCola(setCambios), [])

  const rechazados = cambios.filter((c) => c.estado === 'rechazado')
  const pendientes = cambios.filter((c) => c.estado === 'pendiente')

  // Un rechazo nuevo abre la lista sola: es lo único de aquí que pide una
  // decisión, y cerrado no se ve.
  useEffect(() => {
    if (rechazados.length > 0) setAbierta(true)
  }, [rechazados.length])

  if (cambios.length === 0) return null

  const enviarAhora = async () => {
    setEnviando(true)
    setAviso(null)
    try {
      const r = await reenviarLaCola()
      if (r.detenido === 'sin-red') setAviso('Sigue sin haber conexión con el servidor. Se volverá a intentar sola.')
      else if (r.detenido === 'sin-sesion')
        setAviso('Para enviarlos hay que entrar de nuevo con conexión: cierre la sesión y vuelva a entrar.')
      else if (r.detenido === 'servidor')
        setAviso('El servidor no respondió bien. Se volverá a intentar en un momento.')
    } finally {
      setEnviando(false)
    }
  }

  const titulo =
    pendientes.length > 0
      ? `${pendientes.length} cambio${pendientes.length === 1 ? '' : 's'} guardado${pendientes.length === 1 ? '' : 's'} en este equipo, pendiente${pendientes.length === 1 ? '' : 's'} de enviar al servidor.`
      : ''
  const tituloRechazados =
    rechazados.length > 0
      ? `${rechazados.length} cambio${rechazados.length === 1 ? '' : 's'} que el servidor no aceptó.`
      : ''

  return (
    <div
      role="status"
      style={{
        padding: '8px 22px',
        flexShrink: 0,
        fontSize: 12.5,
        color: 'var(--text-primary)',
        background: rechazados.length > 0 ? 'var(--chip-red-bg)' : 'var(--bg-input)',
        borderBottom: `1px solid ${rechazados.length > 0 ? 'var(--alert-critica)' : 'var(--alert-leve)'}`,
        display: 'flex',
        flexDirection: 'column',
        gap: 6,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, flexWrap: 'wrap' }}>
        {titulo && <strong style={{ color: 'var(--alert-leve)' }}>{titulo}</strong>}
        {tituloRechazados && <strong style={{ color: 'var(--alert-critica)' }}>{tituloRechazados}</strong>}
        <button type="button" style={BOTON} onClick={() => setAbierta((a) => !a)} aria-expanded={abierta}>
          {abierta ? 'Ocultar' : 'Ver cuáles'}
        </button>
        {pendientes.length > 0 && hayTokenDeSesion() && (
          <button type="button" style={BOTON} onClick={enviarAhora} disabled={enviando}>
            {enviando ? 'Enviando…' : 'Enviar ahora'}
          </button>
        )}
        {pendientes.length > 0 && !hayTokenDeSesion() && (
          <span style={{ color: 'var(--text-secondary)' }}>Se envían cuando vuelva a entrar con conexión.</span>
        )}
      </div>
      {aviso && <span style={{ color: 'var(--text-secondary)' }}>{aviso}</span>}
      {abierta && (
        <ul style={{ margin: 0, paddingLeft: 18, display: 'flex', flexDirection: 'column', gap: 6 }}>
          {cambios.map((c) => (
            <li key={c.id}>
              <span>{describirCambio(c.intencion)}</span>
              <span style={{ color: 'var(--text-muted)' }}>
                {' '}
                · hecho {cuandoSeGuardo(Date.parse(c.hechoEn))}, según la hora de este equipo
              </span>
              {c.estado === 'rechazado' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 4, marginTop: 4 }}>
                  <span style={{ color: 'var(--alert-critica)' }}>No entró: {c.motivo}</span>
                  {loQueQuiereElSupervisor(c) && <span>{loQueQuiereElSupervisor(c)}</span>}
                  {resumenDelServidor(c) && <span>{resumenDelServidor(c)}</span>}
                  <div style={{ display: 'flex', gap: 8 }}>
                    <button type="button" style={BOTON} onClick={() => void reintentarCambio(c.id)}>
                      {c.enElServidor ? 'Enviar la mía de todos modos' : 'Volver a intentar'}
                    </button>
                    <button type="button" style={BOTON} onClick={() => void descartarCambio(c.id)}>
                      Descartar el mío
                    </button>
                  </div>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
