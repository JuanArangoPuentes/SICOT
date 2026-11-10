// La franja que cuenta que SICOT se está actualizando, o que hay versión nueva
// que bajar (ADR-017).
//
// <h2>Por qué se ve, en vez de pasar en silencio</h2>
// La actualización de escritorio ocurre sola, y podría no decir nada. Se dice
// por dos razones concretas: la descarga consume la red del Centro mientras el
// supervisor trabaja, y en Windows el instalador **cierra SICOT** para
// reemplazar los archivos. Una aplicación que se cierra sin aviso mientras
// alguien redacta una observación es un fallo, aunque técnicamente sea una
// mejora. Con la franja, lo que ocurre tiene nombre antes de ocurrir.
//
// En el teléfono el mensaje es otro —Android no deja que una aplicación
// instale otra sin que su dueño lo autorice— y dice la verdad: hay versión
// nueva y se baja de esta dirección.

import { useEffect, useState } from 'react'
import {
  buscarActualizacion,
  PAGINA_DE_DESCARGAS,
  reiniciarParaTerminar,
  type EstadoDeActualizacion,
} from '@/services/autoactualizacion'
import { EnlaceExterno } from './supervisor/enlacesExternos'

const FRANJA: React.CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 8,
  flexWrap: 'wrap',
  padding: '8px 22px',
  flexShrink: 0,
  fontSize: 12.5,
  color: 'var(--text-primary)',
  background: 'var(--chip-green-bg, var(--surface-2))',
  borderBottom: '1px solid var(--accent)',
}

export function AvisoDeVersionNueva() {
  const [estado, setEstado] = useState<EstadoDeActualizacion>({ fase: 'al-dia' })
  const [reiniciando, setReiniciando] = useState(false)

  useEffect(() => {
    // Una sola vez al abrir la aplicación. No se repite cada cierto tiempo: una
    // instalación se usa unas horas al día, y comprobar al abrir alcanza para
    // que nadie se quede más de una jornada atrás. Repetirlo mientras alguien
    // trabaja solo añadiría la posibilidad de cerrarle la aplicación encima.
    let vigente = true
    void buscarActualizacion((nuevo) => {
      if (vigente) setEstado(nuevo)
    })
    return () => {
      vigente = false
    }
  }, [])

  if (estado.fase === 'al-dia') return null

  return (
    <div role="status" style={FRANJA}>
      {estado.fase === 'descargando' && (
        <>
          <strong style={{ color: 'var(--accent)' }}>Actualizando SICOT a la versión {estado.version}.</strong>
          <span>
            {estado.porcentaje === null ? 'Descargando…' : `Descargado ${estado.porcentaje} %.`} Puede seguir
            trabajando; al terminar, SICOT se cierra un momento para quedar en la versión nueva.
          </span>
        </>
      )}

      {estado.fase === 'instalada' && (
        <>
          <strong style={{ color: 'var(--accent)' }}>SICOT {estado.version} quedó instalado.</strong>
          <span>Falta reiniciar para empezar a usarlo.</span>
          <button
            type="button"
            disabled={reiniciando}
            onClick={() => {
              setReiniciando(true)
              void reiniciarParaTerminar()
            }}
            style={{
              padding: '4px 12px',
              fontSize: 11,
              cursor: reiniciando ? 'progress' : 'pointer',
              background: 'transparent',
              border: '1px solid var(--accent)',
              borderRadius: 4,
              color: 'var(--accent)',
            }}
          >
            {reiniciando ? 'Reiniciando…' : 'Reiniciar ahora'}
          </button>
        </>
      )}

      {estado.fase === 'hay-que-bajarla' && (
        <>
          <strong style={{ color: 'var(--accent)' }}>Hay una versión nueva de SICOT: la {estado.version}.</strong>
          <span>
            En el teléfono la instalación la autoriza usted, así que hay que descargarla e instalarla encima de esta. Se
            baja de aquí: <EnlaceExterno href={PAGINA_DE_DESCARGAS}>{PAGINA_DE_DESCARGAS}</EnlaceExterno>
          </span>
        </>
      )}

      {estado.fase === 'fallo' && (
        <>
          <strong style={{ color: 'var(--alert-leve)' }}>
            No se pudo actualizar{estado.version === null ? '' : ` a la versión ${estado.version}`}.
          </strong>
          <span>
            Sigue funcionando la versión instalada. Si la actualización hace falta, el instalador está en{' '}
            <EnlaceExterno href={PAGINA_DE_DESCARGAS}>{PAGINA_DE_DESCARGAS}</EnlaceExterno>
          </span>
        </>
      )}
    </div>
  )
}
