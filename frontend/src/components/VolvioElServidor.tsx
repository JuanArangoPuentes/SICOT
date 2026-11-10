// El aviso de que el servidor volvió, en una sesión abierta sin red. Ver
// `services/vueltaDelServidor.ts`: sin él, la sesión sin conexión no se
// enteraba nunca de que ya había red.

import { useEffect, useState } from 'react'
import { alVolverElServidor, servidorDeVuelta } from '@/services/vueltaDelServidor'

export function VolvioElServidor({ onEntrarDeNuevo }: { onEntrarDeNuevo: () => void }) {
  const [deVuelta, setDeVuelta] = useState(servidorDeVuelta)

  useEffect(() => alVolverElServidor(setDeVuelta), [])

  if (!deVuelta) return null

  return (
    <div
      role="status"
      style={{
        display: 'flex',
        alignItems: 'baseline',
        gap: 10,
        flexWrap: 'wrap',
        padding: '8px 22px',
        flexShrink: 0,
        fontSize: 12.5,
        color: 'var(--text-primary)',
        background: 'var(--accent-soft)',
        borderBottom: '1px solid var(--accent-line)',
      }}
    >
      <strong style={{ color: 'var(--accent)' }}>Volvió la conexión con el servidor.</strong>
      <span>
        Entre de nuevo con su contraseña para enviar lo que hizo sin red y ver el expediente al día. Los cambios que
        hizo sin red no se pierden al salir.
      </span>
      <button
        type="button"
        className="btn-green"
        onClick={onEntrarDeNuevo}
        style={{ fontSize: 12, padding: '3px 12px', borderRadius: 4 }}
      >
        Entrar de nuevo
      </button>
    </div>
  )
}
