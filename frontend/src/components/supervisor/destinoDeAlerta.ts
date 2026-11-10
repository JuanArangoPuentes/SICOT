// A dónde lleva el botón de una alerta del supervisor.
//
// Hasta el 02-10-2026 el botón «Ir al paso» venía del prototipo: VENCIMIENTO,
// SECOP y CRONOGRAMA llevaban al Paso 6, FIRMA, DOCUMENTO e IA al 3 y todo lo
// demás al 4, sin relación con la alerta. Un contrato en el Paso 2 con el
// cronograma atrasado mandaba al supervisor al Cierre.

import type { TipoAlerta } from '@/services/api/types'

export type DestinoDeAlerta =
  | { vista: 'contrato'; paso: number; etiqueta: string }
  | { vista: 'documentos'; etiqueta: string }

/**
 * El sitio relacionado con una alerta, o `null` si no hay ninguno al que
 * llevar; en ese caso la alerta se muestra sin botón.
 *
 * Solo se mapean los tipos que el backend emite hoy:
 * - CRONOGRAMA (AvisoDeCronogramaAtrasado), VENCIMIENTO (AvisoDeVencimientoProximo
 *   y AvisoDeContratoVencido), SOLICITUD (NotificacionDeSupervisorAsignado) y
 *   RECORDATORIO (RedaccionDeResumen) hablan del avance del contrato: lo que el
 *   supervisor puede mover es el paso en curso.
 * - DOCUMENTO (AvisoDeIntegridadComprometida) avisa de un documento firmado
 *   cuyo contenido ya no coincide con su huella: el sello de integridad está en
 *   la vista Documentos.
 *
 * FACTURA, FIRMA, IA, SECOP y RECHAZADO existen en el catálogo pero nada los
 * crea; adivinarles un destino sería repetir el error del prototipo.
 */
export function destinoDeAlerta(tipo: TipoAlerta, pasoActivo: number | null): DestinoDeAlerta | null {
  switch (tipo) {
    case 'DOCUMENTO':
      return { vista: 'documentos', etiqueta: 'Ver los documentos' }
    case 'CRONOGRAMA':
    case 'VENCIMIENTO':
    case 'SOLICITUD':
    case 'RECORDATORIO':
      return pasoActivo === null ? null : { vista: 'contrato', paso: pasoActivo, etiqueta: `Ir al paso ${pasoActivo}` }
    default:
      return null
  }
}
