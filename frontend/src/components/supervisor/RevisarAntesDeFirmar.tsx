// «Revisar antes de firmar»: el borrador de un documento formal, puesto delante
// del supervisor antes de que se aplique su firma.
//
// Existe porque hasta el 29 de septiembre de 2026 el panel generaba y firmaba
// el documento de un solo golpe, y lo que el modelo redactaba entraba al
// documento oficial sin que nadie lo leyera. En la prueba en vivo, «se
// devolvieron al contratista 3 monitores» salió como «el contratista ha
// devuelto tres monitores», y «entregó la 2da parte de los bienes» como
// «entregando la segunda parte de los bienes en el término establecido». Las
// comprobaciones automáticas frenan una parte de esos cambios (cifras, palabras
// parecidas, negaciones, valoraciones), pero un cambio de sujeto o de lugar no
// lo ve ninguna: lo ve quien firma. Por eso aquí se muestran las dos cosas, lo
// que el supervisor escribió y lo que irá en el documento, y él decide.
//
// Desde el 01-10-2026 se marcan además las palabras de la redacción que no
// están en las notas y las de las notas que la redacción no repite (ver
// diferenciasDeRedaccion): la revisión ciega de ese día encontró que casi un
// tercio de las redacciones que pasaban las comprobaciones cambiaba algo
// grave, y que el cambio casi siempre estaba en esas palabras.
//
// Desde el 02-10-2026 pasan por aquí los cinco documentos, también los que no
// llevan observaciones, con «Ver borrador completo (PDF)»: un número de
// contrato mal escrito por Gestión o una celda vacía de la tabla de
// obligaciones quedaban firmados sin que nadie abriera el PDF, y el aviso de
// los datos pendientes llegaba cuando ya no se podía corregir.

import { useState, type CSSProperties } from 'react'
import { Modal } from '@/components/ui'
import { verBorrador } from '@/services/documentoService'
import { motivoDelFalloDeDescarga } from '@/services/falloDeDescarga'
import { marcarLoQueNoEstaEn, type Tramo } from './diferenciasDeRedaccion'

/** El texto con sus tramos marcados como se le indique. */
function ConMarcas({ tramos, estilo }: { tramos: Tramo[]; estilo: CSSProperties }) {
  return (
    <>
      {tramos.map((t, i) =>
        t.marcada ? (
          <mark key={i} style={estilo}>
            {t.texto}
          </mark>
        ) : (
          <span key={i}>{t.texto}</span>
        ),
      )}
    </>
  )
}

/** Palabras de la redacción que no están en las notas: lo que el Copiloto agregó o cambió. */
const AGREGADA: CSSProperties = {
  background: 'var(--chip-red-bg)',
  color: 'inherit',
  borderRadius: 3,
  padding: '0 2px',
}

/** Palabras de las notas que la redacción no repite: lo que pudo perderse. */
const OMITIDA: CSSProperties = {
  background: 'transparent',
  color: 'inherit',
  textDecoration: 'underline dotted',
  textDecorationColor: 'var(--alert-leve)',
  textDecorationThickness: 2,
  textUnderlineOffset: 3,
}

export default function RevisarAntesDeFirmar({
  contratoId,
  documentoId,
  documento,
  nombreArchivo,
  notas,
  observaciones,
  redactadasConIa,
  motivoNotasTalCual,
  onFirmar,
  onUsarNotas,
  onCancelar,
}: {
  contratoId: number
  /** El borrador generado, sin firmar. */
  documentoId: number
  /** Nombre del formato («Informe de Supervisión»). */
  documento: string
  /** Nombre del documento generado, para el archivo del borrador. */
  nombreArchivo: string
  /** Lo que el supervisor escribió. */
  notas: string
  /** Lo que quedó en el apartado de observaciones, o null si no hay. */
  observaciones: string | null
  /** Si ese texto lo redactó el Copiloto (false: son las notas tal cual). */
  redactadasConIa: boolean
  /** Por qué van las notas tal cual cuando se pidió la redacción. */
  motivoNotasTalCual: string | null
  onFirmar: () => void
  /** Volver a generar con las notas tal cual, sin la redacción del Copiloto. */
  onUsarNotas: () => void
  onCancelar: () => void
}) {
  const [abriendo, setAbriendo] = useState(false)
  const [avisoBorrador, setAvisoBorrador] = useState<string | null>(null)
  const [errorBorrador, setErrorBorrador] = useState<string | null>(null)

  const verCompleto = () => {
    if (abriendo) return
    setAbriendo(true)
    setAvisoBorrador(null)
    setErrorBorrador(null)
    verBorrador(contratoId, documentoId, nombreArchivo)
      .then((resultado) => {
        // En el navegador la pestaña o el gestor de descargas ya lo muestran;
        // en el teléfono el «Guardar como» se cierra sin ninguna otra señal.
        if (resultado === 'guardado') {
          setAvisoBorrador('El borrador quedó guardado en el teléfono: ábralo con su lector de PDF y vuelva aquí.')
        } else if (resultado === 'navegador') {
          setAvisoBorrador('El borrador se descargó: ábralo desde las descargas del navegador.')
        }
      })
      .catch((err) => setErrorBorrador(motivoDelFalloDeDescarga(err, 'el borrador')))
      .finally(() => setAbriendo(false))
  }

  const bloque = {
    fontSize: 13,
    lineHeight: 1.55,
    padding: '10px 12px',
    borderRadius: 8,
    border: '1px solid var(--border)',
    whiteSpace: 'pre-wrap' as const,
    margin: '4px 0 14px',
  }
  const conRedaccion = redactadasConIa && !!observaciones
  return (
    <Modal title={`Revisar antes de firmar · ${documento}`} onClose={onCancelar} width={620} cerrarAlTocarFuera={false}>
      <p style={{ fontSize: 13, color: 'var(--text-muted)', margin: '0 0 12px', lineHeight: 1.5 }}>
        SICOT armó el borrador con los datos del contrato y los que usted dio; todavía no está firmado. Ábralo completo
        antes de firmar: firmado ya no se puede corregir. Lo que no estaba en el contrato ni en lo que usted escribió
        sale en rojo como «dato pendiente».
      </p>
      <button
        className="btn-ghost"
        style={{ width: '100%', padding: '10px 0', fontSize: 13, marginBottom: 12 }}
        onClick={verCompleto}
        disabled={abriendo}
      >
        {abriendo ? 'Abriendo el borrador…' : 'Ver borrador completo (PDF)'}
      </button>
      {avisoBorrador && (
        <p role="status" style={{ fontSize: 12, color: 'var(--text-muted)', margin: '0 0 12px', lineHeight: 1.5 }}>
          {avisoBorrador}
        </p>
      )}
      {errorBorrador && (
        <p role="alert" style={{ fontSize: 12, color: 'var(--alert-critica)', margin: '0 0 12px', lineHeight: 1.5 }}>
          {errorBorrador}
        </p>
      )}
      {conRedaccion && (
        <>
          <p style={{ fontSize: 13, color: 'var(--text-muted)', margin: '0 0 12px', lineHeight: 1.5 }}>
            El Copiloto redactó sus notas en lenguaje formal. Si algo no dice lo mismo que usted escribió —una cantidad,
            quién hizo qué, un lugar, una fecha—, use sus notas tal cual.
          </p>
          <p style={{ fontSize: 12, color: 'var(--text-muted)', margin: '0 0 12px', lineHeight: 1.5 }}>
            Para ayudarle a leer, se <mark style={AGREGADA}>marcan</mark> las palabras de la redacción que no están en
            sus notas y se <mark style={OMITIDA}>subrayan</mark> las de sus notas que la redacción no repite. No todo lo
            marcado es un error: es lo que conviene leer dos veces.
          </p>
          <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-muted)' }}>Lo que usted escribió</div>
          <div style={{ ...bloque, color: 'var(--text-muted)' }} data-testid="notas-revisadas">
            <ConMarcas tramos={marcarLoQueNoEstaEn(notas, observaciones)} estilo={OMITIDA} />
          </div>
          <div style={{ fontSize: 12, fontWeight: 600 }}>Lo que irá en el documento</div>
          <div style={{ ...bloque, background: 'var(--accent-soft)' }} data-testid="redaccion-revisada">
            <ConMarcas tramos={marcarLoQueNoEstaEn(observaciones, notas)} estilo={AGREGADA} />
          </div>
        </>
      )}
      {observaciones && !redactadasConIa && (
        <>
          <div style={{ fontSize: 12, fontWeight: 600 }}>Observaciones, tal como usted las escribió</div>
          <div style={bloque} data-testid="observaciones-tal-cual">
            {observaciones}
          </div>
          {motivoNotasTalCual && (
            <p style={{ fontSize: 12, color: 'var(--text-muted)', margin: '-6px 0 14px', lineHeight: 1.5 }}>
              Van sin la redacción del Copiloto porque {motivoNotasTalCual}.
            </p>
          )}
        </>
      )}
      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
        <button className="btn-ghost" style={{ flex: 1, padding: '10px 0', fontSize: 13 }} onClick={onCancelar}>
          Cancelar
        </button>
        {conRedaccion && (
          <button className="btn-ghost" style={{ flex: 2, padding: '10px 0', fontSize: 13 }} onClick={onUsarNotas}>
            Usar mis notas tal cual
          </button>
        )}
        <button className="btn-green" style={{ flex: 2, padding: '10px 0', fontSize: 13 }} onClick={onFirmar}>
          Firmar
        </button>
      </div>
    </Modal>
  )
}
