import { describe, expect, it } from 'vitest'

import { mapRegistros } from './mappers'
import type { RegistroResponse } from './api/types'

/**
 * La atribución en la auditoría.
 *
 * La revisión del 8 de septiembre encontró que el panel decía "Sistema" siempre
 * que faltaba el nombre del usuario. Como `registros.usuario_id` es
 * `ON DELETE SET NULL`, borrar una cuenta ponía a nulo el usuario de TODAS sus
 * entradas: las acciones de esa persona pasaban a aparecer atribuidas al
 * sistema, en el registro que existe precisamente para saber quién hizo qué.
 *
 * En un expediente de contratación pública eso no es un defecto de
 * presentación: es una afirmación falsa sobre quién tomó una decisión.
 */
describe('mapRegistros · atribución del actor', () => {
  const base: RegistroResponse = {
    id: 1,
    contratoId: 7,
    usuarioId: 3,
    usuarioNombre: 'Ana Gómez',
    accion: 'SUBETAPA_AVANZADA',
    descripcion: 'Subetapa 2.3 avanzó a COMPLETADA.',
    fecha: '2026-03-15T10:00:00Z',
    origen: 'USUARIO',
  }

  it('atribuye a la persona cuando la acción fue de una persona', () => {
    expect(mapRegistros([base])[0].actor).toBe('Ana Gómez')
  })

  it('atribuye al sistema solo cuando el origen lo dice', () => {
    const delSistema = { ...base, usuarioId: null, usuarioNombre: null, origen: 'SISTEMA' as const }

    expect(mapRegistros([delSistema])[0].actor).toBe('Sistema')
  })

  it('no atribuye al sistema la acción de una cuenta borrada', () => {
    // usuario_id es ON DELETE SET NULL: la persona existió y actuó, pero su
    // cuenta ya no está. Decir "Sistema" aquí sería mentir sobre el expediente.
    const cuentaBorrada = { ...base, usuarioId: null, usuarioNombre: null, origen: 'USUARIO' as const }

    expect(mapRegistros([cuentaBorrada])[0].actor).toBe('Usuario eliminado')
  })
})
