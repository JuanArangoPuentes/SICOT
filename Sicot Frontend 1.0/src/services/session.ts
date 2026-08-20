// Sesión del usuario autenticado — persistida en localStorage para sobrevivir
// a recargas de página. El token viaja en el cliente HTTP (api/client.ts).

import { setAuthToken } from './api/client'
import type { AuthResponse } from './api/types'

const SESSION_KEY = 'sicot.session'

export function getSession(): AuthResponse | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const auth = JSON.parse(raw) as AuthResponse
    // Adjunta el token al cliente HTTP de inmediato (durante el render, no en
    // un efecto): las peticiones de los paneles hijos se disparan en sus
    // propios efectos, que corren ANTES que el efecto de App.tsx que
    // sincroniza el token — sin esto, la primera petición tras recargar la
    // página siempre sale sin token, recibe 401 y cierra la sesión sola.
    setAuthToken(auth.token)
    return auth
  } catch {
    return null
  }
}

export function saveSession(auth: AuthResponse) {
  localStorage.setItem(SESSION_KEY, JSON.stringify(auth))
  setAuthToken(auth.token)
}

export function clearSession() {
  localStorage.removeItem(SESSION_KEY)
  setAuthToken(null)
}
