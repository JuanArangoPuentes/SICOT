import { describe, expect, it } from 'vitest'
import { fechaDelCentro } from './format'

describe('fechaDelCentro', () => {
  // Un documento generado el 30/09 a las 20:15 en Colombia llega como 01:15Z
  // del 01/10. Cortar el texto daba el 01/10; la fecha del Centro es el 30/09.
  it('da la fecha en la hora del Centro, no en UTC', () => {
    expect(fechaDelCentro('2026-10-01T01:15:00Z')).toBe('30/09/2026')
    expect(fechaDelCentro('2026-10-01T05:00:00Z')).toBe('01/10/2026')
  })

  it('sin fecha, o con una que no se puede leer, no inventa ninguna', () => {
    expect(fechaDelCentro(null)).toBe('—')
    expect(fechaDelCentro('no es una fecha')).toBe('—')
  })
})
