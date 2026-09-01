// Pantallas del Supervisor cuando todavía no hay un contrato que mostrar.
//
// Son tres estados DISTINTOS y separarlos es el punto de este archivo: "no
// tiene contrato asignado", "todavía estoy consultando" y "la consulta falló".
// Mostrar el primero cuando lo que ocurrió fue el tercero llevaría al
// supervisor a creer que no tiene nada que hacer — el peor error posible en
// esta pantalla, porque no produce ningún síntoma visible.
//
// Extraídas de SupervisorPanel.tsx, que reunía las cinco vistas y estos tres
// estados en un solo archivo de más de 1.200 líneas.

import AppShell, { type NavGroup } from '@/components/AppShell'
import { IconBell, IconContract, IconFileText, IconHistory, IconInbox, IconPlay } from '@/components/icons'
import type { AuthResponse } from '@/services/api/types'

// ─── Armazón compartido por los estados sin contrato ──────────────────────────

function ShellMinimo({ usuario, onLogout, onOpenSettings, children }: {
  usuario: AuthResponse
  onLogout: () => void
  onOpenSettings: () => void
  children: React.ReactNode
}) {
  // Se muestran TODAS las secciones del panel, no solo la bandeja: si al no
  // haber contrato el menú se redujera a una entrada, parecería que el sistema
  // perdió funcionalidad. Las que dependen de un contrato quedan inactivas y
  // dicen por qué al pasar el cursor.
  const sinContrato = 'Disponible cuando Gestión le asigne un contrato'
  const groups: NavGroup[] = [
    {
      label: 'Supervisión',
      items: [
        { id: 'bandeja', label: 'Bandeja de entrada', icon: <IconInbox size={17} /> },
        { id: 'contrato', label: 'Contrato', icon: <IconContract size={17} />, disabled: true, title: sinContrato },
      ],
    },
    {
      label: 'Seguimiento',
      items: [
        { id: 'alertas', label: 'Alertas', icon: <IconBell size={17} />, disabled: true, title: sinContrato },
        { id: 'documentos', label: 'Documentos', icon: <IconFileText size={17} />, disabled: true, title: sinContrato },
        { id: 'registros', label: 'Registros', icon: <IconHistory size={17} />, disabled: true, title: sinContrato },
      ],
    },
  ]
  return (
    <AppShell
      roleBadge="Panel Supervisor"
      groups={groups}
      activeId="bandeja"
      onNavigate={() => {}}
      usuario={usuario}
      onLogout={onLogout}
      onOpenSettings={onOpenSettings}
      title="Bandeja de entrada"
      subtitle="Supervisión de contratos"
    >
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24, overflowY: 'auto' }}>
        {children}
      </div>
    </AppShell>
  )
}

// ─── Estado vacío — Supervisor sin contrato asignado ────────────────────────
export function EmptyContractState({ usuario, onLogout, onOpenSettings, onStartTour }: {
  usuario: AuthResponse
  onLogout: () => void
  onOpenSettings: () => void
  onStartTour: () => void
}) {
  return (
    <ShellMinimo usuario={usuario} onLogout={onLogout} onOpenSettings={onOpenSettings}>
      <div className="card" style={{ maxWidth: 480, width: '100%', padding: 32, textAlign: 'center' }}>
        <div style={{ width: 40, height: 2, background: 'var(--accent)', borderRadius: 1, margin: '0 auto 20px' }} />
        <h2 style={{ margin: '0 0 12px', fontSize: 18 }}>No tiene un contrato asignado</h2>
        <p style={{ color: 'var(--text-secondary)', fontSize: 14, lineHeight: 1.6, margin: '0 0 24px' }}>
          Actualmente no tiene un contrato asignado para seguimiento. Cuando Gestión le asigne uno, la
          información aparecerá aquí.
        </p>
        <div className="surface" style={{ padding: '12px 16px', marginBottom: 24, fontSize: 13, color: 'var(--text-secondary)', textAlign: 'left' }}>
          <div style={{ fontSize: 10.5, fontWeight: 700, letterSpacing: '0.09em', marginBottom: 6, color: 'var(--accent)' }}>ESTADO</div>
          <div>Esperando asignación de Gestión y Contratación</div>
        </div>
        <button className="btn-ghost" onClick={onStartTour} style={{ width: '100%', padding: '12px 0', fontSize: 13, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}>
          <IconPlay size={10} /> Ver tutorial del proceso
        </button>
      </div>
    </ShellMinimo>
  )
}

/**
 * No se pudo consultar el contrato.
 *
 * Es una pantalla distinta de {@link EmptyContractState} a propósito: afirmar
 * "no tiene un contrato asignado" cuando lo que pasó es que el backend no
 * respondió llevaría al supervisor a creer que no tiene nada que hacer.
 */
export function ErrorContratoState({ usuario, onLogout, onOpenSettings }: {
  usuario: AuthResponse
  onLogout: () => void
  onOpenSettings: () => void
}) {
  return (
    <ShellMinimo usuario={usuario} onLogout={onLogout} onOpenSettings={onOpenSettings}>
      <div className="card" style={{ maxWidth: 480, width: '100%', padding: 32, textAlign: 'center', borderColor: 'var(--alert-critica)' }}>
        <div style={{ width: 40, height: 2, background: 'var(--alert-critica)', borderRadius: 1, margin: '0 auto 20px' }} />
        <h2 style={{ margin: '0 0 12px', fontSize: 18 }}>No se pudo cargar su contrato</h2>
        <p style={{ color: 'var(--text-secondary)', fontSize: 14, lineHeight: 1.6, margin: '0 0 24px' }}>
          El sistema no pudo consultar sus contratos asignados. Esto no significa que no tenga
          ninguno: vuelva a intentarlo y, si el problema persiste, avise al área de sistemas.
        </p>
        <button className="btn-green" onClick={() => window.location.reload()} style={{ width: '100%', padding: '12px 0', fontSize: 13 }}>
          Reintentar
        </button>
      </div>
    </ShellMinimo>
  )
}

// ─── Estado de carga — mientras se consulta el contrato real del supervisor ──
export function CargandoContratoState({ usuario, onLogout, onOpenSettings }: {
  usuario: AuthResponse
  onLogout: () => void
  onOpenSettings: () => void
}) {
  return (
    <ShellMinimo usuario={usuario} onLogout={onLogout} onOpenSettings={onOpenSettings}>
      <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>Consultando su contrato asignado…</div>
    </ShellMinimo>
  )
}
