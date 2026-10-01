// La redacción del Copiloto, puesta delante del supervisor antes de firmar.
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

import type { CSSProperties } from 'react'
import { Modal } from '@/components/ui'
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

export default function RevisarRedaccion({
  documento,
  notas,
  observaciones,
  onFirmar,
  onUsarNotas,
  onCancelar,
}: {
  /** Nombre del documento («Informe de Supervisión»). */
  documento: string
  /** Lo que el supervisor escribió. */
  notas: string
  /** Lo que el Copiloto redactó y quedó en el apartado de observaciones. */
  observaciones: string
  onFirmar: () => void
  /** Volver a generar con las notas tal cual, sin la redacción del Copiloto. */
  onUsarNotas: () => void
  onCancelar: () => void
}) {
  const bloque = {
    fontSize: 13,
    lineHeight: 1.55,
    padding: '10px 12px',
    borderRadius: 8,
    border: '1px solid var(--border)',
    whiteSpace: 'pre-wrap' as const,
    margin: '4px 0 14px',
  }
  return (
    <Modal
      title={`Revise las observaciones de ${documento}`}
      onClose={onCancelar}
      width={620}
      cerrarAlTocarFuera={false}
    >
      <p style={{ fontSize: 13, color: 'var(--text-muted)', margin: '0 0 12px', lineHeight: 1.5 }}>
        El Copiloto redactó sus notas en lenguaje formal. Léalas antes de firmar: el documento firmado ya no se puede
        corregir. Si algo no dice lo mismo que usted escribió —una cantidad, quién hizo qué, un lugar, una fecha—, use
        sus notas tal cual.
      </p>
      <p style={{ fontSize: 12, color: 'var(--text-muted)', margin: '0 0 12px', lineHeight: 1.5 }}>
        Para ayudarle a leer, se <mark style={AGREGADA}>marcan</mark> las palabras de la redacción que no están en sus
        notas y se <mark style={OMITIDA}>subrayan</mark> las de sus notas que la redacción no repite. No todo lo marcado
        es un error: es lo que conviene leer dos veces.
      </p>
      <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-muted)' }}>Lo que usted escribió</div>
      <div style={{ ...bloque, color: 'var(--text-muted)' }} data-testid="notas-revisadas">
        <ConMarcas tramos={marcarLoQueNoEstaEn(notas, observaciones)} estilo={OMITIDA} />
      </div>
      <div style={{ fontSize: 12, fontWeight: 600 }}>Lo que irá en el documento</div>
      <div style={{ ...bloque, background: 'var(--accent-soft)' }} data-testid="redaccion-revisada">
        <ConMarcas tramos={marcarLoQueNoEstaEn(observaciones, notas)} estilo={AGREGADA} />
      </div>
      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
        <button className="btn-ghost" style={{ flex: 1, padding: '10px 0', fontSize: 13 }} onClick={onCancelar}>
          Cancelar
        </button>
        <button className="btn-ghost" style={{ flex: 2, padding: '10px 0', fontSize: 13 }} onClick={onUsarNotas}>
          Usar mis notas tal cual
        </button>
        <button className="btn-green" style={{ flex: 2, padding: '10px 0', fontSize: 13 }} onClick={onFirmar}>
          Firmar con esta redacción
        </button>
      </div>
    </Modal>
  )
}
