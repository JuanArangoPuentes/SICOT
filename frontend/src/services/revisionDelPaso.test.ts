import { describe, expect, it } from 'vitest'
import { intencionEnLaRevision } from './revisionDelPaso'

/**
 * Mientras SICOT espera la descripción del paso, todo lo escrito se tomaba
 * como esa descripción: una pregunta o un «cancelar» acababan en una revisión
 * del modelo de minutos y, después, como notas del acta.
 */
describe('intencionEnLaRevision', () => {
  it.each(['cancelar', 'Cancelar.', 'salir', 'olvídalo', 'No', 'mejor no'])('«%s» cancela la revisión', (texto) => {
    expect(intencionEnLaRevision(texto)).toBe('cancelar')
  })

  it.each([
    'que tengo que poner aqui?',
    'que es el f-031?',
    '¿Quién firma el acta de inicio',
    'Qué es el GCCON-F-031',
    'cómo describo esto',
    'por qué me pide esto',
    'llévame al paso 3',
    'Muéstrame las alertas',
  ])('«%s» va al chat y no a la revisión', (texto) => {
    expect(intencionEnLaRevision(texto)).toBe('pregunta')
  })

  /** Las descripciones empiezan a menudo por «como», «cuando» o «que» sin tilde: siguen siendo descripciones. */
  it.each([
    'Revisé los estudios previos y están completos.',
    'Como supervisor verifiqué la póliza y el CDP.',
    'Cuando llegó la entrega conté las sillas.',
    'Que el contratista entregó todo a tiempo.',
    'Descarga del camión verificada, sin novedades.',
    'No hubo novedades en la entrega.',
  ])('«%s» es la descripción del paso', (texto) => {
    expect(intencionEnLaRevision(texto)).toBe('descripcion')
  })
})
