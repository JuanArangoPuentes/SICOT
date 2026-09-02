// Columna del Copiloto IA dentro de la vista "Contrato".
//
// Extraída de SupervisorPanel.tsx, que seguía siendo el archivo más grande del
// frontend después de sacarle las vistas de Documentos y Alertas. Esta columna
// es la pieza más independiente que quedaba: tiene su propio ciclo de
// conversación y no comparte estructura con el recorrido de etapas que ocupa
// el resto de la pantalla.
//
// Recibe muchas props a propósito. La alternativa —mover aquí el estado del
// chat— repartiría entre dos archivos la lógica que decide cuándo el Copiloto
// está pensando, cuándo hay una revisión de paso pendiente y cuándo se puede
// confirmar. Ese estado gobierna también el recorrido de etapas, así que vive
// en el panel y esta columna solo lo presenta.

import type { RefObject } from "react"
import { AvatarIcon, IconArrowRight, IconChevron } from "@/components/icons"
import type { ChatMsg, Step } from "@/types/domain"
import type { ContratoResponse } from "@/services/api/types"

export type RevisionPaso = {
  stepId: number
  subStepId: string
  listaParaConfirmar: boolean
}

export default function PanelCopiloto({
  prefs,
  contrato,
  chatMsgs,
  pensando,
  tutorialMode,
  revisionPaso,
  activeStep,
  chatInput,
  chatEndRef,
  sugerencias,
  onCerrar,
  onCambiarEntrada,
  onEnviar,
  onSugerencia,
  onIniciarPaso,
  onConfirmarRevision,
  onCancelarRevision,
}: {
  prefs: { avatarId: string; avatarName: string }
  contrato: ContratoResponse
  chatMsgs: ChatMsg[]
  pensando: boolean
  tutorialMode: boolean
  revisionPaso: RevisionPaso | null
  activeStep: Step | undefined
  chatInput: string
  chatEndRef: RefObject<HTMLDivElement | null>
  sugerencias: ReadonlyArray<{ label: string; question: string }>
  onCerrar: () => void
  onCambiarEntrada: (texto: string) => void
  onEnviar: () => void
  onSugerencia: (pregunta: string) => void
  onIniciarPaso: (stepId: number) => void
  onConfirmarRevision: () => void
  onCancelarRevision: () => void
}) {
  const bloqueado = pensando || !!revisionPaso

  return (
    <div
      data-tour="copiloto"
      className="split-aside"
      style={{
        width: 420,
        minWidth: 340,
        flexShrink: 0,
        borderLeft: "1px solid var(--border)",
        display: "flex",
        flexDirection: "column",
        overflow: "hidden",
        background: "var(--bg-rail)",
      }}
    >
      <div
        style={{
          padding: "12px 16px",
          borderBottom: "1px solid var(--border)",
          flexShrink: 0,
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <div
            style={{
              width: 36,
              height: 36,
              borderRadius: "50%",
              color: "var(--accent)",
              background: "var(--bg-card)",
              border: "1.5px solid var(--accent)",
              boxShadow: "0 0 0 3px var(--accent-soft)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <AvatarIcon id={prefs.avatarId} size={18} />
          </div>
          <div>
            <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
              <span style={{ fontSize: 13, fontWeight: 600 }}>
                {prefs.avatarName}
              </span>
              <span
                style={{
                  fontSize: 10,
                  fontWeight: 600,
                  color: "var(--on-accent)",
                  background: "var(--accent)",
                  padding: "1px 6px",
                  borderRadius: 3,
                }}
              >
                Activo
              </span>
            </div>
            <div style={{ fontSize: 11, color: "var(--text-muted)" }}>
              Asistente contractual · {contrato.numeroContrato}
            </div>
          </div>
          <div style={{ flex: 1 }} />
          <button
            type="button"
            onClick={onCerrar}
            title="Ocultar el panel del Copiloto"
            aria-label="Ocultar el panel del Copiloto"
            style={{
              background: "none",
              border: "none",
              color: "var(--text-muted)",
              cursor: "pointer",
              padding: 4,
              display: "flex",
            }}
          >
            <IconChevron size={15} />
          </button>
        </div>
      </div>

      {/* Mensajes */}
      <div
        style={{
          flex: 1,
          overflowY: "auto",
          padding: "14px 14px 8px",
          display: "flex",
          flexDirection: "column",
          gap: 10,
        }}
      >
        {chatMsgs.map((m, i) => (
          <div
            key={i}
            style={{
              display: "flex",
              flexDirection: "column",
              alignItems: m.role === "user" ? "flex-end" : "flex-start",
            }}
          >
            {m.role === "ai" ? (
              <div style={{ display: "flex", gap: 6, alignItems: "flex-start" }}>
                <div
                  style={{
                    width: 24,
                    height: 24,
                    borderRadius: "50%",
                    background: "var(--accent-soft)",
                    border: "1px solid var(--accent-line)",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    flexShrink: 0,
                    marginTop: 2,
                    color: "var(--accent)",
                  }}
                >
                  <AvatarIcon id={prefs.avatarId} size={13} />
                </div>
                <div className="copiloto-msg" style={{ maxWidth: "92%" }}>
                  {m.text}
                </div>
              </div>
            ) : (
              <div className="user-msg" style={{ maxWidth: "88%" }}>
                {m.text}
              </div>
            )}
          </div>
        ))}

        {pensando && (
          <div style={{ display: "flex", gap: 6, alignItems: "flex-start" }}>
            <div
              style={{
                width: 24,
                height: 24,
                borderRadius: "50%",
                background: "var(--accent-soft)",
                border: "1px solid var(--accent-line)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontSize: 12,
                flexShrink: 0,
                marginTop: 2,
                color: "var(--accent)",
              }}
            >
              ★
            </div>
            <div
              className="copiloto-msg"
              style={{
                maxWidth: "92%",
                fontStyle: "italic",
                color: "var(--text-muted)",
              }}
            >
              Pensando… puede tardar uno o varios minutos según la carga del
              servidor. No cierre esta ventana.
            </div>
          </div>
        )}

        {!tutorialMode && !pensando && !revisionPaso && activeStep && (
          <div style={{ paddingLeft: 30 }}>
            <button
              className="btn-green"
              onClick={() => onIniciarPaso(activeStep.id)}
              style={{
                padding: "8px 16px",
                fontSize: 13,
                display: "inline-flex",
                alignItems: "center",
                gap: 7,
              }}
            >
              Iniciar Paso {activeStep.id} <IconArrowRight size={12} />
            </button>
          </div>
        )}

        {revisionPaso?.listaParaConfirmar && !pensando && (
          <div
            style={{
              paddingLeft: 30,
              display: "flex",
              gap: 8,
              flexWrap: "wrap",
            }}
          >
            <button
              className="btn-green"
              onClick={onConfirmarRevision}
              style={{ padding: "8px 16px", fontSize: 13 }}
            >
              Confirmar Paso {revisionPaso.stepId} como completado
            </button>
            <button
              className="btn-ghost"
              onClick={onCancelarRevision}
              style={{ padding: "8px 16px", fontSize: 13 }}
            >
              Cancelar, quiero revisar algo antes
            </button>
          </div>
        )}
        <div ref={chatEndRef} />
      </div>

      {/* Sugerencias rápidas */}
      <div
        style={{
          padding: "8px 12px",
          borderTop: "1px solid var(--border)",
          display: "flex",
          gap: 6,
          flexWrap: "wrap",
          flexShrink: 0,
        }}
      >
        {sugerencias.map(({ label, question }) => (
          <button
            key={label}
            onClick={() => onSugerencia(question)}
            disabled={bloqueado}
            style={{
              background: "var(--bg-card)",
              border: "1px solid var(--border)",
              borderRadius: 20,
              padding: "4px 10px",
              fontSize: 11,
              color: "var(--text-secondary)",
              cursor: bloqueado ? "default" : "pointer",
              opacity: bloqueado ? 0.5 : 1,
              fontFamily: "var(--font-ui)",
              transition: "border-color 0.15s",
            }}
            onMouseEnter={(e) =>
              (e.currentTarget.style.borderColor = "var(--accent-dim)")}
            onMouseLeave={(e) =>
              (e.currentTarget.style.borderColor = "var(--border)")}
          >
            {label}
          </button>
        ))}
      </div>

      {/* Entrada */}
      <div
        style={{
          padding: "8px 12px 12px",
          borderTop: "1px solid var(--border)",
          display: "flex",
          gap: 8,
          flexShrink: 0,
        }}
      >
        <input
          type="text"
          value={chatInput}
          onChange={(e) => onCambiarEntrada(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") onEnviar()
          }}
          placeholder={pensando
            ? "Esperando respuesta del Copiloto…"
            : revisionPaso && !revisionPaso.listaParaConfirmar
            ? "Describa qué hizo o verificó en este paso..."
            : "Escriba una orden o pregunta a la IA..."}
          disabled={pensando}
          style={{ flex: 1, padding: "9px 12px", opacity: pensando ? 0.6 : 1 }}
        />
        <button
          className="btn-green"
          onClick={onEnviar}
          disabled={pensando}
          style={{
            padding: "8px 14px",
            fontSize: 13,
            opacity: pensando ? 0.6 : 1,
            cursor: pensando ? "default" : "pointer",
            display: "inline-flex",
            alignItems: "center",
          }}
        >
          <IconArrowRight size={14} />
        </button>
      </div>
    </div>
  )
}
