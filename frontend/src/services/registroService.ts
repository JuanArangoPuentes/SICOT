// Servicio de registros de auditoría — GET registros de un contrato.

import { apiFetch } from './api/client'
import type { RegistroResponse } from './api/types'

export function getRegistrosContrato(contratoId: number): Promise<RegistroResponse[]> {
  return apiFetch<RegistroResponse[]>(`/api/contratos/${contratoId}/registros`)
}
