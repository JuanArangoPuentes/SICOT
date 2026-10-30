// Paso 2.4 del proceso real: las fechas de entrega acordadas con el contratista.
//
// La hoja de Alex Zapata (paso 5) dice que «se establecen fechas de entrega o
// la prestación del servicio, las fechas se definen de común acuerdo con el
// contratista respetando los tiempos de inicio y fin del contrato». SICOT
// armaba el correo que propone acordarlas, pero el acuerdo no quedaba en
// ninguna parte: el paso se cerraba sin una sola fecha y el correo a SST y
// ambiental prometía compartírselas sin que hubiera de dónde.
//
// Aquí se registran. El backend exige que caigan dentro del plazo, las numera
// por fecha, no deja cerrar el 2.4 sin al menos una y le recuerda cada entrega
// al supervisor tres días antes y el mismo día. Con las fechas guardadas salen
// los dos correos que siguen: la confirmación al contratista y el aviso a SST y
// ambiental.
//
// Sigue a la vista con el 2.4 cerrado: los recordatorios llegan después de
// cerrarlo y los correos prometen avisar si una fecha cambia, así que el
// acuerdo tiene que poder consultarse y corregirse (revisión del 23-10-2026).

import { useCallback, useEffect, useState } from 'react'
import {
  guardarEntregasAcordadas,
  obtenerContactosSstAmbiental,
  obtenerEntregasAcordadas,
} from '@/services/contratoService'
import { ApiError } from '@/services/api/client'
import type { ConPendiente } from '@/services/colaDeCambios'
import { formatFecha } from '@/services/format'
import type { AcuerdoDeEntregasResponse, ContactoDelContrato, ContratoResponse } from '@/services/api/types'
import BorradorDeCorreo from './BorradorDeCorreo'
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
const CAMPO: React.CSSProperties = {
  fontFamily: 'var(--font-ui)',
  fontSize: 11.5,
  padding: '4px 6px',
  borderRadius: 4,
  border: '1px solid var(--border)',
  background: 'var(--bg-card)',
  color: 'var(--text-primary)',
}

interface Fila {
  fecha: string
  descripcion: string
}

/**
 * Lo que no se puede guardar, dicho por fila. El backend lo vuelve a comprobar;
 * esto es para que el supervisor lo vea antes de pulsar y no después.
 */
export function problemasDeLasFilas(filas: Fila[], inicio: string | null, fin: string | null): string[] {
  const problemas: string[] = []
  filas.forEach((f, i) => {
    const n = i + 1
    if (!f.fecha) {
      problemas.push(`A la fila ${n} le falta la fecha.`)
    } else if ((inicio && f.fecha < inicio) || (fin && f.fecha > fin)) {
      problemas.push(
        `La fecha de la fila ${n} (${formatFecha(f.fecha)}) cae fuera del plazo del contrato` +
          (inicio && fin ? ` (del ${formatFecha(inicio)} al ${formatFecha(fin)}).` : '.'),
      )
    }
    if (!f.descripcion.trim()) {
      problemas.push(`En la fila ${n}, diga qué se entrega ese día (por ejemplo, «entrega total» o «ítems 1 a 20»).`)
    }
  })
  return problemas
}

export default function AcuerdoDeEntregas({
  contrato,
  version = 0,
  onGuardado,
}: {
  contrato: ContratoResponse
  /**
   * Sube cuando otro sub-paso guarda algo que este usa (los contactos de SST y
   * ambiental del 2.2): entonces se vuelve a pedir. Los dos están montados a
   * la vez y cada uno leía lo del otro una sola vez, al abrirse.
   */
  version?: number
  /** Para que el panel refresque lo que depende del acuerdo (la auditoría, el 2.2). */
  onGuardado?: () => void
}) {
  // null mientras no se haya podido consultar: es distinto de «no hay fechas».
  // Con la consulta fallida, guardar reemplazaría el acuerdo que sí existe.
  const [acuerdo, setAcuerdo] = useState<(AcuerdoDeEntregasResponse & ConPendiente) | null>(null)
  const [contactos, setContactos] = useState<ContactoDelContrato[]>([])
  const [error, setError] = useState<string | null>(null)
  const [filas, setFilas] = useState<Fila[] | null>(null)
  const [guardando, setGuardando] = useState(false)

  const cargar = useCallback(() => {
    obtenerEntregasAcordadas(contrato.id)
      .then((a) => {
        setAcuerdo(a)
        setError(null)
      })
      .catch((e) => setError(e instanceof ApiError ? e.message : 'No se pudo consultar el acuerdo de entregas.'))
    // Los contactos solo hacen falta para el correo a SST y ambiental: si no
    // llegan, el borrador lo dice, y el acuerdo de entregas sigue funcionando.
    obtenerContactosSstAmbiental(contrato.id)
      .then((r) => setContactos(r.contactos))
      .catch(() => setContactos([]))
  }, [contrato.id])

  useEffect(() => {
    cargar()
  }, [cargar, version])

  const inicio = acuerdo?.inicioDelContrato ?? contrato.fechaInicio ?? null
  const fin = acuerdo?.finDelContrato ?? contrato.fechaFin ?? null
  const entregas = acuerdo?.entregas ?? []
  const problemas = filas ? problemasDeLasFilas(filas, inicio, fin) : []

  const editar = () => {
    setError(null)
    setFilas(
      entregas.length > 0
        ? entregas.map((e) => ({ fecha: e.fecha, descripcion: e.descripcion }))
        : [{ fecha: '', descripcion: '' }],
    )
  }

  const guardar = (lista: Fila[]) => {
    setGuardando(true)
    setError(null)
    guardarEntregasAcordadas(
      contrato.id,
      lista.map((f) => ({ fecha: f.fecha, descripcion: f.descripcion.trim() })),
    )
      .then((a) => {
        setAcuerdo(a)
        setFilas(null)
        onGuardado?.()
      })
      .catch((e) => setError(e instanceof ApiError ? e.message : 'No se pudo guardar el acuerdo de entregas.'))
      .finally(() => setGuardando(false))
  }

  const cambiarFila = (i: number, cambio: Partial<Fila>) =>
    setFilas((actual) => (actual ? actual.map((f, j) => (j === i ? { ...f, ...cambio } : f)) : actual))

  if (!acuerdo) {
    return (
      <div style={CAJA}>
        {error ? (
          <>
            <span style={{ fontSize: 11.5, color: 'var(--alert-critica)' }}>{error}</span>
            <button type="button" style={{ ...BOTON, ...BOTON_SECUNDARIO, alignSelf: 'flex-start' }} onClick={cargar}>
              Volver a consultar
            </button>
          </>
        ) : (
          <span style={NOTA}>Consultando las fechas de entrega acordadas…</span>
        )}
      </div>
    )
  }

  return (
    <>
      <div style={CAJA}>
        <span style={{ ...NOTA, color: 'var(--text-primary)', fontWeight: 600 }}>Fechas de entrega acordadas</span>
        <span style={NOTA}>
          Las que acuerde con el contratista, dentro del plazo del contrato
          {inicio && fin ? ` (del ${formatFecha(inicio)} al ${formatFecha(fin)})` : ''}. SICOT se las recuerda tres días
          antes y el mismo día, y el paso no se cierra sin al menos una.
        </span>
        {/* Los recordatorios solo corren sobre contratos activos: prometerlos
            con el contrato en borrador sería decir algo que no va a pasar. */}
        {contrato.estado !== 'ACTIVO' && (
          <span style={{ fontSize: 11.5, color: 'var(--alert-leve)' }}>
            El contrato no está activo: SICOT empieza a recordar las entregas cuando Gestión lo active.
          </span>
        )}

        {error && <span style={{ fontSize: 11.5, color: 'var(--alert-critica)' }}>{error}</span>}
        {acuerdo.pendienteDeEnviar && !filas && (
          <span style={{ fontSize: 11.5, color: 'var(--alert-leve)' }}>
            Guardadas en este equipo sin conexión: se envían al servidor cuando vuelva la red.
          </span>
        )}

        {entregas.length === 0 && !filas && (
          <span style={NOTA}>Todavía no hay fechas registradas. Regístrelas cuando el contratista le responda.</span>
        )}

        {entregas.length > 0 && !filas && (
          <div style={{ border: '1px solid var(--border)', borderRadius: 4 }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr>
                  {['N.º', 'Fecha', 'Qué se entrega'].map((t) => (
                    <th
                      key={t}
                      style={{
                        ...CELDA,
                        background: 'var(--bg-card)',
                        textAlign: t === 'Qué se entrega' ? 'left' : 'right',
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
                {entregas.map((e) => (
                  <tr key={e.numero}>
                    <td style={{ ...CELDA, textAlign: 'right' }}>{e.numero}</td>
                    <td style={{ ...CELDA, textAlign: 'right', whiteSpace: 'nowrap' }}>{formatFecha(e.fecha)}</td>
                    <td style={CELDA}>{e.descripcion}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {filas && (
          <>
            {filas.map((f, i) => (
              <div key={i} style={{ display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
                <input
                  type="date"
                  aria-label={`Fecha de la entrega ${i + 1}`}
                  value={f.fecha}
                  min={inicio ?? undefined}
                  max={fin ?? undefined}
                  onChange={(e) => cambiarFila(i, { fecha: e.target.value })}
                  style={CAMPO}
                />
                <input
                  type="text"
                  aria-label={`Qué se entrega en la entrega ${i + 1}`}
                  placeholder="Qué se entrega: entrega total, ítems 1 a 20…"
                  value={f.descripcion}
                  maxLength={2000}
                  onChange={(e) => cambiarFila(i, { descripcion: e.target.value })}
                  style={{ ...CAMPO, flex: '1 1 220px' }}
                />
                <button
                  type="button"
                  style={{ ...BOTON, ...BOTON_SECUNDARIO }}
                  onClick={() => setFilas((actual) => (actual ? actual.filter((_, j) => j !== i) : actual))}
                >
                  Quitar
                </button>
              </div>
            ))}
            {problemas.map((p) => (
              <span key={p} style={{ fontSize: 11.5, color: 'var(--alert-leve)' }}>
                {p}
              </span>
            ))}
          </>
        )}

        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {!filas && (
            <button type="button" className="btn-green" style={BOTON} onClick={editar}>
              {entregas.length === 0 ? 'Registrar las fechas acordadas' : 'Cambiar las fechas'}
            </button>
          )}
          {filas && (
            <>
              <button
                type="button"
                className="btn-green"
                style={BOTON}
                disabled={guardando || filas.length === 0 || problemas.length > 0}
                onClick={() => guardar(filas)}
              >
                {guardando ? 'Guardando…' : 'Guardar las fechas'}
              </button>
              <button
                type="button"
                style={{ ...BOTON, ...BOTON_SECUNDARIO }}
                onClick={() => setFilas((actual) => [...(actual ?? []), { fecha: '', descripcion: '' }])}
              >
                Agregar otra fecha
              </button>
              <button type="button" style={{ ...BOTON, ...BOTON_SECUNDARIO }} onClick={() => setFilas(null)}>
                Cancelar
              </button>
              {entregas.length > 0 && (
                <button
                  type="button"
                  style={{ ...BOTON, ...BOTON_SECUNDARIO }}
                  disabled={guardando}
                  onClick={() => guardar([])}
                >
                  Borrar el acuerdo
                </button>
              )}
            </>
          )}
        </div>
      </div>

      {/* Con las fechas guardadas, los dos correos que siguen al acuerdo. Van
          fuera de la caja: el borrador ya trae su propia sangría. */}
      {entregas.length > 0 && !filas && (
        <>
          <BorradorDeCorreo
            tipo="CONFIRMACION_ENTREGAS"
            contrato={contrato}
            datos={{ entregas }}
            etiqueta="Confirmar las fechas al contratista"
          />
          <BorradorDeCorreo
            tipo="ENTREGAS_SST"
            contrato={contrato}
            datos={{ entregas, contactos }}
            etiqueta="Avisar las fechas a SST y ambiental"
          />
        </>
      )}
    </>
  )
}
