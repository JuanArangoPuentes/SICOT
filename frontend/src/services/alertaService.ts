// Servicio de alertas — GET alertas de un contrato y marcado como leída.

import { apiFetch } from './api/client'
import { conCopiaLocal } from './copiaLocal'
import type { AlertaResponse } from './api/types'

// Con copia local: sin red se ven las alertas de la última vez que hubo. Son
// las que calculó el temporizador del servidor, así que sin conexión están
// congeladas — el aviso de la copia dice de cuándo son (ADR-015, punto 6).
export function getAlertasContrato(contratoId: number): Promise<AlertaResponse[]> {
  const ruta = `/api/contratos/${contratoId}/alertas`
  return conCopiaLocal(ruta, () => apiFetch<AlertaResponse[]>(ruta))
}

export function marcarAlertaLeida(alertaId: number): Promise<void> {
  return apiFetch<void>(`/api/alertas/${alertaId}/leida`, { method: 'PATCH' })
}
