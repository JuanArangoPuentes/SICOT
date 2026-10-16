import { describe, expect, it } from 'vitest'
import { marcarLoQueNoEstaEn } from './diferenciasDeRedaccion'

const marcadas = (texto: string, otro: string) =>
  marcarLoQueNoEstaEn(texto, otro)
    .filter((t) => t.marcada)
    .map((t) => t.texto)

// Casos de la revisión ciega del 01-10-2026: redacciones del modelo que
// pasaron todas las comprobaciones y cambiaban algo grave.
describe('marcarLoQueNoEstaEn', () => {
  it('en la redacción marca lo que el Copiloto agregó', () => {
    expect(
      marcadas(
        'Se acordó con el contratista la entrega de los materiales faltantes la semana anterior.',
        'se acordó con el contratista entregar los faltantes la otra semana',
      ),
    ).toContain('anterior')
    expect(
      marcadas(
        'Se recibieron seis cajas de tóner y treinta resmas de papel en buen estado.',
        'recibí 6 cajas de tóner y 30 resmas de papel, todo completo',
      ),
    ).toEqual(['buen', 'estado'])
  })

  it('en las notas marca lo que la redacción dejó fuera', () => {
    expect(
      marcadas(
        'recibí 6 cajas de tóner y 30 resmas de papel, todo completo',
        'Se recibieron seis cajas de tóner y treinta resmas de papel en buen estado.',
      ),
    ).toEqual(['completo'])
    expect(
      marcadas(
        'el contratista no ha pagado la seguridad social de agosto, se le requirió',
        'Se ha requerido al contratista el pago correspondiente a la seguridad social de agosto.',
      ),
    ).toContain('pagado')
  })

  it('en una redacción fiel marca poco: conjugaciones, cifras en letras y giros formales no cuentan', () => {
    expect(
      marcadas(
        'Se recibieron 12 mesas y 5 sillas; se solicitó el cambio de 2 sillas que llegaron con rayones.',
        'llegaron 12 mesas y 5 sillas, 2 sillas con rayones, se pidió el cambio de esas 2',
      ),
    ).toEqual(['recibieron', 'solicitó'])
    expect(
      marcadas(
        'Durante la visita se realizó la revisión de los dos tornos, que no funcionan.',
        'en la visita revisé los 2 tornos, no funcionan',
      ),
    ).toEqual([])
  })

  it('los tramos, unidos, son el texto exacto', () => {
    const texto = '¿Llegó el 50 %? Sí: «buen estado», 3 m², línea\nnueva.'
    expect(
      marcarLoQueNoEstaEn(texto, 'otra cosa')
        .map((t) => t.texto)
        .join(''),
    ).toBe(texto)
  })
})
