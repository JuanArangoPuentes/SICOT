import { render, screen } from '@testing-library/react'
import { createRef } from 'react'
import { describe, expect, it, vi } from 'vitest'
import PanelCopiloto, { type RevisionPaso } from './PanelCopiloto'
import type { ContratoResponse } from '@/services/api/types'

/**
 * Confirmar el último sub-paso de un paso con documento también firma ese
 * documento con la firma electrónica del supervisor. Hasta el 24-09-2026 el
 * botón solo decía «Confirmar Paso N como completado»: el supervisor firmaba
 * un acta sin que nada se lo dijera.
 */
function montar(revisionPaso: RevisionPaso) {
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
    />,
  )
}

describe('PanelCopiloto — confirmar un paso', () => {
  it('dice que se firma el documento cuando el sub-paso lo lleva', () => {
    montar({ stepId: 2, subStepId: '2.7', listaParaConfirmar: true, documento: 'Acta de Inicio' })

    expect(screen.getByRole('button', { name: 'Confirmar Paso 2 y firmar Acta de Inicio' })).toBeInTheDocument()
  })

  it('sin documento, solo confirma el paso', () => {
    montar({ stepId: 1, subStepId: '1.6', listaParaConfirmar: true })

    expect(screen.getByRole('button', { name: 'Confirmar Paso 1 como completado' })).toBeInTheDocument()
  })

  it('el campo y el botón de enviar tienen nombre para un lector de pantalla', () => {
    montar({ stepId: 1, subStepId: '1.6', listaParaConfirmar: true })

    expect(screen.getByRole('textbox', { name: 'Mensaje para el Copiloto' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Enviar al Copiloto' })).toBeInTheDocument()
  })
})
