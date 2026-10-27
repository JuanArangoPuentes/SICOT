// Servicio de contratos — consulta, registro, corrección y cambio de estado
// (autenticado; corregir y cambiar estado exigen rol GESTION o ADMINISTRADOR).

import { apiFetch, apiFetchBlob } from './api/client'
import { conCopiaLocal } from './copiaLocal'
import { guardarArchivo, type ResultadoGuardado } from './guardarArchivo'
import type {
  AcuerdoDeEntregasResponse,
  ActualizarContratoRequest,
  CambiarEstadoContratoRequest,
  ContactoDelContrato,
  ContactosDelContratoResponse,
  ContratoResponse,
  CrearContratoRequest,
  EstadoContrato,
  InvitacionDeSupervisorResponse,
  InvitarSupervisorRequest,
  ItemDeSolicitud,
  LecturaDeContactosResponse,
  SolicitudDePedidoResponse,
  VerificacionSecopResponse,
} from './api/types'

export function getContratos(supervisorId?: number, estado?: string): Promise<ContratoResponse[]> {
  const params = new URLSearchParams()
  if (supervisorId != null) params.set('supervisorId', String(supervisorId))
  if (estado) params.set('estado', estado)
  const qs = params.toString()
  const ruta = `/api/contratos${qs ? `?${qs}` : ''}`
  // Con copia local: es la primera pantalla del supervisor. Sin ella, quedarse
  // sin red deja la bandeja vacía y parece que no tiene contratos.
  return conCopiaLocal(ruta, () => apiFetch<ContratoResponse[]>(ruta))
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

/**
 * Pone un supervisor en el contrato por su correo, creándole la cuenta si no
 * la tiene y mandándole a ese correo el usuario y la clave con los que entrará
 * después de instalar SICOT.
 *
 * Lo que devuelve hay que MIRARLO, no darlo por bueno: si el correo no salió,
 * `claveParaEntregar` trae la única copia de la clave y la pantalla tiene que
 * enseñarla. Un supervisor que ya existía conserva la suya y no recibe nada.
 */
export function invitarSupervisor(id: number, body: InvitarSupervisorRequest): Promise<InvitacionDeSupervisorResponse> {
  return apiFetch<InvitacionDeSupervisorResponse>(`/api/contratos/${id}/supervisor/invitacion`, {
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

// ─────────── Arranque del contrato: pasos 2.2 y 2.4 ───────────
//
// Lo que la hoja de Alex pide recordar y no tenía dónde quedar: las fechas de
// entrega acordadas con el contratista, los profesionales de SST y ambiental,
// y el teléfono del contratista.

export function obtenerEntregasAcordadas(contratoId: number): Promise<AcuerdoDeEntregasResponse> {
  const ruta = `/api/contratos/${contratoId}/entregas-acordadas`
  return conCopiaLocal(ruta, () => apiFetch<AcuerdoDeEntregasResponse>(ruta))
}

/**
 * Reemplaza el acuerdo; una lista vacía lo borra. Las fechas tienen que caer
 * dentro del plazo del contrato (el backend lo exige) y el número lo pone SICOT
 * por fecha.
 */
export function guardarEntregasAcordadas(
  contratoId: number,
  entregas: Array<{ fecha: string; descripcion: string }>,
): Promise<AcuerdoDeEntregasResponse> {
  return apiFetch<AcuerdoDeEntregasResponse>(`/api/contratos/${contratoId}/entregas-acordadas`, {
    method: 'PUT',
    body: JSON.stringify({ entregas }),
  })
}

export function obtenerContactosSstAmbiental(contratoId: number): Promise<ContactosDelContratoResponse> {
  const ruta = `/api/contratos/${contratoId}/contactos-sst-ambiental`
  return conCopiaLocal(ruta, () => apiFetch<ContactosDelContratoResponse>(ruta))
}

/** Reemplaza los contactos; una lista vacía los borra. */
export function guardarContactosSstAmbiental(
  contratoId: number,
  contactos: ContactoDelContrato[],
): Promise<ContactosDelContratoResponse> {
  return apiFetch<ContactosDelContratoResponse>(`/api/contratos/${contratoId}/contactos-sst-ambiental`, {
    method: 'PUT',
    body: JSON.stringify({ contactos }),
  })
}

/** Los correos que trae el estudio previo cargado. No guarda nada: ni el archivo ni los contactos. */
export function leerContactosDelEstudioPrevio(contratoId: number, archivo: File): Promise<LecturaDeContactosResponse> {
  const form = new FormData()
  form.append('archivo', archivo)
  return apiFetch<LecturaDeContactosResponse>(`/api/contratos/${contratoId}/contactos-sst-ambiental/lectura`, {
    method: 'POST',
    body: form,
  })
}

/**
 * Llena el teléfono (y el correo, si falta) del contratista con su inscripción
 * en el registro de proveedores del SECOP II. Nunca reemplaza lo que ya hay.
 */
export function completarContactoDelContratista(contratoId: number): Promise<ContratoResponse> {
  return apiFetch<ContratoResponse>(`/api/contratos/${contratoId}/contacto-contratista/desde-secop`, {
    method: 'POST',
  })
}
