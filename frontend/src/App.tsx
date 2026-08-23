// Orquestador raíz de SICOT — enrutamiento por pantalla (login, bienvenida,
// panel supervisor, panel gestión, panel admin) y providers globales.
// Reestructurado a partir del App.tsx original de Figma Make: misma lógica,
// mismo JSX, mismos estilos — solo dividido en módulos para mantenibilidad.

import { useEffect, useState, useMemo } from 'react'
import { PrefsProvider } from '@/prefs'
import AdminPanel from '@/components/AdminPanel'
import AvatarLayer, { type TourStep } from '@/components/AvatarLayer'
import Settings from '@/components/Settings'
import type { Registro } from '@/components/Registros'
import LoginScreen from '@/screens/LoginScreen'
import SupervisorPanel from '@/screens/SupervisorPanel'
import GestionPanel from '@/screens/GestionPanel'
import type { Screen, Step } from '@/types/domain'
import type { AuthResponse, ContratoResponse } from '@/services/api/types'
import { clearSession, getSession } from '@/services/session'
import { onUnauthorized, setAuthToken } from '@/services/api/client'
import { getContratos } from '@/services/contratoService'
import { getRegistrosContrato } from '@/services/registroService'
import { mapRegistros } from '@/services/mappers'

// ─── Root ─────────────────────────────────────────────────────────────────────

const TOUR_SUPERVISOR: TourStep[] = [
  { selector: '[data-tour="progreso"]', text: 'Esta barra resume las 5 etapas del contrato. Pasa el mouse sobre cada segmento.' },
  { selector: '[data-tour="contrato"]', text: 'Aquí está tu contrato asignado con valor y vigencia.' },
  { selector: '[data-tour="alertas"]', text: 'Las alertas leves parpadean en amarillo; las críticas, en rojo.' },
  { selector: '[data-tour="tab-registros"]', text: 'En Registros consultas comunicaciones y firmas del contrato.' },
  { selector: '[data-tour="copiloto"]', text: 'El copiloto está siempre listo para guiarte paso a paso.' },
]

const TOUR_GESTION: TourStep[] = [
  { selector: '[data-tour="cargar"]', text: 'Empieza aquí para cargar un nuevo contrato en PDF o DOCX.' },
  { selector: '[data-tour="ficha"]', text: 'El sistema detecta el tipo de contrato automáticamente.' },
  { selector: '[data-tour="tabla"]', text: 'Confirma los datos antes de asignar el contrato al supervisor.' },
]

const screenFor = (u: AuthResponse): Screen => {
  if (u.rol === 'SUPERVISOR') return 'supervisor-panel'
  if (u.rol === 'ADMINISTRADOR') return 'admin-panel'
  if (u.rol === 'GESTION') return 'gestion-panel'
  return 'login'
}

function AppInner() {
  const [session, setSession] = useState<AuthResponse | null>(() => getSession())
  const [screen, setScreen] = useState<Screen>(() => {
    const s = getSession()
    return s ? screenFor(s) : 'login'
  })
  // Etapas del contrato real (autoridad: backend). Nunca se usan datos de
  // ejemplo como etapas reales: sin contrato el estado queda vacío y el panel
  // lo reemplaza al cargar las etapas del contrato asignado.
  const [steps, setSteps] = useState<Step[]>([])
  const [newContractNotif, setNewContractNotif] = useState(false)
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [tourActive, setTourActive] = useState(false)
  const [registros, setRegistros] = useState<Registro[]>([])
  const [contrato, setContrato] = useState<ContratoResponse | null>(null)
  // true mientras se consulta el contrato real del supervisor — evita que el
  // panel muestre "sin contrato asignado" por un instante antes de que la
  // respuesta real llegue.
  const [cargandoContrato, setCargandoContrato] = useState(false)

  const refreshRegistros = async () => {
    if (!contrato) return
    const lista = await getRegistrosContrato(contrato.id)
    setRegistros(mapRegistros(lista))
  }

  // Contrato asignado al supervisor (rol SUPERVISOR): el ACTIVO si existe.
  useEffect(() => {
    if (!session || session.rol !== 'SUPERVISOR') {
      setContrato(null)
      setCargandoContrato(false)
      return
    }
    let cancelado = false
    setCargandoContrato(true)
    getContratos(session.usuarioId)
      .then(lista => {
        if (cancelado) return
        const activo = lista.find(c => c.estado === 'ACTIVO') ?? lista[0] ?? null
        setContrato(activo)
      })
      .catch(() => {
        if (!cancelado) setContrato(null)
      })
      .finally(() => {
        if (!cancelado) setCargandoContrato(false)
      })
    return () => { cancelado = true }
  }, [session])

  // Registros de auditoría reales del contrato asignado (autoridad: backend)
  useEffect(() => {
    if (!contrato) {
      setRegistros([])
      return
    }
    let cancelado = false
    getRegistrosContrato(contrato.id)
      .then(lista => {
        if (!cancelado) setRegistros(mapRegistros(lista))
      })
      .catch(() => {})
    return () => { cancelado = true }
  }, [contrato])

  const handleLogin = (auth: AuthResponse) => {
    setSession(auth)
    setScreen(screenFor(auth))
  }

  const logout = () => {
    clearSession()
    setSession(null)
    setScreen('login')
    setTourActive(false)
  }

  useEffect(() => {
    onUnauthorized(() => logout())
  }, [])

  // Mantiene el token del cliente sincronizado con la sesión activa.
  // Al recargar la página la sesión se restaura desde localStorage y este
  // efecto reaplica el token; al cerrar sesión lo limpia.
  useEffect(() => {
    setAuthToken(session?.token ?? null)
  }, [session])

  const tour = useMemo(
    () => (screen === 'gestion-panel' ? TOUR_GESTION : TOUR_SUPERVISOR),
    [screen],
  )

  return (
    <>
      {screen === 'login' && <LoginScreen onLogin={handleLogin} />}
      {screen === 'supervisor-panel' && session && (
        <SupervisorPanel
          steps={steps}
          setSteps={setSteps}
          newContractNotif={newContractNotif}
          usuario={session}
          contrato={contrato}
          cargandoContrato={cargandoContrato}
          onLogout={logout}
          onOpenSettings={() => setSettingsOpen(true)}
          onStartTour={() => setTourActive(true)}
          registros={registros}
          onRefreshRegistros={refreshRegistros}
        />
      )}
      {screen === 'gestion-panel' && session && (
        <GestionPanel
          usuario={session}
          onNewContractAssigned={() => setNewContractNotif(true)}
          onLogout={logout}
          onOpenSettings={() => setSettingsOpen(true)}
          onStartTour={() => setTourActive(true)}
        />
      )}
      {screen === 'admin-panel' && session && (
        <AdminPanel usuario={session} onLogout={logout} onOpenSettings={() => setSettingsOpen(true)} />
      )}

      <Settings open={settingsOpen} onClose={() => setSettingsOpen(false)} />

      {screen !== 'login' && (
        <AvatarLayer
          tour={tour}
          tourActive={tourActive}
          onTourEnd={() => setTourActive(false)}
          onOpenChat={() => setSettingsOpen(false)}
        />
      )}
    </>
  )
}

export default function App() {
  return (
    <PrefsProvider>
      <AppInner />
    </PrefsProvider>
  )
}
