// Armazón común de la aplicación: barra lateral de navegación a la izquierda,
// cabecera con el título de la vista y el área de contenido.
//
// Reemplaza a la barra superior con pestañas que usaban los tres paneles: la
// navegación permanente a la izquierda es el patrón de los sistemas
// institucionales con los que ya trabaja el CTMA, deja el ancho completo para
// el contenido y hace que Supervisor, Gestión y Administración se vean como un
// mismo software y no como tres pantallas distintas.

import { useEffect, useState, type ReactNode } from 'react'
import { SenaLogo, UserMenu } from './ui'
import { IconChevron, IconSettings } from './icons'

export interface NavItem {
  id: string
  label: string
  icon: ReactNode
  /** Contador opcional a la derecha (p. ej. pendientes en la bandeja). */
  count?: number
  countTone?: 'alert' | 'normal'
  title?: string
  /**
   * La entrada se ve pero no se puede abrir todavía. Se usa cuando el rol tiene
   * esa sección pero aún no hay nada que mostrar (p. ej. un supervisor sin
   * contrato asignado): esconder las entradas haría creer que el sistema perdió
   * funcionalidad, y `title` explica por qué está inactiva.
   */
  disabled?: boolean
}

export interface NavGroup {
  label?: string
  items: NavItem[]
}

const RAIL_KEY = 'sicot.rail.collapsed'

function leerColapsada(): boolean {
  try {
    return localStorage.getItem(RAIL_KEY) === '1'
  } catch {
    return false
  }
}

export default function AppShell({
  roleBadge,
  groups,
  activeId,
  onNavigate,
  usuario,
  avatarColor,
  avatarTextColor,
  onLogout,
  onOpenSettings,
  title,
  subtitle,
  actions,
  children,
}: {
  /** Etiqueta del módulo/rol — se muestra en la cabecera y bajo la marca. */
  roleBadge: string
  groups: NavGroup[]
  activeId: string
  onNavigate: (id: string) => void
  usuario: { nombre: string; email: string }
  avatarColor?: string
  avatarTextColor?: string
  onLogout: () => void
  onOpenSettings: () => void
  title: string
  subtitle?: string
  actions?: ReactNode
  children: ReactNode
}) {
  const [collapsed, setCollapsed] = useState<boolean>(leerColapsada)

  useEffect(() => {
    try {
      localStorage.setItem(RAIL_KEY, collapsed ? '1' : '0')
    } catch {
      // Preferencia de comodidad: si el almacenamiento está bloqueado, la barra
      // simplemente vuelve a abrirse en la próxima sesión.
    }
  }, [collapsed])

  return (
    <div className="app-shell">
      {/* ── Barra lateral ── */}
      <nav className={`rail${collapsed ? ' collapsed' : ''}`} aria-label="Navegación principal">
        <div className="rail-brand">
          <SenaLogo size={30} />
          {!collapsed && (
            <div className="rail-label" style={{ minWidth: 0 }}>
              <div style={{ fontFamily: 'var(--font-display)', fontSize: 15, fontWeight: 700, lineHeight: 1.1, letterSpacing: '-0.01em' }}>
                SICOT
              </div>
              <div style={{ fontSize: 9.5, color: 'var(--text-muted)', letterSpacing: '0.08em', lineHeight: 1.3 }}>
                CTMA · SENA
              </div>
            </div>
          )}
        </div>

        <div style={{ flex: 1, overflowY: 'auto', overflowX: 'hidden', paddingBottom: 8 }}>
          {groups.map((grupo, gi) => (
            <div key={grupo.label ?? gi}>
              {grupo.label && !collapsed && <div className="rail-section rail-label">{grupo.label}</div>}
              {grupo.label && collapsed && <div style={{ height: 14 }} />}
              {grupo.items.map(item => (
                <button
                  key={item.id}
                  type="button"
                  data-tour={`nav-${item.id}`}
                  className={`nav-item${activeId === item.id ? ' active' : ''}`}
                  onClick={() => { if (!item.disabled) onNavigate(item.id) }}
                  disabled={item.disabled}
                  aria-current={activeId === item.id ? 'page' : undefined}
                  aria-disabled={item.disabled || undefined}
                  title={item.title ?? item.label}
                  style={{
                    ...(collapsed ? { justifyContent: 'center', padding: '10px 0' } : {}),
                    ...(item.disabled ? { opacity: 0.45, cursor: 'not-allowed' } : {}),
                  }}
                >
                  <span className="nav-icon">{item.icon}</span>
                  {!collapsed && <span className="rail-label">{item.label}</span>}
                  {!collapsed && item.count !== undefined && item.count > 0 && (
                    <span className={`nav-count ${item.countTone === 'alert' ? 'alert' : 'normal'}`}>{item.count}</span>
                  )}
                  {collapsed && item.count !== undefined && item.count > 0 && (
                    <span style={{
                      position: 'absolute', top: 6, right: 12, width: 7, height: 7, borderRadius: '50%',
                      background: item.countTone === 'alert' ? 'var(--alert-critica)' : 'var(--accent)',
                    }} />
                  )}
                </button>
              ))}
            </div>
          ))}
        </div>

        <div className="rail-footer">
          <button
            type="button"
            className="nav-item"
            onClick={onOpenSettings}
            title="Configuración"
            style={collapsed ? { justifyContent: 'center', padding: '10px 0' } : undefined}
          >
            <span className="nav-icon"><IconSettings size={16} /></span>
            {!collapsed && <span className="rail-label">Configuración</span>}
          </button>
          <button
            type="button"
            className="nav-item"
            onClick={() => setCollapsed(v => !v)}
            title={collapsed ? 'Expandir el menú' : 'Contraer el menú'}
            aria-label={collapsed ? 'Expandir el menú' : 'Contraer el menú'}
            style={collapsed ? { justifyContent: 'center', padding: '10px 0' } : undefined}
          >
            <span className="nav-icon">
              <IconChevron size={15} style={{ transform: collapsed ? 'none' : 'rotate(180deg)', transition: 'transform var(--t)' }} />
            </span>
            {!collapsed && <span className="rail-label">Contraer menú</span>}
          </button>
        </div>
      </nav>

      {/* ── Contenido ── */}
      <div className="app-main">
        <header className="app-header">
          <div style={{ minWidth: 0 }}>
            <h1 style={{ fontSize: 16, lineHeight: 1.2, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
              {title}
            </h1>
            {subtitle && (
              <div style={{ fontSize: 11.5, color: 'var(--text-muted)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {subtitle}
              </div>
            )}
          </div>
          <span className="role-badge">{roleBadge}</span>
          <div style={{ flex: 1 }} />
          {actions}
          <UserMenu
            label={usuario.nombre}
            email={usuario.email}
            avatarColor={avatarColor}
            avatarTextColor={avatarTextColor}
            onLogout={onLogout}
            onDark={false}
          />
        </header>

        <div className="app-content">{children}</div>
      </div>
    </div>
  )
}
