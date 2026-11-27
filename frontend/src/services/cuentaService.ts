// La cuenta de quien tiene la sesión abierta, sea cual sea su rol.

import { apiFetch, renovandoElToken } from './api/client'
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
 *
 * <h2>Por qué va envuelto en `renovandoElToken`</h2>
 * El servidor revoca los tokens anteriores mientras atiende la petición, de
 * modo que entre la ida y la vuelta las peticiones de fondo salen con un token
 * que ya no vale y reciben un 401 que no es una sesión muerta. `renovandoElToken`
 * marca ese hueco para que ese 401 no cierre la sesión, y `renovarSesion` va
 * dentro para cerrarlo en cuanto llega la respuesta. Lo de después —la huella
 * sin red— queda fuera a propósito: derivar la clave tarda en un teléfono, y
 * alargar el hueco con eso era parte del problema.
 */
export async function cambiarMiContrasena(
  correo: string,
  contrasenaActual: string,
  contrasenaNueva: string,
): Promise<AuthResponse> {
  const auth = await renovandoElToken(async () => {
    const renovada = await apiFetch<AuthResponse>('/api/cuenta/contrasena', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ contrasenaActual, contrasenaNueva }),
    })
    // Adopta el token nuevo en el acto —`renovarSesion` lo pone en el cliente
    // HTTP de forma síncrona— y no en el efecto de App.tsx, que no corre hasta
    // el siguiente render.
    renovarSesion(renovada)
    return renovada
  })
  // Primero se borra la huella vieja, en el acto: si la nueva no se llega a
  // guardar (el equipo no ofrece cifrado, o falla), lo que queda es «sin
  // acceso sin red hasta el próximo login», nunca la contraseña anterior.
  olvidarAccesoSinRed()
  // Igual que en el login: derivarla tarda en un teléfono y no tiene por qué
  // hacer esperar.
  void recordarAcceso(correo, contrasenaNueva, auth).catch(() => {})
  return auth
}
