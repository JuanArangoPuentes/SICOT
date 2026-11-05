// Servicio de etapas/subetapas — GET etapas de un contrato y PATCH de estado
// de subetapa (el servidor recalcula porcentaje y estado de la etapa).

import { apiFetch } from './api/client'
import { enviarOEncolar } from './colaDeCambios'
import { conCopiaLocal } from './copiaLocal'
import type { EstadoSubetapa, EtapaResponse, SubetapaResponse } from './api/types'

export function getEtapasContrato(contratoId: number): Promise<EtapaResponse[]> {
  const ruta = `/api/contratos/${contratoId}/etapas`
  return conCopiaLocal(ruta, () => apiFetch<EtapaResponse[]>(ruta))
}

/**
 * Cambia el estado de un sub-paso. Sin red el cambio se guarda en la cola de
 * este equipo y devuelve `null`: quien llama vuelve a pedir las etapas, que
 * salen de la copia con el cambio encima y rotulado como pendiente de enviar.
 */
export async function cambiarEstadoSubetapa(
  contratoId: number,
  subetapa: { id: number; codigo: string },
  estado: EstadoSubetapa,
): Promise<SubetapaResponse | null> {
  const resultado = await enviarOEncolar<SubetapaResponse>({
    tipo: 'estado-subetapa',
    contratoId,
    subetapaId: subetapa.id,
    codigo: subetapa.codigo,
    estado,
  })
  return resultado.enCola ? null : resultado.respuesta
}
