// Servicio de contratos — consulta, registro, corrección y cambio de estado
// (autenticado; corregir y cambiar estado exigen rol GESTION o ADMINISTRADOR).

import { apiFetch, apiFetchBlob } from './api/client'
import { guardarArchivo, type ResultadoGuardado } from './guardarArchivo'
import type {
  ActualizarContratoRequest,
  CambiarEstadoContratoRequest,
  ContratoResponse,
  CrearContratoRequest,
  EstadoContrato,
  ItemDeSolicitud,
  SolicitudDePedidoResponse,
  VerificacionSecopResponse,
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

/**
 * Asigna o cambia el supervisor del contrato. Asignarlo a un contrato que nació
 * sin él completa la designación (etapa 1) y le avisa al supervisor.
 */
export function asignarSupervisor(id: number, supervisorId: number): Promise<ContratoResponse> {
  return apiFetch<ContratoResponse>(`/api/contratos/${id}/supervisor`, {
    method: 'PATCH',
    body: JSON.stringify({ supervisorId }),
  })
}

// Lo que publica el SECOP II del contrato, con las diferencias frente a SICOT
// (paso 2.3 del proceso real). Sin conexión responde 200 con disponible=false
// y el motivo: no es un error que haya que mostrar como fallo.
export function verificarEnSecop(contratoId: number): Promise<VerificacionSecopResponse> {
  return apiFetch<VerificacionSecopResponse>(`/api/contratos/${contratoId}/secop`)
}

// ─────────── Solicitud de pedido al contratista (paso 2.4) ───────────
//
// El paso 5 de la lista de chequeo de Alex pide que el correo al contratista
// lleve adjunto un Excel con los bienes y cantidades. Los ítems los pega el
// supervisor desde la oferta económica —el expediente del SECOP no es
// alcanzable desde un programa— y quedan guardados con el contrato.

export function obtenerSolicitudDePedido(contratoId: number): Promise<SolicitudDePedidoResponse> {
  return apiFetch<SolicitudDePedidoResponse>(`/api/contratos/${contratoId}/solicitud-pedido`)
}

/** Reemplaza la solicitud; una lista vacía la borra. */
export function guardarSolicitudDePedido(
  contratoId: number,
  items: Array<Omit<ItemDeSolicitud, 'valorTotal'>>,
): Promise<SolicitudDePedidoResponse> {
  return apiFetch<SolicitudDePedidoResponse>(`/api/contratos/${contratoId}/solicitud-pedido`, {
    method: 'PUT',
    body: JSON.stringify({ items }),
  })
}

/**
 * Descarga el Excel que se adjunta al correo. Como en los formatos, el guardado
 * lo hace `guardarArchivo`: en el APK de Android un enlace `download` no hace
 * nada (MDL-184).
 */
export async function descargarSolicitudDePedido(
  contratoId: number,
  numeroContrato: string,
): Promise<ResultadoGuardado> {
  const blob = await apiFetchBlob(`/api/contratos/${contratoId}/solicitud-pedido/archivo`)
  const nombre = `Solicitud de pedido ${numeroContrato.replace(/[^A-Za-z0-9._-]/g, '_')}.xlsx`
  return guardarArchivo(blob, nombre)
}
