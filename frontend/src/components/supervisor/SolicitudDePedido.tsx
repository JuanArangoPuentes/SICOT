// Paso 2.4 del proceso real: la solicitud de pedido que se adjunta al correo.
//
// Alex Zapata completó este paso el 20-10-2026: el correo al contratista debe
// llevar «un formato con el listado de partes y suministros a recibir, […] un
// documento de excel adjunto en el correo». Hasta ahora el borrador de correo
// dejaba un hueco entre corchetes donde debían ir las cantidades, y el
// supervisor lo llenaba a mano en cada correo.
//
// Los bienes se pegan de la oferta económica adjudicada, que el supervisor ya
// tiene abierta: SICOT no puede traerlos —el expediente del SECOP II no es
// alcanzable desde un programa— y capturar 35 ítems con fichas técnicas de 900
// caracteres no lo haría nadie dos veces. Una vez guardados, el Excel se
// genera cuantas veces haga falta.

import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  descargarSolicitudDePedido,
  guardarSolicitudDePedido,
  obtenerSolicitudDePedido,
} from '@/services/contratoService'
import { ApiError } from '@/services/api/client'
import { formatCOP } from '@/services/format'
import type { ContratoResponse, SolicitudDePedidoResponse } from '@/services/api/types'
import { leerPegado, type ItemPegado } from './pegarSolicitudDePedido'
import { BOTON, BOTON_SECUNDARIO } from './enlacesExternos'

const CAJA: React.CSSProperties = {
  paddingLeft: 26,
  display: 'flex',
  flexDirection: 'column',
  gap: 8,
  marginTop: 6,
}
const NOTA: React.CSSProperties = { fontSize: 11.5, color: 'var(--text-muted)' }
const CELDA: React.CSSProperties = {
  padding: '4px 6px',
  borderBottom: '1px solid var(--border)',
  fontSize: 11.5,
  color: 'var(--text-primary)',
  verticalAlign: 'top',
}

const EJEMPLO_DE_PEGADO =
  '1\tMouse Óptico Alámbrico 1000 DPI\t83\t$ 29,462.51\n2\tUnidad de Estado Sólido 500 GB\t20\t$ 183,122.52'

export default function SolicitudDePedido({ contrato }: { contrato: ContratoResponse }) {
  const [solicitud, setSolicitud] = useState<SolicitudDePedidoResponse | null>(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [pegando, setPegando] = useState(false)
  const [texto, setTexto] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [aviso, setAviso] = useState<string | null>(null)

  const cargar = useCallback(() => {
    setCargando(true)
    obtenerSolicitudDePedido(contrato.id)
      .then((s) => {
        setSolicitud(s)
        setError(null)
      })
      .catch((e) => setError(e instanceof ApiError ? e.message : 'No se pudo consultar la solicitud de pedido.'))
      .finally(() => setCargando(false))
  }, [contrato.id])

  useEffect(() => {
    cargar()
  }, [cargar])

  // La lectura del pegado se hace mientras se escribe, para que el supervisor
  // vea cuántas filas reconoció SICOT antes de guardar nada.
  const leido = useMemo(() => (texto.trim() === '' ? null : leerPegado(texto)), [texto])

  const guardar = (items: ItemPegado[]) => {
    setGuardando(true)
    setAviso(null)
    guardarSolicitudDePedido(contrato.id, items)
      .then((s) => {
        setSolicitud(s)
        setPegando(false)
        setTexto('')
        setError(null)
      })
      .catch((e) => setError(e instanceof ApiError ? e.message : 'No se pudo guardar la solicitud de pedido.'))
      .finally(() => setGuardando(false))
  }

  // `guardarArchivo` devuelve dónde acabó el archivo. Solo se dice algo cuando
  // el teléfono lo escribió de verdad: en el navegador la descarga ya se ve, y
  // si el usuario canceló el «Guardar como» sabe de sobra que no lo guardó.
  const descargar = () => {
    setAviso(null)
    descargarSolicitudDePedido(contrato.id, contrato.numeroContrato)
      .then((r) => setAviso(r === 'guardado' ? 'El archivo quedó guardado. Adjúntelo al correo.' : null))
      .catch((e) => setError(e instanceof ApiError ? e.message : 'No se pudo generar el archivo.'))
  }

  if (cargando) {
    return (
      <div style={CAJA}>
        <span style={NOTA}>Consultando la solicitud de pedido…</span>
      </div>
    )
  }

  const items = solicitud?.items ?? []

  return (
    <div style={CAJA}>
      <span style={{ ...NOTA, color: 'var(--text-primary)', fontWeight: 600 }}>
        Solicitud de pedido (el Excel que se adjunta al correo)
      </span>

      {error && <span style={{ fontSize: 11.5, color: 'var(--alert-critica)' }}>{error}</span>}
      {aviso && <span style={{ fontSize: 11.5, color: 'var(--accent)' }}>{aviso}</span>}

      {items.length === 0 && !pegando && (
        <span style={NOTA}>
          Todavía no ha cargado los bienes. Abra la oferta económica del proceso en el SECOP II (el enlace está en el
          paso 2.3), copie las columnas de ítem, descripción, cantidad y valor unitario, y péguelas aquí.
        </span>
      )}

      {items.length > 0 && (
        <>
          <div style={{ maxHeight: 260, overflowY: 'auto', border: '1px solid var(--border)', borderRadius: 4 }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr>
                  {['Ítem', 'Descripción', 'Cantidad', 'Valor unitario', 'Valor total'].map((t) => (
                    <th
                      key={t}
                      style={{
                        ...CELDA,
                        position: 'sticky',
                        top: 0,
                        background: 'var(--bg-card)',
                        textAlign: t === 'Descripción' ? 'left' : 'right',
                        fontWeight: 600,
                        color: 'var(--text-muted)',
                      }}
                    >
                      {t}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {items.map((i) => (
                  <tr key={i.numero}>
                    <td style={{ ...CELDA, textAlign: 'right' }}>{i.numero}</td>
                    <td style={CELDA}>{i.descripcion}</td>
                    <td style={{ ...CELDA, textAlign: 'right' }}>{i.cantidad}</td>
                    <td style={{ ...CELDA, textAlign: 'right' }}>{formatCOP(i.valorUnitario)}</td>
                    <td style={{ ...CELDA, textAlign: 'right' }}>{formatCOP(i.valorTotal)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* El bloque de presupuesto del formato real: es lo que evita pedir
              más de lo que el contrato permite, y se ve al armar el pedido y no
              cuando llega la factura. */}
          <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', fontSize: 11.5 }}>
            <span style={NOTA}>
              Asignado:{' '}
              <strong style={{ color: 'var(--text-primary)' }}>{formatCOP(solicitud!.presupuestoAsignado)}</strong>
            </span>
            <span style={NOTA}>
              Comprometido:{' '}
              <strong style={{ color: 'var(--text-primary)' }}>{formatCOP(solicitud!.comprometido)}</strong>
            </span>
            <span style={NOTA}>
              Queda:{' '}
              <strong style={{ color: solicitud!.excedeElContrato ? 'var(--alert-critica)' : 'var(--text-primary)' }}>
                {formatCOP(solicitud!.queda)}
              </strong>
            </span>
          </div>
          {solicitud!.excedeElContrato && (
            <span style={{ fontSize: 11.5, color: 'var(--alert-critica)' }}>
              La solicitud excede el valor del contrato. Revise las cantidades, o el valor registrado del contrato si es
              ese el que no cuadra.
            </span>
          )}
        </>
      )}

      {pegando && (
        <>
          <textarea
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
            rows={6}
            aria-label="Bienes pegados de la oferta económica"
            placeholder={EJEMPLO_DE_PEGADO}
            style={{
              fontFamily: 'var(--font-ui)',
              fontSize: 11.5,
              padding: 8,
              borderRadius: 4,
              border: '1px solid var(--border)',
              background: 'var(--bg-card)',
              color: 'var(--text-primary)',
            }}
          />
          <span style={NOTA}>
            Una fila por bien, con las columnas separadas por tabulación: número de ítem, descripción, cantidad y valor
            unitario. La columna del total, si la pega, se ignora: la calcula SICOT. El encabezado y el subtotal de la
            hoja se descartan solos.
          </span>
          {leido && (
            <span style={NOTA}>
              {leido.items.length === 0
                ? 'No se reconoció ninguna fila.'
                : `Se reconocieron ${leido.items.length} bien(es).`}
            </span>
          )}
          {leido?.descartadas.map((d) => (
            <span key={d.linea} style={{ fontSize: 11.5, color: 'var(--alert-leve)' }}>
              {d.motivo}
            </span>
          ))}
        </>
      )}

      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        {!pegando && (
          <button type="button" className="btn-green" style={BOTON} onClick={() => setPegando(true)}>
            {items.length === 0 ? 'Cargar los bienes de la oferta' : 'Reemplazar los bienes'}
          </button>
        )}
        {pegando && (
          <>
            <button
              type="button"
              className="btn-green"
              style={BOTON}
              disabled={guardando || !leido || leido.items.length === 0}
              onClick={() => leido && guardar(leido.items)}
            >
              {guardando ? 'Guardando…' : 'Guardar la solicitud'}
            </button>
            <button
              type="button"
              style={{ ...BOTON, ...BOTON_SECUNDARIO }}
              onClick={() => {
                setPegando(false)
                setTexto('')
              }}
            >
              Cancelar
            </button>
          </>
        )}
        {items.length > 0 && !pegando && (
          <>
            <button type="button" style={{ ...BOTON, ...BOTON_SECUNDARIO }} onClick={descargar}>
              Descargar el Excel para adjuntar
            </button>
            <button
              type="button"
              style={{ ...BOTON, ...BOTON_SECUNDARIO }}
              disabled={guardando}
              onClick={() => guardar([])}
            >
              Borrar la solicitud
            </button>
          </>
        )}
      </div>
    </div>
  )
}
