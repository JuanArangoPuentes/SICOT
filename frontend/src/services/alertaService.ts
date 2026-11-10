// Servicio de alertas — GET alertas de un contrato y marcado como leída.

import { apiFetch } from './api/client'
import { enviarOEncolar } from './colaDeCambios'
import { conCopiaLocal } from './copiaLocal'
import type { AlertaResponse } from './api/types'

// Con copia local: sin red se ven las alertas de la última vez que hubo. Son
// las que calculó el temporizador del servidor, así que sin conexión están
// congeladas — el aviso de la copia dice de cuándo son (ADR-015, punto 6).
export function getAlertasContrato(contratoId: number): Promise<AlertaResponse[]> {
  const ruta = `/api/contratos/${contratoId}/alertas`
  return conCopiaLocal(ruta, () => apiFetch<AlertaResponse[]>(ruta))
}

/** Sin red se guarda en la cola: la alerta deja de salir ya, y el servidor se entera al volver. */
export async function marcarAlertaLeida(alertaId: number, contratoId: number | null = null): Promise<void> {
  await enviarOEncolar<void>({ tipo: 'alerta-leida', contratoId, alertaId })
}
