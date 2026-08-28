// Pantalla de inicio de sesión — autenticación real contra el backend SICOT.
// Extraído 1:1 desde el App.tsx original de Figma Make — sin cambios visuales.

import { useState, type KeyboardEvent } from 'react'
import { SenaLogo } from '@/components/ui'
import { login } from '@/services/authService'
import { ApiError } from '@/services/api/client'
import type { AuthResponse } from '@/services/api/types'

// Colores del inicio de sesión — el panel de identidad usa la superficie más
// oscura del tema (nunca negro puro) con el verde SENA como acento; el panel
// del formulario usa el lienzo base. Todo sale de los tokens del tema, así que
// cambiar de preset en Configuración también cambia esta pantalla.
const LOGIN_STYLE = {
  // Panel de identidad
  idBg:            'var(--bg-rail)',
  idText:          'var(--text-primary)',
  idTextSub:       'var(--text-secondary)',
  idTextMuted:     'var(--text-muted)',
  idBadgeBorder:   'var(--accent-line)',
  idMark:          'var(--accent-line)',
  // Panel del formulario
  formBg:       'var(--bg-base)',
  formSurface:  'var(--bg-input)',
  accent:       'var(--accent)',
  accentTech:   'var(--accent-tech)',
  border:       'var(--border)',
  textPrimary:  'var(--text-primary)',
  textSecondary:'var(--text-secondary)',
  textMuted:    'var(--text-muted)',
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
    if (!correo || !password) { setError('Ingrese correo y contraseña.'); return }
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
          backgroundImage: `linear-gradient(rgba(63,197,90,0.07) 1px, transparent 1px), linear-gradient(90deg, rgba(63,197,90,0.07) 1px, transparent 1px)`,
          backgroundSize: '32px 32px',
        }} />

        {/* Glow inferior */}
        <div style={{
          position: 'absolute', bottom: -180, left: -60,
          width: 500, height: 500, borderRadius: '50%',
          background: `radial-gradient(circle, var(--accent-glow) 0%, transparent 70%)`,
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
                opacity: 0.5, pointerEvents: 'none', color: 'var(--accent)',
              }}>
              <line x1="14" y1="0" x2="14" y2="28" stroke="currentColor" strokeWidth="1" />
              <line x1="0" y1="14" x2="28" y2="14" stroke="currentColor" strokeWidth="1" />
              <circle cx="14" cy="14" r="4" stroke="currentColor" strokeWidth="1" fill="none" />
              <polyline points="8,0 0,0 0,8" stroke="currentColor" strokeWidth="1" fill="none" />
              <polyline points="20,0 28,0 28,8" stroke="currentColor" strokeWidth="1" fill="none" />
              <polyline points="0,20 0,28 8,28" stroke="currentColor" strokeWidth="1" fill="none" />
              <polyline points="28,20 28,28 20,28" stroke="currentColor" strokeWidth="1" fill="none" />
            </svg>
          )
        })}

        {/* Logo + wordmark */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 16, position: 'relative' }}>
          <div style={{ background: 'var(--accent-soft)', border: '1px solid var(--accent-line)', borderRadius: 10, padding: 6 }}>
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
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, opacity: 0.45 }}>
            <div style={{ flex: 1, height: 1, background: 'var(--accent)' }} />
            <svg width="12" height="12" viewBox="0 0 12 12" fill="none" style={{ color: 'var(--accent)' }}>
              <line x1="6" y1="0" x2="6" y2="12" stroke="currentColor" strokeWidth="1" />
              <line x1="0" y1="6" x2="12" y2="6" stroke="currentColor" strokeWidth="1" />
              <circle cx="6" cy="6" r="2.5" stroke="currentColor" strokeWidth="1" fill="none" />
            </svg>
            <div style={{ flex: 1, height: 1, background: 'var(--accent)' }} />
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
        borderLeft: '1px solid var(--border)',
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
            <label htmlFor="login-email" style={{ display: 'block', fontSize: 12, fontWeight: 500, color: s.textSecondary, marginBottom: 6 }}>
              Correo institucional
            </label>
            <div style={{ position: 'relative' }}>
              <span style={{ position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)', color: s.textMuted, fontSize: 13, fontFamily: 'var(--font-mono)' }}>@</span>
              <input
                id="login-email" name="email" autoComplete="username" type="email" value={email}
                onChange={e => setEmail(e.target.value)} onKeyDown={handleKey}
                placeholder="correo@soy.sena.edu.co"
                style={{
                  width: '100%', padding: '12px 12px 12px 36px',
                  background: s.formSurface, border: `1px solid ${s.border}`,
                  borderRadius: 8, color: s.textPrimary,
                  fontFamily: "'IBM Plex Sans', system-ui, sans-serif",
                  fontSize: 14, outline: 'none',
                }}
                onFocus={e => { e.currentTarget.style.borderColor = s.accent; e.currentTarget.style.boxShadow = '0 0 0 3px var(--accent-glow)' }}
                onBlur={e  => { e.currentTarget.style.borderColor = s.border; e.currentTarget.style.boxShadow = 'none' }}
              />
            </div>
          </div>

          {/* Password field */}
          <div style={{ marginBottom: 6 }}>
            <label htmlFor="login-password" style={{ display: 'block', fontSize: 12, fontWeight: 500, color: s.textSecondary, marginBottom: 6 }}>
              Contraseña
            </label>
            <div style={{ position: 'relative' }}>
              <span style={{ position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)', color: s.textMuted, fontSize: 11, fontFamily: 'var(--font-mono)', letterSpacing: '-0.04em' }}>••</span>
              <input
                id="login-password" name="password" autoComplete="current-password" type={showPw ? 'text' : 'password'} value={password}
                onChange={e => setPassword(e.target.value)} onKeyDown={handleKey}
                placeholder="••••••••••"
                style={{
                  width: '100%', padding: '12px 40px 12px 36px',
                  background: s.formSurface, border: `1px solid ${s.border}`,
                  borderRadius: 8, color: s.textPrimary,
                  fontFamily: "'IBM Plex Sans', system-ui, sans-serif",
                  fontSize: 14, outline: 'none',
                }}
                onFocus={e => { e.currentTarget.style.borderColor = s.accent; e.currentTarget.style.boxShadow = '0 0 0 3px var(--accent-glow)' }}
                onBlur={e  => { e.currentTarget.style.borderColor = s.border; e.currentTarget.style.boxShadow = 'none' }}
              />
              <button type="button" onClick={() => setShowPw(v => !v)} aria-label={showPw ? 'Ocultar contraseña' : 'Mostrar contraseña'} title={showPw ? 'Ocultar contraseña' : 'Mostrar contraseña'} style={{
                position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)',
                background: 'none', border: 'none', color: s.textMuted,
                cursor: 'pointer', fontSize: 14, padding: 0,
              }}>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  {showPw ? <><path d="M3 3l18 18" /><path d="M10.6 10.6a2 2 0 0 0 2.8 2.8" /><path d="M9.9 4.2A10.8 10.8 0 0 1 12 4c5 0 8.7 4 10 8a11.8 11.8 0 0 1-3.1 4.8" /><path d="M6.6 6.6C4.8 7.8 3.5 9.7 2 12c1.3 4 5 8 10 8 1 0 2-.2 2.9-.5" /></> : <><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z" /><circle cx="12" cy="12" r="3" /></>}
                </svg>
              </button>
            </div>
          </div>

          {error && (
            <div role="alert" aria-live="polite" style={{
              fontSize: 12, color: 'var(--alert-critica)', margin: '8px 0 0',
              padding: '8px 12px', background: 'var(--chip-red-bg)',
              border: '1px solid var(--alert-critica)', borderRadius: 6,
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

          {/* Ayuda de acceso.
              Es un texto, no un botón: SICOT no tiene autoservicio de
              restablecimiento de contraseña — la asigna el Administrador desde
              su panel. Un botón "¿Olvidó su contraseña?" que no hace nada solo
              hace perder el tiempo a quien no puede entrar. */}
          <p style={{ marginTop: 20, textAlign: 'center', fontSize: 12, color: s.textMuted, lineHeight: 1.6 }}>
            ¿Olvidó su contraseña? Solicite una nueva al Administrador del
            sistema; se la enviará a su correo institucional.
          </p>

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
