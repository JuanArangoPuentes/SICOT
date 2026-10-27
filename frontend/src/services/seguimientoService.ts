// Seguimiento de supervisores — GET /api/seguimiento/supervisores (solo ADMINISTRADOR).

import { apiFetch } from './api/client'
import type { SeguimientoResponse } from './api/types'

export function getSeguimiento(): Promise<SeguimientoResponse> {
  return apiFetch<SeguimientoResponse>('/api/seguimiento/supervisores')
}
