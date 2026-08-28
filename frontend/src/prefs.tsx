import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'

export type PresetId =
  | 'oscuro-institucional'
  | 'oscuro-grafito'
  | 'claro-institucional'
  | 'alto-contraste'

export interface Prefs {
  preset: PresetId
  /**
   * Versión del esquema de tema guardado. Cuando el tema base de SICOT cambia
   * (p. ej. el paso a la paleta oscura institucional), este número sube y las
   * preferencias de color guardadas antes se descartan — sin esto, quien ya
   * había usado el sistema seguiría viendo la paleta vieja restaurada desde
   * localStorage y nunca vería el rediseño.
   */
  themeVersion: number
  // Core palette
  colorPrimary:    string  // → --accent
  colorEmphasis:   string  // → --accent-emphasis
  colorAccentTech: string  // → --accent-tech (data / mono values)
  colorSecondary:  string  // → --chip-blue
  colorBackground: string  // → --bg-base
  colorBgRail:     string  // → --bg-rail (barra lateral + cabecera)
  colorBgCard:     string  // → --bg-card
  colorBgElevated: string  // → --bg-elevated (encabezados de tabla)
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

/** Súbelo al cambiar la paleta base — invalida los colores ya guardados. */
export const THEME_VERSION = 3

export const DEFAULT_PREFS: Prefs = {
  preset:          'oscuro-institucional',
  themeVersion:    THEME_VERSION,
  colorPrimary:    '#3FC55A',
  colorEmphasis:   '#2FA347',
  colorAccentTech: '#E0B65C',
  colorSecondary:  '#5FC4E2',
  colorBackground: '#111C28',
  colorBgRail:     '#0B131D',
  colorBgCard:     '#172433',
  colorBgElevated: '#1D2D3E',
  colorBgSurface:  '#1B2A39',
  colorBgInput:    '#12202D',
  colorBorder:     '#2A3B4E',
  colorText:       '#E9EFF6',
  colorTextSecondary: '#A7BACE',
  colorTextMuted:  '#8698AD',
  colorAlertLeve:  '#E5A93C',
  colorAlertCritica: '#F2686B',
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
    label: 'Azul Institucional Oscuro',
    desc:  'Azul pizarra profundo con acento verde SENA — tema principal (predeterminado)',
    patch: {
      colorPrimary:       '#3FC55A',
      colorEmphasis:      '#2FA347',
      colorAccentTech:    '#E0B65C',
      colorSecondary:     '#5FC4E2',
      colorBackground:    '#111C28',
      colorBgRail:        '#0B131D',
      colorBgCard:        '#172433',
      colorBgElevated:    '#1D2D3E',
      colorBgSurface:     '#1B2A39',
      colorBgInput:       '#12202D',
      colorBorder:        '#2A3B4E',
      colorText:          '#E9EFF6',
      colorTextSecondary: '#A7BACE',
      colorTextMuted:     '#8698AD',
      colorAlertLeve:     '#E5A93C',
      colorAlertCritica:  '#F2686B',
      fontFamily: 'IBM Plex Sans', fontSize: 15, fontWeight: 400, transitionMs: 200,
      blinkAlerts: true, hoverEffects: true,
    },
  },
  'oscuro-grafito': {
    label: 'Grafito Oscuro',
    desc:  'Gris grafito neutro con acento verde SENA — alternativa sobria, sin tinte azul',
    patch: {
      colorPrimary:       '#42C765',
      colorEmphasis:      '#31A44E',
      colorAccentTech:    '#DDB264',
      colorSecondary:     '#68C0D8',
      colorBackground:    '#181A1D',
      colorBgRail:        '#111315',
      colorBgCard:        '#212428',
      colorBgElevated:    '#292D32',
      colorBgSurface:     '#25282D',
      colorBgInput:       '#1B1E21',
      colorBorder:        '#353A40',
      colorText:          '#ECEEF1',
      colorTextSecondary: '#B2B8C0',
      colorTextMuted:     '#949CA6',
      colorAlertLeve:     '#E3A84A',
      colorAlertCritica:  '#EF6A6D',
      fontFamily: 'IBM Plex Sans', fontSize: 15, fontWeight: 400, transitionMs: 200,
      blinkAlerts: true, hoverEffects: true,
    },
  },
  'claro-institucional': {
    label: 'Claro Institucional',
    desc:  'Fondo claro con acento verde SENA — para oficinas con mucha luz o impresión de pantalla',
    patch: {
      colorPrimary:       '#2C9A3C',
      colorEmphasis:      '#218030',
      colorAccentTech:    '#9A6A10',
      colorSecondary:     '#126E8F',
      colorBackground:    '#F4F6F8',
      colorBgRail:        '#12222E',
      colorBgCard:        '#FFFFFF',
      colorBgElevated:    '#EDF1F5',
      colorBgSurface:     '#F0F4F7',
      colorBgInput:       '#FFFFFF',
      colorBorder:        '#D8DFE6',
      colorText:          '#14202B',
      colorTextSecondary: '#4B5B6B',
      colorTextMuted:     '#7A8B9B',
      colorAlertLeve:     '#B8780A',
      colorAlertCritica:  '#C83030',
      fontFamily: 'IBM Plex Sans', fontSize: 15, fontWeight: 400, transitionMs: 200,
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
      colorSecondary:     '#4FD9FF',
      colorBackground:    '#000000',
      colorBgRail:        '#000000',
      colorBgCard:        '#0A0A0A',
      colorBgElevated:    '#141414',
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

export const AVATARS: Array<{ id: string; label: string; desc: string }> = [
  { id: 'profesional', label: 'Asistente Profesional', desc: 'Figura minimalista, traje azul' },
  { id: 'legal',       label: 'Experto Legal',          desc: 'Lentes y portapapeles' },
  { id: 'bot',         label: 'Bot Amigable',           desc: 'Diseño futurista y cálido' },
  { id: 'gestor',      label: 'Gestor Eficiente',       desc: 'Estilo corporativo moderno' },
  { id: 'sena',        label: 'Especialista SENA',      desc: 'Con branding institucional' },
  { id: 'custom',      label: 'Avatar Personalizado',   desc: 'Sube una imagen de 200×200 px' },
]

export const FONT_OPTIONS = ['IBM Plex Sans', 'Space Grotesk', 'IBM Plex Mono']

interface PrefsCtx {
  prefs: Prefs
  patch: (p: Partial<Prefs>) => void
  reset: () => void
}

const Ctx = createContext<PrefsCtx>({ prefs: DEFAULT_PREFS, patch: () => {}, reset: () => {} })

export const usePrefs = () => useContext(Ctx)

const STORAGE_KEY = 'sicot.prefs'

/** Preferencias que NO son de apariencia — sobreviven a un cambio de tema. */
const CLAVES_NO_VISUALES = [
  'avatarId', 'avatarName', 'avatarTone', 'avatarMode',
  'sound', 'alertPosition', 'alertDurationS', 'blinkAlerts', 'hoverEffects',
] as const

/**
 * Lee las preferencias guardadas.
 *
 * Se combinan sobre DEFAULT_PREFS en vez de usarse tal cual: si en una versión
 * futura se añade una preferencia nueva, las que quedaron guardadas antes no la
 * traerán, y sin esta mezcla el valor llegaría `undefined` a la UI.
 *
 * Si el tema guardado es de una versión anterior a THEME_VERSION, se descartan
 * los colores y la tipografía y se conservan solo las preferencias que no son
 * de apariencia (copiloto, notificaciones): así el rediseño se ve de verdad en
 * equipos donde ya había un tema guardado.
 *
 * Cualquier problema al leer (JSON corrupto, localStorage bloqueado por la
 * configuración del navegador) se ignora y se cae a los valores por defecto:
 * no poder restaurar el tema jamás debe impedir que la aplicación arranque.
 */
function cargarPrefs(): Prefs {
  try {
    const guardado = localStorage.getItem(STORAGE_KEY)
    if (!guardado) return DEFAULT_PREFS
    const previo = JSON.parse(guardado) as Partial<Prefs>
    if (previo.themeVersion !== THEME_VERSION) {
      const conservadas: Partial<Prefs> = {}
      for (const clave of CLAVES_NO_VISUALES) {
        if (previo[clave] !== undefined) (conservadas as Record<string, unknown>)[clave] = previo[clave]
      }
      return { ...DEFAULT_PREFS, ...conservadas, themeVersion: THEME_VERSION }
    }
    return { ...DEFAULT_PREFS, ...previo }
  } catch {
    return DEFAULT_PREFS
  }
}

/** rgba() a partir de un color hexadecimal (#RGB o #RRGGBB). */
function toRgba(hex: string, alpha: number): string {
  const c = hex.replace('#', '')
  const full = c.length === 3 ? c.split('').map(x => x + x).join('') : c
  const n = parseInt(full, 16)
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${alpha})`
}

/**
 * Color de texto legible sobre un fondo dado.
 *
 * Los botones sólidos de acento llevaban un verde casi negro fijo: con un
 * acento oscuro (o con el preset de alto contraste) el texto desaparecía. Aquí
 * se elige según la luminancia real del color de fondo.
 */
function textoSobre(hex: string): string {
  const c = hex.replace('#', '')
  const full = c.length === 3 ? c.split('').map(x => x + x).join('') : c
  const n = parseInt(full, 16)
  if (Number.isNaN(n)) return '#06210E'
  const canal = (v: number) => {
    const s = v / 255
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4)
  }
  const L =
    0.2126 * canal((n >> 16) & 255) +
    0.7152 * canal((n >> 8) & 255) +
    0.0722 * canal(n & 255)
  // Se comparan los dos candidatos por relación de contraste real en vez de
  // usar un umbral fijo de luminancia: con un umbral, el verde SENA (que cae
  // justo en la frontera) recibía texto blanco con apenas 2,3:1 de contraste.
  const contrasteOscuro = (L + 0.05) / (0.0111 + 0.05) // #06210E
  const contrasteClaro = 1.05 / (L + 0.05)             // #FFFFFF
  return contrasteOscuro >= contrasteClaro ? '#06210E' : '#FFFFFF'
}

export function PrefsProvider({ children }: { children: ReactNode }) {
  const [prefs, setPrefs] = useState<Prefs>(cargarPrefs)

  // Persistencia real. Antes el panel de Ajustes mostraba "✓ Cambios guardados"
  // pero no guardaba nada: al recargar se perdía todo.
  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(prefs))
    } catch {
      // Sin espacio o almacenamiento deshabilitado: la sesión actual sigue
      // funcionando, solo no sobrevive a una recarga.
    }
  }, [prefs])

  useEffect(() => {
    const r = document.documentElement.style
    const hex = prefs.colorPrimary

    // Backgrounds & borders — explicit per theme
    r.setProperty('--bg-base',     prefs.colorBackground)
    r.setProperty('--bg-rail',     prefs.colorBgRail)
    r.setProperty('--bg-card',     prefs.colorBgCard)
    r.setProperty('--bg-elevated', prefs.colorBgElevated)
    r.setProperty('--bg-surface',  prefs.colorBgSurface)
    r.setProperty('--bg-input',    prefs.colorBgInput)
    r.setProperty('--border',      prefs.colorBorder)
    r.setProperty('--border-soft', toRgba(prefs.colorBorder, 0.7))
    r.setProperty('--border-active', hex)
    r.setProperty('--step-pending', toRgba(prefs.colorTextMuted, 0.45))

    // Accent — derived from primary
    r.setProperty('--accent',          hex)
    r.setProperty('--accent-emphasis', prefs.colorEmphasis)
    r.setProperty('--accent-tech',     prefs.colorAccentTech)
    r.setProperty('--accent-dim',      toRgba(hex, 0.62))
    r.setProperty('--accent-glow',     toRgba(hex, 0.20))
    r.setProperty('--accent-soft',     toRgba(hex, 0.10))
    r.setProperty('--accent-line',     toRgba(hex, 0.26))
    r.setProperty('--grid-line',       toRgba(hex, 0.05))
    r.setProperty('--on-accent',       textoSobre(hex))

    // Text — explicit per theme
    r.setProperty('--text-primary',   prefs.colorText)
    r.setProperty('--text-secondary', prefs.colorTextSecondary)
    r.setProperty('--text-muted',     prefs.colorTextMuted)

    // Secondary chip color
    r.setProperty('--chip-blue',    prefs.colorSecondary)
    r.setProperty('--chip-blue-bg', toRgba(prefs.colorSecondary, 0.16))
    r.setProperty('--chip-gray',    prefs.colorTextSecondary)
    r.setProperty('--chip-gray-bg', toRgba(prefs.colorTextSecondary, 0.14))
    r.setProperty('--chip-green',   hex)
    r.setProperty('--chip-green-bg', toRgba(hex, 0.14))
    r.setProperty('--chip-red',     prefs.colorAlertCritica)
    r.setProperty('--chip-red-bg',  toRgba(prefs.colorAlertCritica, 0.14))
    r.setProperty('--info',         prefs.colorSecondary)

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
