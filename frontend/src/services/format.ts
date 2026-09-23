// Formateo de valores del backend para la UI (es-CO).

const fmt = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 })

export function formatCOP(valor: number | null | undefined): string {
  return valor == null ? '—' : fmt.format(valor)
}

export function formatFecha(iso: string | null | undefined): string {
  if (!iso) return '—'
  const [y, m, d] = iso.slice(0, 10).split('-')
  if (!y || !m || !d) return '—'
  return `${d}/${m}/${y}`
}

// Las fotos de evidencia se leen en la hora del Centro, la misma que usa el
// backend por defecto (sicot.zona-horaria). Se fija aquí en vez de usar la del
// equipo que mira la pantalla para que el panel y el registro del contrato
// digan la misma hora, y para que las pruebas no dependan de la máquina.
const FECHA_Y_HORA_DEL_CENTRO = new Intl.DateTimeFormat('es-CO', {
  timeZone: 'America/Bogota',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

function formatFechaYHora(iso: string): string {
  const partes = Object.fromEntries(FECHA_Y_HORA_DEL_CENTRO.formatToParts(new Date(iso)).map((p) => [p.type, p.value]))
  return `${partes.day}/${partes.month}/${partes.year} a las ${partes.hour}:${partes.minute}`
}

interface ConCaptura {
  tipo: string
  capturaFecha: string | null
  capturaLatitud: number | null
  capturaLongitud: number | null
}

/**
 * Cuándo y dónde se tomó una foto de evidencia, leído de su EXIF al cargarla
 * (MDL-205). null si el documento no es una foto. Si la foto no trae un dato,
 * lo dice en vez de omitirlo: para quien revisa la evidencia, «sin ubicación»
 * también es información.
 */
export function describirCaptura(doc: ConCaptura): string | null {
  if (doc.tipo !== 'IMAGEN') return null
  const cuando = doc.capturaFecha ? `el ${formatFechaYHora(doc.capturaFecha)}` : null
  const donde =
    doc.capturaLatitud != null && doc.capturaLongitud != null
      ? `en ${doc.capturaLatitud.toFixed(5)}, ${doc.capturaLongitud.toFixed(5)}`
      : null
  if (cuando && donde) return `Tomada ${cuando} ${donde}`
  if (cuando) return `Tomada ${cuando}; no trae ubicación`
  if (donde) return `Tomada ${donde}; no trae fecha de captura`
  return 'La foto no trae fecha ni ubicación'
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  const kb = bytes / 1024
  if (kb < 1024) return `${kb.toFixed(kb < 10 ? 1 : 0)} KB`
  return `${(kb / 1024).toFixed(1)} MB`
}
