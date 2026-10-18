// Orquestador raíz de SICOT — enrutamiento por pantalla (login, bienvenida,
// panel supervisor, panel gestión, panel admin) y providers globales.
// Reestructurado a partir del App.tsx original de Figma Make: misma lógica,
// mismo JSX, mismos estilos — solo dividido en módulos para mantenibilidad.

import { useEffect, useState, useMemo, lazy, Suspense } from 'react'
import { Navigate, Route, Routes, useLocation, useNavigate, useNavigationType, useParams } from 'react-router-dom'
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
import { profundidadDeNavegacion, registrarNavegacion, reiniciarProfundidad } from '@/services/pilaDeNavegacion'
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
  {
    selector: '[data-tour="nav-bandeja"]',
    text: 'Su bandeja de entrada: aquí ve lo que le falta y en qué paso va su contrato.',
  },
  {
    selector: '[data-tour="nav-contrato"]',
    text: 'En Contrato están el recorrido de las etapas, la ficha completa, el Copiloto y las gráficas.',
  },
  {
    selector: '[data-tour="nav-alertas"]',
    text: 'Las alertas del contrato, con el semáforo de cronograma calculado con sus fechas reales.',
  },
  {
    selector: '[data-tour="nav-documentos"]',
    text: 'Los documentos formales: SICOT los arma con los datos del contrato y usted los revisa y firma.',
  },
  {
    selector: '[data-tour="nav-registros"]',
    text: 'La bitácora de todo lo que se ha ejecutado sobre el contrato; puede descargarla en CSV.',
  },
]

const TOUR_GESTION: TourStep[] = [
  {
    selector: '[data-tour="nav-contratos"]',
    text: 'El registro de contratos del Centro: todos los que ya están cargados en SICOT.',
  },
  {
    selector: '[data-tour="cargar"]',
    text: 'Empiece aquí para cargar la ficha de un contrato en PDF. El Copiloto propone los datos y usted los confirma.',
  },
  {
    selector: '[data-tour="tabla"]',
    text: 'Confirme los datos antes de asignar el contrato a un supervisor: aquí ve a quién quedó asignado cada uno.',
  },
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

// El contrato que el supervisor eligió en el selector, para volver a él al
// recargar. Es una comodidad de este equipo: si el almacenamiento no está
// disponible (ventana privada, datos borrados), se vuelve al contrato ACTIVO.
const claveContratoElegido = (usuarioId: number) => `sicot.contratoElegido.${usuarioId}`

function leerContratoElegido(usuarioId: number): number | null {
  try {
    const v = localStorage.getItem(claveContratoElegido(usuarioId))
    return v ? Number(v) : null
  } catch {
    return null
  }
}

function guardarContratoElegido(usuarioId: number, contratoId: number) {
  try {
    localStorage.setItem(claveContratoElegido(usuarioId), String(contratoId))
  } catch {
    // sin almacenamiento, el selector sigue funcionando en esta sesión
  }
}
const VISTAS_ADMIN: AdminTab[] = ['dashboard', 'seguimiento', 'documentos', 'usuarios', 'firmas']

/** Una vista desconocida en la URL cae en la de por defecto en vez de romper. */
function vistaValida<T extends string>(valor: string | undefined, permitidas: T[], porDefecto: T): T {
  return permitidas.includes(valor as T) ? (valor as T) : porDefecto
}

function AppInner() {
  const [session, setSession] = useState<AuthResponse | null>(() => getSession())
  const navigate = useNavigate()
  const location = useLocation()
  const tipoDeNavegacion = useNavigationType()
  const enLogin = location.pathname === '/login'
  // Etapas del contrato real (autoridad: backend). Nunca se usan datos de
  // ejemplo como etapas reales: sin contrato el estado queda vacío y el panel
  // lo reemplaza al cargar las etapas del contrato asignado.
  const [steps, setSteps] = useState<Step[]>([])
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [tourActive, setTourActive] = useState(false)
  const [registros, setRegistros] = useState<Registro[]>([])
  const [contrato, setContrato] = useState<ContratoResponse | null>(null)
  // Todos los contratos del supervisor. Antes solo existía `contrato`, el
  // primero ACTIVO de la lista: con un segundo contrato el primero quedaba
  // inaccesible desde el panel. El supervisor real lleva varios lotes a la vez
  // (Lote 4 y Lote 8, octubre de 2026), y el proceso pregunta qué pasa cuando
  // al supervisor que ya tiene SICOT le llega un contrato nuevo: que se suma.
  const [contratos, setContratos] = useState<ContratoResponse[]>([])
  // true mientras se consulta el contrato real del supervisor — evita que el
  // panel muestre "sin contrato asignado" por un instante antes de que la
  // respuesta real llegue.
  const [cargandoContrato, setCargandoContrato] = useState(false)
  const [errorContrato, setErrorContrato] = useState(false)
  // Cuántas veces se pidió abrir el Copiloto desde el avatar flotante. Es un
  // contador y no un booleano para que cada clic vuelva a abrirlo aunque el
  // supervisor lo haya plegado después del anterior.
  const [pedidosCopiloto, setPedidosCopiloto] = useState(0)

  const refreshRegistros = async () => {
    if (!contrato) return
    const lista = await getRegistrosContrato(contrato.id)
    setRegistros(mapRegistros(lista))
  }

  const seleccionarContrato = (id: number) => {
    const elegido = contratos.find((c) => c.id === id)
    if (!elegido || !session) return
    guardarContratoElegido(session.usuarioId, id)
    // Las etapas son las del contrato anterior hasta que lleguen las nuevas:
    // se vacían para no mostrar por un instante el avance de otro contrato.
    setSteps([])
    setRegistros([])
    setContrato(elegido)
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
      .then((lista) => {
        if (cancelado) return
        setContratos(lista)
        // El que eligió la última vez, si sigue siendo suyo; si no, el ACTIVO.
        const elegido = leerContratoElegido(session.usuarioId)
        const activo =
          lista.find((c) => c.id === elegido) ?? lista.find((c) => c.estado === 'ACTIVO') ?? lista[0] ?? null
        setContrato(activo)
      })
      .catch((err) => {
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
    return () => {
      cancelado = true
    }
  }, [session])

  // Registros de auditoría reales del contrato asignado (autoridad: backend)
  useEffect(() => {
    if (!contrato) {
      setRegistros([])
      return
    }
    let cancelado = false
    getRegistrosContrato(contrato.id)
      .then((lista) => {
        if (!cancelado) setRegistros(mapRegistros(lista))
      })
      .catch(() => {})
    return () => {
      cancelado = true
    }
  }, [contrato])

  const handleLogin = (auth: AuthResponse) => {
    setSession(auth)
    // La entrada a la sesión es la base de la pila: `replace` no mueve el
    // índice del historial, así que desde aquí se cuentan las vistas que se
    // apilen (ver services/pilaDeNavegacion.ts).
    reiniciarProfundidad()
    navigate(rutaDeRol(auth), { replace: true })
  }

  const logout = () => {
    clearSession()
    setSession(null)
    setTourActive(false)

    // Cerrar sesión tiene que dejar el historial como estaba al entrar, no solo
    // tapar la última entrada.
    //
    // Antes esto era un `navigate('/login', { replace: true })` a secas, y el
    // comentario decía que `replace` evitaba que «atrás» volviera a una
    // pantalla del panel. Reemplaza, sí, pero solo la entrada de arriba: las
    // que se apilaron al cambiar de vista siguen debajo. En el teléfono eso se
    // ve como que el botón «atrás» deja de funcionar —cada pulsación va a una
    // ruta del panel, el guardia de sesión la devuelve a `/login` y la pantalla
    // no cambia— hasta agotar tantas pulsaciones como vistas se visitaron. En
    // un arranque limpio, sin nada apilado, sí salía de la aplicación: por eso
    // el fallo solo aparecía DESPUÉS de cerrar sesión (punto 13 de
    // docs/operacion/PRUEBA_MANUAL_APK.md, observado el 08-10-2026).
    //
    // El navegador no permite borrar entradas, así que se retrocede sobre ellas
    // y es el guardia de sesión el que aterriza en `/login` con `replace` al no
    // encontrar sesión. El resultado es una pila de un solo elemento: «atrás»
    // vuelve a cerrar la aplicación, como en un arranque limpio.
    const apiladas = profundidadDeNavegacion()
    reiniciarProfundidad()
    if (apiladas > 0) navigate(-apiladas)
    else navigate('/login', { replace: true })
  }

  useEffect(() => {
    onUnauthorized(() => logout())
  }, [])

  // Lleva la cuenta de las entradas de historial que apila la sesión, que es lo
  // que `logout` necesita para dejar el historial como estaba al entrar. Se
  // apunta aquí, en un solo sitio, porque las vistas se cambian desde varios
  // componentes (la barra lateral, las alertas, el avatar del Copiloto) y lo
  // que importa no es quién navegó sino cuántas entradas hay.
  //
  // `location.key` identifica la entrada: sin él, StrictMode contaría dos veces
  // la misma navegación en desarrollo. La primera carga llega como POP con la
  // clave `default` y resta sobre una cuenta que ya está en cero, que es
  // inofensivo (la cuenta no baja de cero).
  useEffect(() => {
    registrarNavegacion(tipoDeNavegacion, location.key)
  }, [location.key, tipoDeNavegacion])

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

  // El avatar flotante abre el Copiloto del contrato. Solo hay uno que abrir en
  // el panel del supervisor con contrato asignado; en Gestión, Administración o
  // sin contrato el avatar flotante no se ofrece.
  const abrirCopiloto =
    session?.rol === 'SUPERVISOR' && contrato
      ? () => {
          setPedidosCopiloto((n) => n + 1)
          navigate('/supervisor/contrato')
        }
      : undefined

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
            element={
              session ? (
                <Navigate to={rutaDeRol(session)} replace />
              ) : (
                <LoginScreen onLogin={handleLogin} onOpenSettings={() => setSettingsOpen(true)} />
              )
            }
          />

          <Route
            path="/supervisor/:vista"
            element={conSesion(['SUPERVISOR'], (s) => (
              <PanelSupervisorEnRuta
                // Un panel nuevo por contrato: el sub-paso abierto, los
                // borradores y las cargas a medias son de un contrato y no deben
                // pasar al siguiente.
                key={contrato?.id ?? 'sin-contrato'}
                usuario={s}
                steps={steps}
                setSteps={setSteps}
                contrato={contrato}
                contratos={contratos}
                onSeleccionarContrato={seleccionarContrato}
                cargandoContrato={cargandoContrato}
                errorContrato={errorContrato}
                onLogout={logout}
                onOpenSettings={() => setSettingsOpen(true)}
                onStartTour={() => setTourActive(true)}
                registros={registros}
                onRefreshRegistros={refreshRegistros}
                pedidosCopiloto={pedidosCopiloto}
              />
            ))}
          />
          <Route path="/supervisor" element={<Navigate to="/supervisor/bandeja" replace />} />

          <Route
            path="/gestion"
            element={conSesion(['GESTION'], (s) => (
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
            element={conSesion(['ADMINISTRADOR'], (s) => (
              <PanelAdminEnRuta usuario={s} onLogout={logout} onOpenSettings={() => setSettingsOpen(true)} />
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
          onOpenChat={abrirCopiloto}
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
      onCambiarVista={(t) => navigate(`/supervisor/${t}`)}
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
      onCambiarVista={(t) => navigate(`/admin/${t}`)}
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
