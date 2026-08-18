// Servicio de etapas/subetapas — GET etapas de un contrato y PATCH de estado
// de subetapa (el servidor recalcula porcentaje y estado de la etapa).

import { apiFetch } from './api/client'
import type { ActualizarEstadoSubetapaRequest, EstadoSubetapa, EtapaResponse, SubetapaResponse } from './api/types'

export function getEtapasContrato(contratoId: number): Promise<EtapaResponse[]> {
  return apiFetch<EtapaResponse[]>(`/api/contratos/${contratoId}/etapas`)
}

export function cambiarEstadoSubetapa(subetapaId: number, estado: EstadoSubetapa): Promise<SubetapaResponse> {
  const body: ActualizarEstadoSubetapaRequest = { estado }
  return apiFetch<SubetapaResponse>(`/api/subetapas/${subetapaId}/estado`, {
    method: 'PATCH',
    body: JSON.stringify(body),
  })
}
