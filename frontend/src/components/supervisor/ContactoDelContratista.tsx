// Paso 2.4 del proceso real: cómo contactar al contratista.
//
// La hoja de Alex Zapata (paso 5) dice que en el acta de inicio «debe reposar
// un correo de contacto + número de teléfono». El acta del Lote 4 trae el
// correo y no el teléfono; el registro de proveedores del SECOP II sí lo
// publica (4489948 para Suministros Maybe). Los contratos que Gestión registra
// desde el 22-10-2026 lo traen ya; para los anteriores, aquí se trae con un
// clic. SICOT no inventa un teléfono: si el registro no lo publica, lo dice.

import { useState } from 'react'
import { completarContactoDelContratista } from '@/services/contratoService'
import { ApiError } from '@/services/api/client'
import type { ContratoResponse } from '@/services/api/types'
import { BotonCopiar, BOTON, BOTON_SECUNDARIO } from './enlacesExternos'

const NOTA: React.CSSProperties = { fontSize: 11.5, color: 'var(--text-muted)' }

export default function ContactoDelContratista({
  contrato,
  onActualizado,
}: {
  contrato: ContratoResponse
  /** El contrato con el teléfono completado, para que el resto del panel lo vea. */
  onActualizado?: (c: ContratoResponse) => void
}) {
  const [telefono, setTelefono] = useState<string | null>(contrato.telefonoContratista ?? null)
  const [trayendo, setTrayendo] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const traer = () => {
    setTrayendo(true)
    setError(null)
    completarContactoDelContratista(contrato.id)
      .then((c) => {
        setTelefono(c.telefonoContratista ?? null)
        onActualizado?.(c)
      })
      .catch((e) =>
        setError(e instanceof ApiError ? e.message : 'No se pudo consultar el registro de proveedores del SECOP II.'),
      )
      .finally(() => setTrayendo(false))
  }

  return (
    <div style={{ paddingLeft: 26, display: 'flex', flexDirection: 'column', gap: 6, marginTop: 6 }}>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
        <span style={{ ...NOTA, color: 'var(--text-primary)' }}>
          Correo del contratista: <strong>{contrato.correoContratista ?? 'sin registrar'}</strong> · Teléfono:{' '}
          <strong>{telefono ?? 'sin registrar'}</strong>
        </span>
        {telefono && <BotonCopiar texto={telefono} etiqueta="Copiar el teléfono" />}
        {!telefono && (
          <button type="button" style={{ ...BOTON, ...BOTON_SECUNDARIO }} disabled={trayendo} onClick={traer}>
            {trayendo ? 'Consultando el SECOP II…' : 'Traer el teléfono del SECOP II'}
          </button>
        )}
      </div>
      {!telefono && !error && (
        <span style={NOTA}>
          El acta de inicio no trae el teléfono; el registro de proveedores del SECOP II lo publica con el NIT del
          contratista.
        </span>
      )}
      {error && <span style={{ fontSize: 11.5, color: 'var(--alert-leve)' }}>{error}</span>}
    </div>
  )
}
