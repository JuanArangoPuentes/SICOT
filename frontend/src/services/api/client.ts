// Cliente HTTP de SICOT — fetch con token Bearer, errores tipados y detección
// de 401 para devolver al login. Sin dependencias externas.

import type { ErrorResponse } from './types'

export const API_BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

export class ApiError extends Error {
  readonly status: number
  readonly detail: ErrorResponse | undefined

  constructor(status: number, message: string, detail?: ErrorResponse) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.detail = detail
  }
}

let authToken: string | null = null
let unauthorizedHandler: (() => void) | null = null

export function setAuthToken(token: string | null) {
  authToken = token
}

export function getAuthToken(): string | null {
  return authToken
}

export function onUnauthorized(handler: (() => void) | null) {
  unauthorizedHandler = handler
}

export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  if (!(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }
  if (authToken) {
    headers.set('Authorization', `Bearer ${authToken}`)
  }

  const res = await fetch(`${API_BASE}${path}`, { ...options, headers })

  if (res.status === 401) {
    unauthorizedHandler?.()
  }

  if (!res.ok) {
    let detail: ErrorResponse | undefined
    try {
      detail = (await res.json()) as ErrorResponse
    } catch {
      // cuerpo vacío o no JSON
    }
    throw new ApiError(res.status, detail?.message ?? `Error ${res.status}`, detail)
  }

  if (res.status === 204) {
    return undefined as T
  }

  const text = await res.text()
  return (text ? (JSON.parse(text) as T) : (undefined as T))
}
