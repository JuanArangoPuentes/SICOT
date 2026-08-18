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
