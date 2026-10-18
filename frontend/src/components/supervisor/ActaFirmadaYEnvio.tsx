// Paso 2.1 del proceso real: firma y devolución del Acta de Inicio.
//
// Según el proceso que documentó el supervisor el 15-10-2026, el acta no la
// genera SICOT: la elabora Contratación y llega por correo; el supervisor la
// firma y la devuelve respondiendo ese correo. El sistema «queda esperando»
// hasta tener dos cosas: el acta firmada y la evidencia de que se devolvió.
// Aquí se cargan las dos, y el backend no deja cerrar el paso sin ellas.

import { useRef, useState } from 'react'
import { subirDocumento } from '@/services/documentoService'
import { ApiError } from '@/services/api/client'
import { formatBytes } from '@/services/format'
import type { DocumentoResponse } from '@/services/api/types'
import { BOTON } from './enlacesExternos'

export const NOMBRE_ACTA = 'Acta de Inicio firmada'
export const NOMBRE_EVIDENCIA = 'Evidencia del envío del acta'

interface Props {
  contratoId: number
  subetapaApiId?: number | null
  /** Documentos del contrato; de aquí salen los ya cargados en este paso. */
  documentos: DocumentoResponse[]
  onCargado: () => void
}

type Pieza = 'acta' | 'evidencia'

/**
 * Qué hay cargado en el paso, por el nombre con que lo guarda este mismo
 * componente. El acta es el PDF con su nombre (o, si no hay ninguno así, un PDF
 * que no sea la evidencia); la evidencia, el documento con su nombre o
 * cualquier otro que no sea el acta. Es la regla del backend: un PDF que no sea
 * la evidencia y dos documentos.
 *
 * Hasta el 18-10-2026 el acta era «el primer PDF»: si se cargaba primero la
 * evidencia en PDF, la pantalla la daba por el acta firmada y dejaba cerrar el
 * paso sin ella.
 */
export function piezasCargadas(documentos: DocumentoResponse[], subetapaApiId?: number | null) {
  const delPaso = documentos.filter((d) => subetapaApiId != null && d.subetapaId === subetapaApiId)
  const esEvidencia = (d: DocumentoResponse) => d.nombre.startsWith(NOMBRE_EVIDENCIA)
  const pdfs = delPaso.filter((d) => d.tipo === 'PDF')
  const acta = pdfs.find((d) => d.nombre.startsWith(NOMBRE_ACTA)) ?? pdfs.find((d) => !esEvidencia(d))
  const evidencia =
    delPaso.find((d) => esEvidencia(d)) ?? delPaso.find((d) => d !== acta && !d.nombre.startsWith(NOMBRE_ACTA))
  return { acta, evidencia }
}

export default function ActaFirmadaYEnvio({ contratoId, subetapaApiId, documentos, onCargado }: Props) {
  const [cargando, setCargando] = useState<Pieza | null>(null)
  const [error, setError] = useState<string | null>(null)
  const campoActa = useRef<HTMLInputElement>(null)
  const campoEvidencia = useRef<HTMLInputElement>(null)
  const { acta, evidencia } = piezasCargadas(documentos, subetapaApiId)

  const cargar = async (pieza: Pieza, archivo: File | undefined) => {
    if (!archivo) return
    if (pieza === 'acta' && archivo.type !== 'application/pdf' && !archivo.name.toLowerCase().endsWith('.pdf')) {
      setError('El acta firmada tiene que ser el PDF que le envió Contratación, firmado.')
      return
    }
    setError(null)
    setCargando(pieza)
    try {
      await subirDocumento(contratoId, archivo, {
        nombre: pieza === 'acta' ? NOMBRE_ACTA : NOMBRE_EVIDENCIA,
        subetapaId: subetapaApiId ?? undefined,
      })
      onCargado()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se pudo cargar el archivo. Intente de nuevo.')
    } finally {
      setCargando(null)
    }
  }

  const fila = (pieza: Pieza, titulo: string, ayuda: string, cargado: DocumentoResponse | undefined) => (
    <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
      <span aria-hidden style={{ width: 14, color: cargado ? 'var(--accent)' : 'var(--text-muted)', fontWeight: 700 }}>
        {cargado ? '✓' : '○'}
      </span>
      <span style={{ fontSize: 12, color: 'var(--text-primary)', minWidth: 210 }}>{titulo}</span>
      {cargado ? (
        <span style={{ fontSize: 11.5, color: 'var(--text-secondary)' }}>
          cargada{cargado.tamanioBytes != null ? ` · ${formatBytes(cargado.tamanioBytes)}` : ''}
          {cargado.subidoPorNombre ? ` · ${cargado.subidoPorNombre}` : ''}
        </span>
      ) : (
        <>
          <button
            type="button"
            className="btn-green"
            style={{ ...BOTON, opacity: cargando ? 0.6 : 1 }}
            disabled={cargando !== null}
            onClick={() => (pieza === 'acta' ? campoActa : campoEvidencia).current?.click()}
          >
            {cargando === pieza ? 'Cargando…' : 'Cargar'}
          </button>
          <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>{ayuda}</span>
        </>
      )}
    </div>
  )

  return (
    <div style={{ paddingLeft: 26, display: 'flex', flexDirection: 'column', gap: 6, marginTop: 6 }}>
      <span style={{ fontSize: 11.5, color: 'var(--text-secondary)' }}>
        Contratación le envía el Acta de Inicio por correo. Fírmela, devuélvala respondiendo ese mismo correo y cargue
        aquí las dos cosas:
      </span>
      <input
        ref={campoActa}
        type="file"
        accept="application/pdf"
        style={{ display: 'none' }}
        onChange={(e) => {
          void cargar('acta', e.target.files?.[0])
          e.target.value = ''
        }}
      />
      <input
        ref={campoEvidencia}
        type="file"
        accept="application/pdf,image/jpeg,image/png"
        style={{ display: 'none' }}
        onChange={(e) => {
          void cargar('evidencia', e.target.files?.[0])
          e.target.value = ''
        }}
      />
      {fila('acta', 'Acta de Inicio firmada', 'PDF', acta)}
      {fila('evidencia', 'Evidencia de que la devolvió', 'pantallazo o PDF del correo enviado', evidencia)}
      {acta && evidencia ? (
        <span style={{ fontSize: 11.5, color: 'var(--accent)' }}>Listo: ya puede marcar el paso como completado.</span>
      ) : (
        <span style={{ fontSize: 11.5, color: 'var(--text-muted)' }}>
          El paso no se puede cerrar hasta cargar{' '}
          {!acta && !evidencia ? 'las dos' : acta ? 'la evidencia del envío' : 'el acta firmada'}.
        </span>
      )}
      {!subetapaApiId && (
        <span style={{ fontSize: 11.5, color: 'var(--alert-leve)' }}>
          Este paso todavía no existe en el servidor; los archivos quedarían en el contrato sin asociarse al paso.
        </span>
      )}
      {error && <span style={{ fontSize: 11.5, color: 'var(--alert-critica)' }}>{error}</span>}
    </div>
  )
}
