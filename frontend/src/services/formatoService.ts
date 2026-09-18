// Servicio de formatos documentales — catálogo oficial que administra el
// Administrador (GCCON-*, GIL-*, ESUCON, etc.). Carga y descarga real de
// archivos contra el backend; sin datos simulados.

import { guardarArchivo, type ResultadoGuardado } from './guardarArchivo'
import { apiFetch, apiFetchBlob } from './api/client'
import type { FormatoDocumentalResponse } from './api/types'

export function getFormatos(): Promise<FormatoDocumentalResponse[]> {
  return apiFetch<FormatoDocumentalResponse[]>('/api/formatos')
}

export function subirFormato(codigo: string, nombre: string, archivo: File): Promise<FormatoDocumentalResponse> {
  const body = new FormData()
  body.append('codigo', codigo)
  body.append('nombre', nombre)
  body.append('archivo', archivo)
  return apiFetch<FormatoDocumentalResponse>('/api/formatos', { method: 'POST', body })
}

export function eliminarFormato(id: number): Promise<void> {
  return apiFetch<void>(`/api/formatos/${id}`, { method: 'DELETE' })
}

// Descarga el archivo real vía fetch (con el token Bearer) y dispara el
// guardado en el navegador — apiFetch no sirve aquí porque la respuesta es
// binaria, no JSON.
//
// El guardado lo hace `guardarArchivo`: en el APK de Android un enlace
// `download` no hace nada (MDL-184).
export async function descargarFormato(id: number, nombreArchivo: string): Promise<ResultadoGuardado> {
  const blob = await apiFetchBlob(`/api/formatos/${id}/archivo`)
  return guardarArchivo(blob, nombreArchivo)
}
