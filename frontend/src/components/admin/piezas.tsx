// Piezas visuales compartidas por las cuatro vistas del panel de Administración.
//
// Extraído de AdminPanel.tsx, que reunía las cuatro vistas, sus cuatro modales y
// sus piezas compartidas en un solo archivo de 913 líneas — el mismo patrón que
// ya se aplicó a SupervisorPanel.tsx (ver components/supervisor/).

import type React from 'react'

export function Widget({ icon, label, value, hint, tone = 'ok' }: { icon?: React.ReactNode; label: string; value: string; hint: string; tone?: 'ok' | 'warn' }) {
  const tint = tone === 'warn' ? 'var(--alert-leve)' : 'var(--accent)'
  return (
    <div className={`stat-card${tone === 'warn' ? ' warn' : ''}`}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
        {icon && (
          <div style={{
            width: 30, height: 30, borderRadius: 8, flexShrink: 0, color: tint,
            background: tone === 'warn' ? 'rgba(184,120,10,0.12)' : 'var(--accent-soft)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>{icon}</div>
        )}
        <div style={{ fontSize: 11, letterSpacing: '0.06em', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>{label}</div>
      </div>
      <div style={{ fontSize: 32, fontWeight: 700, color: tint, lineHeight: 1.3, fontFamily: 'var(--font-display)', margin: '2px 0' }}>{value}</div>
      <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{hint}</div>
    </div>
  )
}

export function SectionHead({ icon, title, desc, action }: { icon?: React.ReactNode; title: string; desc: string; action?: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16, marginBottom: 18, flexWrap: 'wrap' }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
        {icon && (
          <div style={{
            width: 34, height: 34, borderRadius: 9, flexShrink: 0, marginTop: 1, color: 'var(--accent)',
            background: 'var(--accent-soft)', border: '1px solid var(--accent-line)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>{icon}</div>
        )}
        <div>
          <h3 style={{ margin: '0 0 4px', fontSize: 16 }}>{title}</h3>
          <p style={{ margin: 0, fontSize: 13, color: 'var(--text-muted)', maxWidth: 620 }}>{desc}</p>
        </div>
      </div>
      {action}
    </div>
  )
}

export function GridRow({ cols, header, children }: { cols: string; header?: boolean; children: React.ReactNode }) {
  return (
    <div className={header ? 'data-grid' : 'data-grid data-grid-row'} style={{
      display: 'grid', gridTemplateColumns: cols, gap: 12, alignItems: 'center',
      padding: header ? '10px 16px' : '12px 16px',
      borderBottom: '1px solid var(--border)',
      fontSize: header ? 11 : 13,
      fontWeight: header ? 600 : 400,
      letterSpacing: header ? '0.06em' : undefined,
      color: header ? 'var(--text-muted)' : 'var(--text-primary)',
    }}>{children}</div>
  )
}

export function MiniBtn({ children, onClick, accent, disabled, title }: {
  children: React.ReactNode; onClick: () => void; accent?: boolean; disabled?: boolean; title?: string
}) {
  return (
    <button onClick={onClick} disabled={disabled} title={title} style={{
      display: 'inline-flex', alignItems: 'center', gap: 5,
      background: accent ? 'var(--accent-soft)' : 'transparent',
      border: `1px solid ${accent ? 'var(--accent-line)' : 'var(--border)'}`,
      color: disabled ? 'var(--text-muted)' : accent ? 'var(--accent)' : 'var(--text-secondary)',
      borderRadius: 6, padding: '4px 9px', fontSize: 11, cursor: disabled ? 'not-allowed' : 'pointer',
      opacity: disabled ? 0.55 : 1,
      fontFamily: 'var(--font-ui)', whiteSpace: 'nowrap',
    }}>{children}</button>
  )
}
