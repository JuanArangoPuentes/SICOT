// Servicio de usuarios — administración de usuarios (requiere rol ADMINISTRADOR).

import { apiFetch } from './api/client'
import type { CambiarEstadoUsuarioRequest, CrearUsuarioRequest, UsuarioResponse } from './api/types'

export function getUsuarios(): Promise<UsuarioResponse[]> {
  return apiFetch<UsuarioResponse[]>('/api/usuarios')
}

export function crearUsuario(request: CrearUsuarioRequest): Promise<UsuarioResponse> {
  return apiFetch<UsuarioResponse>('/api/usuarios', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
}

export function cambiarEstadoUsuario(id: number, request: CambiarEstadoUsuarioRequest): Promise<UsuarioResponse> {
  return apiFetch<UsuarioResponse>(`/api/usuarios/${id}/estado`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
}
