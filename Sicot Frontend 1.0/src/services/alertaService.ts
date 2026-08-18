// Servicio de alertas — GET alertas de un contrato y marcado como leída.

import { apiFetch } from './api/client'
import type { AlertaResponse } from './api/types'

export function getAlertasContrato(contratoId: number): Promise<AlertaResponse[]> {
  return apiFetch<AlertaResponse[]>(`/api/contratos/${contratoId}/alertas`)
}

export function marcarAlertaLeida(alertaId: number): Promise<void> {
  return apiFetch<void>(`/api/alertas/${alertaId}/leida`, { method: 'PATCH' })
}
