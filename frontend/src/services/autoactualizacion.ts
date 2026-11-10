// Que la aplicación instalada se ponga al día sola (ADR-017).
//
// <h2>Qué problema resuelve</h2>
// SICOT se instala en el equipo del supervisor y en su teléfono, y el servidor
// del Centro se actualiza aparte. Cuando el servidor avanza y la instalación
// no, el supervisor se queda sin poder trabajar **y sin saber por qué**: la
// 0.6.0 dejó de cerrar el sub-paso 2.4 sin una fecha de entrega registrada, y
// esa pantalla solo existe desde la 0.6.0; la 0.9.0 trae «Cambiar contraseña»,
// que es lo primero que necesita un supervisor recién invitado. Hasta ahora eso
// se resolvía llevándole el instalador a cada persona.
//
// <h2>Qué hace en cada sitio, y por qué no es lo mismo</h2>
// - **Escritorio**: se actualiza sola. El actualizador de Tauri descarga el
//   instalador de la última versión publicada en GitHub, **comprueba su firma**
//   contra la clave pública de `tauri.conf.json` y lo instala. Sin firma válida
//   no instala nada, así que nadie puede colar un paquete propio ni siquiera
//   interceptando la descarga.
// - **Android**: avisa, pero no instala. Android solo deja instalar un APK con
//   la autorización explícita de quien tiene el teléfono, y el actualizador de
//   Tauri no existe para esa plataforma. Lo honesto es decir que hay versión
//   nueva y dar la dirección para bajarla; prometer una actualización que el
//   sistema va a bloquear sería peor que no decir nada.
// - **Navegador**: no hace nada. Ahí no hay nada instalado: al recargar, nginx
//   ya sirve la versión que tenga el servidor.
//
// <h2>Por qué las importaciones son diferidas</h2>
// El plugin del actualizador solo existe en la aplicación empaquetada de
// escritorio. Importarlo arriba metería su código en el bundle que descarga
// cualquier navegador y fallaría al cargar donde el plugin no está registrado.

import { enAndroid, enAplicacionEmpaquetada } from './entorno'

/** El `latest.json` que publica el flujo «Publicar escritorio» con cada versión. */
const DESCRIPTOR_DE_LA_ULTIMA = 'https://github.com/JuanArangoPuentes/SICOT/releases/latest/download/latest.json'

/** La página desde donde se baja el APK cuando hay versión nueva. */
export const PAGINA_DE_DESCARGAS = 'https://github.com/JuanArangoPuentes/SICOT/releases/latest'

/** En qué punto está la actualización, para lo que se muestra en pantalla. */
export type EstadoDeActualizacion =
  /** No hay nada que hacer: está al día, o aquí no se actualiza nada. */
  | { fase: 'al-dia' }
  /** Bajando el instalador. `porcentaje` es null mientras no se sabe el tamaño. */
  | { fase: 'descargando'; version: string; porcentaje: number | null }
  /** Instalada. Queda reiniciar para quedar corriendo la versión nueva. */
  | { fase: 'instalada'; version: string }
  /** Hay versión nueva pero aquí la instala el usuario (Android). */
  | { fase: 'hay-que-bajarla'; version: string }
  /** Se intentó y no se pudo. Se dice, no se calla. */
  | { fase: 'fallo'; version: string | null; motivo: string }

/**
 * ¿`publicada` es posterior a `instalada`?
 *
 * Compara número a número («0.10.0» es posterior a «0.9.0», que es lo que una
 * comparación de textos se equivoca). Si alguna de las dos no se entiende
 * —vacía, con letras— responde que no: un aviso de actualización equivocado
 * manda a un supervisor a descargar algo que no necesita.
 */
export function esMasNueva(publicada: string, instalada: string): boolean {
  const partes = (version: string): number[] | null => {
    const limpia = version.trim().replace(/^v/i, '')
    if (!/^\d+(\.\d+)*$/.test(limpia)) return null
    return limpia.split('.').map(Number)
  }
  const a = partes(publicada)
  const b = partes(instalada)
  if (a === null || b === null) return false
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    const uno = a[i] ?? 0
    const otro = b[i] ?? 0
    if (uno !== otro) return uno > otro
  }
  return false
}

/**
 * Busca una versión nueva y, en escritorio, la instala.
 *
 * Nunca lanza: va contando lo que pasa por `avisar`. Un fallo de red no se
 * cuenta como fallo —el supervisor puede estar sin conexión, que es un caso
 * normal (ADR-015)— y entonces queda en `al-dia`, sin molestar a nadie. Lo que
 * sí se cuenta es que la instalación misma falle.
 */
export async function buscarActualizacion(avisar: (estado: EstadoDeActualizacion) => void): Promise<void> {
  if (!enAplicacionEmpaquetada()) return avisar({ fase: 'al-dia' })

  if (enAndroid()) {
    try {
      const respuesta = await fetch(DESCRIPTOR_DE_LA_ULTIMA, { cache: 'no-store' })
      if (!respuesta.ok) return avisar({ fase: 'al-dia' })
      const descriptor = (await respuesta.json()) as { version?: unknown }
      const publicada = typeof descriptor.version === 'string' ? descriptor.version : ''
      if (esMasNueva(publicada, __VERSION_DE_SICOT__)) {
        return avisar({ fase: 'hay-que-bajarla', version: publicada })
      }
    } catch {
      // Sin conexión o GitHub inalcanzable: no es un fallo del que haya que
      // avisar a un supervisor que quizá está en una bodega sin señal.
    }
    return avisar({ fase: 'al-dia' })
  }

  let version: string | null = null
  try {
    const { check } = await import('@tauri-apps/plugin-updater')
    const actualizacion = await check()
    if (actualizacion === null) return avisar({ fase: 'al-dia' })
    version = actualizacion.version

    let total = 0
    let bajado = 0
    avisar({ fase: 'descargando', version, porcentaje: null })
    await actualizacion.downloadAndInstall((evento) => {
      if (evento.event === 'Started') {
        total = evento.data.contentLength ?? 0
      } else if (evento.event === 'Progress') {
        bajado += evento.data.chunkLength
        avisar({
          fase: 'descargando',
          version: version as string,
          porcentaje: total > 0 ? Math.min(100, Math.round((bajado * 100) / total)) : null,
        })
      }
    })
    // En Windows el instalador cierra SICOT para reemplazar los archivos, así
    // que esto puede no llegar a verse nunca; en los sistemas donde sí llega,
    // el aviso ofrece reiniciar.
    avisar({ fase: 'instalada', version })
  } catch (error) {
    // Aquí sí se dice. Si `check` falló por red, el mensaje lo refleja; si
    // falló la instalación, el supervisor tiene que saber que su SICOT sigue
    // siendo el de antes y que puede bajar el instalador a mano.
    avisar({ fase: 'fallo', version, motivo: error instanceof Error ? error.message : String(error) })
  }
}

/** Reinicia la aplicación para quedar corriendo la versión ya instalada. */
export async function reiniciarParaTerminar(): Promise<void> {
  const { relaunch } = await import('@tauri-apps/plugin-process')
  await relaunch()
}
