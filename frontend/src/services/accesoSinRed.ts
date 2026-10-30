// Entrar sin red: el punto 4 de ADR-015.
//
// El token de SICOT dura 8 horas y lo firma el servidor. Sin servidor no hay
// token, y sin este módulo un supervisor que pasa la noche en campo no podía ni
// abrir la aplicación a la mañana siguiente para ver su copia local.
//
// <h2>Cómo</h2>
// Al entrar **con** red, si la cuenta es de supervisor, se guarda en el equipo
// una huella de la contraseña: PBKDF2-SHA-256 con sal aleatoria y 600 000
// iteraciones (lo que recomienda OWASP para PBKDF2-SHA-256). La contraseña no
// se guarda nunca. Sin red, la que escriba el supervisor se deriva igual y se
// compara: si coincide, se abre una **sesión sin conexión**, que no lleva token.
// Sirve para ver la copia local y para guardar cambios en la cola; para
// enviarlos hay que volver a entrar con red, que es cuando el servidor vuelve a
// decir si la cuenta sigue valiendo.
//
// <h2>Lo que esto empeora, dicho antes y no después</h2>
// - **Un portátil robado sigue abriendo la copia durante la ventana.** Por eso
//   la ventana existe y se cuenta desde el último contacto con el servidor:
//   pasadas {@link VENTANA_SIN_RED_MS} sin hablar con él, hay que conectarse.
// - **Cambiar la contraseña en el servidor no llega al portátil sin red.** La
//   vieja sigue abriendo la sesión sin conexión hasta que el equipo vuelva a
//   entrar con red. No da acceso a nada del servidor: solo a la copia que ya
//   estaba en el equipo.
// - **La huella se puede atacar por fuerza bruta** si alguien se lleva el
//   almacenamiento del equipo. Las 600 000 iteraciones encarecen cada intento;
//   no lo impiden.
// - **La contraseña sin red cuida la pantalla, no el disco.** La copia local
//   está en IndexedDB sin cifrar, igual que antes de este módulo: quien tenga
//   el equipo y sepa abrir las herramientas del navegador la lee sin pasar por
//   aquí. Lo que esto evita es que cualquiera que abra SICOT en ese portátil vea
//   el expediente. Cifrar la copia es otra decisión, con su propia clave que
//   guardar.
//
// <h2>Solo para supervisores, y solo el último</h2>
// Gestión y Administración trabajan con el servidor del Centro: no tienen copia
// local que ver ni cola que llenar. Y se recuerda una sola cuenta, la última
// que entró con red, porque es la dueña de la copia local: la de otra cuenta se
// vacía al cambiar de usuario, así que abrir una sesión sin red con ella no
// mostraría nada.
//
// <h2>Sin cifrado disponible</h2>
// PBKDF2 viene de `crypto.subtle`, que el navegador solo ofrece en contextos
// seguros: la aplicación de escritorio, el APK, `https://` y `localhost`. Una
// dirección `http://` de la red local no lo es. Ahí el acceso sin red no se
// ofrece y se dice por qué, en vez de guardar la contraseña de otra forma.

import type { AuthResponse } from './api/types'

const CLAVE = 'sicot.acceso-sin-red'

/**
 * Cuánto puede pasar sin hablar con el servidor antes de exigir conexión.
 *
 * Tres días: alcanza para una salida a campo de varios días y deja acotado el
 * caso del portátil robado. ADR-015 dejó abierta la pregunta de cuántos días
 * seguidos se trabaja sin red; si la respuesta es otra, se cambia aquí.
 */
export const VENTANA_SIN_RED_MS = 72 * 60 * 60 * 1000

/** OWASP, «Password Storage Cheat Sheet», PBKDF2-HMAC-SHA256. */
const ITERACIONES = 600_000

/** Para no escribir en el almacenamiento en cada petición. */
const MARGEN_PARA_ANOTAR_MS = 60_000

type Guardado = {
  correo: string
  usuarioId: number
  nombre: string
  sal: string
  huella: string
  iteraciones: number
  /** La última vez que el servidor aceptó una petición con su sesión. */
  ultimoContacto: number
}

function sutil(): SubtleCrypto | null {
  return globalThis.crypto?.subtle ?? null
}

/** ¿Este equipo puede comprobar una contraseña sin red? Ver «Sin cifrado disponible». */
export function accesoSinRedPosible(): boolean {
  return sutil() !== null
}

function aBase64(bytes: Uint8Array): string {
  let binario = ''
  bytes.forEach((b) => (binario += String.fromCharCode(b)))
  return btoa(binario)
}

function deBase64(texto: string): Uint8Array {
  return Uint8Array.from(atob(texto), (c) => c.charCodeAt(0))
}

async function derivar(password: string, sal: Uint8Array, iteraciones: number): Promise<Uint8Array> {
  const s = sutil()
  if (!s) throw new Error('Sin cifrado disponible')
  const clave = await s.importKey('raw', new TextEncoder().encode(password), 'PBKDF2', false, ['deriveBits'])
  const bits = await s.deriveBits(
    { name: 'PBKDF2', hash: 'SHA-256', salt: sal as BufferSource, iterations: iteraciones },
    clave,
    256,
  )
  return new Uint8Array(bits)
}

/** Comparación sin salir antes al primer byte distinto. */
function iguales(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) return false
  let diferencia = 0
  for (let i = 0; i < a.length; i++) diferencia |= a[i] ^ b[i]
  return diferencia === 0
}

function leer(): Guardado | null {
  try {
    const crudo = localStorage.getItem(CLAVE)
    return crudo ? (JSON.parse(crudo) as Guardado) : null
  } catch {
    return null
  }
}

function escribir(g: Guardado | null) {
  try {
    if (g === null) localStorage.removeItem(CLAVE)
    else localStorage.setItem(CLAVE, JSON.stringify(g))
  } catch {
    /* Sin almacenamiento: simplemente no habrá acceso sin red. */
  }
}

export function olvidarAccesoSinRed(): void {
  escribir(null)
}

/** ¿Hay una cuenta que pueda entrar sin red en este equipo? La pantalla de acceso se prepara si la hay. */
export function hayAccesoSinRedRecordado(): boolean {
  return leer() !== null
}

/**
 * Guarda lo necesario para que esta cuenta pueda entrar sin red, después de
 * un inicio de sesión **con** red. Para cualquier cuenta que no sea de
 * supervisor, borra lo que hubiera: la copia local acaba de cambiar de dueño.
 */
export async function recordarAcceso(
  correo: string,
  password: string,
  auth: AuthResponse,
  ahora: number = Date.now(),
): Promise<void> {
  if (auth.rol !== 'SUPERVISOR') {
    olvidarAccesoSinRed()
    return
  }
  if (!accesoSinRedPosible()) return
  const sal = globalThis.crypto.getRandomValues(new Uint8Array(16))
  const huella = await derivar(password, sal, ITERACIONES)
  escribir({
    correo: correo.trim().toLowerCase(),
    usuarioId: auth.usuarioId,
    nombre: auth.nombre,
    sal: aBase64(sal),
    huella: aBase64(huella),
    iteraciones: ITERACIONES,
    ultimoContacto: ahora,
  })
}

/**
 * Apunta que el servidor acaba de aceptar una petición de esta cuenta. Corre
 * la ventana sin red desde ahora.
 */
export function anotarContactoConElServidor(usuarioId: number, ahora: number = Date.now()): void {
  const g = leer()
  if (!g || g.usuarioId !== usuarioId) return
  if (ahora - g.ultimoContacto < MARGEN_PARA_ANOTAR_MS) return
  escribir({ ...g, ultimoContacto: ahora })
}

/** Hasta cuándo puede entrar sin red esta cuenta, o `null` si no puede. */
export function venceElAccesoSinRed(usuarioId: number): number | null {
  const g = leer()
  return g && g.usuarioId === usuarioId ? g.ultimoContacto + VENTANA_SIN_RED_MS : null
}

/** ¿Sigue valiendo una sesión sin conexión de esta cuenta? Se mira al recargar la aplicación. */
export function sesionSinRedVigente(usuarioId: number, ahora: number = Date.now()): boolean {
  const vence = venceElAccesoSinRed(usuarioId)
  return vence !== null && ahora < vence
}

export type ResultadoSinRed = { ok: true; auth: AuthResponse } | { ok: false; motivo: string }

/**
 * Abre una sesión sin conexión si el correo es el del último supervisor que
 * entró con red en este equipo, la contraseña coincide y no se pasó la
 * ventana. La sesión que devuelve no lleva token.
 */
export async function entrarSinRed(
  correo: string,
  password: string,
  ahora: number = Date.now(),
): Promise<ResultadoSinRed> {
  if (!accesoSinRedPosible()) {
    return {
      ok: false,
      motivo:
        'Este equipo no puede comprobar la contraseña sin red: la dirección del servidor no es segura (http://). ' +
        'Use la aplicación instalada o una dirección https://.',
    }
  }
  const g = leer()
  if (!g || g.correo !== correo.trim().toLowerCase()) {
    return {
      ok: false,
      motivo:
        'Sin red solo puede entrar el último supervisor que entró en este equipo con conexión, y esta cuenta no es esa.',
    }
  }
  if (ahora >= g.ultimoContacto + VENTANA_SIN_RED_MS) {
    return {
      ok: false,
      motivo:
        'Pasaron más de 72 horas desde la última vez que este equipo habló con el servidor con su cuenta. ' +
        'Por seguridad, conéctese a la red del Centro para volver a entrar.',
    }
  }
  const huella = await derivar(password, deBase64(g.sal), g.iteraciones)
  if (!iguales(huella, deBase64(g.huella))) {
    return { ok: false, motivo: 'Credenciales inválidas.' }
  }
  return {
    ok: true,
    auth: { token: '', usuarioId: g.usuarioId, nombre: g.nombre, email: g.correo, rol: 'SUPERVISOR' },
  }
}
