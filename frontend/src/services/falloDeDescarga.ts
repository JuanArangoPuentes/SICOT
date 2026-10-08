// Qué decir cuando no se pudo bajar un documento, según dónde falló.
//
// Lo usan la descarga de Documentos y «Ver borrador completo» antes de firmar.
// «Intente de nuevo» solo cuando reintentar puede arreglarlo: si el teléfono
// no deja escribir el archivo, volverá a fallar igual, y si el servidor no
// respondió, el problema no es el archivo (auditoría del 02-10-2026).

import { ApiError } from './api/client'
import { ErrorAlGuardar } from './guardarArchivo'

/** @param que lo que se quería bajar, ya redactado: «Acta de Inicio» entre comillas, «el borrador». */
export function motivoDelFalloDeDescarga(err: unknown, que: string): string {
  if (err instanceof ErrorAlGuardar) return `No se pudo guardar ${que} en el teléfono: ${err.message}`
  // El motivo real cuando lo hay: «no tiene archivo guardado» no se arregla
  // intentando de nuevo.
  if (err instanceof ApiError || (err instanceof Error && err.message.includes('vacío'))) return err.message
  // fetch rechaza con TypeError cuando no hubo respuesta: servidor caído o
  // sin red.
  if (err instanceof TypeError) {
    return `No se pudo descargar ${que}: no hubo respuesta del servidor de SICOT. Compruebe la conexión e intente de nuevo.`
  }
  return `No se pudo descargar ${que}. Intente de nuevo en un momento.`
}
