// Ficha completa del contrato asignado.
//
// Muestra todos los campos que el backend guarda del contrato. Los campos que
// Gestión todavía no diligenció se marcan explícitamente como "No registrado":
// dejarlos en blanco haría parecer que el dato no existe cuando lo que pasa es
// que falta cargarlo.

import { Chip, type ChipType } from '@/components/ui'
import type { ContratoResponse, EstadoContrato } from '@/services/api/types'
import { formatCOP, formatFecha } from '@/services/format'

const ESTADO_CHIP: Record<EstadoContrato, { label: string; type: ChipType }> = {
  ACTIVO: { label: 'Activo', type: 'vigente' },
  BORRADOR: { label: 'Borrador', type: 'pending' },
  SUSPENDIDO: { label: 'Suspendido', type: 'sugerido' },
  FINALIZADO: { label: 'Finalizado', type: 'finished' },
  CANCELADO: { label: 'Cancelado', type: 'conflicto' },
}

function Dato({ label, value, mono, ancho }: {
  label: string
  value: string | null | undefined
  mono?: boolean
  ancho?: boolean
}) {
  const vacio = value === null || value === undefined || value.trim() === ''
  return (
    <div style={{ gridColumn: ancho ? '1 / -1' : undefined, minWidth: 0 }}>
      <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.09em', textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 3 }}>
        {label}
      </div>
      <div style={{
        fontSize: 13,
        lineHeight: 1.5,
        color: vacio ? 'var(--text-muted)' : 'var(--text-primary)',
        fontStyle: vacio ? 'italic' : 'normal',
        fontFamily: !vacio && mono ? 'var(--font-mono)' : 'var(--font-ui)',
        wordBreak: 'break-word',
      }}>
        {vacio ? 'No registrado' : value}
      </div>
    </div>
  )
}

export default function ContratoInfo({ contrato }: { contrato: ContratoResponse }) {
  const estado = ESTADO_CHIP[contrato.estado] ?? { label: contrato.estado, type: 'pending' as ChipType }

  // Días restantes de vigencia — dato calculado con la fecha de fin real.
  const fin = contrato.fechaFin ? new Date(contrato.fechaFin) : null
  const diasRestantes = fin ? Math.ceil((fin.getTime() - Date.now()) / 86400000) : null
  const vigenciaTexto =
    diasRestantes === null ? null
      : diasRestantes < 0 ? `Vencido hace ${Math.abs(diasRestantes)} día(s)`
        : diasRestantes === 0 ? 'Vence hoy'
          : `Faltan ${diasRestantes} día(s)`

  return (
    <div className="card" style={{ overflow: 'hidden' }}>
      {/* Encabezado */}
      <div style={{
        display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between',
        gap: 16, flexWrap: 'wrap',
        padding: '15px 18px', background: 'var(--bg-elevated)', borderBottom: '1px solid var(--border)',
      }}>
        <div style={{ minWidth: 0 }}>
          <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.1em', color: 'var(--text-muted)', marginBottom: 4 }}>
            CONTRATO ASIGNADO
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
            <span style={{ fontFamily: 'var(--font-mono)', fontSize: 16, fontWeight: 600, color: 'var(--accent-tech)', letterSpacing: '0.02em' }}>
              {contrato.numeroContrato}
            </span>
            <Chip text={estado.label} type={estado.type} />
            {contrato.tipoContrato && <Chip text={contrato.tipoContrato} type="document" />}
          </div>
          <div style={{ fontSize: 13.5, color: 'var(--text-primary)', marginTop: 8, lineHeight: 1.55, maxWidth: 760 }}>
            {contrato.objeto}
          </div>
        </div>

        <div style={{ textAlign: 'right', flexShrink: 0 }}>
          <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.09em', color: 'var(--text-muted)' }}>VALOR DEL CONTRATO</div>
          <div style={{ fontFamily: 'var(--font-display)', fontSize: 22, fontWeight: 700, color: 'var(--text-primary)', lineHeight: 1.25 }}>
            {formatCOP(contrato.valor)}
          </div>
          {vigenciaTexto && (
            <div style={{ fontSize: 11.5, color: diasRestantes !== null && diasRestantes < 0 ? 'var(--alert-critica)' : 'var(--text-muted)', marginTop: 2 }}>
              {vigenciaTexto}
            </div>
          )}
        </div>
      </div>

      {/* Datos */}
      <div style={{
        display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(215px, 1fr))',
        gap: '15px 22px', padding: '16px 18px 18px',
      }}>
        <Dato label="Contratista" value={contrato.contratista} />
        <Dato label="NIT / Identificación" value={contrato.contratistaNit} mono />
        <Dato label="Representante legal" value={contrato.representanteLegal} />
        <Dato label="Fecha de inicio" value={contrato.fechaInicio ? formatFecha(contrato.fechaInicio) : null} mono />
        <Dato label="Fecha de terminación" value={contrato.fechaFin ? formatFecha(contrato.fechaFin) : null} mono />
        <Dato label="Lugar de ejecución" value={contrato.lugarEjecucion} />
        <Dato label="Registro presupuestal" value={contrato.numeroRegistroPresupuestal} mono />
        <Dato label="Fecha del registro presupuestal" value={contrato.fechaRegistroPresupuestal ? formatFecha(contrato.fechaRegistroPresupuestal) : null} mono />
        <Dato label="Centro de costo" value={contrato.centroCosto} />
        <Dato label="Supervisor designado" value={contrato.supervisorNombre} />
        <Dato label="Correo del supervisor" value={contrato.supervisorEmail} />
        <Dato label="Registrado en SICOT" value={contrato.fechaCreacion ? formatFecha(contrato.fechaCreacion.slice(0, 10)) : null} mono />
      </div>
    </div>
  )
}
