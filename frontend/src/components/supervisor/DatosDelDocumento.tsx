// Los datos que un documento formal pide y el contrato no tiene —número de
// factura, póliza, cédulas…—, preguntados justo antes de generarlo y firmarlo.
//
// Existe porque hasta el 28 de septiembre de 2026 SICOT generaba y firmaba el
// documento de un solo golpe con esos datos marcados «[dato pendiente]», y un
// documento firmado ya no se puede regenerar: el certificado para el pago
// quedaba firmado sin factura ni cuenta bancaria. Aquí el supervisor los
// escribe antes; los que deje vacíos siguen saliendo en rojo como pendientes,
// que es honesto, pero ya no por falta de oportunidad de darlos.
//
// Qué datos pide cada formato lo decide el backend (GET /api/ia/plantillas):
// este componente solo los presenta.

import { useState } from 'react'
import { Field, Modal } from '@/components/ui'
import type { PlantillaDocumento } from '@/services/api/types'

export default function DatosDelDocumento({
  plantilla,
  iniciales,
  onConfirmar,
  onCancelar,
}: {
  plantilla: PlantillaDocumento
  /** Lo que el supervisor ya escribió para otro documento de este contrato (su cédula, la fecha de suscripción…). */
  iniciales: Record<string, string>
  onConfirmar: (datos: Record<string, string>) => void
  onCancelar: () => void
}) {
  const [valores, setValores] = useState<Record<string, string>>(() => {
    const v: Record<string, string> = {}
    for (const c of plantilla.campos) if (iniciales[c.clave]) v[c.clave] = iniciales[c.clave]
    return v
  })
  const vacios = plantilla.campos.filter((c) => !valores[c.clave]?.trim()).length

  const confirmar = () => {
    const limpios: Record<string, string> = {}
    for (const [clave, valor] of Object.entries(valores)) if (valor.trim()) limpios[clave] = valor.trim()
    onConfirmar(limpios)
  }

  return (
    <Modal title={`Datos para ${plantilla.nombre} (${plantilla.codigo})`} onClose={onCancelar} width={560}>
      <p style={{ fontSize: 13, color: 'var(--text-muted)', margin: '0 0 14px', lineHeight: 1.5 }}>
        Estos datos van en el formato pero no están registrados en el contrato. Escríbalos como aparecen en sus
        soportes. Lo que deje vacío saldrá en el documento como «dato pendiente», en rojo; después de firmado ya no se
        puede corregir.
      </p>
      {plantilla.campos.map((c) => (
        <Field key={c.clave} label={c.etiqueta}>
          <input
            type="text"
            value={valores[c.clave] ?? ''}
            onChange={(e) => setValores((prev) => ({ ...prev, [c.clave]: e.target.value }))}
            placeholder={`Ej.: ${c.ejemplo}`}
            maxLength={600}
            style={{ width: '100%', padding: '9px 10px' }}
          />
        </Field>
      ))}
      <p style={{ fontSize: 12, color: vacios > 0 ? 'var(--alert-leve)' : 'var(--text-muted)', margin: '4px 0 12px' }}>
        {vacios > 0
          ? `${vacios} de ${plantilla.campos.length} datos quedarán marcados como pendientes.`
          : 'Todos los datos del formato están completos.'}
      </p>
      <div style={{ display: 'flex', gap: 10 }}>
        <button className="btn-ghost" style={{ flex: 1, padding: '10px 0', fontSize: 13 }} onClick={onCancelar}>
          Cancelar
        </button>
        <button className="btn-green" style={{ flex: 2, padding: '10px 0', fontSize: 13 }} onClick={confirmar}>
          Generar y firmar
        </button>
      </div>
    </Modal>
  )
}
