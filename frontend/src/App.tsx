// Orquestador raíz de SICOT — enrutamiento por pantalla (login, bienvenida,
// panel supervisor, panel gestión, panel admin) y providers globales.
// Reestructurado a partir del App.tsx original de Figma Make: misma lógica,
// mismo JSX, mismos estilos — solo dividido en módulos para mantenibilidad.

import { useEffect, useState, useMemo, lazy, Suspense } from 'react'
import { PrefsProvider } from '@/prefs'
import AvatarLayer, { type TourStep } from '@/components/AvatarLayer'
import Settings from '@/components/Settings'
import type { Registro } from '@/components/Registros'
import LoginScreen from '@/screens/LoginScreen'
import CargandoPanel from '@/components/CargandoPanel'

// Los tres paneles se cargan bajo demanda, no de entrada.
//
// Antes el build producía un único paquete de 755 kB: cualquiera que abriera
// SICOT descargaba los tres paneles de rol más Recharts, aunque solo pudiera
// entrar a uno. Cada persona usa exactamente un panel —el de su rol— y la
// pantalla de login no necesita ninguno.
//
// Importa especialmente para el despliegue local del supervisor y para la red
// del centro de formación, donde el ancho de banda no es el de una oficina.
const SupervisorPanel = lazy(() => import('@/screens/SupervisorPanel'))
const GestionPanel = lazy(() => import('@/screens/GestionPanel'))
const AdminPanel = lazy(() => import('@/screens/AdminPanel'))
import type { Screen, Step } from '@/types/domain'
import type { AuthResponse, ContratoResponse } from '@/services/api/types'
import { clearSession, getSession } from '@/services/session'
import { onUnauthorized, setAuthToken } from '@/services/api/client'
import { getContratos } from '@/services/contratoService'
import { getRegistrosContrato } from '@/services/registroService'
import { mapRegistros } from '@/services/mappers'

// ─── Root ─────────────────────────────────────────────────────────────────────

// Recorridos guiados. Se anclan a las entradas de la barra lateral porque
// existen en el DOM sea cual sea la vista abierta: los pasos anteriores
// apuntaban a pestañas y tarjetas que solo existían en una vista concreta (o
// que ya no existen), y el tutorial resaltaba el vacío.
const TOUR_SUPERVISOR: TourStep[] = [
  { selector: '[data-tour="nav-bandeja"]', text: 'Su bandeja de entrada: aquí ve lo que le falta y en qué paso va su contrato.' },
  { selector: '[data-tour="nav-contrato"]', text: 'En Contrato están el recorrido de las etapas, la ficha completa, el Copiloto y las gráficas.' },
  { selector: '[data-tour="nav-alertas"]', text: 'Las alertas del contrato, con el semáforo de cronograma calculado con sus fechas reales.' },
  { selector: '[data-tour="nav-documentos"]', text: 'Los documentos formales: el Copiloto los redacta y usted los revisa y firma.' },
  { selector: '[data-tour="nav-registros"]', text: 'La bitácora de todo lo que se ha ejecutado sobre el contrato; puede descargarla en CSV.' },
]

const TOUR_GESTION: TourStep[] = [
  { selector: '[data-tour="nav-contratos"]', text: 'El registro de contratos del Centro: todos los que ya están cargados en SICOT.' },
  { selector: '[data-tour="cargar"]', text: 'Empiece aquí para cargar la ficha de un contrato en PDF. El Copiloto propone los datos y usted los confirma.' },
  { selector: '[data-tour="tabla"]', text: 'Confirme los datos antes de asignar el contrato a un supervisor: aquí ve a quién quedó asignado cada uno.' },
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
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [tourActive, setTourActive] = useState(false)
  const [registros, setRegistros] = useState<Registro[]>([])
  const [contrato, setContrato] = useState<ContratoResponse | null>(null)
  // true mientras se consulta el contrato real del supervisor — evita que el
  // panel muestre "sin contrato asignado" por un instante antes de que la
  // respuesta real llegue.
  const [cargandoContrato, setCargandoContrato] = useState(false)
  const [errorContrato, setErrorContrato] = useState(false)

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
    setErrorContrato(false)
    getContratos(session.usuarioId)
      .then(lista => {
        if (cancelado) return
        const activo = lista.find(c => c.estado === 'ACTIVO') ?? lista[0] ?? null
        setContrato(activo)
      })
      .catch(err => {
        // Un fallo al consultar NO es lo mismo que "no tiene contrato": el panel
        // del supervisor pinta un estado definitivo ("Actualmente no tiene un
        // contrato asignado") que sería mentira si lo que ocurrió fue que el
        // backend no respondió. Se distingue con `errorContrato`.
        console.error('No se pudo cargar el contrato asignado:', err)
        if (!cancelado) {
          setContrato(null)
          setErrorContrato(true)
        }
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
      <Suspense fallback={screen === 'login' ? null : <CargandoPanel />}>
      {screen === 'supervisor-panel' && session && (
        <SupervisorPanel
          steps={steps}
          setSteps={setSteps}
          usuario={session}
          contrato={contrato}
          cargandoContrato={cargandoContrato}
          errorContrato={errorContrato}
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
          onLogout={logout}
          onOpenSettings={() => setSettingsOpen(true)}
          onStartTour={() => setTourActive(true)}
        />
      )}
      {screen === 'admin-panel' && session && (
        <AdminPanel usuario={session} onLogout={logout} onOpenSettings={() => setSettingsOpen(true)} />
      )}
      </Suspense>

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
