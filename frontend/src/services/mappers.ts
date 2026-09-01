// Mapeo entre los DTOs del backend (etapas/subetapas) y los tipos de dominio
// de la UI (Step/SubStep). El estado/completado viene del servidor; el nombre
// del documento por subetapa es parte de la definición del proceso GCCON-P-010
// (base de conocimiento local, igual que FORMAL_DOCS).

import { STEPS_INITIAL, AI_GENERATED_DOCS } from '@/data/contractFlow'
import type { Step, SubStep } from '@/types/domain'
import type { EtapaResponse, RegistroResponse } from './api/types'
import type { Registro } from '@/components/Registros'

const DOCUMENTOS_POR_CODIGO = new Map<string, string>(
  STEPS_INITIAL.flatMap(s => s.subSteps).map(ss => [ss.id, ss.document]),
)

const STATUS_FROM_ESTADO: Record<EtapaResponse['estado'], Step['status']> = {
  COMPLETADA: 'completed',
  EN_CURSO: 'active',
  PENDIENTE: 'pending',
}

export function mapEtapas(etapas: EtapaResponse[]): Step[] {
  return etapas.map(etapa => ({
    id: etapa.numero,
    title: etapa.nombre,
    status: STATUS_FROM_ESTADO[etapa.estado],
    subSteps: etapa.subEtapas.map((ss): SubStep => ({
      id: ss.codigo,
      label: ss.nombre,
      responsible: ss.responsable,
      document: DOCUMENTOS_POR_CODIGO.get(ss.codigo) ?? ss.descripcion,
      completed: ss.estado === 'COMPLETADA',
      aiGenerated: AI_GENERATED_DOCS.has(ss.codigo),
      apiId: ss.id,
    })),
  }))
}

function formatRegistroFecha(iso: string): string {
  return new Date(iso).toLocaleString('es-CO', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  }).replace(',', '')
}

// Categorías de la bitácora. Cubren las doce acciones que el backend registra
// hoy. Cualquier acción nueva cae en 'Otro' en vez de ser clasificada a la
// fuerza en una categoría que no le corresponde.
const CATEGORIA_POR_ACCION: Record<string, Registro['tipo']> = {
  CONTRATO_CREADO: 'Contrato',
  CONTRATO_ACTUALIZADO: 'Contrato',
  SUPERVISOR_ASIGNADO: 'Contrato',
  ESTADO_CAMBIADO: 'Contrato',
  ETAPA_ACTUALIZADA: 'Etapa',
  // Añadidas con la validación de transiciones de estado: distinguen un avance
  // de un retroceso, que antes se registraban con el mismo texto.
  SUBETAPA_AVANZADA: 'Etapa',
  SUBETAPA_REVERTIDA: 'Etapa',
  ETAPA_RETROCEDIDA: 'Etapa',
  DOCUMENTO_FIRMADO: 'Documento',
  FIRMA_ASIGNADA: 'Firma',
  FIRMA_REVOCADA: 'Firma',
  FIRMA_RESTAURADA: 'Firma',
}

/** `CONTRATO_CREADO` -> `Contrato creado`. */
function humanizarAccion(accion: string): string {
  const texto = accion.replace(/_/g, ' ').toLowerCase()
  return texto.charAt(0).toUpperCase() + texto.slice(1)
}

/**
 * Convierte la bitácora de auditoría del backend en filas para la UI.
 *
 * Es un registro de ACCIONES EJECUTADAS, no de mensajes enviados. Antes esta
 * función derivaba dos campos que el sistema no tiene: un `estado`
 * ('Entregado' / 'Leído' / 'Firmado') que fingía acuses de recibo inexistentes
 * —y que llegaba a etiquetar `FIRMA_REVOCADA` como "Firmado"—, y un
 * `destinatario` que en realidad contenía a quien *ejecutó* la acción, no a
 * quien la recibió. Ambos se eliminaron: aquí solo se muestra lo que el backend
 * realmente sabe.
 */
export function mapRegistros(registros: RegistroResponse[]): Registro[] {
  return registros.map(r => ({
    id: 'r' + r.id,
    tipo: CATEGORIA_POR_ACCION[r.accion.toUpperCase()] ?? 'Otro',
    accion: humanizarAccion(r.accion),
    actor: r.usuarioNombre ?? 'Sistema',
    fecha: formatRegistroFecha(r.fecha),
    asunto: r.descripcion ?? humanizarAccion(r.accion),
  }))
}
