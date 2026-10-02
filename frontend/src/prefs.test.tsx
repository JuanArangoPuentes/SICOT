import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { DEFAULT_PREFS, PrefsProvider, THEME_VERSION, usePrefs } from './prefs'

function Mostrar() {
  const { prefs } = usePrefs()
  return <pre data-testid="prefs">{JSON.stringify(prefs)}</pre>
}

function cargar(guardado: Record<string, unknown>) {
  localStorage.setItem('sicot.prefs', JSON.stringify(guardado))
  render(
    <PrefsProvider>
      <Mostrar />
    </PrefsProvider>,
  )
  return {
    enPantalla: JSON.parse(screen.getByTestId('prefs').textContent ?? '{}') as Record<string, unknown>,
    guardado: JSON.parse(localStorage.getItem('sicot.prefs') ?? '{}') as Record<string, unknown>,
  }
}

// Configuración ofrecía sonido, posición y duración de avisos, parpadeo, efectos
// hover, tono del copiloto y un avatar personalizado. Nada de eso hacía nada y
// se quitó el 02-10-2026; quien ya lo tenía guardado no debe arrastrarlo.
describe('preferencias guardadas', () => {
  it('descarta las preferencias que ya no existen al cargarlas', () => {
    const { enPantalla, guardado } = cargar({
      ...DEFAULT_PREFS,
      sound: true,
      alertPosition: 'bottom-right',
      alertDurationS: 10,
      blinkAlerts: false,
      hoverEffects: false,
      avatarTone: 'tecnico',
    })

    for (const retirada of ['sound', 'alertPosition', 'alertDurationS', 'blinkAlerts', 'hoverEffects', 'avatarTone']) {
      expect(enPantalla).not.toHaveProperty(retirada)
      expect(guardado).not.toHaveProperty(retirada)
    }
  })

  it('conserva lo vigente y cae al avatar por defecto si el guardado ya no se ofrece', () => {
    const { enPantalla } = cargar({
      ...DEFAULT_PREFS,
      themeVersion: THEME_VERSION,
      fontSize: 17,
      avatarName: 'Mi asistente',
      avatarId: 'custom',
    })

    expect(enPantalla.fontSize).toBe(17)
    expect(enPantalla.avatarName).toBe('Mi asistente')
    expect(enPantalla.avatarId).toBe(DEFAULT_PREFS.avatarId)
  })
})
