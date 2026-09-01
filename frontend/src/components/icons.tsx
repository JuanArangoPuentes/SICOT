// Set mínimo de iconos en línea (estilo trazo, 24x24, currentColor) — sustituye
// a los emoji usados como iconografía para un acabado institucional consistente
// entre sistemas operativos y navegadores.

import type { CSSProperties } from 'react'

interface IconProps {
  size?: number
  style?: CSSProperties
  className?: string
}

const base = (size: number) => ({ width: size, height: size, flexShrink: 0 } as CSSProperties)

export function IconSettings({ size = 14, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
    </svg>
  )
}

export function IconPlay({ size = 12, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="currentColor" stroke="none">
      <polygon points="6 3 20 12 6 21 6 3" />
    </svg>
  )
}

export function IconClipboardList({ size = 26, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <rect x="5" y="3.5" width="14" height="18" rx="2" />
      <path d="M9 3.5V3a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v.5" />
      <path d="M9 11h6M9 15h6M9 7h2" />
    </svg>
  )
}

export function IconFileText({ size = 32, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <polyline points="14 2 14 8 20 8" />
      <line x1="8" y1="13" x2="16" y2="13" />
      <line x1="8" y1="17" x2="16" y2="17" />
    </svg>
  )
}

export function IconUpload({ size = 32, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
      <polyline points="17 8 12 3 7 8" />
      <line x1="12" y1="3" x2="12" y2="15" />
    </svg>
  )
}

export function IconLoader({ size = 28, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), animation: 'spin 0.9s linear infinite', ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
      <path d="M12 2a10 10 0 1 0 10 10" opacity="0.85" />
    </svg>
  )
}

export function IconCheckCircle({ size = 40, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
      <polyline points="22 4 12 14.01 9 11.01" />
    </svg>
  )
}

export function IconDownload({ size = 14, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
      <polyline points="7 10 12 15 17 10" />
      <line x1="12" y1="15" x2="12" y2="3" />
    </svg>
  )
}

export function IconTrash({ size = 14, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="3 6 5 6 21 6" />
      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
    </svg>
  )
}

export function IconLock({ size = 32, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <rect x="4" y="11" width="16" height="10" rx="2" />
      <path d="M8 11V7a4 4 0 0 1 8 0v4" />
    </svg>
  )
}

export function IconSignature({ size = 34, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <path d="M3 17c2-1 3-3 4-5.5S8.5 6 10 6s1.5 3 1 5-2 5-1 6 3-2 4.5-3.5S17 11 19 11" />
      <line x1="3" y1="21" x2="21" y2="21" />
    </svg>
  )
}

export function IconUsers({ size = 20, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
      <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
      <circle cx="9" cy="7" r="4" />
      <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
      <path d="M16 3.13a4 4 0 0 1 0 7.75" />
    </svg>
  )
}

export function IconAlertTriangle({ size = 16, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
      <line x1="12" y1="9" x2="12" y2="13" />
      <line x1="12" y1="17" x2="12.01" y2="17" />
    </svg>
  )
}

// ─── Avatares del Copiloto — un ícono con carácter propio por personaje,
// en vez de una letra suelta, manteniendo el mismo trazo lineal del resto
// del set. ────────────────────────────────────────────────────────────────

export function IconAvatarProfesional({ size = 24, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="7.5" r="3.3" />
      <path d="M5 21v-1.2A5.8 5.8 0 0 1 10.8 14h2.4A5.8 5.8 0 0 1 19 19.8V21" />
      <path d="M10.4 14.4 12 17l1.6-2.6" />
      <path d="M12 17v3.4" />
    </svg>
  )
}

export function IconAvatarLegal({ size = 24, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <line x1="12" y1="2.5" x2="12" y2="19" />
      <line x1="5" y1="6.5" x2="19" y2="6.5" />
      <path d="M5 6.5 2.2 12.2a2.9 2.9 0 0 0 5.6 0Z" />
      <path d="M19 6.5l-2.8 5.7a2.9 2.9 0 0 0 5.6 0Z" />
      <path d="M8 21h8" />
      <circle cx="12" cy="2.3" r="1" fill="currentColor" stroke="none" />
    </svg>
  )
}

export function IconAvatarBot({ size = 24, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <rect x="4.5" y="8.5" width="15" height="11" rx="3.5" />
      <line x1="12" y1="3.2" x2="12" y2="8.5" />
      <circle cx="12" cy="2.4" r="1.1" fill="currentColor" stroke="none" />
      <circle cx="9.2" cy="14" r="1.2" fill="currentColor" stroke="none" />
      <circle cx="14.8" cy="14" r="1.2" fill="currentColor" stroke="none" />
      <path d="M9 17.3c1 .8 4 .8 6 0" />
    </svg>
  )
}

export function IconAvatarGestor({ size = 24, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <rect x="4.5" y="3.5" width="15" height="18" rx="2.2" />
      <path d="M9 3.5V3a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v.5" />
      <path d="M8.3 12.5l2.1 2.1 4.3-4.3" />
      <line x1="8" y1="17.5" x2="16" y2="17.5" />
    </svg>
  )
}

export function IconAvatarSena({ size = 24, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 2.2 4.3 5.1v6.1c0 5.2 3.4 8.9 7.7 10.6 4.3-1.7 7.7-5.4 7.7-10.6V5.1Z" />
      <path d="M12 7.6l1.15 2.35 2.55.4-1.85 1.85.45 2.55L12 13.6l-2.3 1.15.45-2.55-1.85-1.85 2.55-.4Z" fill="currentColor" stroke="none" />
    </svg>
  )
}

/** Ícono activo del Copiloto — según el avatar elegido en Configuración. Sustituye a la letra suelta que se usaba antes. */
export function AvatarIcon({ id, size = 24, style, className }: IconProps & { id: string }) {
  const props = { size, style, className }
  switch (id) {
    case 'profesional': return <IconAvatarProfesional {...props} />
    case 'legal': return <IconAvatarLegal {...props} />
    case 'bot': return <IconAvatarBot {...props} />
    case 'gestor': return <IconAvatarGestor {...props} />
    case 'sena': return <IconAvatarSena {...props} />
    case 'custom': return <IconUpload {...props} />
    default: return <IconAvatarBot {...props} />
  }
}

// ─── Iconografía de navegación (barra lateral) ───────────────────────────────

export function IconInbox({ size = 18, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
      <path d="M22 12h-6l-2 3h-4l-2-3H2" />
      <path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z" />
    </svg>
  )
}

export function IconContract({ size = 18, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <path d="M14 2v6h6" />
      <path d="M8 13h5M8 17h8" />
    </svg>
  )
}

export function IconBell({ size = 18, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
      <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
      <path d="M13.73 21a2 2 0 0 1-3.46 0" />
    </svg>
  )
}

export function IconHistory({ size = 18, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
      <path d="M3 3v5h5" />
      <path d="M3.05 13A9 9 0 1 0 6 5.3L3 8" />
      <path d="M12 7v5l4 2" />
    </svg>
  )
}

export function IconChart({ size = 18, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
      <path d="M3 3v18h18" />
      <rect x="7" y="11" width="3" height="6" rx="1" />
      <rect x="12.5" y="7" width="3" height="10" rx="1" />
      <rect x="18" y="13" width="3" height="4" rx="1" />
    </svg>
  )
}

export function IconGrid({ size = 18, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="3" width="7" height="7" rx="1.5" />
      <rect x="14" y="3" width="7" height="7" rx="1.5" />
      <rect x="3" y="14" width="7" height="7" rx="1.5" />
      <rect x="14" y="14" width="7" height="7" rx="1.5" />
    </svg>
  )
}

export function IconChevron({ size = 14, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="9 18 15 12 9 6" />
    </svg>
  )
}

export function IconArrowRight({ size = 14, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <line x1="4" y1="12" x2="19" y2="12" />
      <polyline points="13 6 19 12 13 18" />
    </svg>
  )
}

export function IconCheck({ size = 14, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="4 12.5 9.5 18 20 6.5" />
    </svg>
  )
}

export function IconLogout({ size = 14, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
      <polyline points="16 17 21 12 16 7" />
      <line x1="21" y1="12" x2="9" y2="12" />
    </svg>
  )
}

export function IconClock({ size = 16, style, className }: IconProps) {
  return (
    <svg className={className} style={{ ...base(size), ...style }} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="9" />
      <polyline points="12 7 12 12 15.5 14" />
    </svg>
  )
}
