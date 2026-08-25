import { useState } from 'react'
import { Chip } from './ui'

/**
 * Una entrada de la bitácora de auditoría: una acción que alguien ejecutó
 * sobre el contrato. No es un mensaje ni una notificación — SICOT no registra
 * envíos ni acuses de recibo, así que aquí no hay destinatario ni estado de
 * entrega.
 */
export interface Registro {
  id: string
  tipo: 'Contrato' | 'Etapa' | 'Documento' | 'Firma' | 'Otro'
  /** Acción real ejecutada, legible (p. ej. "Documento firmado"). */
  accion: string
  /** Quien ejecutó la acción. */
  actor: string
  fecha: string
  asunto: string
}

const TIPOS = ['Todos', 'Contrato', 'Etapa', 'Documento', 'Firma', 'Otro'] as const

export default function Registros({ extra }: { extra: Registro[] }) {
  const [tipo, setTipo] = useState<(typeof TIPOS)[number]>('Todos')
  const [actor, setActor] = useState('')
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')

  const toDate = (f: string) => {
    const [d, m, y] = f.slice(0, 10).split('/')
    return new Date(`${y}-${m}-${d}`)
  }

  const filtrados = extra.filter(r => {
    if (tipo !== 'Todos' && r.tipo !== tipo) return false
    if (actor && !r.actor.toLowerCase().includes(actor.toLowerCase())) return false
    if (desde && toDate(r.fecha) < new Date(desde)) return false
    if (hasta && toDate(r.fecha) > new Date(hasta)) return false
    return true
  })

  const iconFor = (t: Registro['tipo']) =>
    t === 'Firma' ? 'F' : t === 'Documento' ? 'D' : t === 'Etapa' ? 'E' : t === 'Contrato' ? 'C' : '·'

  return (
    <div style={{ flex: 1, overflowY: 'auto', padding: 24 }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 16, marginBottom: 16, flexWrap: 'wrap' }}>
        <div>
          <div className="eyebrow">Trazabilidad del contrato</div>
          <h3 style={{ margin: '0 0 4px', fontSize: 18 }}>Registros</h3>
          <p style={{ margin: 0, fontSize: 13, color: 'var(--text-muted)' }}>
            Bitácora de acciones ejecutadas sobre el contrato · {filtrados.length} de {extra.length} registros
          </p>
        </div>
        <button className="btn-ghost" disabled title="La exportación a PDF se habilita en una fase posterior"
          style={{ padding: '8px 14px', fontSize: 12, opacity: 0.5, cursor: 'not-allowed' }}>
          Descargar Log Completo
        </button>
      </div>

      {/* Filtros */}
      <div className="card" style={{ padding: '12px 14px', marginBottom: 16, display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'flex-end' }}>
        <div style={{ minWidth: 150 }}>
          <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 4 }}>Tipo</div>
          <select value={tipo} onChange={e => setTipo(e.target.value as typeof tipo)}>
            {TIPOS.map(t => <option key={t} value={t}>{t}</option>)}
          </select>
        </div>
        <div style={{ flex: 1, minWidth: 200 }}>
          <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 4 }}>Responsable</div>
          <input type="text" value={actor} onChange={e => setActor(e.target.value)}
            placeholder="Nombre de quien ejecutó la acción" style={{ width: '100%', padding: '9px 10px' }} />
        </div>
        <div>
          <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 4 }}>Desde</div>
          <input type="date" value={desde} onChange={e => setDesde(e.target.value)}
            style={{ padding: '8px 10px', background: 'var(--bg-input)', border: '1px solid var(--border)', borderRadius: 8, color: 'var(--text-primary)', fontFamily: 'var(--font-ui)', fontSize: 13, colorScheme: 'dark' }} />
        </div>
        <div>
          <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 4 }}>Hasta</div>
          <input type="date" value={hasta} onChange={e => setHasta(e.target.value)}
            style={{ padding: '8px 10px', background: 'var(--bg-input)', border: '1px solid var(--border)', borderRadius: 8, color: 'var(--text-primary)', fontFamily: 'var(--font-ui)', fontSize: 13, colorScheme: 'dark' }} />
        </div>
        {(tipo !== 'Todos' || actor || desde || hasta) && (
          <button className="btn-ghost" style={{ padding: '8px 12px', fontSize: 12 }}
            onClick={() => { setTipo('Todos'); setActor(''); setDesde(''); setHasta('') }}>Limpiar</button>
        )}
      </div>

      {/* Lista */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {filtrados.map(r => (
          <div key={r.id} className="card" style={{ padding: '12px 14px', display: 'flex', gap: 12, alignItems: 'flex-start' }}>
            <div style={{ width: 30, height: 30, borderRadius: 8, flexShrink: 0, background: 'var(--accent-soft)', border: '1px solid var(--accent-line)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 14 }}>
              {iconFor(r.tipo)}
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', marginBottom: 3 }}>
                <span style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.08em', color: 'var(--accent)' }}>{r.tipo.toUpperCase()}</span>
                <span style={{ fontSize: 11, color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>{r.fecha}</span>
              </div>
              <div style={{ fontSize: 13, color: 'var(--text-primary)', marginBottom: 3 }}>{r.asunto}</div>
              <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>Por: {r.actor}</div>
            </div>
            <Chip text={r.accion} type="running" />
          </div>
        ))}
        {!filtrados.length && (
          <div className="card" style={{ padding: 24, textAlign: 'center', fontSize: 13, color: 'var(--text-muted)' }}>
            {extra.length === 0
              ? 'Aún no hay acciones registradas para este contrato.'
              : 'No hay registros que coincidan con los filtros aplicados.'}
          </div>
        )}
      </div>
    </div>
  )
}
