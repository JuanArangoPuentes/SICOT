// Servicio de novedades — preguntarle al servidor si otro equipo movió algo.

import { apiFetch } from './api/client'
import type { NovedadesResponse } from './api/types'

/**
 * Qué ha pasado en el contrato desde la entrada `desdeId` de la auditoría.
 *
 * Con `desdeId` en `null` —la primera vez— solo vuelve la marca desde la que
 * empezar a mirar, sin novedades: la pantalla acaba de cargar el estado entero.
 *
 * <h2>Por qué este NO pasa por la copia local</h2>
 * Porque la pregunta es justamente «¿cambió algo en el servidor?», y la copia
 * guardada es por definición lo que el servidor contestó antes. Servirla aquí
 * respondería siempre «no ha pasado nada» con los datos viejos, que es la única
 * respuesta que este sondeo no puede dar. Sin red, la llamada falla y quien la
 * hace lo ignora: de avisar de que no hay conexión ya se encargan el aviso de la
 * copia y la franja de cambios sin enviar.
 */
export function getNovedades(contratoId: number, desdeId: number | null): Promise<NovedadesResponse> {
  const marca = desdeId === null ? '' : `?desdeId=${desdeId}`
  return apiFetch<NovedadesResponse>(`/api/contratos/${contratoId}/novedades${marca}`)
}
