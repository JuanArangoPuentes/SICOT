// Tablero de indicadores del contrato asignado.
//
// Todo lo que se grafica aquí sale del estado REAL del contrato (subetapas
// completadas en el backend, documentos realmente generados/firmados y las
// fechas del contrato). No hay series de ejemplo: si el contrato todavía no
// tiene etapas cargadas, cada panel lo dice en vez de dibujar una curva falsa.

import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { Step } from '@/types/domain'
import type { ContratoResponse, DocumentoResponse } from '@/services/api/types'
import { FORMAL_DOCS } from '@/data/contractFlow'

// Las series van con `isAnimationActive={false}`: la animación de entrada de
// recharts depende de requestAnimationFrame, así que en una pestaña que no
// está componiendo (segundo plano, captura de pantalla, impresión) las barras
// y sectores se quedan sin dibujar. Un tablero de seguimiento tiene que verse
// completo desde el primer fotograma.
//
// El color de cada punto viaja en su propio campo `fill` dentro de los datos:
// los <Cell> dentro de <Bar>/<Pie> dejan la figura sin renderizar en recharts 3.
const TOOLTIP_STYLE = {
  background: 'var(--bg-surface)',
  border: '1px solid var(--accent-line)',
  borderRadius: 8,
  fontSize: 12,
  color: 'var(--text-primary)',
} as const

function Panel({
  title,
  desc,
  children,
  alto = 230,
}: {
  title: string
  desc: string
  children: React.ReactNode
  alto?: number
}) {
  return (
    <div className="card" style={{ padding: '15px 17px 12px', display: 'flex', flexDirection: 'column' }}>
      <div className="section-title">{title}</div>
      <div className="section-sub" style={{ margin: '3px 0 12px' }}>
        {desc}
      </div>
      <div style={{ height: alto, width: '100%' }}>{children}</div>
    </div>
  )
}

function SinDatos({ texto }: { texto: string }) {
  return (
    <div
      style={{
        height: '100%',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        textAlign: 'center',
        padding: '0 16px',
        fontSize: 12.5,
        color: 'var(--text-muted)',
        lineHeight: 1.5,
      }}
    >
      {texto}
    </div>
  )
}

export default function ContratoGraficas({
  steps,
  docs,
  contrato,
}: {
  steps: Step[]
  docs: DocumentoResponse[]
  contrato: ContratoResponse
}) {
  // Sub-pasos cerrados sobre el total: alimenta la comparacion de abajo.
  const todosSubPasos = steps.flatMap((s) => s.subSteps)
  const completados = todosSubPasos.filter((ss) => ss.completed).length

  // ── 1. Tiempo transcurrido frente a avance ejecutado ──
  const inicio = contrato.fechaInicio ? new Date(contrato.fechaInicio) : null
  const fin = contrato.fechaFin ? new Date(contrato.fechaFin) : null
  const totalDias = inicio && fin ? (fin.getTime() - inicio.getTime()) / 86400000 : 0
  const diasCorridos = inicio ? (Date.now() - inicio.getTime()) / 86400000 : 0
  const pctTiempo = totalDias > 0 ? Math.max(0, Math.min(100, Math.round((diasCorridos / totalDias) * 100))) : null
  const pctAvance = todosSubPasos.length ? Math.round((completados / todosSubPasos.length) * 100) : 0
  const comparacion =
    pctTiempo === null
      ? []
      : [
          { name: 'Tiempo transcurrido', valor: pctTiempo, fill: 'var(--chip-blue)' },
          { name: 'Avance ejecutado', valor: pctAvance, fill: 'var(--accent)' },
        ]

  // ── 2. Documentos formales del proceso ──
  const generados = FORMAL_DOCS.map((d) => docs.find((x) => x.generadoPorIa && x.nombre.startsWith(d.name)))
  const firmados = generados.filter((d) => d?.estado === 'APROBADO').length
  const sinFirmar = generados.filter((d) => d && d.estado !== 'APROBADO').length
  const sinGenerar = FORMAL_DOCS.length - firmados - sinFirmar
  const documentos = [
    { name: 'Firmados', valor: firmados, fill: 'var(--accent)' },
    { name: 'Generados sin firmar', valor: sinFirmar, fill: 'var(--alert-leve)' },
    { name: 'Sin generar aún', valor: sinGenerar, fill: 'var(--step-pending)' },
  ]

  return (
    <div
      className="rejilla-graficas"
      style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))', gap: 14 }}
    >
      {/* Aqui vivian dos paneles mas: «Avance por etapa» y «Distribucion de
          sub-pasos». Se quitaron porque no anadian informacion, la repetian:
          el avance por etapa ya sale en la barra de recorrido de arriba Y en la
          barra de cada etapa del acordeon, y la distribucion de sub-pasos es el
          mismo avance global convertido en tarta.

          Con ellos, el mismo porcentaje aparecia hasta tres veces en la misma
          pantalla, cada vez con una forma distinta. Los dos que quedan son los
          que dicen algo que ninguna barra de arriba dice. */}
      <Panel
        title="Tiempo frente a avance"
        desc="Porcentaje del plazo del contrato ya transcurrido comparado con el porcentaje de sub-pasos cerrados."
      >
        {!comparacion.length ? (
          <SinDatos texto="El contrato no tiene fechas de inicio y fin registradas, así que no se puede comparar el plazo con el avance." />
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={comparacion} layout="vertical" margin={{ top: 10, right: 26, left: 8, bottom: 0 }}>
              <CartesianGrid stroke="var(--border)" horizontal={false} />
              <XAxis
                type="number"
                domain={[0, 100]}
                unit="%"
                tick={{ fill: 'var(--text-muted)', fontSize: 11 }}
                axisLine={{ stroke: 'var(--border)' }}
                tickLine={false}
              />
              <YAxis
                type="category"
                dataKey="name"
                width={130}
                tick={{ fill: 'var(--text-secondary)', fontSize: 11 }}
                axisLine={false}
                tickLine={false}
              />
              <Tooltip
                cursor={{ fill: 'var(--accent-soft)' }}
                contentStyle={TOOLTIP_STYLE}
                formatter={(v) => [`${String(v)}%`, '']}
              />
              <Bar dataKey="valor" name="Porcentaje" radius={[0, 5, 5, 0]} barSize={26} isAnimationActive={false} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </Panel>

      <Panel
        title="Documentos formales del proceso"
        desc="Estado real de los documentos que SICOT arma con los datos del contrato y el supervisor firma."
      >
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={documentos} margin={{ top: 6, right: 10, left: -26, bottom: 0 }}>
            <CartesianGrid stroke="var(--border)" vertical={false} />
            <XAxis
              dataKey="name"
              tick={{ fill: 'var(--text-muted)', fontSize: 10.5 }}
              axisLine={{ stroke: 'var(--border)' }}
              tickLine={false}
              interval={0}
            />
            <YAxis
              allowDecimals={false}
              tick={{ fill: 'var(--text-muted)', fontSize: 11 }}
              axisLine={false}
              tickLine={false}
            />
            <Tooltip
              cursor={{ fill: 'var(--accent-soft)' }}
              contentStyle={TOOLTIP_STYLE}
              formatter={(v) => [`${String(v)} documento(s)`, '']}
            />
            <Bar dataKey="valor" name="Documentos" radius={[4, 4, 0, 0]} barSize={46} isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      </Panel>
    </div>
  )
}
