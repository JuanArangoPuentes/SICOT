// Bandeja de entrada del Supervisor — pantalla de aterrizaje al iniciar sesión.
//
// Es deliberadamente sobria: un saludo, en qué etapa va el contrato y lo que
// falta. Los indicadores, la barra grande de recorrido y las gráficas viven en
// la vista Contrato — aquí saturaban una pantalla cuyo trabajo es que el
// supervisor sepa en dos segundos qué tiene pendiente.
//
// Todo lo que aparece se deriva del estado real del contrato (subetapas del
// backend, alertas no leídas, documentos generados y la firma electrónica de la
// cuenta); no hay elementos de ejemplo.

import { IconArrowRight, IconBell, IconCheck, IconClock, IconContract, IconInbox } from '@/components/icons'
import type { Step } from '@/types/domain'
import type { ContratoResponse } from '@/services/api/types'

export type SeveridadItem = 'critica' | 'leve' | 'info' | 'ok'

export interface ItemBandeja {
  id: string
  severidad: SeveridadItem
  /** Familia del pendiente — se muestra como etiqueta de la fila. */
  categoria: 'Alerta' | 'Cronograma' | 'Tarea' | 'Documento' | 'Firma'
  titulo: string
  detalle: string
  fecha?: string
  accionLabel?: string
  onAccion?: () => void
  onDescartar?: () => void
}

const SEVERIDAD_META: Record<SeveridadItem, { label: string; color: string; bg: string }> = {
  critica: { label: 'CRÍTICO', color: 'var(--alert-critica)', bg: 'var(--chip-red-bg)' },
  leve: { label: 'PENDIENTE', color: 'var(--alert-leve)', bg: 'rgba(229,169,60,0.14)' },
  info: { label: 'INFORMATIVO', color: 'var(--info)', bg: 'var(--chip-blue-bg)' },
  ok: { label: 'AL DÍA', color: 'var(--accent)', bg: 'var(--accent-soft)' },
}

/** Saludo según la hora del equipo del supervisor. */
function saludo(): string {
  const h = new Date().getHours()
  if (h < 12) return 'Buenos días'
  if (h < 19) return 'Buenas tardes'
  return 'Buenas noches'
}

/**
 * Indicador compacto de etapa: un punto por paso del contrato, el actual
 * resaltado. Responde "¿en qué voy?" sin traer el tablero entero a la bandeja.
 */
function EtapaCompacta({ steps, onAbrir }: { steps: Step[]; onAbrir: () => void }) {
  const actual = steps.find(s => s.status === 'active')
  return (
    <button
      type="button"
      onClick={onAbrir}
      title="Ver el contrato completo"
      style={{
        display: 'flex', alignItems: 'center', gap: 7, flexWrap: 'wrap',
        background: 'none', border: 'none', padding: 0, cursor: 'pointer',
        fontFamily: 'var(--font-ui)',
      }}
    >
      {steps.map(s => {
        const completo = s.subSteps.length > 0 && s.subSteps.every(ss => ss.completed)
        const esActual = s.id === actual?.id
        return (
          <span
            key={s.id}
            style={{
              width: 26, height: 26, borderRadius: '50%',
              display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 11, fontWeight: 700, fontFamily: 'var(--font-mono)',
              background: completo ? 'var(--accent)' : esActual ? 'var(--accent-soft)' : 'transparent',
              border: completo ? 'none' : `1.5px solid ${esActual ? 'var(--accent)' : 'var(--step-pending)'}`,
              color: completo ? 'var(--on-accent)' : esActual ? 'var(--accent)' : 'var(--text-muted)',
              boxShadow: esActual ? '0 0 0 3px var(--accent-glow)' : 'none',
            }}
          >
            {completo ? '✓' : s.id}
          </span>
        )
      })}
    </button>
  )
}

export default function Bandeja({
  nombre,
  contrato,
  steps,
  items,
  errorAlertas,
  onIrAContrato,
  onContinuarPaso,
}: {
  nombre: string
  contrato: ContratoResponse
  steps: Step[]
  items: ItemBandeja[]
  /** El servicio de alertas no respondió: no se puede afirmar que no haya ninguna. */
  errorAlertas: boolean
  onIrAContrato: () => void
  onContinuarPaso: (stepId: number) => void
}) {
  const pasoActivo = steps.find(s => s.status === 'active') ?? steps.find(s => s.subSteps.some(ss => !ss.completed))
  const subPasoActual = pasoActivo?.subSteps.find(ss => !ss.completed)
  const todoCerrado = steps.length > 0 && steps.every(s => s.subSteps.every(ss => ss.completed))
  const criticos = items.filter(i => i.severidad === 'critica').length
  const primerNombre = nombre.split(' ')[0]

  return (
    <div style={{ flex: 1, overflowY: 'auto', padding: '26px 24px 32px', minWidth: 0 }}>
      <div style={{ maxWidth: 940, margin: '0 auto' }}>

        {/* ── Bienvenida y etapa actual ── */}
        <div style={{ marginBottom: 26 }}>
          <h2 style={{ fontSize: 24, letterSpacing: '-0.02em', marginBottom: 6 }}>
            {saludo()}, {primerNombre}
          </h2>

          {todoCerrado ? (
            <p style={{ margin: 0, fontSize: 14.5, color: 'var(--text-secondary)', lineHeight: 1.65, maxWidth: 680 }}>
              Su supervisión del contrato{' '}
              <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--accent-tech)' }}>{contrato.numeroContrato}</span>{' '}
              está cerrada: no quedan sub-pasos pendientes de su parte.
            </p>
          ) : pasoActivo ? (
            <p style={{ margin: 0, fontSize: 14.5, color: 'var(--text-secondary)', lineHeight: 1.65, maxWidth: 680 }}>
              Su contrato{' '}
              <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--accent-tech)' }}>{contrato.numeroContrato}</span>{' '}
              va en el <strong style={{ color: 'var(--text-primary)', fontWeight: 600 }}>Paso {pasoActivo.id} de {steps.length}</strong> — {pasoActivo.title}.
            </p>
          ) : (
            <p style={{ margin: 0, fontSize: 14.5, color: 'var(--text-secondary)', lineHeight: 1.65 }}>
              Las etapas de su contrato todavía no se han cargado desde el servidor.
            </p>
          )}

          {steps.length > 0 && (
            <div style={{ marginTop: 16 }}>
              <EtapaCompacta steps={steps} onAbrir={onIrAContrato} />
            </div>
          )}

          {pasoActivo && subPasoActual && (
            <div style={{ marginTop: 18, display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap' }}>
              <div className="surface" style={{ padding: '11px 14px', flex: '1 1 380px', minWidth: 0 }}>
                <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.09em', color: 'var(--text-muted)', marginBottom: 3 }}>
                  SIGUIENTE SUB-PASO
                </div>
                <div style={{ fontSize: 13.5, color: 'var(--text-primary)', lineHeight: 1.5 }}>
                  <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--accent)', marginRight: 7 }}>{subPasoActual.id}</span>
                  {subPasoActual.label}
                </div>
              </div>
              <button className="btn-green" onClick={() => onContinuarPaso(pasoActivo.id)}
                style={{ padding: '11px 20px', fontSize: 13.5, display: 'inline-flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
                Continuar el Paso {pasoActivo.id} <IconArrowRight size={13} />
              </button>
            </div>
          )}
        </div>

        {/* ── Pendientes ── */}
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, marginBottom: 12 }}>
          <h3 style={{ fontSize: 15.5 }}>Alertas y pendientes</h3>
          <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12.5, color: criticos ? 'var(--alert-critica)' : 'var(--text-muted)' }}>
            {items.length === 0
              ? 'nada pendiente'
              : `${items.length} en total${criticos > 0 ? ` · ${criticos} crítico(s)` : ''}`}
          </span>
        </div>

        {errorAlertas && (
          <div className="card" style={{ padding: '12px 15px', marginBottom: 10, borderColor: 'var(--alert-critica)', background: 'var(--chip-red-bg)', display: 'flex', gap: 10, alignItems: 'flex-start' }}>
            <span className="inbox-icon" style={{ background: 'var(--chip-red-bg)', color: 'var(--alert-critica)' }}>!</span>
            <div>
              <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--alert-critica)' }}>No se pudieron consultar las alertas del contrato</div>
              <div style={{ fontSize: 12.5, color: 'var(--text-secondary)', marginTop: 2, lineHeight: 1.5 }}>
                Puede haber alertas pendientes que no se están mostrando aquí. Recargue la página para reintentar.
              </div>
            </div>
          </div>
        )}

        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {items.map(item => {
            const meta = SEVERIDAD_META[item.severidad]
            return (
              <div key={item.id} className={`inbox-row ${item.severidad}`}>
                <span className="inbox-icon" style={{ background: meta.bg, color: meta.color }}>
                  {item.severidad === 'ok'
                    ? <IconCheck size={15} />
                    : item.categoria === 'Cronograma'
                      ? <IconClock size={16} />
                      : item.categoria === 'Alerta'
                        ? <IconBell size={16} />
                        : item.categoria === 'Documento' || item.categoria === 'Firma'
                          ? <IconContract size={16} />
                          : <IconInbox size={16} />}
                </span>

                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', marginBottom: 3 }}>
                    <span style={{ fontSize: 9, fontWeight: 800, letterSpacing: '0.11em', color: meta.color }}>{meta.label}</span>
                    <span style={{ fontSize: 10.5, color: 'var(--text-muted)', letterSpacing: '0.05em', textTransform: 'uppercase' }}>{item.categoria}</span>
                    {item.fecha && (
                      <span style={{ fontFamily: 'var(--font-mono)', fontSize: 10.5, color: 'var(--text-muted)' }}>{item.fecha}</span>
                    )}
                  </div>
                  <div style={{ fontSize: 13.5, fontWeight: 600, color: 'var(--text-primary)', lineHeight: 1.4 }}>{item.titulo}</div>
                  <div style={{ fontSize: 12.5, color: 'var(--text-secondary)', lineHeight: 1.55, marginTop: 2 }}>{item.detalle}</div>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
                  {item.accionLabel && item.onAccion && (
                    <button type="button" onClick={item.onAccion}
                      style={{
                        display: 'inline-flex', alignItems: 'center', gap: 6, whiteSpace: 'nowrap',
                        padding: '6px 12px', fontSize: 11.5, fontWeight: 600, borderRadius: 7, cursor: 'pointer',
                        background: 'transparent', border: `1px solid ${meta.color}`, color: meta.color,
                        fontFamily: 'var(--font-ui)',
                      }}>
                      {item.accionLabel} <IconArrowRight size={11} />
                    </button>
                  )}
                  {item.onDescartar && (
                    <button type="button" onClick={item.onDescartar} title="Marcar como leída"
                      aria-label="Marcar como leída"
                      style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: 18, lineHeight: 1, padding: '0 2px' }}>
                      ×
                    </button>
                  )}
                </div>
              </div>
            )
          })}

          {items.length === 0 && (
            <div className="card" style={{ padding: '34px 20px', textAlign: 'center' }}>
              <div style={{
                width: 40, height: 40, borderRadius: '50%', margin: '0 auto 12px',
                background: 'var(--accent-soft)', border: '1px solid var(--accent-line)', color: 'var(--accent)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <IconCheck size={18} />
              </div>
              <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--accent)', marginBottom: 4 }}>
                Sin pendientes en su bandeja
              </div>
              <div style={{ fontSize: 12.5, color: 'var(--text-muted)' }}>
                No hay alertas ni sub-pasos esperando por usted en este momento.
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
