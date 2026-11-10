// Qué hacer con lo que el supervisor escribe mientras SICOT le pide que
// describa el paso que va a cerrar.
//
// Antes todo lo que escribía en ese momento se tomaba como la descripción: un
// «¿qué tengo que poner aquí?» o un «cancelar» salían hacia una revisión del
// modelo de hasta 158 s en CPU, y después se ofrecía firmar el acta con esa
// pregunta como notas (auditoría del 02-10-2026). Ahora una pregunta, o una
// orden de las que el Copiloto sabe atender, va al chat normal y el paso sigue
// esperando su descripción; «cancelar» sale del modo revisión sin preguntarle
// nada a nadie.
//
// La regla es conservadora a propósito. Una descripción empieza muchas veces
// por «como», «cuando» o «que» sin tilde («Como supervisor revisé…», «Que el
// contratista entregó…»), así que solo cuenta como pregunta lo que lo es sin
// duda: los signos de interrogación o una palabra interrogativa con su tilde.
// Si se escapa una pregunta, el supervisor no queda atrapado: la revisión es
// consultiva y puede cancelarla o confirmar el paso sin esperarla.

export type IntencionEnLaRevision = 'cancelar' | 'pregunta' | 'descripcion'

/** Lo que, escrito solo, quiere decir «no cierre el paso todavía». */
const CANCELAR = new Set([
  'cancelar',
  'cancela',
  'cancelalo',
  'cancelo',
  'salir',
  'sal',
  'olvidalo',
  'dejalo',
  'no',
  'mejor no',
  'ahora no',
])

const INTERROGATIVAS = new Set([
  'qué',
  'quién',
  'quiénes',
  'cuándo',
  'dónde',
  'cómo',
  'cuál',
  'cuáles',
  'cuánto',
  'cuánta',
  'cuántos',
  'cuántas',
])

// Las órdenes de navegación que el Copiloto atiende sin modelo, solo en las
// formas con las que no empieza una descripción de lo hecho: «descarga» no,
// que en una entrega de mobiliario es «la descarga del camión».
const ORDENES = new Set([
  'llévame',
  'llevame',
  'muéstrame',
  'muestrame',
  'ábreme',
  'abreme',
  'abre',
  'descárgame',
  'descargame',
])

/** Sin tildes, en minúsculas y sin la puntuación de los extremos. */
function normalizar(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/^[\s¿¡.,;:!?]+|[\s¿¡.,;:!?]+$/g, '')
    .replace(/\s+/g, ' ')
}

export function intencionEnLaRevision(texto: string): IntencionEnLaRevision {
  const limpio = texto.trim()
  if (CANCELAR.has(normalizar(limpio))) return 'cancelar'
  if (limpio.startsWith('¿') || limpio.endsWith('?')) return 'pregunta'
  const [primera = '', segunda = ''] = limpio
    .toLowerCase()
    .replace(/^¡/, '')
    .split(/[\s,]+/)
  if (INTERROGATIVAS.has(primera)) return 'pregunta'
  if ((primera === 'por' || primera === 'para') && segunda === 'qué') return 'pregunta'
  if (ORDENES.has(primera)) return 'pregunta'
  return 'descripcion'
}
