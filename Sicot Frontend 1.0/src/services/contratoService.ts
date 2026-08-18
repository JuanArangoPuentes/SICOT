// Servicio de contratos — GET /api/contratos (autenticado).

import { apiFetch } from './api/client'
import type { ContratoResponse, CrearContratoRequest } from './api/types'

export function getContratos(supervisorId?: number, estado?: string): Promise<ContratoResponse[]> {
  const params = new URLSearchParams()
  if (supervisorId != null) params.set('supervisorId', String(supervisorId))
  if (estado) params.set('estado', estado)
  const qs = params.toString()
  return apiFetch<ContratoResponse[]>(`/api/contratos${qs ? `?${qs}` : ''}`)
}

export function getContrato(id: number): Promise<ContratoResponse> {
  return apiFetch<ContratoResponse>(`/api/contratos/${id}`)
}

export function crearContrato(body: CrearContratoRequest): Promise<ContratoResponse> {
  return apiFetch<ContratoResponse>('/api/contratos', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}
