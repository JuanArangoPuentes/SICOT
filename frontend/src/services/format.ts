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

// Toda fecha con hora se lee en la hora del Centro y en 24 h, la misma que usa
// el backend por defecto (sicot.zona-horaria). Se fija aquí en vez de usar la
// del equipo que mira la pantalla para que la evidencia fotográfica, la
// bitácora del contrato y el seguimiento del Administrador digan la misma hora
// —antes la bitácora usaba la zona del equipo y 12 h, y en un portátil o
// emulador en UTC no cuadraba con las otras dos—, y para que las pruebas no
// dependan de la máquina.
const FECHA_Y_HORA_DEL_CENTRO = new Intl.DateTimeFormat('es-CO', {
  timeZone: 'America/Bogota',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

function partesDelCentro(iso: string): Record<string, string> {
  return Object.fromEntries(FECHA_Y_HORA_DEL_CENTRO.formatToParts(new Date(iso)).map((p) => [p.type, p.value]))
}

/** `dd/mm/aaaa HH:MM` en la hora del Centro. */
export function formatFechaYHoraDelCentro(iso: string): string {
  const p = partesDelCentro(iso)
  return `${p.day}/${p.month}/${p.year} ${p.hour}:${p.minute}`
}

function formatFechaYHora(iso: string): string {
  const p = partesDelCentro(iso)
  return `${p.day}/${p.month}/${p.year} a las ${p.hour}:${p.minute}`
}

/**
 * La fecha de un instante («…T01:15:00Z») en la hora del Centro. Cortar los
 * diez primeros caracteres daba la fecha en UTC: un documento generado el
 * 30/09 a las 20:15 de Colombia salía con fecha 01/10, un día que nadie
 * registró y que a fin de mes cae en otro periodo (auditoría del 02-10-2026).
 * Para fechas sin hora (la de inicio del contrato) sigue siendo formatFecha.
 */
export function fechaDelCentro(iso: string | null | undefined): string {
  if (!iso) return '—'
  const instante = new Date(iso)
  if (Number.isNaN(instante.getTime())) return '—'
  const partes = Object.fromEntries(FECHA_Y_HORA_DEL_CENTRO.formatToParts(instante).map((p) => [p.type, p.value]))
  return `${partes.day}/${partes.month}/${partes.year}`
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
