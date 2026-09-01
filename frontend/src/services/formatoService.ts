// Servicio de formatos documentales — catálogo oficial que administra el
// Administrador (GCCON-*, GIL-*, ESUCON, etc.). Carga y descarga real de
// archivos contra el backend; sin datos simulados.

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
export async function descargarFormato(id: number, nombreArchivo: string): Promise<void> {
  const blob = await apiFetchBlob(`/api/formatos/${id}/archivo`)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = nombreArchivo
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
