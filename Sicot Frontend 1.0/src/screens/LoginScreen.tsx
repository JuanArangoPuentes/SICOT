// Pantalla de inicio de sesión — autenticación real contra el backend SICOT.
// Extraído 1:1 desde el App.tsx original de Figma Make — sin cambios visuales.

import { useState, type KeyboardEvent } from 'react'
import { SenaLogo } from '@/components/ui'
import { login } from '@/services/authService'
import { ApiError } from '@/services/api/client'
import type { AuthResponse } from '@/services/api/types'

// Login colors — identity panel uses SENA green bg; form panel uses white
const LOGIN_STYLE = {
  // Panel de identidad (fondo verde SENA)
  idBg:            '#39B54A',
  idText:          '#FFFFFF',
  idTextSub:       'rgba(255,255,255,0.80)',
  idTextMuted:     'rgba(255,255,255,0.55)',
  idBadgeBorder:   'rgba(255,255,255,0.35)',
  idMark:          'rgba(255,255,255,0.50)',
  // Panel formulario (fondo blanco)
  formBg:       '#FFFFFF',
  formSurface:  '#F4FAF5',
  accent:       '#39B54A',
  accentTech:   '#9A6A10',
  border:       '#C2DCC6',
  textPrimary:  '#0C1A0E',
  textSecondary:'#3A6641',
  textMuted:    '#7AAB80',
} as const

export default function LoginScreen({ onLogin }: { onLogin: (auth: AuthResponse) => void }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPw, setShowPw] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const s = LOGIN_STYLE

  const attempt = async () => {
    if (busy) return
    const correo = email.trim().toLowerCase()
    if (!correo || !password) { setError('Ingresa correo y contraseña.'); return }
    setBusy(true)
    setError('')
    try {
      const auth = await login(correo, password)
      onLogin(auth)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se pudo conectar con el servidor.')
    } finally {
      setBusy(false)
    }
  }

  const handleKey = (e: KeyboardEvent) => { if (e.key === 'Enter') attempt() }

  return (
    <div style={{ display: 'flex', minHeight: '100vh', background: s.formBg, fontFamily: "'IBM Plex Sans', system-ui, sans-serif" }}>

      {/* ── Left: Identity panel (60%) — fondo verde SENA ── */}
      <div className="login-identity" style={{
        flex: '0 0 60%', display: 'flex', flexDirection: 'column',
        justifyContent: 'space-between', padding: '48px 56px',
        background: s.idBg, position: 'relative', overflow: 'hidden',
      }}>
        {/* Cuadrícula técnica sobre verde */}
        <div style={{
          position: 'absolute', inset: 0, pointerEvents: 'none',
          backgroundImage: `linear-gradient(rgba(255,255,255,0.08) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.08) 1px, transparent 1px)`,
          backgroundSize: '32px 32px',
        }} />

        {/* Glow inferior */}
        <div style={{
          position: 'absolute', bottom: -180, left: -60,
          width: 500, height: 500, borderRadius: '50%',
          background: `radial-gradient(circle, rgba(0,0,0,0.12) 0%, transparent 70%)`,
          pointerEvents: 'none',
        }} />

        {/* Marcas de registro en esquinas */}
        {(['top-left', 'top-right', 'bottom-left', 'bottom-right'] as const).map(corner => {
          const top = corner.startsWith('top')
          const left = corner.endsWith('left')
          return (
            <svg key={corner} width="28" height="28" viewBox="0 0 28 28" fill="none"
              style={{
                position: 'absolute',
                top: top ? 12 : undefined, bottom: !top ? 12 : undefined,
                left: left ? 12 : undefined, right: !left ? 12 : undefined,
                opacity: 0.30, pointerEvents: 'none',
              }}>
              <line x1="14" y1="0" x2="14" y2="28" stroke="white" strokeWidth="1" />
              <line x1="0" y1="14" x2="28" y2="14" stroke="white" strokeWidth="1" />
              <circle cx="14" cy="14" r="4" stroke="white" strokeWidth="1" fill="none" />
              <polyline points="8,0 0,0 0,8" stroke="white" strokeWidth="1" fill="none" />
              <polyline points="20,0 28,0 28,8" stroke="white" strokeWidth="1" fill="none" />
              <polyline points="0,20 0,28 8,28" stroke="white" strokeWidth="1" fill="none" />
              <polyline points="28,20 28,28 20,28" stroke="white" strokeWidth="1" fill="none" />
            </svg>
          )
        })}

        {/* Logo + wordmark */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 16, position: 'relative' }}>
          <div style={{ background: 'rgba(255,255,255,0.15)', borderRadius: 10, padding: 6, backdropFilter: 'blur(4px)' }}>
            <SenaLogo size={36} />
          </div>
          <div>
            <div style={{ fontFamily: "'Space Grotesk', system-ui, sans-serif", fontSize: 22, fontWeight: 700, color: s.idText, letterSpacing: '-0.01em' }}>
              SICOT
            </div>
            <div style={{ fontSize: 11, color: s.idTextMuted, letterSpacing: '0.06em', fontWeight: 500 }}>
              CTMA · SENA
            </div>
          </div>
        </div>

        {/* Center content */}
        <div style={{ position: 'relative' }}>
          <div style={{
            display: 'inline-block', fontSize: 10, fontWeight: 700, letterSpacing: '0.12em',
            color: s.idText, border: `1px solid ${s.idBadgeBorder}`,
            padding: '4px 12px', borderRadius: 4, marginBottom: 24,
          }}>
            CENTRO TECNOLÓGICO DEL MOBILIARIO
          </div>

          <h1 style={{
            fontFamily: "'Space Grotesk', system-ui, sans-serif",
            fontSize: 36, fontWeight: 700, lineHeight: 1.2,
            color: s.idText, margin: '0 0 12px', letterSpacing: '-0.02em',
          }}>
            Sistema Inteligente<br />de Gestión y<br />Acompañamiento<br />de Contratos
          </h1>

          <p className="identity-tagline" style={{
            fontSize: 15, color: s.idTextSub, margin: '0 0 40px',
            lineHeight: 1.6, maxWidth: 380, fontStyle: 'italic',
          }}>
            "Precisión en cada etapa del contrato"
          </p>

          {/* Separador decorativo */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, opacity: 0.35 }}>
            <div style={{ flex: 1, height: 1, background: 'white' }} />
            <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
              <line x1="6" y1="0" x2="6" y2="12" stroke="white" strokeWidth="1" />
              <line x1="0" y1="6" x2="12" y2="6" stroke="white" strokeWidth="1" />
              <circle cx="6" cy="6" r="2.5" stroke="white" strokeWidth="1" fill="none" />
            </svg>
            <div style={{ flex: 1, height: 1, background: 'white' }} />
          </div>
        </div>

        {/* Bottom version */}
        <div style={{ fontSize: 11, color: s.idTextMuted, position: 'relative' }}>
          SICOT v3.0 · GCCON-P-010
        </div>
      </div>

      {/* ── Right: Form panel (40%) — fondo blanco ── */}
      <div className="login-form-panel" style={{
        flex: '0 0 40%', display: 'flex', flexDirection: 'column',
        alignItems: 'center', justifyContent: 'center',
        background: s.formBg,
        borderLeft: `3px solid rgba(57,181,74,0.25)`,
        padding: '48px 40px',
      }}>
        <div style={{ width: '100%', maxWidth: 340 }}>
          {/* Form header */}
          <div style={{ marginBottom: 32, textAlign: 'center' }}>
            <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 16 }}>
              <SenaLogo size={48} />
            </div>
            <h2 style={{
              fontFamily: "'Space Grotesk', system-ui, sans-serif",
              fontSize: 20, fontWeight: 600, color: s.textPrimary,
              margin: '0 0 6px', letterSpacing: '-0.01em',
            }}>
              Acceso al sistema
            </h2>
            <p style={{ fontSize: 13, color: s.textSecondary, margin: 0 }}>
              Sistema de Gestión y Supervisión<br />de Contratos — CTMA
            </p>
          </div>

          {/* Email field */}
          <div style={{ marginBottom: 14 }}>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 500, color: s.textSecondary, marginBottom: 6 }}>
              Correo institucional
            </label>
            <div style={{ position: 'relative' }}>
              <span style={{ position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)', color: s.textMuted, fontSize: 13, fontFamily: 'var(--font-mono)' }}>@</span>
              <input
                type="email" value={email}
                onChange={e => setEmail(e.target.value)} onKeyDown={handleKey}
                placeholder="correo@soy.sena.edu.co"
                style={{
                  width: '100%', padding: '12px 12px 12px 36px',
                  background: s.formSurface, border: `1px solid ${s.border}`,
                  borderRadius: 8, color: s.textPrimary,
                  fontFamily: "'IBM Plex Sans', system-ui, sans-serif",
                  fontSize: 14, outline: 'none',
                }}
                onFocus={e => { e.currentTarget.style.borderColor = s.accent; e.currentTarget.style.boxShadow = `0 0 0 3px rgba(57,181,74,0.15)` }}
                onBlur={e  => { e.currentTarget.style.borderColor = s.border; e.currentTarget.style.boxShadow = 'none' }}
              />
            </div>
          </div>

          {/* Password field */}
          <div style={{ marginBottom: 6 }}>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 500, color: s.textSecondary, marginBottom: 6 }}>
              Contraseña
            </label>
            <div style={{ position: 'relative' }}>
              <span style={{ position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)', color: s.textMuted, fontSize: 11, fontFamily: 'var(--font-mono)', letterSpacing: '-0.04em' }}>••</span>
              <input
                type={showPw ? 'text' : 'password'} value={password}
                onChange={e => setPassword(e.target.value)} onKeyDown={handleKey}
                placeholder="••••••••••"
                style={{
                  width: '100%', padding: '12px 40px 12px 36px',
                  background: s.formSurface, border: `1px solid ${s.border}`,
                  borderRadius: 8, color: s.textPrimary,
                  fontFamily: "'IBM Plex Sans', system-ui, sans-serif",
                  fontSize: 14, outline: 'none',
                }}
                onFocus={e => { e.currentTarget.style.borderColor = s.accent; e.currentTarget.style.boxShadow = `0 0 0 3px rgba(57,181,74,0.15)` }}
                onBlur={e  => { e.currentTarget.style.borderColor = s.border; e.currentTarget.style.boxShadow = 'none' }}
              />
              <button onClick={() => setShowPw(v => !v)} style={{
                position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)',
                background: 'none', border: 'none', color: s.textMuted,
                cursor: 'pointer', fontSize: 14, padding: 0,
              }}>
                <span style={{ fontSize: 10, fontWeight: 600, letterSpacing: '0.04em' }}>{showPw ? 'OCU' : 'VER'}</span>
              </button>
            </div>
          </div>

          {error && (
            <div style={{
              fontSize: 12, color: '#C83030', margin: '8px 0 0',
              padding: '8px 12px', background: 'rgba(200,48,48,0.06)',
              border: '1px solid rgba(200,48,48,0.2)', borderRadius: 6,
            }}>
              {error}
            </div>
          )}

          {/* Submit */}
          <button className="btn-green" onClick={attempt} disabled={busy} style={{
            width: '100%', padding: '13px 0', fontSize: 15, marginTop: 24,
            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
            borderRadius: 8, fontFamily: "'Space Grotesk', system-ui, sans-serif",
            fontWeight: 600, letterSpacing: '0.01em', opacity: busy ? 0.6 : undefined,
            cursor: busy ? 'default' : 'pointer',
          }}>
            {busy ? 'Verificando…' : 'Ingresar →'}
          </button>

          {/* Help link */}
          <div style={{ marginTop: 20, textAlign: 'center' }}>
            <button style={{ background: 'none', border: 'none', color: s.textMuted, fontSize: 12, cursor: 'pointer', textDecoration: 'underline' }}>
              ¿Olvidaste tu contraseña?
            </button>
          </div>

          <div style={{ marginTop: 32, padding: '12px 0', borderTop: `1px solid ${s.border}`, textAlign: 'center' }}>
            <span style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.1em', color: s.textMuted }}>
              ACCESO RESTRINGIDO · SISTEMA INSTITUCIONAL
            </span>
          </div>
        </div>
      </div>
    </div>
  )
}
