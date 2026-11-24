// La franja que le dice a quien está trabajando que algo del sistema no le va a
// funcionar, y que ya hay alguien avisado.
//
// <h2>Por qué esto existe</h2>
// Porque antes, cuando Ollama se caía, el supervisor se enteraba de la peor
// forma posible: pulsaba «Redactar con el asistente», esperaba, y recibía un
// error. Lo intentaba otra vez. Y otra. Nada le decía que el problema no era su
// consulta ni su conexión, ni que insistir no iba a servir de nada, ni que
// alguien ya lo estaba mirando.
//
// <h2>Dice qué SÍ se puede hacer</h2>
// El texto lo redacta el backend, al lado de lo que falla, y la regla que sigue
// es que un aviso no puede dejar a nadie creyendo que no puede trabajar. SICOT
// funciona sin el modelo de IA —los pasos, los documentos y las firmas no
// dependen de él—, así que el mensaje lo dice en vez de limitarse a anunciar la
// avería.
//
// <h2>Por qué se pregunta cada minuto y no una sola vez</h2>
// Al revés que el aviso de versión nueva, esto cambia mientras alguien trabaja:
// un servicio se cae a media jornada, y el reparador del backend lo intenta
// levantar cada dos minutos. El aviso tiene que aparecer solo cuando empieza a
// ser verdad y, sobre todo, **desaparecer solo cuando deja de serlo**. Un aviso
// que se queda puesto después de que el problema se arregló enseña a ignorar
// los avisos.

import type { CSSProperties } from 'react'
import { useEffect, useState } from 'react'
import { getServiciosCaidos, type AvisoDeServicio } from '@/services/saludService'

const CADA_MINUTO = 60_000

const FRANJA: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 8,
  flexWrap: 'wrap',
  padding: '8px 22px',
  flexShrink: 0,
  fontSize: 12.5,
  color: 'var(--text-primary)',
  background: 'var(--surface-2)',
  borderBottom: '1px solid var(--alert-leve)',
}

export function AvisoDeServicioCaido() {
  const [avisos, setAvisos] = useState<AvisoDeServicio[]>([])

  useEffect(() => {
    let vigente = true

    const preguntar = async () => {
      try {
        const nuevos = await getServiciosCaidos()
        if (vigente) setAvisos(nuevos)
      } catch {
        // Si esta consulta falla es porque no hay red o el backend no está, y
        // de las dos cosas ya avisa la franja de la copia local. Inventar aquí
        // un segundo aviso sería contarle lo mismo dos veces.
        if (vigente) setAvisos([])
      }
    }

    void preguntar()
    const reloj = window.setInterval(() => void preguntar(), CADA_MINUTO)
    return () => {
      vigente = false
      window.clearInterval(reloj)
    }
  }, [])

  if (avisos.length === 0) return null

  return (
    <>
      {avisos.map((aviso) => (
        <div key={aviso.servicio} role="status" style={FRANJA}>
          <strong style={{ color: 'var(--alert-leve)' }}>{aviso.mensaje}</strong>
          {aviso.minutos >= 1 && (
            <span>
              Lleva así {aviso.minutos === 1 ? 'un minuto' : `${aviso.minutos} minutos`}. No hace falta que avise: el
              aviso ya salió.
            </span>
          )}
        </div>
      ))}
    </>
  )
}
