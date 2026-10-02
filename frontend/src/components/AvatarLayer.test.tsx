import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import AvatarLayer from './AvatarLayer'
import { DEFAULT_PREFS, PrefsProvider, type ModoAvatar } from '@/prefs'

function montar(modo: ModoAvatar, onOpenChat?: () => void) {
  localStorage.setItem('sicot.prefs', JSON.stringify({ ...DEFAULT_PREFS, avatarMode: modo }))
  render(
    <PrefsProvider>
      <AvatarLayer tour={[]} tourActive={false} onTourEnd={vi.fn()} onOpenChat={onOpenChat} />
    </PrefsProvider>,
  )
}

// Hasta el 02-10-2026 el avatar flotante decía «abrir chat» y al pulsarlo solo
// mostraba un pulgar arriba, también en Gestión y Administración, donde no hay
// ningún chat.
describe('AvatarLayer', () => {
  it('en modo Follower, pulsar el avatar abre el Copiloto', () => {
    const onOpenChat = vi.fn()
    montar('follower', onOpenChat)

    fireEvent.click(screen.getByRole('button', { name: /abrir el copiloto/i }))

    expect(onOpenChat).toHaveBeenCalledTimes(1)
  })

  it('sin un Copiloto que abrir no pinta el avatar flotante', () => {
    montar('follower')

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('en modo Ghost no pinta el avatar flotante', () => {
    montar('ghost', vi.fn())

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })
})
