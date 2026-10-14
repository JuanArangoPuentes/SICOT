// Modal de carga de un formato documental institucional (carga real, sin simulaciones).
//
// Extraído de AdminPanel.tsx, que reunía las cuatro vistas, sus cuatro modales y
// sus piezas compartidas en un solo archivo de 913 líneas — el mismo patrón que
// ya se aplicó a SupervisorPanel.tsx (ver components/supervisor/).

import { useRef, useState } from 'react'
import { Field, Modal } from '@/components/ui'
import { IconUpload } from '@/components/icons'
import type { FormatoDocumentalResponse } from '@/services/api/types'
import { subirFormato } from '@/services/formatoService'
import { ApiError } from '@/services/api/client'

export function FormatoModal({
  formatoExistente,
  onClose,
  onUploaded,
}: {
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
  // El campo de archivo está oculto: quien lo abre es el botón de abajo.
  const campoArchivo = useRef<HTMLInputElement>(null)

  const esNuevoVersion = formatoExistente !== null

  const subir = async () => {
    if (busy) return
    if (!codigo.trim()) {
      setError('El código del formato es obligatorio (ej. GCCON-F-031).')
      return
    }
    if (!nombre.trim()) {
      setError('El nombre del formato es obligatorio.')
      return
    }
    if (!archivo) {
      setError('Selecciona un archivo PDF, DOCX o XLSX.')
      return
    }
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
          <p style={{ fontSize: 13, color: 'var(--text-secondary)', margin: 0 }}>
            {codigo} — {nombre}
          </p>
        </div>
      </Modal>
    )
  }

  return (
    <Modal
      title={esNuevoVersion ? `Cargar nueva versión — ${formatoExistente!.codigo}` : 'Cargar nuevo formato'}
      onClose={onClose}
      width={520}
    >
      <Field label="Código del formato (ej. GCCON-F-031)">
        <input
          type="text"
          value={codigo}
          disabled={esNuevoVersion}
          // Cada campo limpia el aviso al escribir. Los tres se validan al
          // pulsar «Cargar formato» y el aviso se quedaba fijo: quien ponía el
          // nombre que le faltaba seguía leyendo «El nombre del formato es
          // obligatorio» con el nombre ya escrito delante, y lo mismo al
          // elegir el archivo. Es el patrón que ya usa Configuración.
          onChange={(e) => {
            setCodigo(e.target.value)
            setError('')
          }}
          placeholder="GCCON-F-031"
          style={{
            width: '100%',
            padding: '9px 10px',
            fontFamily: 'var(--font-mono)',
            opacity: esNuevoVersion ? 0.65 : 1,
          }}
        />
      </Field>
      <Field label="Nombre del formato">
        <input
          type="text"
          value={nombre}
          onChange={(e) => {
            setNombre(e.target.value)
            setError('')
          }}
          placeholder="Informe de supervisión"
          style={{ width: '100%', padding: '9px 10px' }}
        />
      </Field>
      <Field label="Archivo (PDF, DOCX o XLSX — máx 20 MB)">
        {/*
          El <input type="file"> va oculto y lo dispara un botón del proyecto.
          Visible, el navegador lo pinta con su propio control: en la app de
          Android eso salía como «Choose File / No file chosen», en inglés y sin
          el estilo del resto de la aplicación, que está toda en español (visto
          en el emulador el 08-10-2026). El texto del control nativo lo escribe
          el navegador y no se puede traducir ni estilar desde aquí, así que la
          única salida es no mostrarlo. Es lo que ya hacen EvidenciaFotografica
          (botón que hace click() sobre el campo) y el cargador de fichas de
          GestionPanel (zona punteada que lo dispara).
        */}
        <input
          ref={campoArchivo}
          type="file"
          accept=".pdf,.docx,.xlsx"
          onChange={(e) => {
            setArchivo(e.target.files?.[0] ?? null)
            setError('')
          }}
          style={{ display: 'none' }}
        />
        <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
          <button
            className="btn-ghost"
            style={{ padding: '8px 14px', fontSize: 12.5, display: 'flex', alignItems: 'center', gap: 7 }}
            onClick={() => campoArchivo.current?.click()}
          >
            <IconUpload size={15} />
            {archivo ? 'Cambiar archivo' : 'Elegir archivo'}
          </button>
          {archivo ? (
            <span style={{ fontSize: 12.5, color: 'var(--text-secondary)', minWidth: 0, wordBreak: 'break-all' }}>
              {archivo.name}{' '}
              <span style={{ color: 'var(--text-muted)' }}>({(archivo.size / 1024 / 1024).toFixed(1)} MB)</span>
            </span>
          ) : (
            // Lo que de verdad hay: ninguno. No se finge un archivo pendiente
            // ni se deja el hueco en blanco, que haría dudar si se eligió algo.
            <span style={{ fontSize: 12.5, color: 'var(--text-muted)' }}>Sin archivo seleccionado</span>
          )}
        </div>
      </Field>

      {error && <p style={{ color: 'var(--alert-critica)', fontSize: 12, margin: '4px 0 10px' }}>{error}</p>}

      <div style={{ display: 'flex', gap: 10, marginTop: 8 }}>
        <button
          className="btn-ghost"
          style={{ flex: 1, padding: '10px 0', fontSize: 13 }}
          onClick={onClose}
          disabled={busy}
        >
          Cancelar
        </button>
        <button
          className="btn-green"
          style={{
            flex: 2,
            padding: '10px 0',
            fontSize: 13,
            opacity: busy ? 0.6 : 1,
            cursor: busy ? 'default' : 'pointer',
          }}
          onClick={subir}
          disabled={busy}
        >
          {busy ? 'Cargando…' : esNuevoVersion ? 'Cargar nueva versión' : 'Cargar formato'}
        </button>
      </div>
    </Modal>
  )
}
