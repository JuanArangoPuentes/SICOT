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

import { useEffect, useState } from "react"
import { Chip, SectionHeader } from "@/components/ui"
import { FORMAL_DOCS } from "@/data/contractFlow"
import {
  descargarDocumento,
  verificarIntegridad,
} from "@/services/documentoService"
import { formatFecha } from "@/services/format"
import type {
  ContratoResponse,
  DocumentoResponse,
  EstadoIntegridad,
} from "@/services/api/types"

const ETAPA_LABEL: Record<number, string> = {
  2: "Inicio",
  3: "Inspección",
  4: "Recepción",
  5: "Certificación",
  6: "Cierre",
}

const BOTON: React.CSSProperties = {
  background: "var(--accent-soft)",
  border: "1px solid var(--accent-line)",
  borderRadius: 6,
  padding: "5px 10px",
  fontSize: 11,
  color: "var(--accent)",
  cursor: "pointer",
  fontFamily: "var(--font-ui)",
  whiteSpace: "nowrap",
}

/**
 * Sello de integridad de un documento firmado.
 *
 * Los cuatro estados dicen cosas distintas y ninguno se puede confundir con
 * otro. En particular, NO_VERIFICABLE no significa "está bien": son documentos
 * firmados antes de que el sistema registrara la huella, y afirmar integridad
 * sobre ellos sería exactamente la clase de mentira que esta función existe
 * para evitar.
 */
function SelloIntegridad({
  estado,
}: {
  estado: EstadoIntegridad | "CONSULTANDO"
}) {
  if (estado === "CONSULTANDO") {
    return (
      <span style={{ fontSize: 11, color: "var(--text-muted)" }}>
        Verificando…
      </span>
    )
  }
  if (estado === "INTEGRO") {
    return <Chip text="Íntegro" type="signed" />
  }
  if (estado === "ALTERADO") {
    return (
      <span
        title="El contenido de este documento cambió después de haber sido firmado."
        style={{
          fontSize: 11,
          fontWeight: 700,
          padding: "3px 8px",
          borderRadius: 5,
          color: "var(--alert-critica)",
          background: "var(--chip-red-bg)",
          border: "1px solid var(--alert-critica)",
        }}
      >
        ⚠ Alterado
      </span>
    )
  }
  if (estado === "NO_VERIFICABLE") {
    return (
      <span
        title="Se firmó antes de que el sistema registrara la huella del contenido; su integridad no se puede confirmar ni descartar."
        style={{ fontSize: 11, color: "var(--text-muted)" }}
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
  tieneFirma,
  onIrASubPaso,
}: {
  contrato: ContratoResponse
  docsContrato: DocumentoResponse[]
  tieneFirma: boolean | null
  onIrASubPaso: (subStepId: string, step: number) => void
}) {
  const [integridad, setIntegridad] =
    useState<Record<number, EstadoIntegridad | "CONSULTANDO">>({})
  const [errorDescarga, setErrorDescarga] = useState<string | null>(null)

  // Se consulta solo lo firmado: verificar recalcula el hash del archivo
  // completo en el servidor, y hacerlo sobre documentos sin firma no
  // respondería nada útil.
  const firmados = docsContrato.filter((d) => d.firmaId !== null)
  const clavesFirmadas = firmados.map((d) => d.id).join(",")

  useEffect(() => {
    if (firmados.length === 0) return
    let cancelado = false
    setIntegridad((previo) => {
      const siguiente = { ...previo }
      firmados.forEach((d) => {
        if (!(d.id in siguiente)) siguiente[d.id] = "CONSULTANDO"
      })
      return siguiente
    })

    Promise.all(
      firmados.map(async (doc) => {
        try {
          const r = await verificarIntegridad(contrato.id, doc.id)
          return [doc.id, r.estado] as const
        } catch {
          // Un fallo de red no es un veredicto sobre el documento: se deja sin
          // sello antes que mostrar uno equivocado en cualquiera de los dos
          // sentidos.
          return [doc.id, null] as const
        }
      }),
    ).then((resultados) => {
      if (cancelado) return
      setIntegridad((previo) => {
        const siguiente = { ...previo }
        resultados.forEach(([id, estado]) => {
          if (estado) siguiente[id] = estado
          else delete siguiente[id]
        })
        return siguiente
      })
    })

    return () => {
      cancelado = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clavesFirmadas, contrato.id])

  const descargar = (doc: DocumentoResponse) => {
    setErrorDescarga(null)
    descargarDocumento(contrato.id, doc.id, doc.nombre).catch((err) => {
      console.error("No se pudo descargar el documento:", err)
      setErrorDescarga(
        `No se pudo descargar "${doc.nombre}". Intente de nuevo en un momento.`,
      )
    })
  }

  return (
    <div style={{ flex: 1, overflowY: "auto", padding: 24, minWidth: 0 }}>
      <SectionHeader
        eyebrow="GCCON-P-010"
        title="Documentos formales"
        desc="El Copiloto IA redacta cada documento cuando usted llega a su sub-paso — antes de eso, todavía no existe. Aquí solo se marca como disponible lo que ya se generó de verdad."
      />

      {tieneFirma === false && (
        <div
          className="card"
          style={{
            padding: "12px 15px",
            marginBottom: 14,
            borderColor: "var(--alert-critica)",
            background: "var(--chip-red-bg)",
            fontSize: 12.5,
            color: "var(--text-primary)",
          }}
        >
          <strong style={{ color: "var(--alert-critica)" }}>
            Falta su firma electrónica.
          </strong>{" "}
          Todavía no se ha obtenido su firma electrónica: solicítela al
          Administrador antes de poder firmar.
        </div>
      )}

      {errorDescarga && (
        <div
          role="alert"
          className="card"
          style={{
            padding: "12px 15px",
            marginBottom: 14,
            borderColor: "var(--alert-critica)",
            fontSize: 12.5,
            color: "var(--text-primary)",
          }}
        >
          {errorDescarga}
        </div>
      )}

      {/* Documentos formales — Copiloto genera, supervisor firma.
          El estado viene de docsContrato (datos reales), no de los pasos locales:
          si el documento no existe todavía en el backend, se marca "Sin generar",
          nunca se ofrece firmar algo que no fue realmente redactado. */}
      <div className="card" style={{ overflow: "hidden", marginBottom: 24 }}>
        <div
          style={{
            padding: "10px 16px",
            borderBottom: "1px solid var(--border)",
            fontSize: 10.5,
            fontWeight: 700,
            color: "var(--text-muted)",
            letterSpacing: "0.08em",
            display: "grid",
            gridTemplateColumns: "1fr 150px 1fr 100px 160px",
            gap: 12,
            background: "var(--bg-elevated)",
          }}
        >
          <span>DOCUMENTO</span>
          <span>CÓDIGO</span>
          <span>DESCRIPCIÓN</span>
          <span>ETAPA</span>
          <span>ESTADO</span>
        </div>
        {FORMAL_DOCS.map((doc) => {
          const generado = docsContrato.find(
            (d) => d.generadoPorIa && d.nombre.startsWith(doc.name),
          )
          return (
            <div
              key={doc.subStepId}
              className="data-grid-row"
              style={{
                padding: "12px 16px",
                borderBottom: "1px solid var(--border)",
                display: "grid",
                gridTemplateColumns: "1fr 150px 1fr 100px 160px",
                gap: 12,
                alignItems: "center",
                transition: "background var(--t)",
              }}
            >
              <div>
                <div
                  style={{
                    fontSize: 13,
                    fontWeight: 500,
                    color: "var(--text-primary)",
                  }}
                >
                  {doc.name}
                </div>
                <div
                  style={{
                    fontSize: 11,
                    color: "var(--text-muted)",
                    marginTop: 2,
                  }}
                >
                  IA genera · sub-paso {doc.subStepId}
                </div>
              </div>
              <span
                style={{
                  fontFamily: "var(--font-mono)",
                  fontSize: 11,
                  color: "var(--accent-tech)",
                }}
              >
                {doc.code === "PENDIENTE_DE_DEFINIR"
                  ? "Código pendiente de definir"
                  : doc.code}
              </span>
              <span
                style={{
                  fontSize: 11.5,
                  color: "var(--text-secondary)",
                  lineHeight: 1.45,
                }}
              >
                {doc.desc}
              </span>
              <span
                style={{
                  fontSize: 11.5,
                  color: "var(--text-muted)",
                  fontWeight: 500,
                }}
              >
                {ETAPA_LABEL[doc.step]}
              </span>
              {!generado ? (
                <Chip text="Sin generar aún" type="pending" />
              ) : generado.estado === "APROBADO" ? (
                <div
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: 6,
                    flexWrap: "wrap",
                  }}
                >
                  <Chip text="Firmado" type="signed" />
                  <SelloIntegridad
                    estado={integridad[generado.id] ?? "CONSULTANDO"}
                  />
                </div>
              ) : (
                <button
                  onClick={() => onIrASubPaso(doc.subStepId, doc.step)}
                  style={BOTON}
                >
                  Ir a firmar →
                </button>
              )}
            </div>
          )
        })}
      </div>

      {/* Documentos reales del contrato (backend) */}
      {docsContrato.length > 0 && (
        <div>
          <div
            style={{
              fontSize: 10.5,
              fontWeight: 700,
              color: "var(--text-muted)",
              letterSpacing: "0.09em",
              marginBottom: 12,
            }}
          >
            DOCUMENTOS DEL CONTRATO
          </div>
          {docsContrato.map((doc) => {
            const estado =
              doc.estado === "APROBADO"
                ? { text: "Disponible", type: "done" as const }
                : doc.estado === "RECHAZADO"
                  ? { text: "Rechazado", type: "conflicto" as const }
                  : { text: "Pendiente", type: "pending" as const }
            return (
              <div
                key={doc.id}
                className="card"
                style={{
                  padding: "12px 16px",
                  marginBottom: 8,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  gap: 12,
                }}
              >
                <div>
                  <div
                    style={{
                      fontSize: 13,
                      fontWeight: 500,
                      color: "var(--accent-tech)",
                      fontFamily: "var(--font-mono)",
                    }}
                  >
                    {doc.nombre}
                  </div>
                  <div
                    style={{
                      fontSize: 12,
                      color: "var(--text-secondary)",
                      marginTop: 2,
                    }}
                  >
                    {doc.tipo} · {formatFecha(doc.fechaSubida.slice(0, 10))}
                    {doc.generadoPorIa ? " · Generado por el Copiloto IA" : ""}
                    {doc.firmadoPorNombre
                      ? ` · Firmado por ${doc.firmadoPorNombre}`
                      : ""}
                  </div>
                </div>
                <div
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: 8,
                    flexShrink: 0,
                  }}
                >
                  {doc.firmaId && (
                    <SelloIntegridad
                      estado={integridad[doc.id] ?? "CONSULTANDO"}
                    />
                  )}
                  <Chip text={estado.text} type={estado.type} />
                  <button onClick={() => descargar(doc)} style={BOTON}>
                    Descargar
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
