// La copia local de lectura: lo último que el servidor contestó, guardado en
// este equipo, para que el supervisor siga viendo su trabajo cuando se queda
// sin red.
//
// Es el punto 1 de ADR-015 y la interpretación C de ADR-001, que ya estaba
// pre-aprobada. **Solo lectura**: aquí no se escribe nada. Lo que el supervisor
// cambia sin red lo guarda la cola de cambios (`colaDeCambios.ts`, punto 3 del
// ADR), que se engancha aquí para que cada lectura salga con esos cambios
// encima y rotulados como pendientes.
//
// <h2>Por qué IndexedDB</h2>
// La aplicación es la misma en tres sitios (ADR-012) y esto tiene que
// funcionar en los tres. IndexedDB es el único almacén que existe igual en el
// navegador, en el WebView2 del instalador de escritorio y en el WebView de
// Android: una sola implantación. `localStorage` no sirve —son unos 5 MB y es
// síncrono, así que bloquearía el dibujado— y los complementos de Tauri solo
// existen dentro del empaquetado, lo que obligaría a un segundo camino para la
// web. Va incluido en la plataforma, sin dependencias ni licencias.
//
// <h2>Lo que NO se guarda, y por qué</h2>
// - **El contenido de los documentos.** Son hasta 20 MB cada uno y un contrato
//   puede llevar catorce: guardarlos exige una política de desalojo y un tope,
//   que es una decisión aparte. Hoy se guarda la ficha de cada documento (que
//   existe, su nombre, su estado), no sus bytes.
// - **La verificación contra el SECOP.** Depende de datos.gov.co por
//   definición. Enseñar una verificación vieja como si fuera de ahora sería
//   decir que el contrato está verificado cuando no se ha podido mirar.
// - **El Copiloto.** Ollama contesta o no contesta; una respuesta guardada a
//   otra pregunta no vale.

import { esCorteDelCamino } from './api/client'

/** Lo que se guarda por cada lectura. */
type Entrada = {
  /** De quién es: una copia de otra cuenta no se le enseña a nadie. */
  usuario: string
  /** Cuándo contestó el servidor, en milisegundos desde epoch. */
  cuando: number
  datos: unknown
}

const BASE = 'sicot-copia-local'
const ALMACEN = 'lecturas'

/**
 * Un almacén mínimo: guardar, leer y vaciar. Existe como interfaz para que
 * haya dos implantaciones —IndexedDB y memoria— sin que el resto del módulo
 * sepa cuál está usando.
 */
type Almacen = {
  guardar(clave: string, entrada: Entrada): Promise<void>
  leer(clave: string): Promise<Entrada | null>
  vaciar(): Promise<void>
}

/**
 * Cuando IndexedDB no está (ventana privada, políticas del equipo, el entorno
 * de pruebas), se usa memoria.
 *
 * No es un apaño: es la respuesta honesta. En una ventana privada no hay
 * ningún sitio donde dejar una copia que sobreviva a cerrar la aplicación, y
 * fingir que sí lo hay sería peor que no tenerla. Dentro de la misma sesión
 * sigue sirviendo, y al cerrarla se pierde, que es exactamente lo que el
 * usuario pidió al abrir esa ventana.
 */
function almacenEnMemoria(): Almacen {
  const mapa = new Map<string, Entrada>()
  return {
    async guardar(clave, entrada) {
      mapa.set(clave, entrada)
    },
    async leer(clave) {
      return mapa.get(clave) ?? null
    },
    async vaciar() {
      mapa.clear()
    },
  }
}

function pedir<T>(solicitud: IDBRequest<T>): Promise<T> {
  return new Promise((resolver, rechazar) => {
    solicitud.onsuccess = () => resolver(solicitud.result)
    solicitud.onerror = () => rechazar(solicitud.error)
  })
}

function almacenEnIndexedDB(): Almacen {
  let base: Promise<IDBDatabase> | null = null

  function abrir(): Promise<IDBDatabase> {
    if (!base) {
      base = new Promise((resolver, rechazar) => {
        const solicitud = indexedDB.open(BASE, 1)
        solicitud.onupgradeneeded = () => {
          if (!solicitud.result.objectStoreNames.contains(ALMACEN)) {
            solicitud.result.createObjectStore(ALMACEN)
          }
        }
        solicitud.onsuccess = () => resolver(solicitud.result)
        solicitud.onerror = () => rechazar(solicitud.error)
      })
      // Una base que no se pudo abrir no se reintenta en bucle en cada
      // lectura, pero tampoco se queda cacheada como rota para siempre.
      base.catch(() => {
        base = null
      })
    }
    return base
  }

  async function transaccion<T>(modo: IDBTransactionMode, usar: (s: IDBObjectStore) => IDBRequest<T>): Promise<T> {
    const db = await abrir()
    return pedir(usar(db.transaction(ALMACEN, modo).objectStore(ALMACEN)))
  }

  return {
    async guardar(clave, entrada) {
      await transaccion('readwrite', (s) => s.put(entrada, clave))
    },
    async leer(clave) {
      return (await transaccion<Entrada | undefined>('readonly', (s) => s.get(clave))) ?? null
    },
    async vaciar() {
      await transaccion('readwrite', (s) => s.clear())
    },
  }
}

let almacen: Almacen | null = null

function elAlmacen(): Almacen {
  if (!almacen) {
    almacen = typeof indexedDB === 'undefined' ? almacenEnMemoria() : almacenEnIndexedDB()
  }
  return almacen
}

// ── De quién es la copia ────────────────────────────────────────────────────

let usuarioActual: string | null = null

/**
 * De quién es lo que hay guardado, **fuera de la memoria del módulo**.
 *
 * Sin esto la copia no servía para nada. Al arrancar, `usuarioActual` es
 * siempre `null`, así que la primera llamada de la sesión siempre parecía un
 * cambio de usuario y vaciaba el almacén: la copia se borraba en cada
 * recarga y jamás sobrevivía a cerrar la aplicación, que es justo para lo que
 * existe. No lo vio ninguna prueba —no recargan la página— sino la
 * comprobación en el navegador del 26-10-2026.
 */
const CLAVE_DUENO = 'sicot.copia-local.de'

function duenoGuardado(): string | null {
  try {
    return localStorage.getItem(CLAVE_DUENO)
  } catch {
    // Almacenamiento bloqueado: se responde «de nadie», que hace vaciar. Es el
    // lado seguro del error.
    return null
  }
}

function anotarDueno(usuario: string | null) {
  try {
    if (usuario === null) localStorage.removeItem(CLAVE_DUENO)
    else localStorage.setItem(CLAVE_DUENO, usuario)
  } catch {
    /* Sin sitio donde anotarlo: la próxima vez se vaciará, que es inofensivo. */
  }
}

/**
 * Dice de quién son las copias que se guarden a partir de ahora.
 *
 * Es una medida de seguridad, no de orden: dos supervisores pueden usar el
 * mismo portátil del Centro, y una copia guardada por uno no puede aparecerle
 * al otro. Al **cambiar** de usuario se vacía todo lo guardado, porque es más
 * barato volver a pedirlo que arriesgarse a enseñar el expediente de otro.
 * Volver a entrar con la misma cuenta no vacía nada: ahí está el valor.
 */
export async function copiaLocalDe(usuario: string | null): Promise<void> {
  usuarioActual = usuario
  if (duenoGuardado() === usuario) return
  await olvidarLaCopiaLocal()
  anotarDueno(usuario)
}

/** Borra todo lo guardado. Se llama al cerrar sesión y al cambiar de usuario. */
export async function olvidarLaCopiaLocal(): Promise<void> {
  try {
    await elAlmacen().vaciar()
  } catch {
    // Que no se pueda vaciar no debe impedir cerrar la sesión.
  }
  anotarDueno(null)
  anunciar({ sirviendoCopia: false, cuando: null })
}

// ── Qué se le está enseñando al usuario ─────────────────────────────────────

/** Si lo que hay en pantalla salió de la copia local, y de cuándo es. */
export type EstadoDeLaCopia = { sirviendoCopia: boolean; cuando: number | null }

let estado: EstadoDeLaCopia = { sirviendoCopia: false, cuando: null }
const oyentes = new Set<(e: EstadoDeLaCopia) => void>()

function anunciar(nuevo: EstadoDeLaCopia) {
  if (nuevo.sirviendoCopia === estado.sirviendoCopia && nuevo.cuando === estado.cuando) return
  estado = nuevo
  oyentes.forEach((o) => o(estado))
}

export function estadoDeLaCopia(): EstadoDeLaCopia {
  return estado
}

/** Avisa cuando se empieza (o se deja) de servir de la copia local. */
export function alCambiarLaCopia(oyente: (e: EstadoDeLaCopia) => void): () => void {
  oyentes.add(oyente)
  return () => {
    oyentes.delete(oyente)
  }
}

// ── Lo que se pone encima de cada lectura ───────────────────────────────────

type Transformador = (clave: string, datos: unknown) => Promise<unknown>

let transformador: Transformador | null = null

/**
 * Registra lo que se aplica a cada lectura antes de entregarla: hoy, los
 * cambios que el supervisor hizo y el servidor todavía no recibió.
 *
 * Es un registro y no una importación de la cola a propósito: la cola ya
 * depende de este módulo para leer la base de cada cambio, y la dependencia
 * de vuelta haría un ciclo entre los dos.
 *
 * Se aplica a lo que se entrega, **nunca** a lo que se guarda: la copia tiene
 * que seguir siendo lo que contestó el servidor, o un cambio rechazado
 * quedaría pegado a ella como si hubiera entrado.
 */
export function alServirUnaLectura(fn: Transformador): void {
  transformador = fn
}

async function servir<T>(clave: string, datos: T): Promise<T> {
  if (!transformador) return datos
  try {
    return (await transformador(clave, datos)) as T
  } catch {
    // Que no se puedan poner encima los pendientes no puede dejar la pantalla
    // sin datos: se entrega lo del servidor tal cual.
    return datos
  }
}

/**
 * Lo último que contestó el servidor para esa lectura, de la cuenta que entró,
 * o `null`. Lo usa la cola para recordar qué tenía delante el supervisor al
 * hacer un cambio.
 */
export async function leerDeLaCopia(clave: string): Promise<unknown> {
  const deQuien = usuarioActual
  if (deQuien === null) return null
  try {
    const entrada = await elAlmacen().leer(clave)
    return entrada !== null && entrada.usuario === deQuien ? entrada.datos : null
  } catch {
    return null
  }
}

// ── La lectura ──────────────────────────────────────────────────────────────

/**
 * Pide al servidor y, si no hay forma de llegar, devuelve lo último que
 * contestó.
 *
 * <h2>Solo ante un corte del camino</h2>
 * La copia se sirve únicamente cuando la petición no llegó a ninguna parte
 * —sin red, o un intermediario que no responde ({@link esCorteDelCamino})—.
 * Ante un 403 o un 404 se propaga el error tal cual: que el servidor diga «ya
 * no puede ver esto» y SICOT conteste con una copia de cuando sí podía sería
 * saltarse un permiso, no sobrevivir a un apagón.
 *
 * <h2>Si no hay copia</h2>
 * Se relanza el error original. Quien llama verá el mismo fallo de red de
 * siempre, con su mensaje de siempre: esto añade una salida, no cambia la que
 * ya había.
 *
 * @param clave identifica la lectura (la ruta de la API sirve).
 */
export async function conCopiaLocal<T>(clave: string, pedirAlServidor: () => Promise<T>): Promise<T> {
  const deQuien = usuarioActual
  try {
    const datos = await pedirAlServidor()
    // Guardar no puede estropear una lectura que funcionó.
    try {
      if (deQuien !== null) {
        await elAlmacen().guardar(clave, { usuario: deQuien, cuando: Date.now(), datos })
      }
    } catch {
      /* Sin sitio donde guardar: se sigue, sin copia. */
    }
    anunciar({ sirviendoCopia: false, cuando: null })
    return servir(clave, datos)
  } catch (e) {
    if (!esCorteDelCamino(e)) throw e
    let entrada: Entrada | null = null
    try {
      entrada = await elAlmacen().leer(clave)
    } catch {
      entrada = null
    }
    // La copia de otra cuenta no se enseña, aunque esté ahí.
    if (entrada === null || deQuien === null || entrada.usuario !== deQuien) throw e
    anunciar({ sirviendoCopia: true, cuando: entrada.cuando })
    return servir(clave, entrada.datos as T)
  }
}

/** Solo para las pruebas: devuelve el módulo a su estado inicial, almacén incluido. */
export function reiniciarCopiaLocalParaPruebas(): void {
  almacen = null
  anotarDueno(null)
  simularReinicioParaPruebas()
}

/**
 * Solo para las pruebas: como cerrar y volver a abrir la aplicación. Olvida lo
 * que el módulo tenía en memoria y **conserva el almacén**, que es lo que en
 * un equipo de verdad sobrevive a la recarga.
 */
export function simularReinicioParaPruebas(): void {
  usuarioActual = null
  estado = { sirviendoCopia: false, cuando: null }
  oyentes.clear()
}
