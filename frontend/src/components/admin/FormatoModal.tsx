// Modal de carga de un formato documental institucional (carga real, sin simulaciones).
//
// Extraído de AdminPanel.tsx, que reunía las cuatro vistas, sus cuatro modales y
// sus piezas compartidas en un solo archivo de 913 líneas — el mismo patrón que
// ya se aplicó a SupervisorPanel.tsx (ver components/supervisor/).

import { useState } from 'react'
import { Field, Modal } from '@/components/ui'
import { IconUpload } from '@/components/icons'
import type { FormatoDocumentalResponse } from '@/services/api/types'
import { subirFormato } from '@/services/formatoService'
import { ApiError } from '@/services/api/client'

export function FormatoModal({ formatoExistente, onClose, onUploaded }: {
  formatoExistente: FormatoDocumentalResponse | null
  onClose: () => void
  onUploaded: () => void
}) {
  const [codigo, setCodigo] = useState(formatoExistente?.codigo ?? '')
  const [nombre, setNombre] = useState(formatoExistente?.nombre ?? '')
  const [archivo, setArchivo] = useState<File | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [done, setDone] = useState(false)

  const esNuevoVersion = formatoExistente !== null

  const subir = async () => {
    if (busy) return
    if (!codigo.trim()) { setError('El código del formato es obligatorio (ej. GCCON-F-031).'); return }
    if (!nombre.trim()) { setError('El nombre del formato es obligatorio.'); return }
    if (!archivo) { setError('Selecciona un archivo PDF, DOCX o XLSX.'); return }
    setError('')
    setBusy(true)
    try {
      await subirFormato(codigo.trim(), nombre.trim(), archivo)
      setDone(true)
      setTimeout(onUploaded, 900)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se pudo cargar el archivo.')
      setBusy(false)
    }
  }

  if (done) {
    return (
      <Modal title="Formato cargado" onClose={onClose} width={460}>
        <div style={{ textAlign: 'center', padding: '10px 0 4px' }}>
          <IconUpload size={40} style={{ color: 'var(--accent)', margin: '0 auto 10px' }} />
          <p style={{ fontSize: 14, color: 'var(--accent)', fontWeight: 600, margin: '0 0 4px' }}>
            {esNuevoVersion ? 'Nueva versión cargada correctamente' : 'Formato agregado al catálogo'}
          </p>
          <p style={{ fontSize: 13, color: 'var(--text-secondary)', margin: 0 }}>{codigo} — {nombre}</p>
        </div>
      </Modal>
    )
  }

  return (
    <Modal title={esNuevoVersion ? `Cargar nueva versión — ${formatoExistente!.codigo}` : 'Cargar nuevo formato'} onClose={onClose} width={520}>
      <Field label="Código del formato (ej. GCCON-F-031)">
        <input type="text" value={codigo} disabled={esNuevoVersion}
          onChange={e => setCodigo(e.target.value)} placeholder="GCCON-F-031"
          style={{ width: '100%', padding: '9px 10px', fontFamily: 'var(--font-mono)', opacity: esNuevoVersion ? 0.65 : 1 }} />
      </Field>
      <Field label="Nombre del formato">
        <input type="text" value={nombre} onChange={e => setNombre(e.target.value)}
          placeholder="Informe de supervisión" style={{ width: '100%', padding: '9px 10px' }} />
      </Field>
      <Field label="Archivo (PDF, DOCX o XLSX — máx 20 MB)">
        <input type="file" accept=".pdf,.docx,.xlsx"
          onChange={e => setArchivo(e.target.files?.[0] ?? null)}
          style={{ width: '100%', fontSize: 13, color: 'var(--text-secondary)' }} />
      </Field>

      {error && <p style={{ color: 'var(--alert-critica)', fontSize: 12, margin: '4px 0 10px' }}>{error}</p>}

      <div style={{ display: 'flex', gap: 10, marginTop: 8 }}>
        <button className="btn-ghost" style={{ flex: 1, padding: '10px 0', fontSize: 13 }} onClick={onClose} disabled={busy}>Cancelar</button>
        <button className="btn-green" style={{ flex: 2, padding: '10px 0', fontSize: 13, opacity: busy ? 0.6 : 1, cursor: busy ? 'default' : 'pointer' }} onClick={subir} disabled={busy}>
          {busy ? 'Cargando…' : esNuevoVersion ? 'Cargar nueva versión' : 'Cargar formato'}
        </button>
      </div>
    </Modal>
  )
}
