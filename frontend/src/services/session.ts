// Sesión del usuario autenticado — persistida en localStorage para sobrevivir
// a recargas de página. El token viaja en el cliente HTTP (api/client.ts).

import { alResponderConSesion, setAuthToken } from './api/client'
import { anotarContactoConElServidor, olvidarAccesoSinRed, sesionSinRedVigente } from './accesoSinRed'
import { colaDe, reenviarLaCola } from './colaDeCambios'
import { copiaLocalDe } from './copiaLocal'
import type { AuthResponse } from './api/types'

const SESSION_KEY = 'sicot.session'

/**
 * La sesión tal como se guarda. `sinConexion` marca la que se abrió sin red
 * con la contraseña recordada en el equipo (ADR-015, punto 4): no lleva token,
 * así que sirve para ver la copia y guardar cambios, no para enviarlos.
 */
export type Sesion = AuthResponse & { sinConexion?: boolean }

// Lee la fecha de expiración ("exp", en segundos desde epoch) del payload del
// JWT sin verificar la firma — solo para decidir si vale la pena mandar el
// token o cerrar sesión de una vez. La verificación real (firma, tiempo)
// siempre la hace el backend en cada petición; esto es una optimización de
// UX para no esperar a un 401 cuando ya sabemos que el token venció.
function tokenExpirado(token: string): boolean {
  try {
    const payload = token.split('.')[1]
    if (!payload) return false
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
    const { exp } = JSON.parse(json) as { exp?: number }
    if (!exp) return false
    return Date.now() >= exp * 1000
  } catch {
    return false
  }
}

/**
 * Lo que va con cualquier sesión que se abre o se retoma: de quién son la
 * copia local y la cola, y a quién se le apunta el contacto con el servidor.
 */
function adoptar(auth: Sesion) {
  setAuthToken(auth.sinConexion ? null : auth.token)
  // La copia local es de esta cuenta y de ninguna otra: dos supervisores
  // pueden compartir el portátil del Centro. Al cambiar de usuario,
  // `copiaLocalDe` vacía lo guardado antes de que nadie lo vea.
  void copiaLocalDe(String(auth.usuarioId))
  const cola = colaDe(String(auth.usuarioId))
  alResponderConSesion(() => anotarContactoConElServidor(auth.usuarioId))
  // Con token, lo que quedó guardado sin red sale ya: es justo el momento en
  // que el supervisor vuelve a tener con qué presentarse al servidor.
  if (!auth.sinConexion) void cola.then(() => reenviarLaCola())
}

export function getSession(): Sesion | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const auth = JSON.parse(raw) as Sesion
    const vencida = auth.sinConexion ? !sesionSinRedVigente(auth.usuarioId) : tokenExpirado(auth.token)
    if (vencida) {
      localStorage.removeItem(SESSION_KEY)
      setAuthToken(null)
      return null
    }
    // Adjunta el token al cliente HTTP de inmediato (durante el render, no en
    // un efecto): las peticiones de los paneles hijos se disparan en sus
    // propios efectos, que corren ANTES que el efecto de App.tsx que
    // sincroniza el token — sin esto, la primera petición tras recargar la
    // página siempre sale sin token, recibe 401 y cierra la sesión sola.
    adoptar(auth)
    return auth
  } catch {
    return null
  }
}

export function saveSession(auth: Sesion) {
  localStorage.setItem(SESSION_KEY, JSON.stringify(auth))
  adoptar(auth)
}

/**
 * Lo guardado, sin retomarlo. `getSession` además adopta la sesión (recarga
 * la cola, intenta enviarla); esto es para quien solo pregunta, como el aviso
 * que dice si se entró sin red.
 */
export function leerSesionGuardada(): Sesion | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    return raw ? (JSON.parse(raw) as Sesion) : null
  } catch {
    return null
  }
}

export function clearSession() {
  localStorage.removeItem(SESSION_KEY)
  setAuthToken(null)
  alResponderConSesion(null)
  // Cerrar sesión borra la copia: lo que el supervisor veía sin red era el
  // expediente de un contrato, y no puede quedar en el equipo para el
  // siguiente que entre.
  void copiaLocalDe(null)
  // Y con ella el acceso sin red, que sin copia abriría una sesión vacía.
  // Cerrar sesión es decir «terminé en este equipo»; el caso que el acceso
  // sin red cubre es otro —el token de 8 horas vence en campo sin que nadie
  // cierre nada— y ahí la copia sigue intacta.
  olvidarAccesoSinRed()
  // La cola, en cambio, no se borra: es trabajo suyo que todavía no llegó al
  // servidor. Deja de verse y se envía la próxima vez que entre esta cuenta.
  void colaDe(null)
}
