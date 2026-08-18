// Sesión del usuario autenticado — persistida en localStorage para sobrevivir
// a recargas de página. El token viaja en el cliente HTTP (api/client.ts).

import { setAuthToken } from './api/client'
import type { AuthResponse } from './api/types'

const SESSION_KEY = 'sicot.session'

export function getSession(): AuthResponse | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    return raw ? (JSON.parse(raw) as AuthResponse) : null
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
