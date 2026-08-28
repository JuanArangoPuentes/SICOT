import React, { useState, useRef, useEffect } from 'react'
import senaLogo from '@/imports/image.png'
import { IconLogout } from './icons'

// ─── Marca institucional ──────────────────────────────────────────────────────

export function SenaLogo({ size = 72 }: { size?: number }) {
  return (
    <div style={{
      width: size, height: size, borderRadius: size * 0.22,
      background: '#ffffff', padding: size * 0.08,
      border: '1px solid var(--accent-line)',
      boxShadow: '0 0 24px var(--accent-glow)',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      flexShrink: 0,
    }}>
      <img src={senaLogo} alt="Logotipo del SENA" style={{ width: '100%', height: '100%', objectFit: 'contain', display: 'block' }} />
    </div>
  )
}

// ─── Chips ────────────────────────────────────────────────────────────────────

export type ChipType =
  | 'responsible' | 'document' | 'pending' | 'done' | 'signed' | 'running'
  | 'unassigned' | 'finished' | 'vigente' | 'sugerido' | 'conflicto' | 'inactive'

export function Chip({ text, type }: { text: string; type: ChipType }) {
  const map: Record<ChipType, { bg: string; color: string }> = {
    responsible: { bg: 'var(--chip-purple-bg)', color: 'var(--chip-purple)' },
    document: { bg: 'var(--chip-blue-bg)', color: 'var(--chip-blue)' },
    pending: { bg: 'var(--chip-gray-bg)', color: 'var(--chip-gray)' },
    done: { bg: 'var(--accent-soft)', color: 'var(--accent)' },
    signed: { bg: 'var(--accent-soft)', color: 'var(--accent)' },
    vigente: { bg: 'var(--accent-soft)', color: 'var(--accent)' },
    running: { bg: 'var(--chip-blue-bg)', color: 'var(--chip-blue)' },
    unassigned: { bg: 'rgba(229,169,60,0.14)', color: 'var(--alert-leve)' },
    sugerido: { bg: 'rgba(229,169,60,0.14)', color: 'var(--alert-leve)' },
    conflicto: { bg: 'var(--chip-red-bg)', color: 'var(--alert-critica)' },
    finished: { bg: 'var(--chip-gray-bg)', color: 'var(--chip-gray)' },
    inactive: { bg: 'var(--chip-gray-bg)', color: 'var(--chip-gray)' },
  }
  const s = map[type]
  return (
    <span className="chip" style={{ background: s.bg, color: s.color }}>
      <span style={{ width: 5, height: 5, borderRadius: '50%', background: 'currentColor', marginRight: 5, flexShrink: 0 }} />
      {text}
    </span>
  )
}

// ─── Recorrido del contrato (barra grande de etapas) ──────────────────────────

export type StageState = 'ok' | 'warning' | 'critical' | 'idle'

export interface Stage {
  key: string
  /** Nombre corto de la etapa — "Inspección". */
  label: string
  /** Nombre completo mostrado cuando la etapa es la actual. */
  fullLabel?: string
  state: StageState
  pct: number
  detail: string
}

const STAGE_COLOR: Record<StageState, string> = {
  ok: 'var(--accent)',
  warning: 'var(--alert-leve)',
  critical: 'var(--alert-critica)',
  idle: 'var(--step-pending)',
}

/**
 * Barra de recorrido del contrato — ocupa todo el ancho disponible, una
 * sección por etapa, y dice explícitamente en qué etapa está el contrato
 * ahora mismo.
 *
 * Sustituye a la barra lineal anterior, que tenía un ancho fijo de 600 px y
 * solo mostraba un porcentaje global: había que interpretar la posición del
 * relleno para deducir la etapa actual.
 */
export function StageJourney({ stages, currentKey, overallPct, onStageClick }: {
  stages: Stage[]
  /** Etapa en curso — la que se resalta como "actual". */
  currentKey?: string | null
  /**
   * Avance global del contrato. Se recibe de fuera para que sea exactamente la
   * misma cifra que muestran los indicadores (sub-pasos cerrados sobre el
   * total); el promedio de porcentajes por etapa daría un número distinto para
   * lo que el usuario lee como "lo mismo".
   */
  overallPct?: number
  onStageClick?: (key: string) => void
}) {
  const [hoveredKey, setHoveredKey] = useState<string | null>(null)
  const total = stages.length

  const avance = overallPct ?? (total
    ? Math.round(stages.reduce((acc, s) => acc + s.pct, 0) / total)
    : 0)

  if (!total) {
    return (
      <div className="card" style={{ padding: '24px 20px', textAlign: 'center', fontSize: 13, color: 'var(--text-muted)' }}>
        Las etapas de este contrato todavía no se han cargado desde el servidor.
      </div>
    )
  }

  const currentIdx = currentKey ? stages.findIndex(s => s.key === currentKey) : -1
  const actual = currentIdx >= 0 ? stages[currentIdx] : null
  const detalle = stages.find(s => s.key === hoveredKey) ?? actual

  return (
    <div className="card" style={{ padding: '16px 20px 18px' }}>
      {/* Cabecera: etapa actual + avance global */}
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 20, flexWrap: 'wrap', marginBottom: 16 }}>
        <div style={{ minWidth: 0 }}>
          <div className="eyebrow">Recorrido del contrato · GCCON-P-010</div>
          {actual ? (
            <>
              <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, flexWrap: 'wrap' }}>
                <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--text-muted)', letterSpacing: '0.04em' }}>
                  PASO {currentIdx + 1} DE {total}
                </span>
                <h2 style={{ fontSize: 21, color: STAGE_COLOR[actual.state], letterSpacing: '-0.015em' }}>
                  {(actual.fullLabel ?? actual.label).toUpperCase()}
                </h2>
              </div>
              <div style={{ fontSize: 12.5, color: 'var(--text-secondary)', marginTop: 3, lineHeight: 1.5, maxWidth: 620 }}>
                {actual.detail}
              </div>
            </>
          ) : (
            <h2 style={{ fontSize: 19 }}>Etapas del contrato</h2>
          )}
        </div>

        <div style={{ textAlign: 'right', flexShrink: 0 }}>
          <div style={{ fontSize: 10.5, fontWeight: 700, letterSpacing: '0.1em', color: 'var(--text-muted)' }}>AVANCE GLOBAL</div>
          <div style={{ fontFamily: 'var(--font-display)', fontSize: 30, fontWeight: 700, color: 'var(--accent)', lineHeight: 1.05 }}>
            {avance}<span style={{ fontSize: 17 }}>%</span>
          </div>
        </div>
      </div>

      {/* Barra: una sección por etapa, de extremo a extremo */}
      <div className="journey">
        {stages.map((s, i) => {
          const color = STAGE_COLOR[s.state]
          const esActual = s.key === currentKey
          const completa = s.pct === 100
          return (
            <button
              key={s.key}
              type="button"
              className={`journey-seg${esActual ? ' actual' : ''}`}
              onClick={() => onStageClick?.(s.key)}
              onMouseEnter={() => setHoveredKey(s.key)}
              onMouseLeave={() => setHoveredKey(null)}
              onFocus={() => setHoveredKey(s.key)}
              onBlur={() => setHoveredKey(null)}
              title={`${s.label} — ${s.pct}% completado`}
            >
              {/* Etiqueta superior */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6, minHeight: 18 }}>
                <span style={{
                  width: 17, height: 17, borderRadius: '50%', flexShrink: 0,
                  display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: 9.5, fontWeight: 700, fontFamily: 'var(--font-mono)',
                  background: completa ? color : esActual ? 'var(--accent-soft)' : 'transparent',
                  border: completa ? 'none' : `1.5px solid ${esActual ? color : 'var(--step-pending)'}`,
                  color: completa ? 'var(--on-accent)' : esActual ? color : 'var(--text-muted)',
                }}>
                  {completa ? '✓' : i + 1}
                </span>
                <span style={{
                  fontSize: 10.5, fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase',
                  color: esActual ? color : s.state === 'idle' ? 'var(--text-muted)' : 'var(--text-secondary)',
                  whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                }}>
                  {s.label}
                </span>
              </div>

              {/* Riel */}
              <div className="journey-track">
                <div className="journey-fill" style={{ width: `${s.pct}%`, background: color }} />
              </div>

              {/* Pie: porcentaje + marca de etapa actual */}
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 6, marginTop: 5, minHeight: 16 }}>
                <span style={{ fontFamily: 'var(--font-mono)', fontSize: 10, color: esActual ? color : 'var(--text-muted)' }}>
                  {s.pct}%
                </span>
                {esActual && (
                  <span style={{
                    fontSize: 8.5, fontWeight: 800, letterSpacing: '0.1em',
                    color: 'var(--on-accent)', background: color,
                    padding: '1px 6px', borderRadius: 3, whiteSpace: 'nowrap',
                  }}>
                    AQUÍ
                  </span>
                )}
              </div>
            </button>
          )
        })}
      </div>

      {/* Detalle de la etapa señalada (o de la actual si no hay ninguna señalada) */}
      {detalle && (
        <div className="surface" style={{ marginTop: 14, padding: '10px 13px', display: 'flex', gap: 10, alignItems: 'flex-start' }}>
          <span style={{ width: 3, alignSelf: 'stretch', borderRadius: 2, background: STAGE_COLOR[detalle.state], flexShrink: 0 }} />
          <div style={{ minWidth: 0 }}>
            <div style={{ fontSize: 11.5, fontWeight: 700, color: STAGE_COLOR[detalle.state], letterSpacing: '0.04em', textTransform: 'uppercase' }}>
              {detalle.label} · {detalle.pct}% completado
            </div>
            <div style={{ fontSize: 12.5, color: 'var(--text-secondary)', lineHeight: 1.55, marginTop: 2 }}>
              {detalle.detail}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

// ─── Alerta viva (cronograma y alertas del backend) ───────────────────────────

export interface LiveAlert {
  id: string
  severity: 'ok' | 'leve' | 'critica'
  text: string
}

// ─── Modal genérico ───────────────────────────────────────────────────────────

export function Modal({ title, onClose, width = 520, hideClose = false, children }: {
  title: string
  onClose: () => void
  width?: number
  hideClose?: boolean
  children: React.ReactNode
}) {
  return (
    <div role="presentation" onClick={onClose}
      style={{ position: 'fixed', inset: 0, background: 'rgba(4,9,15,0.74)', backdropFilter: 'blur(3px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 200, padding: 16 }}>
      <div className="card" role="dialog" aria-modal="true" aria-labelledby="modal-title" onClick={e => e.stopPropagation()}
        style={{ width: '100%', maxWidth: width, maxHeight: '90vh', display: 'flex', flexDirection: 'column', borderRadius: 16, boxShadow: 'var(--shadow-lg)', overflow: 'hidden' }}>
        <div style={{ height: 4, flexShrink: 0, background: 'linear-gradient(90deg, var(--accent) 0%, var(--accent-emphasis) 100%)' }} />
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, padding: '20px 24px 16px', borderBottom: '1px solid var(--border)', flexShrink: 0 }}>
          <h3 id="modal-title" style={{ fontSize: 16 }}>{title}</h3>
          {!hideClose && (
            <button type="button" onClick={onClose} aria-label="Cerrar ventana" title="Cerrar" style={{ background: 'none', border: 'none', color: 'var(--text-muted)', fontSize: 22, cursor: 'pointer', padding: 0, lineHeight: 1 }}>×</button>
          )}
        </div>
        <div style={{ overflowY: 'auto', padding: '20px 24px 24px' }}>
          {children}
        </div>
      </div>
    </div>
  )
}

export function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label style={{ display: 'block', marginBottom: 12 }}>
      <span style={{ display: 'block', fontSize: 11, color: 'var(--text-muted)', marginBottom: 5, letterSpacing: '0.04em' }}>{label}</span>
      {children}
    </label>
  )
}

// ─── Menú de usuario (avatar + desplegable "Cerrar sesión") ───────────────────

export function UserMenu({ label, email, avatarColor = 'var(--accent)', avatarTextColor = 'var(--on-accent)', onLogout, onDark = false }: {
  label: string
  email: string
  avatarColor?: string
  avatarTextColor?: string
  onLogout: () => void
  /** true cuando el menú vive sobre un fondo de color sólido (barra verde). */
  onDark?: boolean
}) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [open])

  const nombreColor = onDark ? '#ffffff' : 'var(--text-primary)'
  const emailColor = onDark ? 'rgba(255,255,255,0.78)' : 'var(--text-muted)'

  return (
    <div ref={ref} style={{ position: 'relative', display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
      <button type="button" aria-expanded={open} aria-label={`Abrir menú de ${label}`} onClick={() => setOpen(v => !v)}
        style={{
          width: 32, height: 32, borderRadius: '50%', background: avatarColor,
          border: '1px solid var(--border)', boxShadow: 'var(--shadow-xs)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 13, fontWeight: 700,
          color: avatarTextColor, cursor: 'pointer', userSelect: 'none', flexShrink: 0,
        }}
      >
        {label[0].toUpperCase()}
      </button>
      <button type="button" aria-expanded={open} aria-label={`Abrir menú de ${label}`} style={{ lineHeight: 1.3, cursor: 'pointer', background: 'none', border: 'none', padding: 0, textAlign: 'left' }} onClick={() => setOpen(v => !v)}>
        <div style={{ fontSize: 13, fontWeight: 600, color: nombreColor }}>{label}</div>
        <div style={{ fontSize: 11, color: emailColor }}>{email}</div>
      </button>
      {open && (
        <div style={{
          position: 'absolute', top: '100%', right: 0, marginTop: 8,
          background: 'var(--bg-card)', border: '1px solid var(--border)',
          borderRadius: 10, boxShadow: 'var(--shadow-lg)',
          minWidth: 200, zIndex: 999, overflow: 'hidden',
        }}>
          <div style={{ padding: '10px 14px 8px', borderBottom: '1px solid var(--border)' }}>
            <div style={{ fontSize: 12, fontWeight: 600 }}>{label}</div>
            <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>{email}</div>
          </div>
          <button
            onClick={() => { setOpen(false); onLogout() }}
            style={{ display: 'flex', alignItems: 'center', gap: 8, width: '100%', padding: '10px 14px', background: 'none', border: 'none', fontSize: 13, color: 'var(--text-primary)', cursor: 'pointer', textAlign: 'left', fontFamily: 'var(--font-ui)' }}
            onMouseEnter={e => (e.currentTarget.style.background = 'var(--accent-soft)')}
            onMouseLeave={e => (e.currentTarget.style.background = 'none')}
          >
            <IconLogout size={14} />
            Cerrar sesión
          </button>
        </div>
      )}
    </div>
  )
}

// ─── Tarjeta de indicador (KPI) ───────────────────────────────────────────────

export function StatCard({ label, value, hint, tone = 'accent', icon }: {
  label: string
  value: string
  hint?: string
  tone?: 'accent' | 'warn' | 'danger' | 'info'
  icon?: React.ReactNode
}) {
  const cls = tone === 'accent' ? '' : ` ${tone}`
  const color =
    tone === 'warn' ? 'var(--alert-leve)'
      : tone === 'danger' ? 'var(--alert-critica)'
        : tone === 'info' ? 'var(--info)'
          : 'var(--accent)'
  return (
    <div className={`stat-card${cls}`}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
        {icon && <span style={{ color, display: 'flex' }}>{icon}</span>}
        <span style={{ fontSize: 11, fontWeight: 600, letterSpacing: '0.06em', textTransform: 'uppercase', color: 'var(--text-muted)' }}>
          {label}
        </span>
      </div>
      <div style={{ fontFamily: 'var(--font-display)', fontSize: 26, fontWeight: 700, color, lineHeight: 1.1 }}>
        {value}
      </div>
      {hint && <div style={{ fontSize: 11.5, color: 'var(--text-muted)', marginTop: 4, lineHeight: 1.45 }}>{hint}</div>}
    </div>
  )
}

// ─── Encabezado de sección ────────────────────────────────────────────────────

export function SectionHeader({ eyebrow, title, desc, actions }: {
  eyebrow?: string
  title: string
  desc?: string
  actions?: React.ReactNode
}) {
  return (
    <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 16, marginBottom: 16, flexWrap: 'wrap' }}>
      <div style={{ minWidth: 0 }}>
        {eyebrow && <div className="eyebrow">{eyebrow}</div>}
        <h3 style={{ fontSize: 18 }}>{title}</h3>
        {desc && <p className="section-sub" style={{ margin: '4px 0 0', maxWidth: 760 }}>{desc}</p>}
      </div>
      {actions && <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexShrink: 0 }}>{actions}</div>}
    </div>
  )
}
