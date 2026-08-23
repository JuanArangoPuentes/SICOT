// Servicio de documentos — carga real, generación y firma con el Copiloto IA
// (Ollama local, sin costo de licencia). Todas las llamadas pasan por el
// backend; el frontend nunca habla con Ollama directamente.

import { apiFetch, apiFetchBlob } from './api/client'
import type { ChatResponse, DocumentoResponse, ExtraccionContratoResponse, GenerarDocumentoRequest } from './api/types'
import type { ChatMsg } from '@/types/domain'

export function getDocumentosContrato(contratoId: number): Promise<DocumentoResponse[]> {
  return apiFetch<DocumentoResponse[]>(`/api/contratos/${contratoId}/documentos`)
}

export function subirDocumento(
  contratoId: number,
  archivo: File,
  opciones?: { nombre?: string; subetapaId?: number },
): Promise<DocumentoResponse> {
  const form = new FormData()
  form.append('archivo', archivo)
  if (opciones?.nombre) form.append('nombre', opciones.nombre)
  if (opciones?.subetapaId != null) form.append('subetapaId', String(opciones.subetapaId))
  return apiFetch<DocumentoResponse>(`/api/contratos/${contratoId}/documentos`, { method: 'POST', body: form })
}

export function generarDocumento(contratoId: number, request: GenerarDocumentoRequest): Promise<DocumentoResponse> {
  return apiFetch<DocumentoResponse>(`/api/contratos/${contratoId}/documentos/generar`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function firmarDocumento(contratoId: number, documentoId: number): Promise<DocumentoResponse> {
  return apiFetch<DocumentoResponse>(`/api/contratos/${contratoId}/documentos/${documentoId}/firmar`, {
    method: 'POST',
  })
}

// Descarga el archivo real (PDF generado por la IA o cargado manualmente)
// vía fetch con token Bearer — apiFetch no sirve aquí porque la respuesta es
// binaria, no JSON (mismo patrón que formatoService.descargarFormato).
export async function descargarDocumento(contratoId: number, documentoId: number, nombreArchivo: string): Promise<void> {
  const blob = await apiFetchBlob(`/api/contratos/${contratoId}/documentos/${documentoId}/archivo`)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = nombreArchivo.toLowerCase().endsWith('.pdf') ? nombreArchivo : `${nombreArchivo}.pdf`
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

// Extracción de datos con IA (Gestión, antes de que el contrato exista) —
// acepta varios documentos a la vez (Acta de Inicio, notificación, formatos,
// etc., como llegan en el correo real de asignación) y combina lo que
// encuentre en cada uno. No crea nada, solo propone valores para revisión.
export function extraerDatosContrato(archivos: File[]): Promise<ExtraccionContratoResponse> {
  const form = new FormData()
  archivos.forEach(archivo => form.append('archivos', archivo))
  return apiFetch<ExtraccionContratoResponse>('/api/ia/extraer-contrato', { method: 'POST', body: form })
}

// Chat real del Copiloto IA (Ollama) — reemplaza el antiguo CHAT_RESPONSES por
// coincidencia de palabras clave. La respuesta viene anclada a los datos
// reales del contrato y al estado real de sus etapas (ver CopilotoChatService).
// `historial` son los turnos previos de esta conversación (opcional) — le dan
// memoria real al Copiloto para que las preguntas de seguimiento tengan
// sentido en vez de responderse como si la conversación empezara de cero.
export function preguntarCopiloto(contratoId: number, pregunta: string, historial?: ChatMsg[]): Promise<ChatResponse> {
  return apiFetch<ChatResponse>(`/api/contratos/${contratoId}/copiloto/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      pregunta,
      historial: historial?.map(m => ({ rol: m.role === 'ai' ? 'ai' : 'user', texto: m.text })),
    }),
  })
}
