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

export function mapRegistros(registros: RegistroResponse[]): Registro[] {
  return registros.map(r => {
    const accion = r.accion.toUpperCase()
    const tipo: Registro['tipo'] =
      accion.includes('FIRMA') || accion.includes('DOCUMENTO') ? 'Firma'
      : accion.includes('CORREO') ? 'Correo Enviado'
      : 'Notificación'
    const estado: Registro['estado'] =
      accion.includes('FIRMA') || accion.includes('DOCUMENTO') ? 'Firmado'
      : accion.includes('CORREO') ? 'Entregado'
      : 'Leído'
    return {
      id: 'r' + r.id,
      tipo,
      destinatario: r.usuarioNombre ?? 'Sistema',
      fecha: formatRegistroFecha(r.fecha),
      asunto: r.descripcion ?? r.accion,
      estado,
    }
  })
}
