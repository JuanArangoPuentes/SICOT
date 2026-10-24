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
// Desde el 30-09-2026 también pide las tablas que el formato llena fila por
// fila: las obligaciones del contrato con su cumplimiento y su evidencia, los
// amparos de la póliza y las órdenes de pago. El Informe Final real del Centro
// relaciona treinta obligaciones; SICOT solo tenía una fila pendiente y el
// documento no se podía presentar tal como salía.
//
// Qué datos pide cada formato lo decide el backend (GET /api/ia/plantillas):
// este componente solo los presenta.

import { useRef, useState } from 'react'
import { Field, Modal } from '@/components/ui'
import type { PlantillaDocumento, TablasDelDocumento } from '@/services/api/types'

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
type Tabla = NonNullable<PlantillaDocumento['tablas']>[number]

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

/** Las filas con algo escrito, con cada celda recortada; las vacías no cuentan ni se envían. */
export function filasConContenido(filas: string[][] | undefined, columnas: number): string[][] {
  return (filas ?? [])
    .map((fila) => Array.from({ length: columnas }, (_, i) => (fila[i] ?? '').trim()))
    .filter((fila) => fila.some((celda) => celda !== ''))
}

/**
 * Lo que de las tablas es del contrato —el texto de cada obligación, la
 * póliza— para ofrecerlo en el siguiente documento. Las columnas de este
 * documento (lo hecho en el periodo, su evidencia) se dejan vacías: ofrecer
 * la evidencia del mes pasado era invitar a firmarla como si fuera de este.
 * Una tabla sin columnas del contrato (las órdenes de pago) no se recuerda.
 */
export function tablasDelContrato(plantilla: PlantillaDocumento, tablas: TablasDelDocumento): TablasDelDocumento {
  const recordadas: TablasDelDocumento = {}
  for (const tabla of plantilla.tablas ?? []) {
    if (!tabla.columnas.some((c) => c.delContrato)) continue
    const filas = filasConContenido(tablas[tabla.clave], tabla.columnas.length)
      .map((fila) => fila.map((celda, i) => (tabla.columnas[i].delContrato ? celda : '')))
      .filter((fila) => fila.some((celda) => celda !== ''))
    recordadas[tabla.clave] = filas
  }
  return recordadas
}

const claveDeTablas = (contratoId: number) => `sicot.tablasDelContrato.${contratoId}`

/**
 * Las tablas del contrato guardadas en este equipo (ver SupervisorPanel:
 * cláusulas y amparos públicos, no datos personales). Lo que no tenga la
 * forma esperada —otra versión de SICOT, almacenamiento bloqueado— se ignora
 * y el formulario abre vacío, que es lo mismo que pasaba antes.
 */
export function leerTablasDelContrato(contratoId: number): TablasDelDocumento {
  try {
    const crudo: unknown = JSON.parse(localStorage.getItem(claveDeTablas(contratoId)) ?? '{}')
    if (typeof crudo !== 'object' || crudo === null || Array.isArray(crudo)) return {}
    const tablas: TablasDelDocumento = {}
    for (const [clave, filas] of Object.entries(crudo)) {
      if (Array.isArray(filas) && filas.every((f) => Array.isArray(f) && f.every((c) => typeof c === 'string')))
        tablas[clave] = filas as string[][]
    }
    return tablas
  } catch {
    return {}
  }
}

export function guardarTablasDelContrato(contratoId: number, tablas: TablasDelDocumento): void {
  try {
    localStorage.setItem(claveDeTablas(contratoId), JSON.stringify(tablas))
  } catch {
    // Sin almacenamiento (ventana privada, cuota llena) se recuerda solo
    // mientras SICOT siga abierto.
  }
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

/** Filas que admite el backend por tabla (GeneracionDocumentoService.MAX_FILAS). */
export const MAX_FILAS = 60

/**
 * Celdas vacías en filas que sí tienen algo escrito: salen en el documento
 * como «dato pendiente» aunque la tabla no esté vacía, y el contador lo dice
 * para que no se firme creyendo que estaba completa.
 */
export function celdasVacias(filas: string[][] | undefined, columnas: number): number {
  return filasConContenido(filas, columnas).reduce((n, fila) => n + fila.filter((c) => c === '').length, 0)
}

/** Una tabla del formato: una tarjeta por fila, con un campo por columna. */
function EditorDeTabla({
  tabla,
  filas,
  onCambio,
}: {
  tabla: Tabla
  filas: string[][]
  onCambio: (filas: string[][]) => void
}) {
  const cambiarCelda = (fila: number, columna: number, valor: string) =>
    onCambio(filas.map((f, i) => (i === fila ? f.map((c, j) => (j === columna ? valor : c)) : f)))
  const agregar = useRef<HTMLButtonElement>(null)
  // Tras quitar una fila el foco pasa a «Agregar fila»: si se quedaba donde
  // estaba, el «Quitar» de la fila siguiente ocupaba su lugar y un doble clic
  // o un Enter repetido borraba también esa (revisión del 01-10-2026).
  const quitar = (i: number) => {
    onCambio(filas.filter((_, j) => j !== i))
    // Después de dibujar: con la tabla llena, el botón aparece solo al quitar.
    setTimeout(() => agregar.current?.focus(), 0)
  }
  return (
    <fieldset
      style={{ border: '1px solid var(--border)', borderRadius: 8, padding: '10px 12px 12px', margin: '4px 0 16px' }}
    >
      <legend style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-primary)', padding: '0 4px' }}>
        {tabla.etiqueta}
      </legend>
      <p style={{ fontSize: 12, color: 'var(--text-muted)', margin: '0 0 10px', lineHeight: 1.45 }}>{tabla.ayuda}</p>
      {filas.map((fila, i) => (
        <div
          key={i}
          role="group"
          aria-label={`${tabla.etiqueta}, fila ${i + 1}`}
          style={{
            borderTop: i > 0 ? '1px dashed var(--border)' : 'none',
            paddingTop: i > 0 ? 10 : 0,
            marginBottom: 6,
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
            <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>Fila {i + 1}</span>
            <button
              type="button"
              className="btn-ghost"
              style={{ padding: '3px 10px', fontSize: 12 }}
              aria-label={`Quitar la fila ${i + 1} de ${tabla.etiqueta}`}
              onClick={(e) => {
                // El segundo clic de un doble clic no quita otra fila.
                if (e.detail > 1) return
                quitar(i)
              }}
            >
              Quitar
            </button>
          </div>
          {tabla.columnas.map((columna, j) => (
            <Field key={j} label={`${columna.etiqueta} (fila ${i + 1})`}>
              <textarea
                // Varias tablas tienen una columna «Obligación»: el nombre
                // accesible dice de cuál es cada campo.
                aria-label={`${columna.etiqueta} (fila ${i + 1}) de ${tabla.etiqueta}`}
                value={fila[j] ?? ''}
                onChange={(e) => cambiarCelda(i, j, e.target.value)}
                placeholder={`Ej.: ${columna.ejemplo}`}
                maxLength={1500}
                rows={columna.ejemplo.length > 40 ? 2 : 1}
                style={{ width: '100%', padding: '8px 10px', resize: 'vertical', font: 'inherit', fontSize: 13 }}
              />
            </Field>
          ))}
        </div>
      ))}
      {filas.length < MAX_FILAS ? (
        <button
          ref={agregar}
          type="button"
          className="btn-ghost"
          style={{ padding: '7px 12px', fontSize: 12 }}
          onClick={() => onCambio([...filas, tabla.columnas.map(() => '')])}
        >
          + Agregar fila a «{tabla.etiqueta}»
        </button>
      ) : (
        <p style={{ fontSize: 12, color: 'var(--text-muted)', margin: 0 }}>
          El formato admite hasta {MAX_FILAS} filas en esta tabla.
        </p>
      )}
    </fieldset>
  )
}

export default function DatosDelDocumento({
  plantilla,
  iniciales,
  tablasIniciales = {},
  onConfirmar,
  onCancelar,
}: {
  plantilla: PlantillaDocumento
  /** Lo que el supervisor ya escribió para otro documento de este contrato (su cédula, la fecha de suscripción…). */
  iniciales: Record<string, string>
  /** Las filas ya escritas: las de este documento si se canceló antes, o las obligaciones del contrato. */
  tablasIniciales?: TablasDelDocumento
  onConfirmar: (datos: Record<string, string>, tablas: TablasDelDocumento) => void
  /** Recibe lo escrito, para no perderlo si el supervisor vuelve a abrir el formulario. */
  onCancelar: (valores: Record<string, string>, tablas: TablasDelDocumento) => void
}) {
  const tablas = plantilla.tablas ?? []
  const [valores, setValores] = useState<Record<string, string>>(() => {
    const v: Record<string, string> = {}
    for (const c of plantilla.campos) if (iniciales[c.clave]) v[c.clave] = iniciales[c.clave]
    return v
  })
  // Cada tabla abre con lo ya escrito o con una fila vacía para empezar.
  const [filas, setFilas] = useState<TablasDelDocumento>(() => {
    const f: TablasDelDocumento = {}
    for (const t of tablas) {
      const previas = filasConContenido(tablasIniciales[t.clave], t.columnas.length)
      f[t.clave] = previas.length > 0 ? previas : [t.columnas.map(() => '')]
    }
    return f
  })
  // Los opcionales no se cuentan: el plazo se deduce de las fechas y el correo
  // del contratista no siempre existe; que falten no deja nada pendiente. Una
  // tabla cuenta como un dato: pendiente si no tiene ninguna fila escrita.
  const obligatorios = plantilla.campos.filter((c) => esObligatorio(c, valores))
  const tablasVacias = tablas.filter((t) => filasConContenido(filas[t.clave], t.columnas.length).length === 0)
  const vacios = obligatorios.filter((c) => !valores[c.clave]?.trim()).length + tablasVacias.length
  const totalObligatorios = obligatorios.length + tablas.length
  const celdasSinLlenar = tablas.reduce((n, t) => n + celdasVacias(filas[t.clave], t.columnas.length), 0)

  const limpios = () => {
    const l: Record<string, string> = {}
    for (const [clave, valor] of Object.entries(valores)) if (valor.trim()) l[clave] = valor.trim()
    return l
  }
  const tablasLimpias = () => {
    const l: TablasDelDocumento = {}
    for (const t of tablas) l[t.clave] = filasConContenido(filas[t.clave], t.columnas.length)
    return l
  }
  const cancelar = () => onCancelar(limpios(), tablasLimpias())

  // El código va en el título solo si ya existe: «(PENDIENTE_DE_DEFINIR)» es
  // la marca interna de un formato sin código oficial, no algo que leer.
  const titulo = plantilla.codigo.startsWith('PENDIENTE')
    ? `Datos para ${plantilla.nombre}`
    : `Datos para ${plantilla.nombre} (${plantilla.codigo})`

  return (
    <Modal title={titulo} onClose={cancelar} width={tablas.length > 0 ? 640 : 560} cerrarAlTocarFuera={false}>
      <p style={{ fontSize: 13, color: 'var(--text-muted)', margin: '0 0 14px', lineHeight: 1.5 }}>
        Estos datos van en el formato pero no están registrados en el contrato. Escríbalos como aparecen en sus
        soportes. Lo que deje vacío saldrá en el documento como «dato pendiente», en rojo; después de firmado ya no se
        puede corregir. Antes de firmar verá el borrador completo.
      </p>
      {plantilla.campos.map((c) => (
        <Field key={c.clave} label={esObligatorio(c, valores) ? c.etiqueta : `${c.etiqueta} (opcional)`}>
          {c.largo ? (
            <textarea
              value={valores[c.clave] ?? ''}
              onChange={(e) => setValores((prev) => ({ ...prev, [c.clave]: e.target.value }))}
              placeholder={`Ej.: ${c.ejemplo}`}
              maxLength={2000}
              rows={3}
              style={{ width: '100%', padding: '9px 10px', resize: 'vertical', font: 'inherit', fontSize: 13 }}
            />
          ) : (
            <input
              type="text"
              value={valores[c.clave] ?? ''}
              onChange={(e) => setValores((prev) => ({ ...prev, [c.clave]: e.target.value }))}
              placeholder={`Ej.: ${c.ejemplo}`}
              maxLength={600}
              style={{ width: '100%', padding: '9px 10px' }}
            />
          )}
        </Field>
      ))}
      {tablas.map((t) => (
        <EditorDeTabla
          key={t.clave}
          tabla={t}
          filas={filas[t.clave] ?? []}
          onCambio={(nuevas) => setFilas((prev) => ({ ...prev, [t.clave]: nuevas }))}
        />
      ))}
      <p
        style={{
          fontSize: 12,
          color: vacios + celdasSinLlenar > 0 ? 'var(--alert-leve)' : 'var(--text-muted)',
          margin: '4px 0 12px',
        }}
      >
        {vacios > 0
          ? `${vacios} de ${totalObligatorios} datos obligatorios quedarán marcados como pendientes.`
          : celdasSinLlenar === 0
            ? 'Todos los datos del formato están completos.'
            : ''}
        {celdasSinLlenar > 0 &&
          ` ${celdasSinLlenar === 1 ? 'Una celda' : `${celdasSinLlenar} celdas`} de las tablas ${celdasSinLlenar === 1 ? 'está vacía y saldrá' : 'están vacías y saldrán'} como «dato pendiente».`}
      </p>
      <div style={{ display: 'flex', gap: 10 }}>
        <button className="btn-ghost" style={{ flex: 1, padding: '10px 0', fontSize: 13 }} onClick={cancelar}>
          Cancelar
        </button>
        <button
          className="btn-green"
          style={{ flex: 2, padding: '10px 0', fontSize: 13 }}
          onClick={() => onConfirmar(limpios(), tablasLimpias())}
        >
          Generar y revisar
        </button>
      </div>
    </Modal>
  )
}
