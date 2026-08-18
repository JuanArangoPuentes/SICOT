import React, { useState, useRef, useEffect } from 'react'
import senaLogo from '@/imports/image.png'

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

export function SicotBadge({ small }: { small?: boolean }) {
  return (
    <div style={{ display: 'inline-flex', alignItems: 'center', gap: small ? 7 : 9 }}>
      <SenaLogo size={small ? 22 : 30} />
      <div>
        <span style={{
          display: 'block',
          fontFamily: "'Space Grotesk', system-ui, sans-serif",
          fontSize: small ? 13 : 15, fontWeight: 700, color: 'var(--text-primary)',
          letterSpacing: '-0.01em', lineHeight: 1.1,
        }}>
          SICOT
        </span>
        {!small && (
          <span style={{ display: 'block', fontSize: 9, fontWeight: 500, color: 'var(--text-muted)', letterSpacing: '0.08em', lineHeight: 1 }}>
            CTMA · SENA
          </span>
        )}
      </div>
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
    unassigned: { bg: 'rgba(255,215,0,0.12)', color: 'var(--alert-leve)' },
    sugerido: { bg: 'rgba(255,215,0,0.12)', color: 'var(--alert-leve)' },
    conflicto: { bg: 'rgba(239,68,68,0.12)', color: 'var(--alert-critica)' },
    finished: { bg: 'rgba(107,114,128,0.15)', color: '#9ca3af' },
    inactive: { bg: 'rgba(107,114,128,0.15)', color: '#9ca3af' },
  }
  const s = map[type]
  return <span className="chip" style={{ background: s.bg, color: s.color }}>{text}</span>
}

// ─── Barra de progreso (etapas) ───────────────────────────────────────────────

export type StageState = 'ok' | 'warning' | 'critical' | 'idle'

export interface Stage {
  key: string
  label: string
  state: StageState
  pct: number
  detail: string
}

const STAGE_COLOR: Record<StageState, string> = {
  ok: 'var(--accent)',
  warning: 'var(--alert-leve)',
  critical: 'var(--alert-critica)',
  idle: 'rgba(204,204,204,0.22)',
}

// ─── Barra lineal SICOT v2.1 (GCCON-P-010) ───────────────────────────────────
// Single 6px track with per-stage tick markers; no segments.
export function StageProgressBar({ stages, onStageClick }: {
  stages: Stage[]
  onStageClick?: (key: string) => void
}) {
  const [hoveredKey, setHoveredKey] = React.useState<string | null>(null)
  const total = stages.length

  // Overall fill %: average of per-stage pct
  const overallPct = stages.length
    ? Math.round(stages.reduce((acc, s) => acc + s.pct, 0) / stages.length)
    : 0

  // Dominant state drives bar color
  const dominant: StageState =
    stages.some(s => s.state === 'critical') ? 'critical'
    : stages.some(s => s.state === 'warning') ? 'warning'
    : stages.every(s => s.state === 'ok') ? 'ok'
    : 'idle'

  const barColor = STAGE_COLOR[dominant]

  return (
    <div style={{ marginBottom: 20, maxWidth: 600, position: 'relative' }}>
      {/* Label row */}
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 10 }}>
        {stages.map((s, i) => {
          const isHov = hoveredKey === s.key
          const color = STAGE_COLOR[s.state]
          return (
            <button
              key={s.key}
              onClick={() => onStageClick?.(s.key)}
              onMouseEnter={() => setHoveredKey(s.key)}
              onMouseLeave={() => setHoveredKey(null)}
              style={{
                background: 'none', border: 'none', padding: 0, cursor: 'pointer',
                display: 'flex', flexDirection: 'column',
                alignItems: i === 0 ? 'flex-start' : i === total - 1 ? 'flex-end' : 'center',
                flex: 1, gap: 2,
              }}
            >
              <span style={{
                fontSize: 9, fontWeight: 700, letterSpacing: '0.1em', textTransform: 'uppercase',
                color: isHov ? color : s.state === 'idle' ? 'var(--text-muted)' : color,
                transition: 'color var(--t)',
              }}>
                {s.label}
              </span>
              <span style={{
                fontFamily: 'var(--font-mono)', fontSize: 9,
                color: isHov ? 'var(--accent-tech)' : 'var(--text-muted)',
                transition: 'color var(--t)',
              }}>
                {s.pct}%
              </span>
            </button>
          )
        })}
      </div>

      {/* Track */}
      <div style={{ position: 'relative', height: 6, background: 'var(--border)', borderRadius: 3, overflow: 'visible' }}>
        {/* Fill bar */}
        <div style={{
          position: 'absolute', top: 0, left: 0, height: '100%',
          width: `${overallPct}%`,
          background: barColor,
          borderRadius: 3,
          transition: 'width 0.5s ease, background 0.3s',
        }} />

        {/* Glow tip at filled end */}
        {overallPct > 0 && overallPct < 100 && (
          <div style={{
            position: 'absolute', top: '50%', left: `${overallPct}%`,
            transform: 'translate(-50%, -50%)',
            width: 10, height: 10, borderRadius: '50%',
            background: barColor,
            boxShadow: `0 0 8px 3px ${barColor}88`,
            transition: 'left 0.5s ease',
          }} />
        )}

        {/* Stage tick marks */}
        {stages.map((s, i) => {
          if (i === 0) return null
          const pos = (i / total) * 100
          const filled = overallPct >= pos
          return (
            <div key={s.key} style={{
              position: 'absolute', top: -2, left: `${pos}%`, transform: 'translateX(-50%)',
              width: 2, height: 10, borderRadius: 1,
              background: filled ? barColor : 'var(--border)',
              transition: 'background 0.3s',
            }} />
          )
        })}
      </div>

      {/* Tooltip */}
      {hoveredKey && (() => {
        const s = stages.find(x => x.key === hoveredKey)!
        const idx = stages.findIndex(x => x.key === hoveredKey)
        const leftPct = ((idx + 0.5) / total) * 100
        return (
          <div style={{
            position: 'absolute', top: 48, left: `${leftPct}%`, transform: 'translateX(-50%)',
            background: 'var(--bg-surface)', border: `1px solid ${STAGE_COLOR[s.state]}`,
            borderRadius: 10, padding: '10px 14px', zIndex: 20,
            fontSize: 12, color: 'var(--text-primary)',
            minWidth: 180, whiteSpace: 'normal',
            boxShadow: `0 4px 20px rgba(0,0,0,0.4), 0 0 0 1px ${STAGE_COLOR[s.state]}22`,
            pointerEvents: 'none',
          }}>
            <div style={{ fontFamily: 'var(--font-display)', fontWeight: 700, color: STAGE_COLOR[s.state], marginBottom: 4 }}>{s.label}</div>
            <div style={{ color: 'var(--text-secondary)', fontSize: 11, lineHeight: 1.45 }}>{s.detail}</div>
            <div style={{ marginTop: 6, fontFamily: 'var(--font-mono)', fontSize: 11, color: 'var(--accent-tech)' }}>
              {s.pct}% completado
            </div>
          </div>
        )
      })()}

      {/* Overall % */}
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 8, gap: 6, alignItems: 'center' }}>
        <span style={{ fontSize: 10, color: 'var(--text-muted)' }}>Avance global:</span>
        <span style={{ fontFamily: 'var(--font-mono)', fontSize: 11, fontWeight: 600, color: barColor }}>
          {overallPct}%
        </span>
      </div>
    </div>
  )
}

// ─── Alertas parpadeantes ─────────────────────────────────────────────────────

export interface LiveAlert {
  id: string
  severity: 'leve' | 'critica'
  text: string
}

export function AlertRail({ alerts, blink, onDismiss, onOpen }: {
  alerts: LiveAlert[]
  blink: boolean
  onDismiss: (id: string) => void
  onOpen?: (id: string) => void
}) {
  if (!alerts.length) return null
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      {alerts.slice(0, 3).map(a => {
        const critical = a.severity === 'critica'
        const color = critical ? 'var(--alert-critica)' : 'var(--alert-leve)'
        const colorVal = critical ? '#EF4B4B' : '#F2B84B'
        return (
          <div key={a.id}
            className={blink ? (critical ? 'blink-fast' : 'blink-slow') : undefined}
            style={{
              display: 'flex', alignItems: 'center', gap: 10,
              border: `1px solid ${colorVal}33`,
              borderLeft: `3px solid ${color}`,
              background: 'transparent',
              borderRadius: 8, padding: '8px 10px',
            }}>
            {/* Glow ring icon */}
            <div style={{
              width: 26, height: 26, borderRadius: '50%', flexShrink: 0,
              border: `1px solid ${color}`,
              boxShadow: `0 0 8px ${colorVal}44`,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 12, color,
            }}>
              {critical ? '!' : '·'}
            </div>
            <button onClick={() => onOpen?.(a.id)} style={{
              flex: 1, textAlign: 'left', background: 'none', border: 'none', padding: 0,
              cursor: onOpen ? 'pointer' : 'default', fontSize: 11.5, lineHeight: 1.45,
              color: 'var(--text-primary)', fontFamily: 'inherit',
            }}>
              <span style={{ color, fontWeight: 700, fontSize: 9, letterSpacing: '0.1em', display: 'block', marginBottom: 1 }}>
                {critical ? 'ALERTA CRÍTICA' : 'ALERTA LEVE'}
              </span>
              {a.text}
            </button>
            <button onClick={() => onDismiss(a.id)} title="Ocultar"
              style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: 16, lineHeight: 1, padding: 0, flexShrink: 0 }}>
              ×
            </button>
          </div>
        )
      })}
    </div>
  )
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
    <div onClick={onClose}
      style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.78)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 200, padding: 16 }}>
      <div className="card" onClick={e => e.stopPropagation()}
        style={{ width: '100%', maxWidth: width, maxHeight: '90vh', display: 'flex', flexDirection: 'column' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, padding: '24px 24px 18px', flexShrink: 0 }}>
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, fontFamily: 'var(--font-display, var(--font-ui))' }}>{title}</h3>
          {!hideClose && (
            <button onClick={onClose} style={{ background: 'none', border: 'none', color: 'var(--text-muted)', fontSize: 22, cursor: 'pointer', padding: 0, lineHeight: 1 }}>×</button>
          )}
        </div>
        <div style={{ overflowY: 'auto', padding: '0 24px 24px' }}>
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

// ─── User Menu (avatar + dropdown "Cerrar sesión") ────────────────────────────

export function UserMenu({ label, email, avatarColor = 'var(--accent)', avatarTextColor = '#03200D', onLogout }: {
  label: string
  email: string
  avatarColor?: string
  avatarTextColor?: string
  onLogout: () => void
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

  return (
    <div ref={ref} style={{ position: 'relative', display: 'flex', alignItems: 'center', gap: 8 }}>
      <div
        style={{ width: 32, height: 32, borderRadius: '50%', background: avatarColor, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 13, fontWeight: 700, color: avatarTextColor, cursor: 'pointer', userSelect: 'none', flexShrink: 0 }}
        onClick={() => setOpen(v => !v)}
      >
        {label[0].toUpperCase()}
      </div>
      <div style={{ lineHeight: 1.3, cursor: 'pointer' }} onClick={() => setOpen(v => !v)}>
        <div style={{ fontSize: 13, fontWeight: 600 }}>{label}</div>
        <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>{email}</div>
      </div>
      {open && (
        <div style={{
          position: 'absolute', top: '100%', right: 0, marginTop: 6,
          background: 'var(--bg-card)', border: '1px solid var(--border)',
          borderRadius: 8, boxShadow: '0 8px 24px rgba(0,0,0,0.18)',
          minWidth: 190, zIndex: 999, overflow: 'hidden',
        }}>
          <div style={{ padding: '10px 14px 8px', borderBottom: '1px solid var(--border)' }}>
            <div style={{ fontSize: 12, fontWeight: 600 }}>{label}</div>
            <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>{email}</div>
          </div>
          <button
            onClick={() => { setOpen(false); onLogout() }}
            style={{ display: 'flex', alignItems: 'center', gap: 8, width: '100%', padding: '9px 14px', background: 'none', border: 'none', fontSize: 13, color: 'var(--text-primary)', cursor: 'pointer', textAlign: 'left' }}
            onMouseEnter={e => (e.currentTarget.style.background = 'var(--accent-soft)')}
            onMouseLeave={e => (e.currentTarget.style.background = 'none')}
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
              <polyline points="16 17 21 12 16 7"/>
              <line x1="21" y1="12" x2="9" y2="12"/>
            </svg>
            Cerrar sesión
          </button>
        </div>
      )}
    </div>
  )
}
