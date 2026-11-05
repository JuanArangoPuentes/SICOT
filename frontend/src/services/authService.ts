// Servicio de autenticación — POST /api/auth/login → AuthResponse.
// Al loguear se persiste la sesión (token + usuario) en localStorage.

import { apiFetch } from './api/client'
import { entrarSinRed, recordarAcceso, type ResultadoSinRed } from './accesoSinRed'
import { saveSession } from './session'
import type { AuthResponse, LoginRequest } from './api/types'

export async function login(email: string, password: string): Promise<AuthResponse> {
  const body: LoginRequest = { email, password }
  const auth = await apiFetch<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(body),
  })
  saveSession(auth)
  // Después y sin esperar: derivar la huella tarda medio segundo o más en un
  // teléfono y no tiene por qué retrasar la entrada. Si no se llega a guardar,
  // lo único que se pierde es poder entrar sin red esta vez.
  void recordarAcceso(email, password, auth).catch(() => {})
  return auth
}

/**
 * Sin servidor al que llegar, abre la sesión sin conexión si este equipo
 * recuerda la cuenta (ADR-015, punto 4). Ver `accesoSinRed.ts`.
 */
export async function loginSinConexion(email: string, password: string): Promise<ResultadoSinRed> {
  const resultado = await entrarSinRed(email, password)
  if (resultado.ok) saveSession({ ...resultado.auth, sinConexion: true })
  return resultado
}
