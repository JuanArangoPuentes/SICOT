import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'

export type PresetId = 'oscuro-institucional' | 'claro-institucional' | 'pastel-suave' | 'alto-contraste'

export interface Prefs {
  preset: PresetId
  // Core palette
  colorPrimary:    string  // → --accent
  colorEmphasis:   string  // → --accent-emphasis
  colorAccentTech: string  // → --accent-tech (data / mono values)
  colorSecondary:  string  // → --chip-blue
  colorBackground: string  // → --bg-base
  colorBgCard:     string  // → --bg-card
  colorBgSurface:  string  // → --bg-surface
  colorBgInput:    string  // → --bg-input
  colorBorder:     string  // → --border
  colorText:       string  // → --text-primary
  colorTextSecondary: string // → --text-secondary
  colorTextMuted:  string  // → --text-muted
  colorAlertLeve:  string
  colorAlertCritica: string
  // Typography
  fontFamily:   string
  fontSize:     number
  fontWeight:   400 | 600 | 700
  // Motion
  transitionMs: number
  blinkAlerts:  boolean
  hoverEffects: boolean
  // Notifications
  alertDurationS: number
  alertPosition:  'top-right' | 'top-center' | 'bottom-right'
  sound: boolean
  // Copiloto / avatar
  avatarId:   string
  avatarName: string
  avatarTone: 'formal' | 'amable' | 'tecnico'
  avatarMode: 'ghost' | 'follower' | 'guide'
}

export const DEFAULT_PREFS: Prefs = {
  preset:          'oscuro-institucional',
  colorPrimary:    '#39B54A',
  colorEmphasis:   '#2C9A3C',
  colorAccentTech: '#9A6A10',
  colorSecondary:  '#126E8F',
  colorBackground: '#FFFFFF',
  colorBgCard:     '#F4FAF5',
  colorBgSurface:  '#EAF4EC',
  colorBgInput:    '#FFFFFF',
  colorBorder:     '#C2DCC6',
  colorText:       '#0C1A0E',
  colorTextSecondary: '#3A6641',
  colorTextMuted:  '#7AAB80',
  colorAlertLeve:  '#B8780A',
  colorAlertCritica: '#C83030',
  fontFamily:      'IBM Plex Sans',
  fontSize:        15,
  fontWeight:      400,
  transitionMs:    200,
  blinkAlerts:     true,
  hoverEffects:    true,
  alertDurationS:  6,
  alertPosition:   'top-right',
  sound:           false,
  avatarId:        'sena',
  avatarName:      'Copiloto SICOT',
  avatarTone:      'amable',
  avatarMode:      'ghost',
}

type ThemePatch = Partial<Prefs>

export const PRESETS: Record<PresetId, { label: string; desc: string; patch: ThemePatch }> = {
  'oscuro-institucional': {
    label: 'Blanco Institucional SENA',
    desc:  'Fondo blanco con acento verde SENA — tema principal (predeterminado)',
    patch: {
      colorPrimary:       '#39B54A',
      colorEmphasis:      '#2C9A3C',
      colorAccentTech:    '#9A6A10',
      colorBackground:    '#FFFFFF',
      colorBgCard:        '#F4FAF5',
      colorBgSurface:     '#EAF4EC',
      colorBgInput:       '#FFFFFF',
      colorBorder:        '#C2DCC6',
      colorText:          '#0C1A0E',
      colorTextSecondary: '#3A6641',
      colorTextMuted:     '#7AAB80',
      colorAlertLeve:     '#B8780A',
      colorAlertCritica:  '#C83030',
      fontFamily: 'IBM Plex Sans', fontSize: 15, transitionMs: 200,
      blinkAlerts: true, hoverEffects: true,
    },
  },
  'claro-institucional': {
    label: 'Claro Institucional',
    desc:  'Tonos neutros sobre fondo claro — ideal en oficinas con mucha luz',
    patch: {
      colorPrimary:       '#147A5A',
      colorEmphasis:      '#1FAE79',
      colorAccentTech:    '#B8823A',
      colorBackground:    '#F6F7F9',
      colorBgCard:        '#FFFFFF',
      colorBgSurface:     '#EEF1F5',
      colorBgInput:       '#FFFFFF',
      colorBorder:        '#DEE3E9',
      colorText:          '#1A212B',
      colorTextSecondary: '#5C6675',
      colorTextMuted:     '#8A9BB0',
      colorAlertLeve:     '#C98A2D',
      colorAlertCritica:  '#D6453F',
      fontFamily: 'IBM Plex Sans', fontSize: 15, transitionMs: 200,
      blinkAlerts: true, hoverEffects: true,
    },
  },
  'pastel-suave': {
    label: 'Pastel Suave',
    desc:  'Paleta suave con acento verde salvia — menor fatiga visual en uso prolongado',
    patch: {
      colorPrimary:       '#6FA79A',
      colorEmphasis:      '#8FC6B4',
      colorAccentTech:    '#CBA46B',
      colorBackground:    '#F5F2FA',
      colorBgCard:        '#FFFFFF',
      colorBgSurface:     '#EDE8F5',
      colorBgInput:       '#FFFFFF',
      colorBorder:        '#E0D8EE',
      colorText:          '#33303F',
      colorTextSecondary: '#7C7690',
      colorTextMuted:     '#9D97B0',
      colorAlertLeve:     '#D9AF6B',
      colorAlertCritica:  '#E38585',
      fontFamily: 'IBM Plex Sans', fontSize: 15, transitionMs: 200,
      blinkAlerts: true, hoverEffects: true,
    },
  },
  'alto-contraste': {
    label: 'Alto Contraste',
    desc:  'Máxima legibilidad — accesibilidad AA/AAA, foco de teclado prominente',
    patch: {
      colorPrimary:       '#FFD400',
      colorEmphasis:      '#FFE444',
      colorAccentTech:    '#FFD400',
      colorBackground:    '#000000',
      colorBgCard:        '#0A0A0A',
      colorBgSurface:     '#111111',
      colorBgInput:       '#0A0A0A',
      colorBorder:        '#FFFFFF',
      colorText:          '#FFFFFF',
      colorTextSecondary: '#CCCCCC',
      colorTextMuted:     '#999999',
      colorAlertLeve:     '#FFE600',
      colorAlertCritica:  '#FF2D2D',
      fontFamily: 'IBM Plex Sans', fontSize: 16, fontWeight: 600, transitionMs: 0,
      blinkAlerts: true, hoverEffects: true,
    },
  },
}

export const AVATARS: Array<{ id: string; label: string; glyph: string; desc: string }> = [
  { id: 'profesional', label: 'Asistente Profesional', glyph: 'P', desc: 'Figura minimalista, traje azul' },
  { id: 'legal',       label: 'Experto Legal',          glyph: 'L', desc: 'Lentes y portapapeles' },
  { id: 'bot',         label: 'Bot Amigable',           glyph: 'IA', desc: 'Diseño futurista y cálido' },
  { id: 'gestor',      label: 'Gestor Eficiente',       glyph: 'G', desc: 'Estilo corporativo moderno' },
  { id: 'sena',        label: 'Especialista SENA',      glyph: 'S', desc: 'Con branding institucional' },
  { id: 'custom',      label: 'Avatar Personalizado',   glyph: '+', desc: 'Sube una imagen de 200×200 px' },
]

export function avatarGlyph(id: string) {
  return AVATARS.find(a => a.id === id)?.glyph ?? 'IA'
}

export const FONT_OPTIONS = ['IBM Plex Sans', 'Space Grotesk', 'IBM Plex Mono']

interface PrefsCtx {
  prefs: Prefs
  patch: (p: Partial<Prefs>) => void
  reset: () => void
}

const Ctx = createContext<PrefsCtx>({ prefs: DEFAULT_PREFS, patch: () => {}, reset: () => {} })

export const usePrefs = () => useContext(Ctx)

export function PrefsProvider({ children }: { children: ReactNode }) {
  const [prefs, setPrefs] = useState<Prefs>(DEFAULT_PREFS)

  useEffect(() => {
    const r = document.documentElement.style
    const hex = prefs.colorPrimary
    const toRgba = (h: string, a: number) => {
      const c = h.replace('#', '')
      const full = c.length === 3 ? c.split('').map(x => x + x).join('') : c
      const n = parseInt(full, 16)
      return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${a})`
    }

    // Backgrounds & borders — explicit per theme
    r.setProperty('--bg-base',    prefs.colorBackground)
    r.setProperty('--bg-card',    prefs.colorBgCard)
    r.setProperty('--bg-surface', prefs.colorBgSurface)
    r.setProperty('--bg-input',   prefs.colorBgInput)
    r.setProperty('--border',     prefs.colorBorder)
    r.setProperty('--border-active', hex)

    // Accent — derived from primary
    r.setProperty('--accent',          hex)
    r.setProperty('--accent-emphasis', prefs.colorEmphasis)
    r.setProperty('--accent-tech',     prefs.colorAccentTech)
    r.setProperty('--accent-dim',      toRgba(hex, 0.7))
    r.setProperty('--accent-glow',     toRgba(hex, 0.14))
    r.setProperty('--accent-soft',     toRgba(hex, 0.08))
    r.setProperty('--accent-line',     toRgba(hex, 0.2))
    r.setProperty('--grid-line',       toRgba(hex, 0.04))

    // Text — explicit per theme
    r.setProperty('--text-primary',   prefs.colorText)
    r.setProperty('--text-secondary', prefs.colorTextSecondary)
    r.setProperty('--text-muted',     prefs.colorTextMuted)

    // Secondary chip color
    r.setProperty('--chip-blue',    prefs.colorSecondary)
    r.setProperty('--chip-blue-bg', toRgba(prefs.colorSecondary, 0.15))

    // Alerts
    r.setProperty('--alert-leve',    prefs.colorAlertLeve)
    r.setProperty('--alert-critica', prefs.colorAlertCritica)

    // Typography
    r.setProperty('--font-base',        `${prefs.fontSize}px`)
    r.setProperty('--font-weight-base', String(prefs.fontWeight ?? 400))
    r.setProperty('--font-ui', `'${prefs.fontFamily}', 'IBM Plex Sans', system-ui, sans-serif`)

    // Motion
    r.setProperty('--t', `${prefs.transitionMs}ms`)

    document.body.classList.toggle('no-hover', !prefs.hoverEffects)
  }, [prefs])

  const patch = (p: Partial<Prefs>) => setPrefs(prev => ({ ...prev, ...p }))
  const reset = () => setPrefs(DEFAULT_PREFS)

  return <Ctx.Provider value={{ prefs, patch, reset }}>{children}</Ctx.Provider>
}
