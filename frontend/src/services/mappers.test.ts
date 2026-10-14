import { describe, expect, it } from 'vitest'

import { mapEtapas, mapRegistros } from './mappers'
import type { EtapaResponse, RegistroResponse } from './api/types'

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

/**
 * La hora de la bitácora. Usaba la zona del equipo y 12 h: en un portátil o un
 * emulador en UTC decía «08:15 p. m.» para una firma que la evidencia
 * fotográfica y el seguimiento del Administrador fechan a las 15:15.
 */
describe('mapRegistros · hora', () => {
  it('sale en la hora del Centro y en 24 h, como el resto del sistema', () => {
    const [registro] = mapRegistros([
      {
        id: 1,
        contratoId: 7,
        usuarioId: 3,
        usuarioNombre: 'Ana Gómez',
        accion: 'DOCUMENTO_FIRMADO',
        descripcion: null,
        fecha: '2026-10-02T20:15:00Z',
        origen: 'USUARIO',
      },
    ])

    expect(registro.fecha).toBe('02/10/2026 15:15')
  })
})

/**
 * El documento de cada sub-paso. Lo único que la interfaz pone de su parte: el
 * resto de la subetapa (nombre, responsable, estado) llega del backend. Antes
 * salía de una copia completa de las 27 subetapas de la que solo se leía este
 * campo.
 */
describe('mapEtapas · documento del sub-paso', () => {
  const etapa = (codigo: string): EtapaResponse => ({
    id: 2,
    numero: 2,
    nombre: 'INICIO — Acta de Inicio',
    estado: 'EN_CURSO',
    porcentaje: 0,
    subEtapas: [
      {
        id: 27,
        codigo,
        nombre: 'Del backend',
        descripcion: 'Descripción del backend',
        estado: 'PENDIENTE',
        responsable: 'Supervisor',
      },
    ],
  })

  it('pone el documento del GCCON-P-010 y deja el resto como lo da el backend', () => {
    const [sub] = mapEtapas([etapa('2.7')])[0].subSteps

    expect(sub.document).toBe('GCCON-F-018')
    expect(sub.label).toBe('Del backend')
    expect(sub.responsible).toBe('Supervisor')
  })

  it('una subetapa que no conoce muestra la descripción del backend', () => {
    expect(mapEtapas([etapa('9.9')])[0].subSteps[0].document).toBe('Descripción del backend')
  })
})
