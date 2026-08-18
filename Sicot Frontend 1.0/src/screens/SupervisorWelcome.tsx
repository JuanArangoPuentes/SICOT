// Pantalla de bienvenida del Supervisor — puente entre login y el panel principal.
// Extraído 1:1 desde el App.tsx original de Figma Make — sin cambios visuales.

import { avatarGlyph, usePrefs } from '@/prefs'
import { SicotBadge } from '@/components/ui'
import { IconPlay } from '@/components/icons'
import type { ContratoResponse } from '@/services/api/types'

export default function SupervisorWelcome({ contrato, onVerContrato, onTutorial }: {
  contrato: ContratoResponse | null
  onVerContrato: () => void
  onTutorial: () => void
}) {
  const { prefs } = usePrefs()

  // Estado A: Supervisor CON contrato asignado
  if (contrato) {
    return (
      <div className="grid-bg" style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
        <div style={{ padding: '12px 20px', borderBottom: '1px solid var(--border)', display: 'flex', alignItems: 'center', gap: 10 }}>
          <SicotBadge small />
          <span style={{ fontSize: 11, color: 'var(--text-muted)', padding: '2px 8px', border: '1px solid var(--border)', borderRadius: 4 }}>Panel Supervisor</span>
        </div>
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: 24 }}>
          <div className="avatar-float" style={{ width: 64, height: 64, borderRadius: '50%', background: 'var(--bg-card)', border: '2px solid var(--accent)', boxShadow: '0 0 32px var(--accent-glow)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 28, marginBottom: 12 }}>{avatarGlyph(prefs.avatarId)}</div>
          <span style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.12em', color: 'var(--accent)', background: 'var(--accent-soft)', padding: '3px 10px', borderRadius: 4, marginBottom: 20 }}>
            {prefs.avatarName.toUpperCase()}
          </span>
          <div className="card" style={{ maxWidth: 420, width: '100%', padding: 28, textAlign: 'center' }}>
            <div style={{ width: 40, height: 2, background: 'var(--accent)', borderRadius: 1, margin: '0 auto 16px' }} />
            <h2 style={{ margin: '0 0 10px', fontSize: 20, fontWeight: 700, fontFamily: 'var(--font-display, var(--font-ui))', letterSpacing: '-0.01em' }}>Bienvenido a SICOT.</h2>
            <p style={{ color: 'var(--text-secondary)', fontSize: 14, lineHeight: 1.6, margin: '0 0 16px' }}>
              Tiene un contrato asignado — le acompañaré paso a paso en su ejecución.
            </p>
            <div style={{ background: 'var(--accent-soft)', border: '1px solid var(--accent-line)', borderRadius: 8, padding: '10px 14px', marginBottom: 20, fontSize: 13, color: 'var(--accent-tech)', fontFamily: 'var(--font-mono)', letterSpacing: '0.03em' }}>
              {contrato.numeroContrato}
            </div>
            <button className="btn-green" onClick={onVerContrato} style={{ width: '100%', padding: '12px 0', fontSize: 15 }}>
              Ver mi contrato asignado →
            </button>
            <button className="btn-ghost" onClick={onTutorial} style={{ width: '100%', padding: '10px 0', fontSize: 13, marginTop: 10, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}>
              <IconPlay size={10} /> Iniciar tutorial guiado
            </button>
          </div>
        </div>
        <div style={{ padding: '10px 20px', display: 'flex', justifyContent: 'flex-end' }}>
          <button className="btn-ghost" disabled title="Disponible en una fase posterior" style={{ padding: '4px 10px', fontSize: 12, opacity: 0.5, cursor: 'not-allowed' }}>? Ayuda</button>
        </div>
      </div>
    )
  }

  // Estado B: Supervisor SIN contrato asignado
  return (
    <div className="grid-bg" style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <div style={{ padding: '12px 20px', borderBottom: '1px solid var(--border)', display: 'flex', alignItems: 'center', gap: 10 }}>
        <SicotBadge small />
        <span style={{ fontSize: 11, color: 'var(--text-muted)', padding: '2px 8px', border: '1px solid var(--border)', borderRadius: 4 }}>Panel Supervisor</span>
      </div>
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: 24 }}>
        <div className="avatar-float" style={{ width: 64, height: 64, borderRadius: '50%', background: 'var(--bg-card)', border: '2px solid var(--accent)', boxShadow: '0 0 32px var(--accent-glow)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 28, marginBottom: 12 }}>{avatarGlyph(prefs.avatarId)}</div>
        <span style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.12em', color: 'var(--accent)', background: 'var(--accent-soft)', padding: '3px 10px', borderRadius: 4, marginBottom: 20 }}>
          {prefs.avatarName.toUpperCase()}
        </span>
        <div className="card" style={{ maxWidth: 420, width: '100%', padding: 28, textAlign: 'center' }}>
          <div style={{ width: 40, height: 2, background: 'var(--accent)', borderRadius: 1, margin: '0 auto 16px' }} />
          <h2 style={{ margin: '0 0 10px', fontSize: 20, fontWeight: 700, fontFamily: 'var(--font-display, var(--font-ui))', letterSpacing: '-0.01em' }}>No tiene un contrato asignado</h2>
          <p style={{ color: 'var(--text-secondary)', fontSize: 14, lineHeight: 1.6, margin: '0 0 20px' }}>
            Actualmente no tiene un contrato asignado para seguimiento. Cuando Gestión le asigne uno, la información aparecerá aquí.
          </p>
          <div style={{ background: 'var(--accent-soft)', border: '1px solid var(--accent-line)', borderRadius: 8, padding: '12px 16px', marginBottom: 20, fontSize: 13, color: 'var(--text-muted)' }}>
            <div style={{ fontSize: 11, fontWeight: 600, letterSpacing: '0.06em', marginBottom: 6, color: 'var(--accent)' }}>ESTADO</div>
            <div>Esperando asignación de Gestión y Contratación</div>
          </div>
          <button className="btn-ghost" onClick={onTutorial} style={{ width: '100%', padding: '12px 0', fontSize: 13, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}>
            <IconPlay size={10} /> Ver tutorial del proceso
          </button>
        </div>
      </div>
      <div style={{ padding: '10px 20px', display: 'flex', justifyContent: 'flex-end' }}>
        <button className="btn-ghost" disabled title="Disponible en una fase posterior" style={{ padding: '4px 10px', fontSize: 12, opacity: 0.5, cursor: 'not-allowed' }}>? Ayuda</button>
      </div>
    </div>
  )
}
