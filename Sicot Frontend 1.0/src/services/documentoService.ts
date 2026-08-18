// Servicio de documentos — GET documentos reales de un contrato.

import { apiFetch } from './api/client'
import type { DocumentoResponse } from './api/types'

export function getDocumentosContrato(contratoId: number): Promise<DocumentoResponse[]> {
  return apiFetch<DocumentoResponse[]>(`/api/contratos/${contratoId}/documentos`)
}
