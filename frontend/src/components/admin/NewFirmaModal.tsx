// Modal de generación de una firma electrónica para una cuenta.
//
// Extraído de AdminPanel.tsx, que reunía las cuatro vistas, sus cuatro modales y
// sus piezas compartidas en un solo archivo de 913 líneas — el mismo patrón que
// ya se aplicó a SupervisorPanel.tsx (ver components/supervisor/).

import { useState } from 'react'
import { Field, Modal } from '@/components/ui'
import { IconLock, IconSignature } from '@/components/icons'
import type { FirmaResponse } from '@/services/api/types'
import { crearFirma } from '@/services/firmaService'
import { ApiError } from '@/services/api/client'
import { mapFirma, type FirmaRow, type UserRow } from './tipos'

export function NewFirmaModal({ usuarios, usuarioPreseleccionado, onClose, onCreate }: {
  usuarios: UserRow[]
  usuarioPreseleccionado: UserRow | null
  onClose: () => void
  onCreate: (f: FirmaRow) => void
}) {
  const activos = usuarios.filter(u => u.activo)
  const [usuarioId, setUsuarioId] = useState(usuarioPreseleccionado?.id ?? String(activos[0]?.id ?? ''))
  const [phase, setPhase] = useState<'form' | 'gen' | 'done'>('form')
  const [creada, setCreada] = useState<FirmaResponse | null>(null)
  const [error, setError] = useState('')

  const seleccionado = activos.find(u => u.id === usuarioId) ?? null

  const generar = async () => {
    if (!seleccionado) return
    setError('')
    setPhase('gen')
    try {
      const resultado = await crearFirma({ usuarioId: Number(seleccionado.id) })
      setCreada(resultado)
      setPhase('done')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se pudo asignar la firma.')
      setPhase('form')
    }
  }

  const confirmar = () => {
    if (!creada) return
    onCreate(mapFirma(creada))
  }

  return (
    <Modal title="Asignar firma electrónica" onClose={onClose} width={480}>
      {phase === 'form' && (
        <>
          {activos.length === 0 ? (
            <p style={{ fontSize: 13, color: 'var(--text-muted)' }}>No hay cuentas activas disponibles para asignar una firma.</p>
          ) : (
            <Field label="Cuenta">
              <select value={usuarioId} onChange={e => setUsuarioId(e.target.value)}>
                {activos.map(u => <option key={u.id} value={u.id}>{u.nombre} — {u.rol}</option>)}
              </select>
            </Field>
          )}

          {seleccionado && (
            <div className="card" style={{ padding: '10px 14px', marginBottom: 4, fontSize: 12, color: 'var(--text-secondary)' }}>
              {seleccionado.correo} · {seleccionado.cargo}
            </div>
          )}

          <p style={{ fontSize: 11.5, color: 'var(--text-muted)', margin: '10px 0 14px', lineHeight: 1.5 }}>
            Esto asigna un identificador de firma de referencia a la cuenta y queda guardado en
            el sistema. La integración con un proveedor real de firma electrónica (PKI) se
            implementa en una fase posterior.
          </p>

          {error && <p style={{ color: 'var(--alert-critica)', fontSize: 12, margin: '0 0 10px' }}>{error}</p>}

          <button className="btn-green" style={{ width: '100%', padding: '10px 0', fontSize: 13, opacity: seleccionado ? 1 : 0.6 }}
            onClick={generar} disabled={!seleccionado}>
            Asignar firma
          </button>
        </>
      )}

      {phase === 'gen' && (
        <div style={{ textAlign: 'center', padding: '34px 0' }}>
          <IconLock size={30} style={{ color: 'var(--accent)', margin: '0 auto 12px' }} />
          <p style={{ color: 'var(--accent)', fontSize: 14, fontWeight: 600, margin: 0 }}>Asignando firma...</p>
        </div>
      )}

      {phase === 'done' && creada && (
        <div style={{ textAlign: 'center' }}>
          <IconSignature size={34} style={{ color: 'var(--accent)', margin: '0 auto 6px' }} />
          <p style={{ fontSize: 14, fontWeight: 600, color: 'var(--accent)', margin: '8px 0 2px' }}>Firma {creada.firmaId} asignada</p>
          <p style={{ fontSize: 13, color: 'var(--text-secondary)', margin: 0 }}>
            Cuenta: {creada.usuarioNombre} ({creada.usuarioEmail})
          </p>
          <button className="btn-green" style={{ width: '100%', padding: '10px 0', fontSize: 13, marginTop: 18 }} onClick={confirmar}>
            Aceptar
          </button>
        </div>
      )}
    </Modal>
  )
}
