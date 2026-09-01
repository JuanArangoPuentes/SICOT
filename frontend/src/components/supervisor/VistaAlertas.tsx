// Vista "Alertas" del panel del Supervisor.
//
// Extraída de SupervisorPanel.tsx junto con su tarjeta, que solo se usa aquí.
//
// La distinción importante de esta pantalla: "no se pudieron consultar las
// alertas" y "no hay alertas" son estados diferentes. Pintar el segundo cuando
// ocurrió el primero le diría al supervisor que está al día justo cuando el
// sistema no puede saberlo.

import { SectionHeader, type LiveAlert } from '@/components/ui'
import type { AlertaResponse, ContratoResponse } from '@/services/api/types'

export default function VistaAlertas({
  contrato,
  alertaCronograma,
  alertasApi,
  errorAlertas,
  onResolver,
}: {
  contrato: ContratoResponse
  alertaCronograma: LiveAlert | null
  alertasApi: AlertaResponse[]
  errorAlertas: boolean
  onResolver: (id: string) => void
}) {
  const resolveAlert = onResolver
  return (
  <div style={{ flex: 1, overflowY: 'auto', padding: 24, minWidth: 0 }}>
    <SectionHeader
      eyebrow="Seguimiento en vivo"
      title={`Alertas del contrato ${contrato.numeroContrato}`}
      desc="Alertas registradas por el sistema y la revisión de cronograma calculada con las fechas reales del contrato."
    />

    {alertaCronograma && (
      <AlertCard
        severity={alertaCronograma.severity === 'ok' ? 'ok' : alertaCronograma.severity === 'critica' ? 'urgent' : 'attention'}
        title="Cronograma del paso activo"
        desc={alertaCronograma.text}
        actionLabel="Ver paso"
        onAction={() => resolveAlert(alertaCronograma.id)}
      />
    )}

    {alertasApi.filter(a => !a.leida).map(a => {
      const severity = a.prioridad === 'ALTA' ? 'urgent' as const
        : a.prioridad === 'MEDIA' ? 'attention' as const
          : 'info' as const
      return (
        <AlertCard
          key={a.id}
          severity={severity}
          title={a.tipo.charAt(0) + a.tipo.slice(1).toLowerCase().replace('_', ' ')}
          desc={a.mensaje}
          actionLabel="Ir al paso"
          onAction={() => resolveAlert('api-' + a.id)}
        />
      )
    })}

    {errorAlertas ? (
      <div className="card" style={{ padding: '40px 20px', textAlign: 'center', borderColor: 'var(--alert-critica)' }}>
        <div style={{ width: 34, height: 34, borderRadius: '50%', background: 'var(--alert-critica)', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, fontSize: 16, margin: '0 auto 12px' }}>!</div>
        <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--alert-critica)', marginBottom: 4 }}>No se pudieron consultar las alertas</div>
        <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>
          Puede haber alertas pendientes que no se están mostrando. Recargue la página para reintentar.
        </div>
      </div>
    ) : alertasApi.filter(a => !a.leida).length === 0 && !alertaCronograma && (
      <div className="card" style={{ padding: '40px 20px', textAlign: 'center' }}>
        <div style={{ width: 34, height: 34, borderRadius: '50%', background: 'var(--accent)', color: 'var(--on-accent)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, fontSize: 16, margin: '0 auto 12px' }}>✓</div>
        <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--accent)', marginBottom: 4 }}>Sin alertas activas</div>
        <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>No hay alertas pendientes para este contrato.</div>
      </div>
    )}
  </div>
  )
}

// ─── Tarjeta de alerta ───────────────────────────────────────────────────────

function AlertCard({ severity, title, desc, actionLabel, onAction }: {
  severity: 'urgent' | 'attention' | 'info' | 'ok'
  title: string
  desc: string
  actionLabel?: string
  onAction?: () => void
}) {
  const config = {
    urgent: { color: 'var(--alert-critica)', bg: 'var(--chip-red-bg)', label: 'URGENTE' },
    attention: { color: 'var(--alert-leve)', bg: 'rgba(229,169,60,0.12)', label: 'ATENCIÓN' },
    info: { color: 'var(--info)', bg: 'var(--chip-blue-bg)', label: 'INFORMATIVO' },
    ok: { color: 'var(--accent)', bg: 'var(--accent-soft)', label: 'A TIEMPO' },
  }[severity]

  return (
    <div className="card" style={{ padding: '14px 16px', marginBottom: 10, borderLeft: `3px solid ${config.color}` }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
        <span className="inbox-icon" style={{ background: config.bg, color: config.color }}>
          {severity === 'ok' ? '✓' : '!'}
        </span>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4, flexWrap: 'wrap' }}>
            <span style={{ fontSize: 9.5, fontWeight: 800, letterSpacing: '0.1em', color: config.color }}>{config.label}</span>
            <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-primary)' }}>{title}</span>
          </div>
          <p style={{ margin: 0, fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.55 }}>{desc}</p>
          {actionLabel && onAction && (
            <button onClick={onAction}
              style={{ marginTop: 10, background: 'transparent', border: `1px solid ${config.color}`, borderRadius: 7, padding: '5px 12px', fontSize: 11.5, fontWeight: 600, color: config.color, cursor: 'pointer', fontFamily: 'var(--font-ui)' }}>
              {actionLabel} →
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
