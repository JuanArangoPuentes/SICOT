// Sesión del usuario autenticado — persistida en localStorage para sobrevivir
// a recargas de página. El token viaja en el cliente HTTP (api/client.ts).

import { setAuthToken } from './api/client'
import { copiaLocalDe } from './copiaLocal'
import type { AuthResponse } from './api/types'

const SESSION_KEY = 'sicot.session'

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

export function getSession(): AuthResponse | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const auth = JSON.parse(raw) as AuthResponse
    if (tokenExpirado(auth.token)) {
      localStorage.removeItem(SESSION_KEY)
      setAuthToken(null)
      return null
    }
    // Adjunta el token al cliente HTTP de inmediato (durante el render, no en
    // un efecto): las peticiones de los paneles hijos se disparan en sus
    // propios efectos, que corren ANTES que el efecto de App.tsx que
    // sincroniza el token — sin esto, la primera petición tras recargar la
    // página siempre sale sin token, recibe 401 y cierra la sesión sola.
    setAuthToken(auth.token)
    // La copia local es de esta cuenta y de ninguna otra: dos supervisores
    // pueden compartir el portátil del Centro. Al cambiar de usuario,
    // `copiaLocalDe` vacía lo guardado antes de que nadie lo vea.
    void copiaLocalDe(String(auth.usuarioId))
    return auth
  } catch {
    return null
  }
}

export function saveSession(auth: AuthResponse) {
  localStorage.setItem(SESSION_KEY, JSON.stringify(auth))
  setAuthToken(auth.token)
  void copiaLocalDe(String(auth.usuarioId))
}

export function clearSession() {
  localStorage.removeItem(SESSION_KEY)
  setAuthToken(null)
  // Cerrar sesión borra la copia: lo que el supervisor veía sin red era el
  // expediente de un contrato, y no puede quedar en el equipo para el
  // siguiente que entre.
  void copiaLocalDe(null)
}
