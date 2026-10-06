// Vista "Documentos" del panel del Supervisor.
//
// Extraída de SupervisorPanel.tsx, que concentraba las cinco vistas en un solo
// archivo de más de 1.200 líneas: cualquier cambio en una vista obligaba a
// releer y arriesgar las otras cuatro, y dos personas tocando pantallas
// distintas chocaban en cada merge.
//
// Además de mover el código, esta vista añade la verificación de integridad:
// para cada documento firmado el backend recalcula el SHA-256 del contenido y
// lo compara con la huella que registró al firmarlo. Antes "firmado" era solo
// una etiqueta que sobrevivía a cualquier modificación posterior del archivo.

import { useEffect, useState } from 'react'
import { Chip, SectionHeader } from '@/components/ui'
import { FORMAL_DOCS } from '@/data/contractFlow'
import { descargarDocumento, verificarIntegridad } from '@/services/documentoService'
import { motivoDelFalloDeDescarga } from '@/services/falloDeDescarga'
import { describirCaptura, fechaDelCentro } from '@/services/format'
import type { ContratoResponse, DocumentoResponse, EstadoIntegridad } from '@/services/api/types'

const ETAPA_LABEL: Record<number, string> = {
  2: 'Inicio',
  3: 'Inspección',
  4: 'Recepción',
  5: 'Certificación',
  6: 'Cierre',
}

const BOTON: React.CSSProperties = {
  background: 'var(--accent-soft)',
  border: '1px solid var(--accent-line)',
  borderRadius: 6,
  padding: '5px 10px',
  fontSize: 11,
  color: 'var(--accent)',
  cursor: 'pointer',
  fontFamily: 'var(--font-ui)',
  whiteSpace: 'nowrap',
}

/**
 * Lo que la vista sabe de la integridad de un documento firmado: el veredicto
 * del servidor, que la consulta sigue en curso, o que no se pudo consultar.
 */
type Integridad = EstadoIntegridad | 'CONSULTANDO' | 'NO_CONSULTADO'

/**
 * Sello de integridad de un documento firmado.
 *
 * Los estados dicen cosas distintas y ninguno se puede confundir con otro. En
 * particular, NO_VERIFICABLE no significa "está bien": son documentos firmados
 * antes de que el sistema registrara la huella, y afirmar integridad sobre
 * ellos sería exactamente la clase de mentira que esta función existe para
 * evitar. Y NO_CONSULTADO no es «Verificando…»: hasta la auditoría del
 * 02-10-2026 un fallo de red dejaba ese rótulo para siempre, diciendo que se
 * comprobaba algo que ya nadie estaba comprobando.
 */
function SelloIntegridad({ estado, onReintentar }: { estado: Integridad; onReintentar: () => void }) {
  if (estado === 'CONSULTANDO') {
    return <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>Verificando…</span>
  }
  if (estado === 'NO_CONSULTADO') {
    return (
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
        <span
          title="El servidor no respondió a la verificación. No es un veredicto sobre el documento."
          style={{ fontSize: 11, color: 'var(--alert-leve)' }}
        >
          No se pudo verificar
        </span>
        <button onClick={onReintentar} aria-label="Reintentar la verificación" style={BOTON}>
          Reintentar
        </button>
      </span>
    )
  }
  if (estado === 'INTEGRO') {
    return <Chip text="Íntegro" type="signed" />
  }
  if (estado === 'ALTERADO') {
    return (
      <span
        title="El contenido de este documento cambió después de haber sido firmado."
        style={{
          fontSize: 11,
          fontWeight: 700,
          padding: '3px 8px',
          borderRadius: 5,
          color: 'var(--alert-critica)',
          background: 'var(--chip-red-bg)',
          border: '1px solid var(--alert-critica)',
        }}
      >
        ⚠ Alterado
      </span>
    )
  }
  if (estado === 'NO_VERIFICABLE') {
    return (
      <span
        title="Se firmó antes de que el sistema registrara la huella del contenido; su integridad no se puede confirmar ni descartar."
        style={{ fontSize: 11, color: 'var(--text-muted)' }}
      >
        Sin huella registrada
      </span>
    )
  }
  return null
}

export default function VistaDocumentos({
  contrato,
  docsContrato,
  cargando,
  error,
  onReintentar,
  tieneFirma,
  onIrASubPaso,
}: {
  contrato: ContratoResponse
  docsContrato: DocumentoResponse[]
  /** La lista se está consultando: vacía todavía no quiere decir «sin generar». */
  cargando: boolean
  /** No se pudo consultar: la lista vacía no dice nada sobre el contrato. */
  error: boolean
  onReintentar: () => void
  tieneFirma: boolean | null
  onIrASubPaso: (subStepId: string, step: number) => void
}) {
  const [integridad, setIntegridad] = useState<Record<number, Integridad>>({})
  const [errorDescarga, setErrorDescarga] = useState<string | null>(null)
  // El documento que se está descargando. Un PDF firmado de varios MB tarda
  // segundos con datos móviles; sin esta marca el botón no mostraba nada, y
  // un segundo toque abría otro «Guardar como» o bajaba otra copia.
  const [descargando, setDescargando] = useState<number | null>(null)
  // Confirmación de guardado. Solo se usa en el APK de Android: allí el
  // «Guardar como» del sistema se cierra y vuelve a la aplicación sin ninguna
  // otra señal de que el archivo quedó escrito. En el navegador no hace falta,
  // porque el propio gestor de descargas lo muestra.
  const [avisoDescarga, setAvisoDescarga] = useState<string | null>(null)

  // Se consulta solo lo firmado: verificar recalcula el hash del archivo
  // completo en el servidor, y hacerlo sobre documentos sin firma no
  // respondería nada útil.
  const firmados = docsContrato.filter((d) => d.firmaId !== null)
  const clavesFirmadas = firmados.map((d) => d.id).join(',')

  const consultarIntegridad = (docs: DocumentoResponse[], vigente: () => boolean) => {
    Promise.all(
      docs.map(async (doc) => {
        try {
          const r = await verificarIntegridad(contrato.id, doc.id)
          return [doc.id, r.estado] as const
        } catch {
          // Un fallo de red no es un veredicto sobre el documento: se dice
          // que no se pudo verificar, sin sello en ninguno de los dos sentidos.
          return [doc.id, 'NO_CONSULTADO'] as const
        }
      }),
    ).then((resultados) => {
      if (vigente()) setIntegridad((previo) => ({ ...previo, ...Object.fromEntries(resultados) }))
    })
  }

  useEffect(() => {
    if (firmados.length === 0) return
    let cancelado = false
    setIntegridad((previo) => {
      const siguiente = { ...previo }
      firmados.forEach((d) => {
        if (!(d.id in siguiente) || siguiente[d.id] === 'NO_CONSULTADO') siguiente[d.id] = 'CONSULTANDO'
      })
      return siguiente
    })
    consultarIntegridad(firmados, () => !cancelado)
    return () => {
      cancelado = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clavesFirmadas, contrato.id])

  // Los ids son de toda la base, así que un resultado tardío no puede caer
  // sobre el documento de otro contrato: no hace falta cancelar el reintento.
  const reintentarVerificacion = (doc: DocumentoResponse) => {
    setIntegridad((previo) => ({ ...previo, [doc.id]: 'CONSULTANDO' }))
    consultarIntegridad([doc], () => true)
  }

  const descargar = (doc: DocumentoResponse) => {
    if (descargando !== null) return
    setDescargando(doc.id)
    setErrorDescarga(null)
    setAvisoDescarga(null)
    descargarDocumento(contrato.id, doc.id, doc.nombre)
      .then((resultado) => {
        if (resultado === 'guardado') setAvisoDescarga(`«${doc.nombre}» quedó guardado en el teléfono.`)
      })
      .catch((err) => {
        console.error('No se pudo descargar el documento:', err)
        setErrorDescarga(motivoDelFalloDeDescarga(err, `«${doc.nombre}»`))
      })
      .finally(() => setDescargando(null))
  }

  return (
    <div style={{ flex: 1, overflowY: 'auto', padding: 24, minWidth: 0 }}>
      <SectionHeader
        eyebrow="GCCON-P-010"
        title="Documentos formales"
        desc="SICOT arma cada documento con los datos del contrato cuando usted llega a su sub-paso; lo que falte sale marcado como dato pendiente. Aquí solo se marca como disponible lo que ya se generó de verdad."
      />

      {tieneFirma === false && (
        <div
          className="card"
          style={{
            padding: '12px 15px',
            marginBottom: 14,
            borderColor: 'var(--alert-critica)',
            background: 'var(--chip-red-bg)',
            fontSize: 12.5,
            color: 'var(--text-primary)',
          }}
        >
          <strong style={{ color: 'var(--alert-critica)' }}>Falta su firma electrónica.</strong> Todavía no se ha
          obtenido su firma electrónica: solicítela al Administrador antes de poder firmar.
        </div>
      )}

      {errorDescarga && (
        <div
          role="alert"
          className="card"
          style={{
            padding: '12px 15px',
            marginBottom: 14,
            borderColor: 'var(--alert-critica)',
            fontSize: 12.5,
            color: 'var(--text-primary)',
          }}
        >
          {errorDescarga}
        </div>
      )}

      {avisoDescarga && (
        <div
          role="status"
          className="card"
          style={{
            padding: '12px 15px',
            marginBottom: 14,
            borderColor: 'var(--accent)',
            fontSize: 12.5,
            color: 'var(--text-primary)',
          }}
        >
          {avisoDescarga}
        </div>
      )}

      {/* Sin la lista no se sabe qué está generado ni qué está firmado. La
          tabla de abajo, con la lista vacía, diría «Sin generar aún» en los
          cinco formatos: con el servidor caído, a un supervisor que ya firmó
          el Acta de Inicio (auditoría del 02-10-2026). */}
      {error ? (
        <div
          role="alert"
          className="card"
          style={{
            padding: '12px 15px',
            marginBottom: 14,
            borderColor: 'var(--alert-critica)',
            fontSize: 12.5,
            color: 'var(--text-primary)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 12,
            flexWrap: 'wrap',
          }}
        >
          <span>
            No se pudieron consultar los documentos del contrato. Hasta que el servidor responda, SICOT no puede decir
            cuáles están generados o firmados.
          </span>
          <button onClick={onReintentar} style={BOTON}>
            Reintentar
          </button>
        </div>
      ) : cargando && docsContrato.length === 0 ? (
        <div
          role="status"
          className="card"
          style={{ padding: '12px 15px', fontSize: 12.5, color: 'var(--text-muted)' }}
        >
          Consultando los documentos del contrato…
        </div>
      ) : (
        /* Documentos formales — SICOT los arma, el supervisor firma.
           El estado viene de docsContrato (datos reales), no de los pasos locales:
           si el documento no existe todavía en el backend, se marca "Sin generar",
           nunca se ofrece firmar algo que no fue realmente redactado. */
        <div className="card" style={{ overflow: 'hidden', marginBottom: 24 }}>
          <div
            className="tabla-cabecera"
            style={{
              padding: '10px 16px',
              borderBottom: '1px solid var(--border)',
              fontSize: 10.5,
              fontWeight: 700,
              color: 'var(--text-muted)',
              letterSpacing: '0.08em',
              display: 'grid',
              gridTemplateColumns: '1fr 150px 1fr 100px 160px',
              gap: 12,
              background: 'var(--bg-elevated)',
            }}
          >
            <span>DOCUMENTO</span>
            <span>CÓDIGO</span>
            <span>DESCRIPCIÓN</span>
            <span>ETAPA</span>
            <span>ESTADO</span>
          </div>
          {FORMAL_DOCS.map((doc) => {
            const generado = docsContrato.find((d) => d.generadoPorIa && d.nombre.startsWith(doc.name))
            return (
              <div
                key={doc.subStepId}
                className="data-grid-row tabla-fila"
                style={{
                  padding: '12px 16px',
                  borderBottom: '1px solid var(--border)',
                  display: 'grid',
                  gridTemplateColumns: '1fr 150px 1fr 100px 160px',
                  gap: 12,
                  alignItems: 'center',
                  transition: 'background var(--t)',
                }}
              >
                <div data-col="Documento">
                  <div
                    style={{
                      fontSize: 13,
                      fontWeight: 500,
                      color: 'var(--text-primary)',
                    }}
                  >
                    {doc.name}
                  </div>
                  <div
                    style={{
                      fontSize: 11,
                      color: 'var(--text-muted)',
                      marginTop: 2,
                    }}
                  >
                    SICOT genera · sub-paso {doc.subStepId}
                  </div>
                </div>
                <span
                  data-col="Código"
                  style={{
                    fontFamily: 'var(--font-mono)',
                    fontSize: 11,
                    color: 'var(--accent-tech)',
                  }}
                >
                  {doc.code === 'PENDIENTE_DE_DEFINIR' ? 'Código pendiente de definir' : doc.code}
                </span>
                <span
                  data-col="Descripción"
                  style={{
                    fontSize: 11.5,
                    color: 'var(--text-secondary)',
                    lineHeight: 1.45,
                  }}
                >
                  {doc.desc}
                </span>
                <span
                  data-col="Etapa"
                  style={{
                    fontSize: 11.5,
                    color: 'var(--text-muted)',
                    fontWeight: 500,
                  }}
                >
                  {ETAPA_LABEL[doc.step]}
                </span>
                {/* La celda de estado envuelve las tres ramas en un solo elemento
                  para que en teléfono lleve su etiqueta como las demás: era
                  justo la columna que quedaba recortada y sin ella la lista no
                  decía en qué estado estaba ningún documento. */}
                <span data-col="Estado">
                  {!generado ? (
                    <Chip text="Sin generar aún" type="pending" />
                  ) : generado.estado === 'APROBADO' ? (
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 6,
                        flexWrap: 'wrap',
                      }}
                    >
                      <Chip text="Firmado" type="signed" />
                      <SelloIntegridad
                        estado={integridad[generado.id] ?? 'CONSULTANDO'}
                        onReintentar={() => reintentarVerificacion(generado)}
                      />
                    </div>
                  ) : (
                    <button onClick={() => onIrASubPaso(doc.subStepId, doc.step)} style={BOTON}>
                      Ir a firmar →
                    </button>
                  )}
                </span>
              </div>
            )
          })}
        </div>
      )}

      {/* Documentos reales del contrato (backend) */}
      {docsContrato.length > 0 && (
        <div>
          <div
            style={{
              fontSize: 10.5,
              fontWeight: 700,
              color: 'var(--text-muted)',
              letterSpacing: '0.09em',
              marginBottom: 12,
            }}
          >
            DOCUMENTOS DEL CONTRATO
          </div>
          {docsContrato.map((doc) => {
            // Solo dos estados posibles: nada en el backend asigna
            // EstadoDocumento.RECHAZADO —no hay endpoint de aprobación ni de
            // rechazo— así que la rama «Rechazado» que había aquí no se
            // ejecutaba nunca. Si algún día existe ese trámite, vuelve con él y
            // no antes: una etiqueta que ningún camino produce hace creer que la
            // revisión de documentos ya está implementada.
            const estado =
              doc.estado === 'APROBADO'
                ? { text: 'Disponible', type: 'done' as const }
                : { text: 'Pendiente', type: 'pending' as const }
            return (
              <div
                key={doc.id}
                // `fila-apilable`: en pantalla estrecha el bloque de acciones
                // baja a su propio renglón. Sin esa clase, el nombre del
                // documento —lo único que lo identifica— se quedaba con 86 px
                // de 360, porque las acciones miden 164 px y no encogen. Se vio
                // solo al poblar la base: con un único documento cargado, que
                // es como se midió el 16 de septiembre, la tarjeta cabía.
                className="card fila-apilable"
                style={{
                  padding: '12px 16px',
                  marginBottom: 8,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: 12,
                }}
              >
                <div>
                  <div
                    style={{
                      fontSize: 13,
                      fontWeight: 500,
                      color: 'var(--accent-tech)',
                      fontFamily: 'var(--font-mono)',
                    }}
                  >
                    {doc.nombre}
                  </div>
                  <div
                    style={{
                      fontSize: 12,
                      color: 'var(--text-secondary)',
                      marginTop: 2,
                    }}
                  >
                    {doc.formatoCodigo ? `${doc.formatoCodigo} · ` : ''}
                    {doc.tipo} · {fechaDelCentro(doc.fechaSubida)}
                    {doc.generadoPorIa ? ' · Generado por SICOT' : ''}
                    {doc.firmadoPorNombre ? ` · Firmado por ${doc.firmadoPorNombre}` : ''}
                  </div>
                  {doc.tipo === 'IMAGEN' && (
                    // La fecha de la línea de arriba es la de CARGA. En una foto de
                    // evidencia la que importa es la de captura, y se muestra
                    // aparte para que no se confundan.
                    <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 2 }}>
                      {describirCaptura(doc)}
                    </div>
                  )}
                </div>
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    flexShrink: 0,
                  }}
                >
                  {doc.firmaId && (
                    <SelloIntegridad
                      estado={integridad[doc.id] ?? 'CONSULTANDO'}
                      onReintentar={() => reintentarVerificacion(doc)}
                    />
                  )}
                  <Chip text={estado.text} type={estado.type} />
                  <button
                    onClick={() => descargar(doc)}
                    disabled={descargando !== null}
                    style={{ ...BOTON, cursor: descargando !== null ? 'default' : 'pointer' }}
                  >
                    {descargando === doc.id ? 'Descargando…' : 'Descargar'}
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
