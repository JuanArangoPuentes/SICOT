// Servicio de documentos — carga real, generación y firma con el Copiloto IA
// (Ollama local, sin costo de licencia). Todas las llamadas pasan por el
// backend; el frontend nunca habla con Ollama directamente.

import { guardarArchivo, type ResultadoGuardado } from './guardarArchivo'
import { apiFetch, apiFetchBlob } from './api/client'
import type {
  ChatResponse,
  DocumentoGeneradoResponse,
  DocumentoResponse,
  ExtraccionContratoResponse,
  GenerarDocumentoRequest,
  PlantillaDocumento,
  VerificacionIntegridadResponse,
} from './api/types'
import type { ChatMsg } from '@/types/domain'

export function getDocumentosContrato(contratoId: number): Promise<DocumentoResponse[]> {
  return apiFetch<DocumentoResponse[]>(`/api/contratos/${contratoId}/documentos`)
}

// `formatoId` indica de qué formato institucional es instancia el archivo — el
// Acta de Inicio, el GCCON-F-031 del paquete de asignación… Es opcional: no
// todo documento de un contrato representa un formato oficial, y obligar a
// elegir uno llevaría a etiquetar cualquier cosa con tal de poder guardar.
export function subirDocumento(
  contratoId: number,
  archivo: File,
  opciones?: { nombre?: string; subetapaId?: number; formatoId?: number },
): Promise<DocumentoResponse> {
  const form = new FormData()
  form.append('archivo', archivo)
  if (opciones?.nombre) form.append('nombre', opciones.nombre)
  if (opciones?.subetapaId != null) form.append('subetapaId', String(opciones.subetapaId))
  if (opciones?.formatoId != null) form.append('formatoId', String(opciones.formatoId))
  return apiFetch<DocumentoResponse>(`/api/contratos/${contratoId}/documentos`, { method: 'POST', body: form })
}

export function generarDocumento(
  contratoId: number,
  request: GenerarDocumentoRequest,
): Promise<DocumentoGeneradoResponse> {
  return apiFetch<DocumentoGeneradoResponse>(`/api/contratos/${contratoId}/documentos/generar`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function firmarDocumento(contratoId: number, documentoId: number): Promise<DocumentoResponse> {
  return apiFetch<DocumentoResponse>(`/api/contratos/${contratoId}/documentos/${documentoId}/firmar`, {
    method: 'POST',
  })
}

// Comprueba que un documento firmado no haya cambiado desde que se firmó: el
// backend recalcula el SHA-256 del contenido y lo compara con la huella que
// registró al firmar. Es lo que permite decirle a un supervisor si el archivo
// que está viendo es exactamente el que firmó.
export function verificarIntegridad(contratoId: number, documentoId: number): Promise<VerificacionIntegridadResponse> {
  return apiFetch<VerificacionIntegridadResponse>(`/api/contratos/${contratoId}/documentos/${documentoId}/verificacion`)
}

// Descarga el archivo real (PDF generado por SICOT o cargado manualmente)
// vía fetch con token Bearer — apiFetch no sirve aquí porque la respuesta es
// binaria, no JSON (mismo patrón que formatoService.descargarFormato).
//
// El guardado lo hace `guardarArchivo`, no un enlace `download`: en el APK de
// Android ese enlace no hacía nada y el acta firmada se perdía (MDL-184).
export async function descargarDocumento(
  contratoId: number,
  documentoId: number,
  nombreArchivo: string,
): Promise<ResultadoGuardado> {
  const blob = await apiFetchBlob(`/api/contratos/${contratoId}/documentos/${documentoId}/archivo`)
  // Un archivo vacío no se guarda: se bajaba un «.pdf» de 0 bytes que el
  // lector daba por dañado (auditoría del 28-09-2026). El backend ya responde
  // 404 en ese caso; esto cubre cualquier otro camino por el que llegue vacío.
  if (blob.size === 0) {
    throw new Error(`El archivo de «${nombreArchivo}» llegó vacío; no se guardó.`)
  }
  return guardarArchivo(blob, nombreConExtension(nombreArchivo, blob.type))
}

const EXTENSIONES: Record<string, string> = {
  'application/pdf': 'pdf',
  'image/jpeg': 'jpg',
  'image/png': 'png',
  'image/webp': 'webp',
  'text/csv': 'csv',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'docx',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': 'xlsx',
}

// La extensión sale del tipo real del archivo, no se supone. Antes se le
// pegaba «.pdf» a todo nombre que no terminara en .pdf: una foto de evidencia
// bajaba como «…jpg.pdf», Windows la abría con el lector de PDF y la daba por
// dañada; lo mismo habría pasado con un .docx o un .xlsx de Gestión.
//
// El nombre se limpia antes, igual que en el backend (NombreDeDescarga): en la
// aplicación de escritorio va como ruta propuesta al diálogo de guardar, y un
// «/» de «Informe 3/2025» lo partía en una carpeta que no existe; Windows,
// además, rechaza los nombres con «: * ? " < > |» o que acaban en punto.
export function nombreConExtension(nombreOriginal: string, tipo: string): string {
  const nombre = nombreOriginal.replace(/[\\/:*?"<>|]/g, '_').replace(/[. ]+$/, '') || 'documento'
  const extension = EXTENSIONES[tipo.split(';')[0].trim().toLowerCase()]
  if (!extension) return nombre
  const minusculas = nombre.toLowerCase()
  const yaLaTiene = minusculas.endsWith(`.${extension}`) || (extension === 'jpg' && minusculas.endsWith('.jpeg'))
  return yaLaTiene ? nombre : `${nombre}.${extension}`
}

// Los documentos que SICOT arma y los datos que pide cada uno. Cambia solo con
// un despliegue, así que se pide una vez por sesión.
let plantillas: Promise<PlantillaDocumento[]> | null = null
export function getPlantillasDocumento(): Promise<PlantillaDocumento[]> {
  if (!plantillas) {
    plantillas = apiFetch<PlantillaDocumento[]>('/api/ia/plantillas').catch((e) => {
      plantillas = null
      throw e
    })
  }
  return plantillas
}

// Extracción de datos con IA (Gestión, antes de que el contrato exista) —
// acepta varios documentos a la vez (Acta de Inicio, notificación, formatos,
// etc., como llegan en el correo real de asignación) y combina lo que
// encuentre en cada uno. No crea nada, solo propone valores para revisión.
export function extraerDatosContrato(archivos: File[]): Promise<ExtraccionContratoResponse> {
  const form = new FormData()
  archivos.forEach((archivo) => form.append('archivos', archivo))
  return apiFetch<ExtraccionContratoResponse>('/api/ia/extraer-contrato', { method: 'POST', body: form })
}

// Deja el contexto del contrato caliente en el modelo, antes de que el
// supervisor pregunte nada.
//
// Medido el 14 de septiembre de 2026: la PRIMERA pregunta sobre un contrato
// tardaba ~158 s, de los que ~120 eran solo que el modelo leyera el prompt; la
// SEGUNDA tardaba 0,8 s en esa fase, porque Ollama reutiliza el prefijo
// cacheado. Llamando a esto al abrir el contrato, ese minuto y medio transcurre
// mientras el supervisor lee la ficha en vez de mientras mira una pantalla
// parada esperando su respuesta.
//
// No devuelve nada útil y NO se espera: si falla —Ollama apagado, por ejemplo—
// no debe estropear la apertura del contrato. El copiloto seguirá funcionando,
// solo que la primera pregunta será lenta como antes.
export function precalentarCopiloto(contratoId: number): void {
  void apiFetch<void>(`/api/contratos/${contratoId}/copiloto/precalentar`, { method: 'POST' }).catch(() => {
    /* Silencio deliberado: es una optimización, no una función. */
  })
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
      historial: historial?.map((m) => ({ rol: m.role === 'ai' ? 'ai' : 'user', texto: m.text })),
    }),
  })
}
