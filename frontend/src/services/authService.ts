// Servicio de autenticación — POST /api/auth/login → AuthResponse.
// Al loguear se persiste la sesión (token + usuario) en localStorage.

import { apiFetch } from './api/client'
import { saveSession } from './session'
import type { AuthResponse, LoginRequest } from './api/types'

export async function login(email: string, password: string): Promise<AuthResponse> {
  const body: LoginRequest = { email, password }
  const auth = await apiFetch<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(body),
  })
  saveSession(auth)
  return auth
}
