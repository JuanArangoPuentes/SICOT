// La cola de cambios: lo que el supervisor hizo sin red, guardado en este
// equipo en el orden en que lo hizo, para enviarlo cuando vuelva la conexión.
//
// Es el punto 3 de ADR-015, con la forma que el ADR eligió: **cola de
// intenciones**, no réplica de la base. El cliente no decide nada: recuerda qué
// quiso hacer el supervisor y, al volver la red, lo pide **en orden** a los
// mismos endpoints de siempre. El servidor valida exactamente igual que con
// conexión, y lo que rechace se le muestra al supervisor con el motivo, sin
// perder su trabajo.
//
// <h2>Qué entra en la cola</h2>
// Las cuatro escrituras del supervisor que no llevan archivos ni dependen del
// servidor para producirse: cerrar o avanzar un sub-paso, las fechas de entrega
// acordadas, los contactos de SST y ambiental, y marcar una alerta como leída.
// Cargar, generar y **firmar** documentos siguen exigiendo red. Firmar, porque
// así lo decidió el ADR: la firma registra la hora y comprueba que no haya otro
// documento firmado en el paso, y ninguna de las dos cosas se puede hacer bien
// desde un solo portátil.
//
// <h2>Por qué no hace falta deduplicar en el servidor</h2>
// Las cuatro ya son idempotentes allí: pasar un sub-paso al estado que ya tiene
// no hace nada (ni deja constancia), los dos PUT reemplazan la lista entera, y
// marcar leída una alerta leída la deja igual. Si la red se corta después de
// que el servidor aplicó un cambio y antes de que llegara la respuesta, el
// reenvío da el mismo resultado. Lo comprueba
// `TrabajoSinConexionIntegrationTest`.
//
// <h2>Lo que la cola NO hace: decidir un conflicto</h2>
// Las fechas de entrega y los contactos se guardan como lista entera. Si
// mientras el supervisor estaba sin red alguien cambió esa misma lista en el
// servidor, reenviar la suya borraría la otra sin que nadie lo viera. Por eso
// cada cambio de lista guarda lo que el supervisor tenía delante cuando lo hizo
// (`base`) y, antes de enviarlo, se compara con lo que hay ahora. Si no
// coincide, **no se envía**: queda rechazado con las dos versiones a la vista y
// decide el supervisor. Fusionar listas en el cliente sería inventar una regla
// del expediente, que es justo lo que ADR-015 prohíbe.

import { ApiError, apiFetch, esCorteDelCamino, hayTokenDeSesion } from './api/client'
import { alServirUnaLectura, leerDeLaCopia } from './copiaLocal'
import type {
  AcuerdoDeEntregasResponse,
  AlertaResponse,
  ContactoDelContrato,
  ContactosDelContratoResponse,
  EstadoSubetapa,
  EtapaResponse,
} from './api/types'

/** La cabecera con la que el servidor guarda aparte la hora declarada por el equipo (V24). */
export const CABECERA_HECHO_SIN_CONEXION = 'X-SICOT-Hecho-Sin-Conexion'

// ── Qué se guarda ───────────────────────────────────────────────────────────

/** Lo que el supervisor quiso hacer. De aquí salen la petición y cómo se ve en pantalla. */
export type Intencion =
  | { tipo: 'estado-subetapa'; contratoId: number; subetapaId: number; codigo: string; estado: EstadoSubetapa }
  | { tipo: 'entregas-acordadas'; contratoId: number; entregas: Array<{ fecha: string; descripcion: string }> }
  | { tipo: 'contactos-sst-ambiental'; contratoId: number; contactos: ContactoDelContrato[] }
  | { tipo: 'alerta-leida'; contratoId: number | null; alertaId: number }

export type Cambio = {
  /** El orden en que se hizo. Lo asigna el almacén y es el orden de envío. */
  id: number
  /** De quién es: la cola de otra cuenta ni se enseña ni se envía. */
  usuario: string
  intencion: Intencion
  /** Cuándo lo hizo, según el reloj de este equipo (ISO-8601). */
  hechoEn: string
  /**
   * Lo que había en el servidor, según la última lectura guardada, cuando el
   * supervisor hizo el cambio. Solo en los cambios de lista; sirve para no
   * pisar lo que otro haya cambiado mientras tanto.
   */
  base?: unknown
  estado: 'pendiente' | 'rechazado'
  /** Por qué no entró, en las palabras del servidor o de esta cola. */
  motivo?: string
  /** En un conflicto, lo que tiene ahora el servidor, para ponerlo al lado. */
  enElServidor?: unknown
  /** Fallos del servidor (5xx) seguidos: tras unos cuantos deja de bloquear la cola. */
  intentosFallidos: number
}

type CambioNuevo = Omit<Cambio, 'id'>

// ── El almacén ──────────────────────────────────────────────────────────────

type Almacen = {
  agregar(cambio: CambioNuevo): Promise<number>
  todos(): Promise<Cambio[]>
  actualizar(cambio: Cambio): Promise<void>
  borrar(id: number): Promise<void>
}

function almacenEnMemoria(): Almacen {
  const mapa = new Map<number, Cambio>()
  let siguiente = 1
  return {
    async agregar(cambio) {
      const id = siguiente++
      mapa.set(id, { ...cambio, id })
      return id
    },
    async todos() {
      return [...mapa.values()].sort((a, b) => a.id - b.id)
    },
    async actualizar(cambio) {
      mapa.set(cambio.id, cambio)
    },
    async borrar(id) {
      mapa.delete(id)
    },
  }
}

/**
 * Una base aparte de la copia local, a propósito: la copia se vacía al cerrar
 * sesión o al cambiar de cuenta, y esto no puede vaciarse con ella. Es trabajo
 * del supervisor que todavía no llegó al servidor; borrarlo al cerrar sesión
 * sería perderlo sin que nadie lo decidiera.
 */
function almacenEnIndexedDB(): Almacen {
  const BASE = 'sicot-cola-de-cambios'
  const ALMACEN = 'cambios'
  let base: Promise<IDBDatabase> | null = null

  function abrir(): Promise<IDBDatabase> {
    if (!base) {
      base = new Promise((resolver, rechazar) => {
        const solicitud = indexedDB.open(BASE, 1)
        solicitud.onupgradeneeded = () => {
          if (!solicitud.result.objectStoreNames.contains(ALMACEN)) {
            solicitud.result.createObjectStore(ALMACEN, { keyPath: 'id', autoIncrement: true })
          }
        }
        solicitud.onsuccess = () => resolver(solicitud.result)
        solicitud.onerror = () => rechazar(solicitud.error)
      })
      base.catch(() => {
        base = null
      })
    }
    return base
  }

  async function pedir<T>(modo: IDBTransactionMode, usar: (s: IDBObjectStore) => IDBRequest<T>): Promise<T> {
    const db = await abrir()
    return new Promise((resolver, rechazar) => {
      const solicitud = usar(db.transaction(ALMACEN, modo).objectStore(ALMACEN))
      solicitud.onsuccess = () => resolver(solicitud.result)
      solicitud.onerror = () => rechazar(solicitud.error)
    })
  }

  return {
    async agregar(cambio) {
      return Number(await pedir('readwrite', (s) => s.add(cambio)))
    },
    async todos() {
      const lista = await pedir<Cambio[]>('readonly', (s) => s.getAll() as IDBRequest<Cambio[]>)
      return lista.sort((a, b) => a.id - b.id)
    },
    async actualizar(cambio) {
      await pedir('readwrite', (s) => s.put(cambio))
    },
    async borrar(id) {
      await pedir('readwrite', (s) => s.delete(id))
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

// ── El estado en memoria y quién lo escucha ─────────────────────────────────

let usuarioActual: string | null = null
/** Los cambios de la cuenta que entró, en orden. Espejo del almacén para leer sin esperar. */
let cambios: Cambio[] = []
let cargada: Promise<void> = Promise.resolve()

const oyentes = new Set<(lista: readonly Cambio[]) => void>()
const oyentesDeEnvio = new Set<() => void>()

function anunciar() {
  const copia = [...cambios]
  oyentes.forEach((o) => o(copia))
}

/** Avisa cada vez que la cola cambia: un cambio nuevo, uno enviado, uno rechazado. */
export function alCambiarLaCola(oyente: (lista: readonly Cambio[]) => void): () => void {
  oyentes.add(oyente)
  return () => {
    oyentes.delete(oyente)
  }
}

/**
 * Avisa cuando un reenvío dejó al menos un cambio aplicado en el servidor:
 * es el momento de volver a pedir lo que la pantalla muestra, que ya no es lo
 * de la copia sino lo de verdad.
 */
export function alEnviarCambios(oyente: () => void): () => void {
  oyentesDeEnvio.add(oyente)
  return () => {
    oyentesDeEnvio.delete(oyente)
  }
}

export function cambiosDeLaCola(): readonly Cambio[] {
  return cambios
}

/**
 * Dice de quién es la cola que se ve y se envía a partir de ahora. Al cerrar
 * sesión (`null`) no se borra nada: solo deja de mostrarse hasta que vuelva a
 * entrar esa misma cuenta.
 */
export function colaDe(usuario: string | null): Promise<void> {
  usuarioActual = usuario
  cargada = (async () => {
    try {
      const todos = await elAlmacen().todos()
      cambios = usuario === null ? [] : todos.filter((c) => c.usuario === usuario)
    } catch {
      cambios = []
    }
    anunciar()
  })()
  return cargada
}

/** Cuántos cambios sin enviar hay en este equipo, de cualquier cuenta. Para la pantalla de acceso. */
export async function cambiosGuardadosEnEsteEquipo(): Promise<number> {
  try {
    return (await elAlmacen().todos()).length
  } catch {
    return 0
  }
}

// ── Cómo se traduce cada intención ──────────────────────────────────────────

function peticionDe(intencion: Intencion): { ruta: string; metodo: 'PATCH' | 'PUT'; cuerpo?: unknown } {
  switch (intencion.tipo) {
    case 'estado-subetapa':
      return {
        ruta: `/api/subetapas/${intencion.subetapaId}/estado`,
        metodo: 'PATCH',
        cuerpo: { estado: intencion.estado },
      }
    case 'entregas-acordadas':
      return {
        ruta: `/api/contratos/${intencion.contratoId}/entregas-acordadas`,
        metodo: 'PUT',
        cuerpo: { entregas: intencion.entregas },
      }
    case 'contactos-sst-ambiental':
      return {
        ruta: `/api/contratos/${intencion.contratoId}/contactos-sst-ambiental`,
        metodo: 'PUT',
        cuerpo: { contactos: intencion.contactos },
      }
    case 'alerta-leida':
      return { ruta: `/api/alertas/${intencion.alertaId}/leida`, metodo: 'PATCH' }
  }
}

/** La lectura que un cambio de lista reemplaza entera; es la que se compara antes de enviarlo. */
function lecturaQueReemplaza(intencion: Intencion): string | null {
  switch (intencion.tipo) {
    case 'entregas-acordadas':
      return `/api/contratos/${intencion.contratoId}/entregas-acordadas`
    case 'contactos-sst-ambiental':
      return `/api/contratos/${intencion.contratoId}/contactos-sst-ambiental`
    default:
      return null
  }
}

/**
 * Lo que importa de una lista para saber si cambió, sin lo que el servidor
 * añade solo (el número de cada entrega, el orden). Dos listas con las mismas
 * fechas y textos son la misma aunque el servidor las haya renumerado.
 */
function comparable(tipo: Intencion['tipo'], datos: unknown): string | null {
  if (datos === null || datos === undefined) return null
  if (tipo === 'entregas-acordadas') {
    const entregas = (datos as Partial<AcuerdoDeEntregasResponse>).entregas ?? []
    return JSON.stringify(
      entregas.map((e) => [e.fecha, e.descripcion.trim()]).sort((a, b) => (a.join('|') < b.join('|') ? -1 : 1)),
    )
  }
  if (tipo === 'contactos-sst-ambiental') {
    const contactos = (datos as Partial<ContactosDelContratoResponse>).contactos ?? []
    return JSON.stringify(
      contactos
        .map((c) => [c.rol, c.correo.trim().toLowerCase(), (c.nombre ?? '').trim()])
        .sort((a, b) => (a.join('|') < b.join('|') ? -1 : 1)),
    )
  }
  return null
}

/** La lista que quiere dejar un cambio, con la forma de la lectura, para compararla igual que la base. */
function loQueDeja(intencion: Intencion): unknown {
  if (intencion.tipo === 'entregas-acordadas') return { entregas: intencion.entregas }
  if (intencion.tipo === 'contactos-sst-ambiental') return { contactos: intencion.contactos }
  return null
}

/** Cómo se lo nombra al supervisor en la lista de pendientes. */
export function describirCambio(intencion: Intencion): string {
  switch (intencion.tipo) {
    case 'estado-subetapa':
      return intencion.estado === 'COMPLETADA'
        ? `Cerrar el sub-paso ${intencion.codigo}`
        : intencion.estado === 'EN_CURSO'
          ? `Empezar el sub-paso ${intencion.codigo}`
          : `Devolver el sub-paso ${intencion.codigo} a pendiente`
    case 'entregas-acordadas': {
      const n = intencion.entregas.length
      return n === 0
        ? 'Borrar las fechas de entrega acordadas'
        : `Guardar ${n} fecha${n === 1 ? '' : 's'} de entrega acordada${n === 1 ? '' : 's'}`
    }
    case 'contactos-sst-ambiental': {
      const n = intencion.contactos.length
      return n === 0
        ? 'Borrar los contactos de SST y ambiental'
        : `Guardar ${n} contacto${n === 1 ? '' : 's'} de SST y ambiental`
    }
    case 'alerta-leida':
      return 'Marcar una alerta como leída'
  }
}

// ── Escribir: con red se envía, sin red se guarda ───────────────────────────

export type ResultadoDeEscritura<T> = { enCola: false; respuesta: T } | { enCola: true; cambio: Cambio }

function hayPendientes(): boolean {
  return cambios.some((c) => c.estado === 'pendiente')
}

async function encolar(intencion: Intencion): Promise<Cambio> {
  if (usuarioActual === null) {
    throw new Error('No hay una sesión con la que guardar el cambio en este equipo.')
  }
  const lectura = lecturaQueReemplaza(intencion)
  const nuevo: CambioNuevo = {
    usuario: usuarioActual,
    intencion,
    hechoEn: new Date().toISOString(),
    base: lectura ? ((await leerDeLaCopia(lectura)) ?? undefined) : undefined,
    estado: 'pendiente',
    intentosFallidos: 0,
  }
  const id = await elAlmacen().agregar(nuevo)
  const cambio: Cambio = { ...nuevo, id }
  cambios = [...cambios, cambio]
  anunciar()
  return cambio
}

/**
 * Envía el cambio, o lo guarda en este equipo si no hay forma de llegar al
 * servidor.
 *
 * <h2>Solo ante un corte del camino</h2>
 * Como la copia local: se encola únicamente cuando la petición no llegó a
 * ninguna parte ({@link esCorteDelCamino}). Un 400 o un 403 son respuestas
 * del servidor y se propagan como siempre; encolarlos sería guardar para luego
 * algo que el servidor ya dijo que no.
 *
 * <h2>El orden manda</h2>
 * Si ya hay cambios esperando, el nuevo se pone detrás aunque ahora sí haya
 * red: mandarlo directo lo adelantaría a los anteriores, y cerrar el 2.3 antes
 * que el 2.2 no es lo que hizo el supervisor.
 */
export async function enviarOEncolar<T>(intencion: Intencion): Promise<ResultadoDeEscritura<T>> {
  await cargada
  if (hayPendientes()) {
    const cambio = await encolar(intencion)
    void reenviarLaCola()
    return { enCola: true, cambio }
  }
  const { ruta, metodo, cuerpo } = peticionDe(intencion)
  try {
    const respuesta = await apiFetch<T>(ruta, {
      method: metodo,
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    })
    return { enCola: false, respuesta }
  } catch (e) {
    if (!esCorteDelCamino(e)) throw e
    return { enCola: true, cambio: await encolar(intencion) }
  }
}

// ── Reenviar ────────────────────────────────────────────────────────────────

/** Tras tantos 5xx seguidos el cambio se aparta: un fallo fijo del servidor no puede tapar la cola entera. */
const MAXIMO_DE_FALLOS_DEL_SERVIDOR = 3

let reenvioEnCurso: Promise<ResultadoDelReenvio> | null = null

export type ResultadoDelReenvio = {
  enviados: number
  rechazados: number
  /** Por qué se detuvo antes de vaciar la cola, si se detuvo. */
  detenido: 'sin-red' | 'sin-sesion' | 'servidor' | null
}

async function quitar(cambio: Cambio) {
  await elAlmacen().borrar(cambio.id)
  cambios = cambios.filter((c) => c.id !== cambio.id)
}

async function guardarEstado(cambio: Cambio) {
  await elAlmacen().actualizar(cambio)
  cambios = cambios.map((c) => (c.id === cambio.id ? cambio : c))
}

/**
 * Antes de enviar una lista entera, mira si alguien la cambió en el servidor
 * mientras el supervisor estaba sin red. Devuelve `'enviar'`, `'ya-estaba'`
 * (el servidor ya tiene exactamente lo que el supervisor quería: la respuesta
 * anterior se perdió por el camino) o lo que hay ahora, si es otra cosa.
 */
async function comprobarLaBase(cambio: Cambio): Promise<'enviar' | 'ya-estaba' | { otro: unknown }> {
  const lectura = lecturaQueReemplaza(cambio.intencion)
  if (!lectura || cambio.base === undefined) return 'enviar'
  const ahora = await apiFetch<unknown>(lectura)
  const tipo = cambio.intencion.tipo
  const actual = comparable(tipo, ahora)
  if (actual === comparable(tipo, loQueDeja(cambio.intencion))) return 'ya-estaba'
  if (actual === comparable(tipo, cambio.base)) return 'enviar'
  return { otro: ahora }
}

async function reenviar(): Promise<ResultadoDelReenvio> {
  await cargada
  const resultado: ResultadoDelReenvio = { enviados: 0, rechazados: 0, detenido: null }
  if (!hayTokenDeSesion()) {
    // Una sesión abierta sin red no tiene con qué presentarse al servidor:
    // hay que volver a entrar con conexión, y entonces se envía.
    if (hayPendientes()) resultado.detenido = 'sin-sesion'
    return resultado
  }
  const intentados = new Set<number>()
  for (;;) {
    // La cola se vuelve a mirar en cada vuelta, no se toma una foto al
    // empezar: un cambio que entra mientras se envía sale en este mismo
    // reenvío (con la foto esperaba al siguiente, medio minuto), y uno que el
    // supervisor descartó mientras tanto ya no sale.
    const cambio = cambios.find((c) => c.estado === 'pendiente' && !intentados.has(c.id))
    if (!cambio) break
    intentados.add(cambio.id)
    try {
      const comprobacion = await comprobarLaBase(cambio)
      if (comprobacion === 'ya-estaba') {
        await quitar(cambio)
        resultado.enviados++
        continue
      }
      if (comprobacion !== 'enviar') {
        await guardarEstado({
          ...cambio,
          estado: 'rechazado',
          motivo:
            'Mientras usted estaba sin conexión, alguien cambió esta lista en el servidor. No se envió la suya para no ' +
            'borrar la otra: compárelas y decida.',
          enElServidor: comprobacion.otro,
        })
        resultado.rechazados++
        continue
      }
      const { ruta, metodo, cuerpo } = peticionDe(cambio.intencion)
      await apiFetch<unknown>(ruta, {
        method: metodo,
        body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
        headers: { [CABECERA_HECHO_SIN_CONEXION]: cambio.hechoEn },
      })
      await quitar(cambio)
      resultado.enviados++
    } catch (e) {
      if (esCorteDelCamino(e)) {
        resultado.detenido = 'sin-red'
        break
      }
      if (e instanceof ApiError && e.esNoAutenticado) {
        resultado.detenido = 'sin-sesion'
        break
      }
      // Una alerta que ya no existe no tiene nada que marcar: lo que el
      // supervisor quería —que dejara de salirle— ya es así.
      if (e instanceof ApiError && e.status === 404 && cambio.intencion.tipo === 'alerta-leida') {
        await quitar(cambio)
        continue
      }
      if (e instanceof ApiError && (e.status >= 500 || e.esDemasiadasSolicitudes)) {
        const intentos = cambio.intentosFallidos + 1
        if (intentos < MAXIMO_DE_FALLOS_DEL_SERVIDOR) {
          await guardarEstado({ ...cambio, intentosFallidos: intentos })
          resultado.detenido = 'servidor'
          break
        }
        await guardarEstado({ ...cambio, estado: 'rechazado', motivo: e.message, intentosFallidos: intentos })
        resultado.rechazados++
        continue
      }
      // Un 400, 403, 404 o 409: el servidor revisó el cambio y dijo que no.
      // Queda a la vista con su motivo; los siguientes siguen su camino,
      // porque el servidor valida cada uno por su cuenta.
      await guardarEstado({
        ...cambio,
        estado: 'rechazado',
        motivo: e instanceof Error ? e.message : 'El servidor no aceptó el cambio.',
      })
      resultado.rechazados++
    }
  }
  anunciar()
  if (resultado.enviados > 0) oyentesDeEnvio.forEach((o) => o())
  return resultado
}

/**
 * Envía, en orden, lo que haya pendiente. Si ya hay un envío en curso devuelve
 * ese mismo: dos envíos a la vez mandarían dos veces el mismo cambio.
 */
export function reenviarLaCola(): Promise<ResultadoDelReenvio> {
  if (!reenvioEnCurso) {
    reenvioEnCurso = reenviar().finally(() => {
      reenvioEnCurso = null
    })
  }
  return reenvioEnCurso
}

/** El supervisor decide no enviar un cambio rechazado (o uno pendiente). */
export async function descartarCambio(id: number): Promise<void> {
  const cambio = cambios.find((c) => c.id === id)
  if (!cambio) return
  await quitar(cambio)
  anunciar()
}

/**
 * Vuelve a poner en la cola un cambio rechazado. En un conflicto de lista eso
 * significa «envíe la mía de todos modos»: se toma como base lo que hay ahora
 * en el servidor, que es lo que el supervisor acaba de ver y aceptó reemplazar.
 */
export async function reintentarCambio(id: number): Promise<void> {
  const cambio = cambios.find((c) => c.id === id)
  if (!cambio) return
  await guardarEstado({
    ...cambio,
    estado: 'pendiente',
    motivo: undefined,
    base: cambio.enElServidor ?? cambio.base,
    enElServidor: undefined,
    intentosFallidos: 0,
  })
  anunciar()
  void reenviarLaCola()
}

// ── Lo que se ve: la copia con los cambios todavía sin enviar encima ────────

/** Marca en lo que se pinta que todavía no lo aceptó el servidor (ADR-015: el «tercer estado»). */
export type ConPendiente = { pendienteDeEnviar?: boolean }

const RUTA_ETAPAS = /^\/api\/contratos\/(\d+)\/etapas$/
const RUTA_ENTREGAS = /^\/api\/contratos\/(\d+)\/entregas-acordadas$/
const RUTA_CONTACTOS = /^\/api\/contratos\/(\d+)\/contactos-sst-ambiental$/
const RUTA_ALERTAS = /^\/api\/contratos\/(\d+)\/alertas$/

/** El último cambio pendiente que cumple la condición: es el que vale si hay varios sobre lo mismo. */
function ultimoPendiente<I extends Intencion['tipo']>(
  tipo: I,
  cumple: (i: Extract<Intencion, { tipo: I }>) => boolean,
): Extract<Intencion, { tipo: I }> | null {
  for (let i = cambios.length - 1; i >= 0; i--) {
    const c = cambios[i]
    if (
      c.estado === 'pendiente' &&
      c.intencion.tipo === tipo &&
      cumple(c.intencion as Extract<Intencion, { tipo: I }>)
    ) {
      return c.intencion as Extract<Intencion, { tipo: I }>
    }
  }
  return null
}

/**
 * Pone encima de una lectura los cambios que el supervisor ya hizo y el
 * servidor todavía no recibió, marcados como pendientes.
 *
 * No recalcula nada que calcule el servidor —el porcentaje de la etapa, la
 * numeración de las entregas más allá de ordenarlas por fecha—: muestra la
 * intención y la rotula. Cuando el servidor la acepte, la siguiente lectura
 * trae los números de verdad.
 */
export async function aplicarCambiosPendientes<T>(ruta: string, datos: T): Promise<T> {
  await cargada
  if (!hayPendientes()) return datos

  let m = RUTA_ETAPAS.exec(ruta)
  if (m && Array.isArray(datos)) {
    return (datos as EtapaResponse[]).map((etapa) => ({
      ...etapa,
      subEtapas: etapa.subEtapas.map((sub) => {
        const cambio = ultimoPendiente('estado-subetapa', (i) => i.subetapaId === sub.id)
        return cambio ? { ...sub, estado: cambio.estado, pendienteDeEnviar: true } : sub
      }),
    })) as T
  }

  m = RUTA_ENTREGAS.exec(ruta)
  if (m) {
    const contratoId = Number(m[1])
    const cambio = ultimoPendiente('entregas-acordadas', (i) => i.contratoId === contratoId)
    if (!cambio) return datos
    const entregas = [...cambio.entregas]
      .sort((a, b) => (a.fecha < b.fecha ? -1 : a.fecha > b.fecha ? 1 : 0))
      .map((e, i) => ({ numero: i + 1, fecha: e.fecha, descripcion: e.descripcion }))
    return { ...(datos as AcuerdoDeEntregasResponse), entregas, pendienteDeEnviar: true } as T
  }

  m = RUTA_CONTACTOS.exec(ruta)
  if (m) {
    const contratoId = Number(m[1])
    const cambio = ultimoPendiente('contactos-sst-ambiental', (i) => i.contratoId === contratoId)
    if (!cambio) return datos
    return { ...(datos as ContactosDelContratoResponse), contactos: cambio.contactos, pendienteDeEnviar: true } as T
  }

  m = RUTA_ALERTAS.exec(ruta)
  if (m && Array.isArray(datos)) {
    return (datos as AlertaResponse[]).map((a) =>
      ultimoPendiente('alerta-leida', (i) => i.alertaId === a.id) ? { ...a, leida: true } : a,
    ) as T
  }

  return datos
}

alServirUnaLectura(aplicarCambiosPendientes)

// ── Cuándo se intenta enviar ────────────────────────────────────────────────

/** Cada cuánto se reintenta mientras quede algo pendiente y haya sesión. */
const INTERVALO_DE_REINTENTO_MS = 30_000

let vigilando = false

/**
 * Engancha los reintentos: al volver la red, cada medio minuto mientras quede
 * algo, y al entrar con conexión. Se llama una vez al arrancar la aplicación.
 */
export function vigilarLaCola(): void {
  if (vigilando || typeof window === 'undefined') return
  vigilando = true
  window.addEventListener('online', () => void reenviarLaCola())
  window.setInterval(() => {
    if (hayPendientes() && hayTokenDeSesion()) void reenviarLaCola()
  }, INTERVALO_DE_REINTENTO_MS)
}

/** Solo para las pruebas: vacía el almacén y el estado del módulo. */
export async function reiniciarLaColaParaPruebas(): Promise<void> {
  almacen = almacenEnMemoria()
  usuarioActual = null
  cambios = []
  cargada = Promise.resolve()
  reenvioEnCurso = null
  oyentes.clear()
  oyentesDeEnvio.clear()
}
