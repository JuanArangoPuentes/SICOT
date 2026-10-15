// Paso 2.3 del proceso real: verificar el contrato en el SECOP II.
//
// El supervisor tenía que entrar al SECOP, buscar el contrato y comparar a ojo
// cifra por cifra. El sitio no se deja leer por un programa (responde con un
// reCAPTCHA), pero la misma información se publica como datos abiertos, y el
// backend la trae (GET /api/contratos/{id}/secop). Aquí se muestra lo que no
// coincide con SICOT, los datos clave y el índice de documentos del expediente
// —la oferta económica y el estudio previo están ahí— para que la verificación,
// que sigue siendo del supervisor, no sea transcripción a ciegas.

import { useCallback, useEffect, useState } from 'react'
import { verificarEnSecop } from '@/services/contratoService'
import { ApiError } from '@/services/api/client'
import { formatBytes, formatCOP, formatFecha } from '@/services/format'
import type { VerificacionSecopResponse } from '@/services/api/types'
import { BOTON, BOTON_SECUNDARIO, EnlaceExterno } from './enlacesExternos'

type Estado =
  | { fase: 'consultando' }
  | { fase: 'lista'; datos: VerificacionSecopResponse }
  | { fase: 'error'; mensaje: string }

export default function VerificacionSecop({ contratoId }: { contratoId: number }) {
  const [estado, setEstado] = useState<Estado>({ fase: 'consultando' })

  const consultar = useCallback(() => {
    setEstado({ fase: 'consultando' })
    verificarEnSecop(contratoId)
      .then((datos) => setEstado({ fase: 'lista', datos }))
      .catch((e) =>
        setEstado({
          fase: 'error',
          mensaje: e instanceof ApiError ? e.message : 'No se pudo consultar la verificación.',
        }),
      )
  }, [contratoId])

  useEffect(() => {
    consultar()
  }, [consultar])

  const caja: React.CSSProperties = { paddingLeft: 26, display: 'flex', flexDirection: 'column', gap: 8, marginTop: 6 }

  if (estado.fase === 'consultando') {
    return (
      <div style={caja}>
        <span style={{ fontSize: 11.5, color: 'var(--text-muted)' }}>Consultando lo que publica el SECOP II…</span>
      </div>
    )
  }
  if (estado.fase === 'error') {
    return (
      <div style={caja}>
        <span style={{ fontSize: 11.5, color: 'var(--alert-critica)' }}>{estado.mensaje}</span>
        <button type="button" style={{ ...BOTON, ...BOTON_SECUNDARIO, alignSelf: 'flex-start' }} onClick={consultar}>
          Volver a consultar
        </button>
      </div>
    )
  }

  const { datos } = estado
  const f = datos.ficha
  return (
    <div style={caja}>
      {datos.enlaceProceso && <EnlaceExterno href={datos.enlaceProceso}>Abrir el proceso en el SECOP II</EnlaceExterno>}

      {!datos.disponible || !f ? (
        <span style={{ fontSize: 11.5, color: 'var(--alert-leve)' }}>{datos.motivo}</span>
      ) : (
        <>
          <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
            Publicado en los datos abiertos del SECOP II · contrato {f.estado?.toLowerCase() ?? 'sin estado'} ·
            consultado {formatFecha(datos.consultadoEn)}
          </span>

          {datos.diferencias.length === 0 ? (
            <span style={{ fontSize: 12, color: 'var(--accent)' }}>
              Lo registrado en SICOT coincide con lo publicado: valor, fecha de terminación, contratista, NIT,
              representante legal y supervisor.
            </span>
          ) : (
            <div>
              <span style={{ fontSize: 12, color: 'var(--alert-leve)' }}>
                {datos.diferencias.length === 1
                  ? 'Un dato no coincide con lo publicado:'
                  : `${datos.diferencias.length} datos no coinciden con lo publicado:`}
              </span>
              <table style={TABLA}>
                <thead>
                  <tr>
                    <th style={TH}>Dato</th>
                    <th style={TH}>En SICOT</th>
                    <th style={TH}>En el SECOP II</th>
                  </tr>
                </thead>
                <tbody>
                  {datos.diferencias.map((d) => (
                    <tr key={d.campo}>
                      <td style={TD}>{d.campo}</td>
                      <td style={TD}>{d.enSicot}</td>
                      <td style={TD}>{d.enSecop}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '2px 12px', fontSize: 11.5 }}>
            <span style={ETIQUETA}>Valor</span>
            <span>{formatCOP(f.valor)}</span>
            <span style={ETIQUETA}>Plazo</span>
            <span>{f.duracion ?? '—'}</span>
            <span style={ETIQUETA}>Firma / terminación</span>
            <span>
              {formatFecha(f.fechaFirma)} / {formatFecha(f.fechaFin)}
            </span>
            {(f.inicioLiquidacion || f.finLiquidacion) && (
              <>
                <span style={ETIQUETA}>Liquidación</span>
                <span>
                  del {formatFecha(f.inicioLiquidacion)} al {formatFecha(f.finLiquidacion)}
                </span>
              </>
            )}
            {f.obligacionAmbiental != null && (
              <>
                <span style={ETIQUETA}>Obligación ambiental</span>
                <span>{f.obligacionAmbiental ? 'Sí — tenga en cuenta al profesional ambiental (paso 2.2)' : 'No'}</span>
              </>
            )}
            {f.valorPagado != null && (
              <>
                <span style={ETIQUETA}>Pagado / pendiente</span>
                <span>
                  {formatCOP(f.valorPagado)} / {formatCOP(f.valorPendienteDePago)}
                </span>
              </>
            )}
          </div>

          {datos.documentos.length > 0 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
              <span style={{ fontSize: 11.5, color: 'var(--text-secondary)' }}>
                Documentos del expediente ({datos.documentos.length}). Las cantidades y especificaciones están en la
                oferta económica y el estudio previo:
              </span>
              {datos.documentos.map((d) => (
                <span key={`${d.nombre}-${d.url}`} style={{ fontSize: 11.5 }}>
                  {d.url ? <EnlaceExterno href={d.url}>{d.nombre}</EnlaceExterno> : d.nombre}
                  <span style={{ color: 'var(--text-muted)' }}>
                    {' '}
                    · {d.tamanoBytes != null ? formatBytes(d.tamanoBytes) : 'tamaño sin dato'}
                    {d.fechaCarga ? ` · ${formatFecha(d.fechaCarga)}` : ''}
                  </span>
                </span>
              ))}
            </div>
          )}

          {datos.garantias.length > 0 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
              <span style={{ fontSize: 11.5, color: 'var(--text-secondary)' }}>Garantías registradas</span>
              {datos.garantias.map((g) => (
                <span key={`${g.numero}-${g.valor}-${g.vigenteHasta}`} style={{ fontSize: 11.5 }}>
                  {g.aseguradora ?? 'Aseguradora sin dato'} · póliza {g.numero ?? 'sin número'} · {formatCOP(g.valor)} ·
                  vigente hasta {formatFecha(g.vigenteHasta)} · {g.estado?.toLowerCase() ?? 'sin estado'}
                </span>
              ))}
            </div>
          )}
        </>
      )}

      <button type="button" style={{ ...BOTON, ...BOTON_SECUNDARIO, alignSelf: 'flex-start' }} onClick={consultar}>
        Volver a consultar
      </button>
    </div>
  )
}

const TABLA: React.CSSProperties = { borderCollapse: 'collapse', fontSize: 11.5, marginTop: 4 }
const TH: React.CSSProperties = {
  textAlign: 'left',
  padding: '3px 10px 3px 0',
  color: 'var(--text-muted)',
  fontWeight: 600,
  borderBottom: '1px solid var(--border)',
}
const TD: React.CSSProperties = { padding: '3px 10px 3px 0', color: 'var(--text-primary)' }
const ETIQUETA: React.CSSProperties = { color: 'var(--text-muted)' }
