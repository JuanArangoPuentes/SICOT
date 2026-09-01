// Cliente HTTP de SICOT — fetch con token Bearer, errores tipados y detección
// de 401 para devolver al login. Sin dependencias externas.

import type { ErrorResponse } from './types'

export const API_BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

export class ApiError extends Error {
  readonly status: number
  readonly detail: ErrorResponse | undefined
  /**
   * Segundos que pidió esperar el servidor (cabecera Retry-After), solo en
   * respuestas 429. Permite que la interfaz diga "vuelva a intentar en 2
   * minutos" en vez de dejar a la persona reintentando a ciegas.
   */
  readonly reintentarEnSegundos: number | undefined

  constructor(status: number, message: string, detail?: ErrorResponse, reintentarEnSegundos?: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.detail = detail
    this.reintentarEnSegundos = reintentarEnSegundos
  }

  /** Credenciales incorrectas o sesión no válida. */
  get esNoAutenticado(): boolean {
    return this.status === 401
  }

  /** Límite de frecuencia: intentos de inicio de sesión o uso del Copiloto. */
  get esDemasiadasSolicitudes(): boolean {
    return this.status === 429
  }
}

let authToken: string | null = null
let unauthorizedHandler: (() => void) | null = null

export function setAuthToken(token: string | null) {
  authToken = token
}

export function onUnauthorized(handler: (() => void) | null) {
  unauthorizedHandler = handler
}

/**
 * Un 401 en `/api/auth/login` significa "esas credenciales no son correctas", y
 * uno en cualquier otra ruta significa "su sesión ya no vale".
 *
 * Distinguirlos es imprescindible desde que el backend responde 401 —y no 400—
 * a unas credenciales equivocadas: sin esta comprobación, escribir mal la
 * contraseña dispararía el cierre de sesión global y la pantalla de login se
 * reiniciaría sola, borrando lo que la persona acababa de escribir.
 */
function esFalloDeSesion(path: string): boolean {
  return !path.startsWith('/api/auth/')
}

function segundosDeEspera(res: Response): number | undefined {
  const cabecera = res.headers.get('Retry-After')
  if (!cabecera) return undefined
  const segundos = Number(cabecera)
  return Number.isFinite(segundos) && segundos > 0 ? segundos : undefined
}

async function lanzarError(res: Response, path: string): Promise<never> {
  let detail: ErrorResponse | undefined
  try {
    detail = (await res.json()) as ErrorResponse
  } catch {
    // cuerpo vacío o no JSON
  }
  throw new ApiError(
    res.status,
    detail?.message ?? `Error ${res.status}`,
    detail,
    res.status === 429 ? segundosDeEspera(res) : undefined,
  )
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

  if (res.status === 401 && esFalloDeSesion(path)) {
    unauthorizedHandler?.()
  }

  if (!res.ok) {
    await lanzarError(res, path)
  }

  if (res.status === 204) {
    return undefined as T
  }

  const text = await res.text()
  return (text ? (JSON.parse(text) as T) : (undefined as T))
}

export async function apiFetchBlob(path: string, options: RequestInit = {}): Promise<Blob> {
  const headers = new Headers(options.headers)
  if (authToken) headers.set('Authorization', `Bearer ${authToken}`)

  const res = await fetch(`${API_BASE}${path}`, { ...options, headers })
  if (res.status === 401 && esFalloDeSesion(path)) unauthorizedHandler?.()

  if (!res.ok) {
    await lanzarError(res, path)
  }
  return res.blob()
}
