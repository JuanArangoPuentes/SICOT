// La cuenta de quien tiene la sesión abierta, sea cual sea su rol.

import { apiFetch, hayTokenDeSesion } from './api/client'
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

/**
 * Cierra la sesión **en el servidor**: `POST /api/cuenta/cerrar-sesion`.
 *
 * Hasta el 20-11-2026 cerrar sesión era solo del cliente: se borraba el token
 * de este equipo y seguía siendo válido en el servidor hasta ocho horas más
 * (la duración del JWT). Quien lo hubiera copiado antes —de un equipo
 * compartido del Centro, de un portátil que cambió de manos— seguía entrando
 * con él, y quien pulsó «Cerrar sesión» tenía todos los motivos para creer que
 * no.
 *
 * No lanza nunca. Es a propósito, y es la única decisión de peso de esta
 * función: cerrar sesión en la pantalla tiene que ocurrir **siempre**, haya red
 * o no. Si el aviso al servidor falla, el token se queda sin invalidar hasta
 * que caduque —exactamente como antes— pero la persona sale de todos modos;
 * negarle la salida porque el servidor no contesta sería peor que el problema
 * que esto arregla.
 *
 * Una sesión abierta sin red (ADR-015, punto 4) no lleva token, así que no hay
 * nada que cerrar allá y se omite la llamada.
 */
export async function cerrarSesionEnElServidor(): Promise<void> {
  if (!hayTokenDeSesion()) return
  try {
    await apiFetch<void>('/api/cuenta/cerrar-sesion', { method: 'POST' })
  } catch {
    // Sin red, o el servidor rechazó: ver arriba. El cierre local sigue.
  }
}
