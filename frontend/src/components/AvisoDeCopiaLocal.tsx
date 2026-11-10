// La franja que aparece cuando lo que se está viendo no vino del servidor sino
// de la copia guardada en este equipo.
//
// <h2>Por qué existe, y por qué dice tanto</h2>
// Una copia local sin aviso es peor que no tener copia. El supervisor miraría
// un expediente de hace horas creyendo que es el de ahora, cerraría un paso
// sobre esa base y nadie le habría dicho nada. El aviso dice tres cosas, y las
// tres hacen falta: que no hay conexión, **de cuándo** es lo que ve, y qué
// puede hacer así. Desde la cola de cambios (ADR-015, punto 3) eso es casi
// todo: lo que cambie se guarda en este equipo y sale al volver la red. Lo que
// no puede es cargar, generar ni firmar documentos, y se dice.

import { useEffect, useState } from 'react'
import { alCambiarLaCopia, estadoDeLaCopia, type EstadoDeLaCopia } from '@/services/copiaLocal'
import { venceElAccesoSinRed } from '@/services/accesoSinRed'
import { leerSesionGuardada } from '@/services/session'

/** «hoy a las 14:32», «ayer a las 9:05», «el 24/10/2026 a las 16:40». */
export function cuandoSeGuardo(cuando: number, ahora: number = Date.now()): string {
  const fecha = new Date(cuando)
  const hora = fecha.toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' })
  const dia = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
  const diasDeDiferencia = Math.round((dia(new Date(ahora)) - dia(fecha)) / 86_400_000)
  if (diasDeDiferencia === 0) return `hoy a las ${hora}`
  if (diasDeDiferencia === 1) return `ayer a las ${hora}`
  return `el ${fecha.toLocaleDateString('es-CO')} a las ${hora}`
}

export function AvisoDeCopiaLocal() {
  const [estado, setEstado] = useState<EstadoDeLaCopia>(estadoDeLaCopia)

  useEffect(() => alCambiarLaCopia(setEstado), [])

  if (!estado.sirviendoCopia || estado.cuando === null) return null

  return (
    <div
      role="status"
      style={{
        display: 'flex',
        alignItems: 'baseline',
        gap: 8,
        flexWrap: 'wrap',
        padding: '8px 22px',
        flexShrink: 0,
        fontSize: 12.5,
        color: 'var(--text-primary)',
        background: 'var(--chip-red-bg)',
        borderBottom: '1px solid var(--alert-leve)',
      }}
    >
      <strong style={{ color: 'var(--alert-leve)' }}>Sin conexión con el servidor.</strong>
      <span>
        {conPunto(`Lo que ve es la copia guardada en este equipo ${cuandoSeGuardo(estado.cuando)}`)} Puede haber
        cambiado desde entonces. Lo que cierre o registre se guarda aquí y se envía cuando vuelva la red; cargar,
        generar y firmar documentos necesitan conexión.
      </span>
      <SesionSinConexion />
    </div>
  )
}

/**
 * Cierra la frase sin duplicar el punto: la hora en es-CO termina en «p. m.»,
 * y añadirle otro dejaba «02:18 p. m..» en pantalla.
 */
function conPunto(frase: string): string {
  return frase.endsWith('.') ? frase : `${frase}.`
}

/**
 * Si la sesión se abrió sin red, lo dice y hasta cuándo vale: pasada la
 * ventana hay que conectarse para volver a entrar (ADR-015, punto 4).
 */
function SesionSinConexion() {
  const sesion = leerSesionGuardada()
  if (!sesion?.sinConexion) return null
  const vence = venceElAccesoSinRed(sesion.usuarioId)
  const hasta = vence
    ? `; vale hasta el ${new Date(vence).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' })}`
    : ''
  return (
    <span style={{ color: 'var(--text-secondary)' }}>
      {conPunto(`Entró sin conexión con la contraseña recordada en este equipo${hasta}`)}
    </span>
  )
}
