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

/**
 * La misma lectura de «no» que hace el backend al redactar
 * (RedactorDeDocumentos.esNo): si el supervisor escribe «No» en la adición,
 * el valor actualizado no hace falta, y contarlo como pendiente era falso.
 */
export function esRespuestaNegativa(valor: string | undefined): boolean {
  if (!valor) return false
  const v = valor.trim().normalize('NFD').replace(/\p{M}/gu, '').toUpperCase().replace(/\./g, '')
  return (
    v === 'NO' ||
    v === 'N/A' ||
    v === 'NA' ||
    v.startsWith('NO APLICA') ||
    v.startsWith('NINGUN') ||
    v.startsWith('NO HUBO') ||
    v.startsWith('NO SE PRESENTARON')
  )
}

type Campo = PlantillaDocumento['campos'][number]

/**
 * De lo escrito, solo lo que es de este documento (la factura, el periodo, el
 * número de informe): es lo único que se guarda como borrador del sub-paso.
 * Lo del contrato ya se recuerda aparte, y guardarlo también en el borrador
 * pisaba una corrección hecha después en otro documento.
 */
export function soloLoDelDocumento(
  plantilla: PlantillaDocumento,
  valores: Record<string, string>,
): Record<string, string> {
  const propios: Record<string, string> = {}
  for (const campo of plantilla.campos) {
    const valor = valores[campo.clave]?.trim()
    if (campo.porDocumento && valor) propios[campo.clave] = valor
  }
  return propios
}

/**
 * Un opcional pasa a obligatorio cuando el dato del que depende dice que sí:
 * con una adición, el valor actualizado del contrato ya no se puede deducir y
 * sin él el informe queda con un pendiente.
 */
export function esObligatorio(campo: Campo, valores: Record<string, string>): boolean {
  if (!campo.opcional) return true
  if (!campo.dependeDe) return false
  const base = valores[campo.dependeDe]?.trim()
  return !!base && !esRespuestaNegativa(base)
}

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
  /** Recibe lo escrito, para no perderlo si el supervisor vuelve a abrir el formulario. */
  onCancelar: (valores: Record<string, string>) => void
}) {
  const [valores, setValores] = useState<Record<string, string>>(() => {
    const v: Record<string, string> = {}
    for (const c of plantilla.campos) if (iniciales[c.clave]) v[c.clave] = iniciales[c.clave]
    return v
  })
  // Los opcionales no se cuentan: el plazo se deduce de las fechas y el correo
  // del contratista no siempre existe; que falten no deja nada pendiente.
  const obligatorios = plantilla.campos.filter((c) => esObligatorio(c, valores))
  const vacios = obligatorios.filter((c) => !valores[c.clave]?.trim()).length

  const limpios = () => {
    const l: Record<string, string> = {}
    for (const [clave, valor] of Object.entries(valores)) if (valor.trim()) l[clave] = valor.trim()
    return l
  }
  const cancelar = () => onCancelar(limpios())

  // El código va en el título solo si ya existe: «(PENDIENTE_DE_DEFINIR)» es
  // la marca interna de un formato sin código oficial, no algo que leer.
  const titulo = plantilla.codigo.startsWith('PENDIENTE')
    ? `Datos para ${plantilla.nombre}`
    : `Datos para ${plantilla.nombre} (${plantilla.codigo})`

  return (
    <Modal title={titulo} onClose={cancelar} width={560} cerrarAlTocarFuera={false}>
      <p style={{ fontSize: 13, color: 'var(--text-muted)', margin: '0 0 14px', lineHeight: 1.5 }}>
        Estos datos van en el formato pero no están registrados en el contrato. Escríbalos como aparecen en sus
        soportes. Lo que deje vacío saldrá en el documento como «dato pendiente», en rojo; después de firmado ya no se
        puede corregir.
      </p>
      {plantilla.campos.map((c) => (
        <Field key={c.clave} label={esObligatorio(c, valores) ? c.etiqueta : `${c.etiqueta} (opcional)`}>
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
          ? `${vacios} de ${obligatorios.length} datos obligatorios quedarán marcados como pendientes.`
          : 'Todos los datos del formato están completos.'}
      </p>
      <div style={{ display: 'flex', gap: 10 }}>
        <button className="btn-ghost" style={{ flex: 1, padding: '10px 0', fontSize: 13 }} onClick={cancelar}>
          Cancelar
        </button>
        <button
          className="btn-green"
          style={{ flex: 2, padding: '10px 0', fontSize: 13 }}
          onClick={() => onConfirmar(limpios())}
        >
          Generar y firmar
        </button>
      </div>
    </Modal>
  )
}
