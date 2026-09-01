// Servicio de usuarios — administración de usuarios (requiere rol ADMINISTRADOR).

import { apiFetch } from './api/client'
import type {
  ActualizarUsuarioRequest,
  CambiarEstadoUsuarioRequest,
  CrearUsuarioRequest,
  EnviarCredencialesRequest,
  EnviarCredencialesResponse,
  UsuarioResponse,
} from './api/types'

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

// Actualiza los datos del usuario. `password` es opcional: cuando llega, el
// backend la re-codifica y reemplaza la anterior — es el camino real para
// restablecer la clave de alguien desde el panel de Administración.
export function actualizarUsuario(id: number, request: ActualizarUsuarioRequest): Promise<UsuarioResponse> {
  return apiFetch<UsuarioResponse>(`/api/usuarios/${id}`, {
    method: 'PUT',
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

// Envío real de correo (SMTP) — devuelve enviado:false con el error real si
// el correo no está configurado o falla, nunca finge éxito.
export function enviarCredenciales(id: number, request: EnviarCredencialesRequest): Promise<EnviarCredencialesResponse> {
  return apiFetch<EnviarCredencialesResponse>(`/api/usuarios/${id}/enviar-credenciales`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
}
