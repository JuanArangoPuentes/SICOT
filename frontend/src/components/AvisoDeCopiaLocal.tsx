// La franja que aparece cuando lo que se está viendo no vino del servidor sino
// de la copia guardada en este equipo.
//
// <h2>Por qué existe, y por qué dice tanto</h2>
// Una copia local sin aviso es peor que no tener copia. El supervisor miraría
// un expediente de hace horas creyendo que es el de ahora, cerraría un paso
// sobre esa base y nadie le habría dicho nada. El aviso dice tres cosas, y las
// tres hacen falta: que no hay conexión, **de cuándo** es lo que ve, y que
// todavía no puede escribir — la copia es de lectura (ADR-015, punto 1); la
// cola de intenciones, que es lo que permitirá trabajar sin red, es el punto 3.

import { useEffect, useState } from 'react'
import { alCambiarLaCopia, estadoDeLaCopia, type EstadoDeLaCopia } from '@/services/copiaLocal'

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
        Lo que ve es la copia guardada en este equipo {cuandoSeGuardo(estado.cuando)}. Puede haber cambiado desde
        entonces, y mientras no vuelva la red no se puede cargar, generar ni firmar nada.
      </span>
    </div>
  )
}
