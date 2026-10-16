// Servicio de contratos — GET /api/contratos (autenticado).

import { apiFetch } from './api/client'
import type { ContratoResponse, CrearContratoRequest, VerificacionSecopResponse } from './api/types'

export function getContratos(supervisorId?: number, estado?: string): Promise<ContratoResponse[]> {
  const params = new URLSearchParams()
  if (supervisorId != null) params.set('supervisorId', String(supervisorId))
  if (estado) params.set('estado', estado)
  const qs = params.toString()
  return apiFetch<ContratoResponse[]>(`/api/contratos${qs ? `?${qs}` : ''}`)
}

export function crearContrato(body: CrearContratoRequest): Promise<ContratoResponse> {
  return apiFetch<ContratoResponse>('/api/contratos', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

// Lo que publica el SECOP II del contrato, con las diferencias frente a SICOT
// (paso 2.3 del proceso real). Sin conexión responde 200 con disponible=false
// y el motivo: no es un error que haya que mostrar como fallo.
export function verificarEnSecop(contratoId: number): Promise<VerificacionSecopResponse> {
  return apiFetch<VerificacionSecopResponse>(`/api/contratos/${contratoId}/secop`)
}
