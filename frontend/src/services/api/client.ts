// Cliente HTTP de SICOT — fetch con token Bearer, errores tipados y detección
// de 401 para devolver al login. Sin dependencias externas.

import { enAplicacionEmpaquetada } from '../entorno'
import type { ErrorResponse } from './types'

/**
 * Origen del backend fijado al compilar — el valor por defecto, no la última
 * palabra: lo que manda en ejecución es {@link apiBase}.
 *
 * Vacío significa **mismo origen**, no "sin configurar": es lo que usa el
 * despliegue de producción, donde el proxy con TLS sirve la SPA y enruta /api al
 * backend bajo el mismo dominio (ADR-009).
 *
 * Se compara contra `undefined` y no se usa `??` a secas para que una cadena
 * vacía sea una elección válida y no caiga al valor de desarrollo.
 *
 * <p>Salvo dentro de la aplicación empaquetada (instalador y APK): allí el
 * «mismo origen» es `http://tauri.localhost`, el propio WebView, donde nunca
 * hay API. Desde ADR-016 la convención es dejar `VITE_API_URL` vacío, y quien
 * compilara un APK en una consola con esa variable exportada vacía obtendría
 * una instalación que pide `/api` a sí misma y falla con un 404 o un JSON
 * inválido que no parece un problema de dirección. Con la dirección de
 * desarrollo, en cambio, la pantalla de acceso ya sabe decir «escriba la
 * dirección del servidor de su Centro» (ver entorno.ts).
 */
const origenConfigurado = import.meta.env.VITE_API_URL
export const ORIGEN_COMPILADO =
  origenConfigurado === undefined ||
  origenConfigurado === null ||
  (origenConfigurado === '' && enAplicacionEmpaquetada())
    ? 'http://localhost:8080'
    : origenConfigurado

/** Dónde se guarda la dirección del servidor elegida en esta máquina. */
export const CLAVE_SERVIDOR = 'sicot.servidor'

/**
 * Origen efectivo del backend, resuelto en cada llamada.
 *
 * <h2>Por qué no basta con el valor de compilación</h2>
 * Vite hornea `VITE_API_URL` dentro del paquete. Para el despliegue web da
 * igual, porque cada despliegue construye el suyo y además usa mismo origen.
 * Pero la aplicación de escritorio del supervisor se distribuye **compilada, a
 * máquinas que no controlamos**: si la dirección viajara dentro del binario,
 * el ejecutable que descarga un supervisor llevaría incrustada la dirección de
 * un servidor concreto, y cambiar de servidor obligaría a recompilar y volver a
 * publicar el instalador para todo el mundo.
 *
 * <p>Por eso la dirección guardada en la máquina gana sobre la compilada. El
 * instalador queda así siendo <b>un único artefacto válido para cualquier
 * despliegue</b>, y quien lo instala apunta al servidor de su Centro desde la
 * pantalla de Configuración.
 *
 * <p>Se lee en cada llamada y no una sola vez al cargar el módulo para que un
 * cambio de servidor tenga efecto sin reiniciar la aplicación.
 *
 * <h2>Dónde surte efecto de verdad</h2>
 * En la <b>aplicación de escritorio</b>, siempre: su CSP permite hablar con
 * cualquier servidor http/https, que es justo lo que necesita un instalador
 * distribuido a Centros distintos.
 *
 * <p>En el <b>despliegue web</b> manda además la CSP que pone nginx
 * ({@code CSP_CONNECT_SRC_EXTRA}): apuntar aquí a un origen que esa cabecera no
 * autorice deja las llamadas bloqueadas por el navegador. No es una
 * contradicción sino el reparto correcto — en la web el frontend y la API
 * comparten origen (ADR-009) y no hay nada que configurar.
 */
export function apiBase(): string {
  let guardado: string | null = null
  try {
    guardado = localStorage.getItem(CLAVE_SERVIDOR)
  } catch {
    // Almacenamiento bloqueado (ventana privada, políticas del equipo): se
    // sigue con el valor de compilación en vez de dejar la aplicación inútil.
    guardado = null
  }
  if (guardado === null || guardado.trim() === '') {
    return ORIGEN_COMPILADO
  }
  // Sin barra final: todas las rutas del cliente empiezan por "/" y una barra
  // de más produce "//api/...", que algunos proxys no normalizan.
  return guardado.trim().replace(/\/+$/, '')
}

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

/**
 * ¿Hay con qué presentarse al servidor? Una sesión abierta sin red
 * (`accesoSinRed.ts`) no lleva token: sirve para ver la copia y guardar
 * cambios en la cola, pero no para enviarlos.
 */
export function hayTokenDeSesion(): boolean {
  return Boolean(authToken)
}

let oyenteDeContacto: (() => void) | null = null

/**
 * Avisa cada vez que el servidor acepta una petición con la sesión: es la
 * prueba de que la cuenta sigue valiendo. El acceso sin red cuenta su ventana
 * desde el último aviso, no desde el último inicio de sesión: quien trabaja con
 * red todo el día no tiene por qué quedarse con menos margen al salir a campo.
 */
export function alResponderConSesion(oyente: (() => void) | null) {
  oyenteDeContacto = oyente
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

/** Cuántas renovaciones del token hay en curso. Ver {@link renovandoElToken}. */
let renovacionesEnCurso = 0

/**
 * Envuelve una operación que sustituye el token de la sesión sin cerrarla
 * —hoy, el cambio de la contraseña propia—, desde que sale la petición hasta
 * que el token nuevo está puesto.
 *
 * <h2>Qué hueco tapa</h2>
 * El servidor revoca los tokens anteriores de la cuenta mientras atiende esa
 * petición ({@code Usuario.revocarSesiones}), y el cliente no se entera hasta
 * que le llega la respuesta: durante ese viaje de red el token que llevan las
 * demás peticiones ya no vale, y no hay forma de adelantarse. Cualquier
 * petición de fondo que salga en ese instante —el sondeo de novedades de
 * ADR-016, el refresco en vivo, el precalentado del Copiloto— vuelve con un
 * 401 que NO significa «su sesión ya no vale».
 *
 * <p>Sin esto, ese 401 disparaba el cierre global y la contraseña se cambiaba
 * con éxito para aterrizar en la pantalla de acceso, justo lo contrario de lo
 * que promete la ventana: «En este equipo sigue trabajando sin volver a
 * entrar». Pasaba de forma intermitente, porque depende de que una de esas
 * peticiones caiga dentro del hueco (observado el 18-11-2026 con el sondeo
 * activo en el panel de contrato).
 *
 * <p>El token nuevo se pone DENTRO de la operación envuelta, para que el hueco
 * se cierre en cuanto llega la respuesta y no en un render posterior de React.
 */
export async function renovandoElToken<T>(renovar: () => Promise<T>): Promise<T> {
  renovacionesEnCurso++
  try {
    return await renovar()
  } finally {
    renovacionesEnCurso--
  }
}

/**
 * ¿Salió esta petición con un token que la sesión ya sustituyó? Entonces su
 * 401 es del token viejo y no de la cuenta: se repite con el vigente.
 *
 * <p>Repetirla es seguro aunque cambiara algo: un 401 se responde antes de
 * tocar nada, así que el primer intento no llegó a hacer efecto.
 */
function tokenQuedoAtras(tokenUsado: string | null): boolean {
  return tokenUsado !== null && authToken !== null && authToken !== tokenUsado
}

/**
 * El viaje de ida y vuelta de toda petición al backend: le pone el token de la
 * sesión y es el ÚNICO sitio desde donde se declara que la sesión se terminó.
 *
 * <p>Las cabeceras llegan ya armadas por quien llama (cada forma de pedir tiene
 * su propio criterio sobre el Content-Type); de la de autorización se encarga
 * esto, porque es lo que puede cambiar entre un intento y el siguiente.
 */
async function pedirConSesion(path: string, options: RequestInit, base: Headers): Promise<Response> {
  const intentar = (token: string | null) => {
    const headers = new Headers(base)
    if (token) headers.set('Authorization', `Bearer ${token}`)
    return fetch(`${apiBase()}${path}`, { ...options, headers })
  }

  const tokenUsado = authToken
  const res = await intentar(tokenUsado)
  if (res.status !== 401 || !esFalloDeSesion(path)) return res

  if (tokenQuedoAtras(tokenUsado)) {
    const tokenVigente = authToken
    const reintento = await intentar(tokenVigente)
    // Si el token vigente tampoco vale, la sesión sí se terminó. Salvo que haya
    // vuelto a cambiar mientras tanto: entonces se deja estar, y quien vuelva a
    // preguntar lo hará con el de ese momento.
    if (reintento.status === 401 && !tokenQuedoAtras(tokenVigente)) unauthorizedHandler?.()
    return reintento
  }

  // El token no ha cambiado todavía, pero está cambiando: esta petición salió
  // con el anterior y aún no hay con qué repetirla. Falla para quien la pidió
  // —el sondeo vuelve a preguntar en segundos, ya con el nuevo— sin cerrarle la
  // sesión a nadie. Si de verdad se terminó, el siguiente 401 lo dirá.
  if (renovacionesEnCurso > 0) return res

  unauthorizedHandler?.()
  return res
}

function segundosDeEspera(res: Response): number | undefined {
  const cabecera = res.headers.get('Retry-After')
  if (!cabecera) return undefined
  const segundos = Number(cabecera)
  return Number.isFinite(segundos) && segundos > 0 ? segundos : undefined
}

/**
 * Códigos con los que responde un intermediario (proxy, balanceador, túnel)
 * cuando el backend no contestó a tiempo o no estaba: 502, 504 y los 520-524
 * de Cloudflare. El 503 queda fuera a propósito: es el «Ollama no disponible»
 * del propio backend, que llega con su mensaje en JSON.
 */
export const CODIGOS_DE_PASARELA = new Set([502, 504, 520, 521, 522, 523, 524])

/**
 * ¿Es un corte del camino y no una respuesta del servidor de SICOT? Un fallo
 * de red (fetch sin respuesta: TypeError) o la página de error de un
 * intermediario, que llega sin el JSON del backend.
 */
export function esCorteDelCamino(e: unknown): boolean {
  if (e instanceof TypeError) return true
  return e instanceof ApiError && CODIGOS_DE_PASARELA.has(e.status) && e.detail === undefined
}

async function lanzarError(res: Response, path: string): Promise<never> {
  let detail: ErrorResponse | undefined
  try {
    detail = (await res.json()) as ErrorResponse
  } catch {
    // cuerpo vacío o no JSON
  }
  // Sin el JSON del backend, «Error 524» no le dice nada a nadie: lo que pasó
  // es que un intermediario se cansó de esperar.
  const mensaje =
    detail?.message ??
    (CODIGOS_DE_PASARELA.has(res.status)
      ? `El servidor de SICOT no respondió a tiempo (error ${res.status} del intermediario).`
      : `Error ${res.status}`)
  throw new ApiError(res.status, mensaje, detail, res.status === 429 ? segundosDeEspera(res) : undefined)
}

export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  if (!(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }

  const res = await pedirConSesion(path, options, headers)

  if (!res.ok) {
    await lanzarError(res, path)
  }

  if (authToken) oyenteDeContacto?.()

  if (res.status === 204) {
    return undefined as T
  }

  const text = await res.text()
  return text ? (JSON.parse(text) as T) : (undefined as T)
}

/**
 * La respuesta tal cual, para leerla a medida que llega (la respuesta en vivo
 * del Copiloto). Los errores se lanzan igual que en {@link apiFetch}.
 */
export async function apiFetchRespuesta(path: string, options: RequestInit = {}): Promise<Response> {
  const headers = new Headers(options.headers)
  headers.set('Content-Type', 'application/json')

  const res = await pedirConSesion(path, options, headers)
  if (!res.ok) {
    await lanzarError(res, path)
  }
  return res
}

export async function apiFetchBlob(path: string, options: RequestInit = {}): Promise<Blob> {
  const headers = new Headers(options.headers)

  const res = await pedirConSesion(path, options, headers)

  if (!res.ok) {
    await lanzarError(res, path)
  }
  return res.blob()
}
