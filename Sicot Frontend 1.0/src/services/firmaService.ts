// Servicio de firmas electrónicas — asignación real a cuentas existentes
// (requiere rol ADMINISTRADOR). Reemplaza el mock anterior (useState local).

import { apiFetch } from './api/client'
import type { CambiarEstadoFirmaRequest, CrearFirmaRequest, FirmaResponse, MiFirmaResponse } from './api/types'

export function getFirmas(): Promise<FirmaResponse[]> {
  return apiFetch<FirmaResponse[]>('/api/firmas')
}

// Consulta si la cuenta actual (cualquier rol) tiene una firma electrónica
// activa asignada por el Administrador — usado para mostrar honestamente
// "no se ha obtenido la firma" en vez de dejar que el intento de firmar falle.
export function getMiFirma(): Promise<MiFirmaResponse> {
  return apiFetch<MiFirmaResponse>('/api/firmas/mia')
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
