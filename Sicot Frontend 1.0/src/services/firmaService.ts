// Servicio de firmas electrónicas — asignación real a cuentas existentes
// (requiere rol ADMINISTRADOR). Reemplaza el mock anterior (useState local).

import { apiFetch } from './api/client'
import type { CambiarEstadoFirmaRequest, CrearFirmaRequest, FirmaResponse } from './api/types'

export function getFirmas(): Promise<FirmaResponse[]> {
  return apiFetch<FirmaResponse[]>('/api/firmas')
}

export function crearFirma(request: CrearFirmaRequest): Promise<FirmaResponse> {
  return apiFetch<FirmaResponse>('/api/firmas', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
}

export function cambiarEstadoFirma(id: number, request: CambiarEstadoFirmaRequest): Promise<FirmaResponse> {
  return apiFetch<FirmaResponse>(`/api/firmas/${id}/estado`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
}
