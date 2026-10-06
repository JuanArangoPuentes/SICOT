// Servicio de contratos — consulta, registro, corrección y cambio de estado
// (autenticado; corregir y cambiar estado exigen rol GESTION o ADMINISTRADOR).

import { apiFetch } from './api/client'
import type {
  ActualizarContratoRequest,
  CambiarEstadoContratoRequest,
  ContratoResponse,
  CrearContratoRequest,
  EstadoContrato,
} from './api/types'

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

/**
 * Corrige los datos generales del contrato. Reemplaza el contrato completo:
 * `body` tiene que traer también los campos que no cambiaron (ver
 * `ActualizarContratoRequest`).
 */
export function actualizarContrato(id: number, body: ActualizarContratoRequest): Promise<ContratoResponse> {
  return apiFetch<ContratoResponse>(`/api/contratos/${id}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}

/**
 * Cambia el estado del contrato. Es lo que lleva un contrato de BORRADOR a
 * ACTIVO, y solo los ACTIVO entran en las reglas de calendario del motor de
 * automatizaciones: hasta que alguien active el contrato, SICOT no vigila su
 * plazo ni su cronograma.
 */
export function cambiarEstadoContrato(id: number, estado: EstadoContrato): Promise<ContratoResponse> {
  const body: CambiarEstadoContratoRequest = { estado }
  return apiFetch<ContratoResponse>(`/api/contratos/${id}/estado`, {
    method: 'PATCH',
    body: JSON.stringify(body),
  })
}
