// Orquestador raíz de SICOT — enrutamiento por pantalla (login, bienvenida,
// panel supervisor, panel gestión, panel admin) y providers globales.
// Reestructurado a partir del App.tsx original de Figma Make: misma lógica,
// mismo JSX, mismos estilos — solo dividido en módulos para mantenibilidad.

import { useEffect, useState, useMemo, lazy, Suspense } from 'react'
import { Navigate, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom'
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
import type { AdminTab, Step, Tab } from '@/types/domain'
import type { AuthResponse, ContratoResponse, Rol } from '@/services/api/types'
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

// Ruta inicial de cada rol. La URL es la fuente de verdad de qué se ve; el rol
// solo decide a dónde se entra tras iniciar sesión. La autoridad sobre permisos
// sigue siendo el backend: una URL no es un control de acceso.
const rutaDeRol = (u: AuthResponse): string => {
  if (u.rol === 'SUPERVISOR') return '/supervisor/bandeja'
  if (u.rol === 'ADMINISTRADOR') return '/admin/dashboard'
  if (u.rol === 'GESTION') return '/gestion'
  return '/login'
}

const VISTAS_SUPERVISOR: Tab[] = ['bandeja', 'contrato', 'alertas', 'documentos', 'registros']
const VISTAS_ADMIN: AdminTab[] = ['dashboard', 'documentos', 'usuarios', 'firmas']

/** Una vista desconocida en la URL cae en la de por defecto en vez de romper. */
function vistaValida<T extends string>(valor: string | undefined, permitidas: T[], porDefecto: T): T {
  return permitidas.includes(valor as T) ? (valor as T) : porDefecto
}

function AppInner() {
  const [session, setSession] = useState<AuthResponse | null>(() => getSession())
  const navigate = useNavigate()
  const location = useLocation()
  const enLogin = location.pathname === '/login'
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
    navigate(rutaDeRol(auth), { replace: true })
  }

  const logout = () => {
    clearSession()
    setSession(null)
    // `replace` a propósito: tras cerrar sesión, el botón atrás no debe devolver
    // a una pantalla del panel que ya no se puede cargar.
    navigate('/login', { replace: true })
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
    () => (location.pathname.startsWith('/gestion') ? TOUR_GESTION : TOUR_SUPERVISOR),
    [location.pathname],
  )

  // Envoltura que protege una ruta.
  //
  // Dos comprobaciones, y ninguna es un control de seguridad: la autoridad sobre
  // permisos es el backend, que responde 403 a cualquier petición fuera de rol
  // sin importar qué URL tenga abierta el navegador. Esto es experiencia de uso.
  //
  //  1. Sin sesión -> al login.
  //  2. Con sesión de otro rol -> a SU panel, no a un error. Sin esto, un
  //     supervisor que escribiera /admin/usuarios veía el panel de
  //     administración pintándose y fallando a pedazos con 403 en consola:
  //     el backend hacía su trabajo, pero la pantalla resultante no le decía
  //     nada útil a la persona.
  const conSesion = (rolesPermitidos: Rol[], contenido: (s: AuthResponse) => React.ReactNode) => {
    if (!session) return <Navigate to="/login" replace />
    if (!rolesPermitidos.includes(session.rol)) return <Navigate to={rutaDeRol(session)} replace />
    return contenido(session)
  }

  return (
    <>
      <Suspense fallback={enLogin ? null : <CargandoPanel />}>
        <Routes>
          <Route
            path="/login"
            element={session ? <Navigate to={rutaDeRol(session)} replace /> : <LoginScreen onLogin={handleLogin} />}
          />

          <Route
            path="/supervisor/:vista"
            element={conSesion(['SUPERVISOR'], s => (
              <PanelSupervisorEnRuta
                usuario={s}
                steps={steps}
                setSteps={setSteps}
                contrato={contrato}
                cargandoContrato={cargandoContrato}
                errorContrato={errorContrato}
                onLogout={logout}
                onOpenSettings={() => setSettingsOpen(true)}
                onStartTour={() => setTourActive(true)}
                registros={registros}
                onRefreshRegistros={refreshRegistros}
              />
            ))}
          />
          <Route path="/supervisor" element={<Navigate to="/supervisor/bandeja" replace />} />

          <Route
            path="/gestion"
            element={conSesion(['GESTION'], s => (
              <GestionPanel
                usuario={s}
                onLogout={logout}
                onOpenSettings={() => setSettingsOpen(true)}
                onStartTour={() => setTourActive(true)}
              />
            ))}
          />

          <Route
            path="/admin/:vista"
            element={conSesion(['ADMINISTRADOR'], s => (
              <PanelAdminEnRuta
                usuario={s}
                onLogout={logout}
                onOpenSettings={() => setSettingsOpen(true)}
              />
            ))}
          />
          <Route path="/admin" element={<Navigate to="/admin/dashboard" replace />} />

          {/* Raíz y cualquier ruta desconocida: al panel del rol, o al login. */}
          <Route path="*" element={<Navigate to={session ? rutaDeRol(session) : '/login'} replace />} />
        </Routes>
      </Suspense>

      <Settings open={settingsOpen} onClose={() => setSettingsOpen(false)} />

      {!enLogin && session && (
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

/**
 * Traduce el segmento de la URL a la vista del panel y viceversa.
 *
 * Vive en un componente aparte porque `useParams` solo funciona dentro de la
 * ruta que declara el parámetro. Un segmento desconocido cae en la vista por
 * defecto en vez de romper.
 */
function PanelSupervisorEnRuta(props: Omit<React.ComponentProps<typeof SupervisorPanel>, 'vista' | 'onCambiarVista'>) {
  const { vista } = useParams()
  const navigate = useNavigate()
  return (
    <SupervisorPanel
      {...props}
      vista={vistaValida<Tab>(vista, VISTAS_SUPERVISOR, 'bandeja')}
      onCambiarVista={t => navigate(`/supervisor/${t}`)}
    />
  )
}

function PanelAdminEnRuta(props: Omit<React.ComponentProps<typeof AdminPanel>, 'vista' | 'onCambiarVista'>) {
  const { vista } = useParams()
  const navigate = useNavigate()
  return (
    <AdminPanel
      {...props}
      vista={vistaValida<AdminTab>(vista, VISTAS_ADMIN, 'dashboard')}
      onCambiarVista={t => navigate(`/admin/${t}`)}
    />
  )
}

export default function App() {
  return (
    <PrefsProvider>
      <AppInner />
    </PrefsProvider>
  )
}
