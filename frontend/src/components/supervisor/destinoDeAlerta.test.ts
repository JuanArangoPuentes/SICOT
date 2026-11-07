import { describe, expect, it } from 'vitest'
import { destinoDeAlerta } from './destinoDeAlerta'
import type { TipoAlerta } from '@/services/api/types'

describe('destinoDeAlerta', () => {
  // Las cuatro alertas que el backend emite sobre el avance del contrato. Un
  // contrato en el Paso 2 iba al 6 (cronograma, vencimiento) o al 4 (solicitud,
  // recordatorio).
  it.each<TipoAlerta>(['CRONOGRAMA', 'VENCIMIENTO', 'SOLICITUD', 'RECORDATORIO'])(
    '%s lleva al paso en curso',
    (tipo) => {
      expect(destinoDeAlerta(tipo, 2)).toEqual({ vista: 'contrato', paso: 2, etiqueta: 'Ir al paso 2' })
    },
  )

  it.each<TipoAlerta>(['CRONOGRAMA', 'VENCIMIENTO', 'SOLICITUD', 'RECORDATORIO'])(
    '%s sin paso en curso no ofrece botón',
    (tipo) => {
      expect(destinoDeAlerta(tipo, null)).toBeNull()
    },
  )

  // La integridad comprometida de un documento firmado iba al Paso 3 aunque el
  // documento fuera el Acta de Inicio (2.7).
  it('DOCUMENTO lleva a la vista Documentos, donde está el sello de integridad', () => {
    expect(destinoDeAlerta('DOCUMENTO', 2)).toEqual({ vista: 'documentos', etiqueta: 'Ver los documentos' })
  })

  it.each<TipoAlerta>(['FACTURA', 'FIRMA', 'IA', 'SECOP', 'RECHAZADO'])(
    '%s, que el backend no emite, no adivina un destino',
    (tipo) => {
      expect(destinoDeAlerta(tipo, 2)).toBeNull()
    },
  )
})
