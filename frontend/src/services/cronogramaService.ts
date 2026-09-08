// Estado de cronograma de un contrato — GET /api/contratos/{id}/cronograma.
//
// El panel NO calcula el semáforo. Lo hacía antes, con un criterio distinto al
// de la alerta que el backend persiste, y el sistema podía contradecirse a sí
// mismo sobre si un contrato iba atrasado. Ver docs/REVISION_ARQUITECTURA.

import { apiFetch } from './api/client'
import type { CronogramaResponse } from './api/types'

export function getCronograma(contratoId: number): Promise<CronogramaResponse> {
  return apiFetch<CronogramaResponse>(`/api/contratos/${contratoId}/cronograma`)
}
