// Guardar un archivo que la aplicación ya tiene en memoria —un PDF firmado que
// llegó del backend, un CSV construido en el momento— en el dispositivo.
//
// <h2>Por qué no basta con el patrón de navegador</h2>
// Las tres descargas de SICOT (documentos del contrato, formatos del catálogo y
// la exportación de registros) usaban una URL `blob:` y un enlace con el
// atributo `download`. En un navegador eso abre el gestor de descargas. Un
// WebView de Android no tiene gestor de descargas: el clic no hace nada, sin
// error y sin aviso. Se comprobó dentro del APK el 18 de septiembre de 2026 —el
// acta firmada llegaba del backend con un 200 y se perdía— y el síntoma era el
// peor posible: el supervisor pulsaba «Descargar» sobre el acta que acababa de
// firmar y no pasaba nada.
//
// <h2>Qué se hace en su lugar, y solo en Android</h2>
// Dentro del APK se abre el «Guardar como» del sistema, el usuario elige dónde
// —normalmente Descargas— y los bytes se escriben ahí. Es el mecanismo oficial
// de Tauri para Android (plugins `dialog` y `fs`).
//
// En el navegador y en el instalador de escritorio **no cambia nada**: allí el
// patrón de navegador funciona, y cambiar lo que funciona para unificar sería
// añadir riesgo sin beneficio. Por eso los plugins se importan de forma
// diferida, solo en la rama de Android: la aplicación web no carga ese código.

import { enAndroid, enAplicacionEmpaquetada } from './entorno'

/** Qué pasó al intentar guardar. */
export type ResultadoGuardado =
  /** El archivo quedó escrito donde el usuario eligió (Android). */
  | 'guardado'
  /** El usuario cerró el «Guardar como» sin elegir sitio (Android). */
  | 'cancelado'
  /** Se entregó al gestor de descargas del navegador, que sigue por su cuenta. */
  | 'navegador'

/** Filtros del «Guardar como» por extensión, para que el sistema proponga bien el tipo. */
const FILTROS: Record<string, { name: string; extensions: string[] }> = {
  pdf: { name: 'Documento PDF', extensions: ['pdf'] },
  csv: { name: 'Hoja de cálculo CSV', extensions: ['csv'] },
  docx: { name: 'Documento de Word', extensions: ['docx'] },
  xlsx: { name: 'Libro de Excel', extensions: ['xlsx'] },
}

export async function guardarArchivo(contenido: Blob, nombreArchivo: string): Promise<ResultadoGuardado> {
  if (enAplicacionEmpaquetada() && enAndroid()) {
    return guardarEnAndroid(contenido, nombreArchivo)
  }
  descargarComoNavegador(contenido, nombreArchivo)
  return 'navegador'
}

async function guardarEnAndroid(contenido: Blob, nombreArchivo: string): Promise<ResultadoGuardado> {
  const [{ save }, { writeFile }] = await Promise.all([
    import('@tauri-apps/plugin-dialog'),
    import('@tauri-apps/plugin-fs'),
  ])
  const extension = nombreArchivo.split('.').pop()?.toLowerCase() ?? ''
  const filtro = FILTROS[extension]
  const destino = await save({ defaultPath: nombreArchivo, filters: filtro ? [filtro] : undefined })
  // `null` es que el usuario cerró el diálogo. No es un error y no se trata
  // como tal: quien decide no guardar no necesita que se le diga que falló.
  if (destino === null) return 'cancelado'
  await writeFile(destino, new Uint8Array(await contenido.arrayBuffer()))
  return 'guardado'
}

function descargarComoNavegador(contenido: Blob, nombreArchivo: string) {
  const url = URL.createObjectURL(contenido)
  const enlace = document.createElement('a')
  enlace.href = url
  enlace.download = nombreArchivo
  document.body.appendChild(enlace)
  enlace.click()
  enlace.remove()
  URL.revokeObjectURL(url)
}
