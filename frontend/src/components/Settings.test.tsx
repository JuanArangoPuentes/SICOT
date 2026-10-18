import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import Settings from './Settings'
import { PrefsProvider } from '@/prefs'

function abrir(seccion: 'manual' | 'copiloto' | 'servidor') {
  render(
    <PrefsProvider>
      <Settings open onClose={vi.fn()} initialSection={seccion} />
    </PrefsProvider>,
  )
}

function simularApkAndroid() {
  ;(window as unknown as Record<string, unknown>).__TAURI_INTERNALS__ = {}
  vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue(
    'Mozilla/5.0 (Linux; Android 15; Pixel 7) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36',
  )
}

afterEach(() => {
  delete (window as unknown as Record<string, unknown>).__TAURI_INTERNALS__
})

// Todo control de Configuración tiene que hacer lo que dice. Hasta el
// 02-10-2026 había una sección de notificaciones, parpadeo, efectos hover, tono
// del copiloto, un avatar personalizado y un modo «Guide» que no movían nada.
describe('Configuración no ofrece controles sin función', () => {
  it('personalización manual: sin notificaciones, parpadeo ni efectos hover', () => {
    abrir('manual')

    for (const texto of [/notificaciones/i, /sonido/i, /duración de alertas/i, /parpadeo/i, /efectos hover/i]) {
      expect(screen.queryByText(texto)).not.toBeInTheDocument()
    }
  })

  it('copiloto: sin tono, sin avatar personalizado y sin modo Guide', () => {
    abrir('copiloto')

    expect(screen.queryByText(/tono de comunicación/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/avatar personalizado/i)).not.toBeInTheDocument()
    expect(screen.queryByText('Guide')).not.toBeInTheDocument()
    expect(screen.getByText(/al pulsarlo abre el copiloto/i)).toBeInTheDocument()
  })
})

describe('Configuración · servidor', () => {
  // --alert-alta no existe: la declaración `border` quedaba inválida y el aviso
  // se veía como un párrafo gris más.
  it('el aviso de tráfico sin cifrar lleva el borde de alerta', () => {
    simularApkAndroid()
    abrir('servidor')

    fireEvent.change(screen.getByLabelText(/dirección del servidor/i), {
      target: { value: 'http://192.168.1.50:8080' },
    })

    expect(screen.getByRole('status').getAttribute('style')).toContain('var(--alert-leve)')
  })

  it('en el APK no dice que vacío sea «el mismo origen»', () => {
    simularApkAndroid()
    abrir('servidor')

    expect(screen.queryByText(/déjelo vacío para usar el mismo origen/i)).not.toBeInTheDocument()
    expect(screen.getByText(/propio teléfono/i)).toBeInTheDocument()
  })

  it('el ejemplo de dirección es https://, el único que el APK deja salir', () => {
    abrir('servidor')

    expect(screen.getByLabelText(/dirección del servidor/i)).toHaveAttribute(
      'placeholder',
      expect.stringMatching(/^https:\/\//),
    )
  })
})
