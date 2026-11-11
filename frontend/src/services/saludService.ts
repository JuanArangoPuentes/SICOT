// Qué partes del sistema no están funcionando ahora mismo.
//
// Lista vacía = todo bien, que es el caso normal. El backend solo devuelve lo
// que hay que contarle a quien está usando SICOT, con el mensaje ya redactado:
// el texto vive al lado de lo que falla (co.sena.sicot.salud.Servicio) y no
// aquí, para que un servicio nuevo no aparezca en pantalla como «error» a
// secas.

import { apiFetch } from './api/client'

export interface AvisoDeServicio {
  /** Clave estable: `ia`, `correo`. Sirve para decidir dónde pintar el aviso. */
  servicio: string
  /** El texto que se le enseña, tal cual viene. */
  mensaje: string
  /** Cuántos minutos lleva así. */
  minutos: number
}

export function getServiciosCaidos(): Promise<AvisoDeServicio[]> {
  // Sin copia local a propósito, al revés que las alertas: un estado de hace
  // media hora guardado en el navegador diría «la IA está caída» cuando ya
  // volvió, y un aviso falso es peor que ninguno. Si no hay red, no hay aviso,
  // y de que no hay red ya avisa la franja de la copia local.
  return apiFetch<AvisoDeServicio[]>('/api/salud')
}
