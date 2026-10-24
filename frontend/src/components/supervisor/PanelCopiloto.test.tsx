import { fireEvent, render, screen } from '@testing-library/react'
import { createRef, type ComponentProps } from 'react'
import { describe, expect, it, vi } from 'vitest'
import PanelCopiloto, { type RevisionPaso } from './PanelCopiloto'
import type { AccionCopiloto, ContratoResponse } from '@/services/api/types'

function montar(revisionPaso: RevisionPaso | null, props: Partial<ComponentProps<typeof PanelCopiloto>> = {}) {
  render(
    <PanelCopiloto
      prefs={{ avatarId: 'a', avatarName: 'Copiloto' }}
      contrato={{ id: 1, numeroContrato: 'CO1.PCCNTR.1' } as ContratoResponse}
      chatMsgs={[]}
      pensando={false}
      tutorialMode={false}
      revisionPaso={revisionPaso}
      activeStep={undefined}
      chatInput=""
      chatEndRef={createRef<HTMLDivElement>()}
      sugerencias={[]}
      onCerrar={vi.fn()}
      onCambiarEntrada={vi.fn()}
      onEnviar={vi.fn()}
      onSugerencia={vi.fn()}
      onIniciarPaso={vi.fn()}
      onConfirmarRevision={vi.fn()}
      onCancelarRevision={vi.fn()}
      onAccion={vi.fn()}
      {...props}
    />,
  )
}

/**
 * Confirmar el último sub-paso de un paso con documento también firma ese
 * documento con la firma electrónica del supervisor. Hasta el 24-09-2026 el
 * botón solo decía «Confirmar Paso N como completado»: el supervisor firmaba
 * un acta sin que nada se lo dijera.
 */
describe('PanelCopiloto — confirmar un paso', () => {
  it('dice que se firma el documento cuando el sub-paso lo lleva', () => {
    montar({ stepId: 2, subStepId: '2.7', listaParaConfirmar: true, documento: 'Acta de Inicio' })

    expect(screen.getByRole('button', { name: 'Confirmar Paso 2 y firmar Acta de Inicio' })).toBeInTheDocument()
  })

  it('sin documento, solo confirma el paso', () => {
    montar({ stepId: 1, subStepId: '1.6', listaParaConfirmar: true })

    expect(screen.getByRole('button', { name: 'Confirmar Paso 1 como completado' })).toBeInTheDocument()
  })

  /**
   * Confirmar y cancelar solo aparecían cuando el modelo ya había revisado:
   * para salir de la revisión había que describir algo y esperar minutos.
   */
  it('antes de describir nada ya se puede cancelar o confirmar sin revisión', () => {
    montar({ stepId: 1, subStepId: '1.6', listaParaConfirmar: false })

    expect(screen.getByRole('button', { name: /cancelar, quiero revisar algo antes/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirmar Paso 1 como completado sin revisión' })).toBeInTheDocument()
  })

  it('mientras el Copiloto revisa, confirmar dice que no se espera la revisión', () => {
    montar(
      { stepId: 2, subStepId: '2.7', listaParaConfirmar: false, documento: 'Acta de Inicio' },
      { pensando: true, revisando: true },
    )

    expect(
      screen.getByRole('button', { name: 'Confirmar Paso 2 y firmar Acta de Inicio sin esperar la revisión' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /cancelar, quiero revisar algo antes/i })).toBeInTheDocument()
  })

  /** Con una pregunta suelta en curso, la espera es la de esa pregunta: no se ofrece confirmar. */
  it('con una pregunta suelta en curso solo deja cancelar', () => {
    montar({ stepId: 1, subStepId: '1.6', listaParaConfirmar: false }, { pensando: true })

    expect(screen.queryByRole('button', { name: /confirmar paso 1/i })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /cancelar, quiero revisar algo antes/i })).toBeInTheDocument()
  })

  it('el campo y el botón de enviar tienen nombre para un lector de pantalla', () => {
    montar({ stepId: 1, subStepId: '1.6', listaParaConfirmar: true })

    expect(screen.getByRole('textbox', { name: 'Mensaje para el Copiloto' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Enviar al Copiloto' })).toBeInTheDocument()
  })
})

describe('PanelCopiloto — el campo de texto', () => {
  /**
   * Decía «Escriba una orden o pregunta a la IA», y no había órdenes: el
   * supervisor escribía «firma el acta» y el modelo podía contestar que ya lo
   * había hecho. Ahora invita a lo que se atiende de verdad: preguntas y las
   * órdenes de ir a una pantalla, que el servidor resuelve sin el modelo.
   */
  it('invita a preguntar o a pedir ir a un paso', () => {
    montar(null)

    expect(screen.getByRole('textbox', { name: 'Mensaje para el Copiloto' })).toHaveAttribute(
      'placeholder',
      'Pregunte, o pida «llévame al paso 3»…',
    )
  })
})

describe('PanelCopiloto — lo que ofrece una respuesta', () => {
  const abrirActa: AccionCopiloto = {
    tipo: 'ABRIR_DOCUMENTO',
    paso: 2,
    subpaso: '2.7',
    documentoTipo: 'ACTA_INICIO',
    documentoId: null,
    etiqueta: 'Abrir el sub-paso 2.7 · Acta de Inicio',
  }

  /**
   * El Copiloto no puede abrir ni firmar nada por su cuenta: la respuesta trae
   * a dónde llevar, y es el supervisor quien decide ir pulsando el botón.
   */
  it('pinta la acción como un botón con su etiqueta y la pasa solo al pulsarlo', () => {
    const onAccion = vi.fn()
    montar(null, {
      onAccion,
      chatMsgs: [{ role: 'ai', text: 'El Acta de Inicio se firma en el 2.7.', origen: 'sistema', accion: abrirActa }],
    })

    expect(onAccion).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: /abrir el sub-paso 2\.7 · acta de inicio/i }))
    expect(onAccion).toHaveBeenCalledExactlyOnceWith(abrirActa)
  })

  /** Una ficha armada con el catálogo no es una respuesta de la IA, y no se presenta como tal. */
  it('marca las respuestas del sistema, y solo esas', () => {
    montar(null, {
      chatMsgs: [
        { role: 'ai', text: 'Acta de Inicio (GCCON-F-018)…', origen: 'sistema' },
        { role: 'ai', text: 'Le recomiendo revisar la póliza.', origen: 'modelo' },
      ],
    })

    expect(screen.getAllByText('Respuesta del sistema')).toHaveLength(1)
  })
})
