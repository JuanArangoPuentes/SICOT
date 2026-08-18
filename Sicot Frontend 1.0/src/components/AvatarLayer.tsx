import { useEffect, useState } from 'react'
import { avatarGlyph, usePrefs } from '@/prefs'

export interface TourStep {
  selector: string
  text: string
}

/**
 * Capa del avatar del Copiloto.
 * - follower: avatar flotante que sigue suavemente el cursor y descansa abajo a la derecha.
 * - guide: tutorial asistido, el avatar se posiciona junto al elemento que explica.
 */
export default function AvatarLayer({ tour, tourActive, onTourEnd, onOpenChat }: {
  tour: TourStep[]
  tourActive: boolean
  onTourEnd: () => void
  onOpenChat: () => void
}) {
  const { prefs } = usePrefs()
  const [pos, setPos] = useState({ x: window.innerWidth - 110, y: window.innerHeight - 130 })
  const [step, setStep] = useState(0)
  const [rect, setRect] = useState<DOMRect | null>(null)
  const [reaction, setReaction] = useState<string | null>(null)

  const glyph = avatarGlyph(prefs.avatarId)
  const followerOn = prefs.avatarMode === 'follower' && !tourActive

  // Follower: sigue el cursor con retardo y vuelve a la esquina al quedar inactivo
  useEffect(() => {
    if (!followerOn) return
    let idle: ReturnType<typeof setTimeout>
    const move = (e: MouseEvent) => {
      setPos({ x: Math.min(e.clientX + 26, window.innerWidth - 96), y: Math.min(e.clientY + 26, window.innerHeight - 96) })
      clearTimeout(idle)
      idle = setTimeout(() => setPos({ x: window.innerWidth - 110, y: window.innerHeight - 130 }), 1600)
    }
    window.addEventListener('mousemove', move)
    return () => { window.removeEventListener('mousemove', move); clearTimeout(idle) }
  }, [followerOn])

  // Guide: localiza el elemento del paso actual
  useEffect(() => {
    if (!tourActive) { setRect(null); return }
    const target = document.querySelector(tour[step]?.selector ?? '')
    if (!target) { setRect(null); return }
    target.scrollIntoView({ behavior: 'smooth', block: 'center' })
    const update = () => setRect(target.getBoundingClientRect())
    update()
    window.addEventListener('resize', update)
    window.addEventListener('scroll', update, true)
    return () => { window.removeEventListener('resize', update); window.removeEventListener('scroll', update, true) }
  }, [tourActive, step, tour])

  useEffect(() => { if (tourActive) setStep(0) }, [tourActive])

  if (tourActive && tour.length) {
    const s = tour[step]
    const top = rect ? Math.min(rect.bottom + 16, window.innerHeight - 200) : window.innerHeight / 2
    const left = rect ? Math.min(Math.max(rect.left, 16), window.innerWidth - 400) : window.innerWidth / 2 - 190

    return (
      <>
        {rect && (
          <div style={{
            position: 'fixed', pointerEvents: 'none', zIndex: 300,
            top: rect.top - 6, left: rect.left - 6, width: rect.width + 12, height: rect.height + 12,
            border: '2px solid var(--accent)', borderRadius: 12,
            boxShadow: '0 0 0 9999px rgba(0,0,0,0.6), 0 0 28px var(--accent-glow)',
            transition: 'all 300ms cubic-bezier(0.4, 0, 0.2, 1)',
          }} />
        )}
        <div className="fade-in-up" style={{
          position: 'fixed', zIndex: 310, top, left, display: 'flex', alignItems: 'flex-start', gap: 12,
          transition: 'top 300ms cubic-bezier(0.4,0,0.2,1), left 300ms cubic-bezier(0.4,0,0.2,1)',
        }}>
          <div className="avatar-float" style={{
            width: 76, height: 76, borderRadius: '50%', flexShrink: 0, fontSize: 34,
            background: 'var(--bg-card)', border: '2px solid var(--accent)',
            boxShadow: '0 0 32px var(--accent-glow)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>{glyph}</div>
          <div className="card" style={{ maxWidth: 300, padding: '14px 16px', borderColor: 'var(--accent-line)' }}>
            <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.1em', color: 'var(--accent)', marginBottom: 6 }}>
              {prefs.avatarName.toUpperCase()} · PASO {step + 1}/{tour.length}
            </div>
            <p style={{ margin: 0, fontSize: 13.5, lineHeight: 1.55 }}>{s.text}</p>
            <div style={{ display: 'flex', gap: 8, marginTop: 14 }}>
              <button className="btn-ghost" style={{ padding: '6px 10px', fontSize: 12 }}
                disabled={step === 0} onClick={() => setStep(v => Math.max(0, v - 1))}>← Anterior</button>
              {step < tour.length - 1
                ? <button className="btn-green" style={{ padding: '6px 12px', fontSize: 12 }} onClick={() => setStep(v => v + 1)}>Siguiente →</button>
                : <button className="btn-green" style={{ padding: '6px 12px', fontSize: 12 }} onClick={onTourEnd}>Completar tutorial ✓</button>}
              <button className="btn-ghost" style={{ padding: '6px 10px', fontSize: 12, marginLeft: 'auto' }} onClick={onTourEnd}>✕</button>
            </div>
          </div>
        </div>
      </>
    )
  }

  if (!followerOn) return null

  return (
    <button
      onClick={() => { onOpenChat(); setReaction('👍'); setTimeout(() => setReaction(null), 1200) }}
      title={`${prefs.avatarName} — abrir chat`}
      className="avatar-float fade-in-up"
      style={{
        position: 'fixed', zIndex: 180, top: pos.y, left: pos.x,
        width: 64, height: 64, borderRadius: '50%', fontSize: 28, cursor: 'pointer',
        background: 'var(--bg-card)', border: '2px solid var(--accent)',
        boxShadow: '0 0 28px var(--accent-glow)',
        transition: 'top 320ms cubic-bezier(0.4,0,0.2,1), left 320ms cubic-bezier(0.4,0,0.2,1)',
      }}>
      {reaction ?? glyph}
    </button>
  )
}
