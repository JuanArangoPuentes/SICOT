// La cuenta de quien tiene la sesión abierta, sea cual sea su rol.

import { apiFetch } from './api/client'
import { olvidarAccesoSinRed, recordarAcceso } from './accesoSinRed'
import { renovarSesion } from './session'
import type { AuthResponse } from './api/types'

/**
 * Cambia la contraseña propia: `PUT /api/cuenta/contrasena`.
 *
 * El servidor cierra todas las sesiones de la cuenta, incluida esta, y
 * devuelve una nueva: se adopta aquí para seguir trabajando sin volver a
 * entrar.
 *
 * La huella del acceso sin red (ADR-015, punto 4) se rehace con la contraseña
 * nueva, igual que al iniciar sesión. Sin esto, el portátil del supervisor
 * seguiría abriendo la copia local sin red con la contraseña anterior.
 */
export async function cambiarMiContrasena(
  correo: string,
  contrasenaActual: string,
  contrasenaNueva: string,
): Promise<AuthResponse> {
  const auth = await apiFetch<AuthResponse>('/api/cuenta/contrasena', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ contrasenaActual, contrasenaNueva }),
  })
  renovarSesion(auth)
  // Primero se borra la huella vieja, en el acto: si la nueva no se llega a
  // guardar (el equipo no ofrece cifrado, o falla), lo que queda es «sin
  // acceso sin red hasta el próximo login», nunca la contraseña anterior.
  olvidarAccesoSinRed()
  // Igual que en el login: derivarla tarda en un teléfono y no tiene por qué
  // hacer esperar.
  void recordarAcceso(correo, contrasenaNueva, auth).catch(() => {})
  return auth
}
