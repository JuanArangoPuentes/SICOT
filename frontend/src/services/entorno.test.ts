import { afterEach, describe, expect, it, vi } from 'vitest'
import { avisoTraficoSinCifrar, enAndroid, enAplicacionEmpaquetada } from './entorno'

/** Simula estar dentro del APK: marca de Tauri + agente de usuario de Android. */
function simularApkAndroid() {
  ;(window as unknown as Record<string, unknown>).__TAURI_INTERNALS__ = {}
  vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue(
    'Mozilla/5.0 (Linux; Android 15; Pixel 7) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36',
  )
}

afterEach(() => {
  delete (window as unknown as Record<string, unknown>).__TAURI_INTERNALS__
  vi.restoreAllMocks()
})

describe('avisoTraficoSinCifrar', () => {
  it('avisa cuando el APK de Android apunta a una dirección http://', () => {
    simularApkAndroid()
    expect(avisoTraficoSinCifrar('http://192.168.1.50:8080')).toContain('Android bloquea')
  })

  it('no avisa si la dirección ya es https://', () => {
    simularApkAndroid()
    expect(avisoTraficoSinCifrar('https://sicot.centro.sena.edu.co')).toBeNull()
  })

  it('no avisa en el navegador, donde http:// funciona', () => {
    // Sin la marca de Tauri: es la aplicación web, y ahí no hay nada que Android
    // bloquee. Avisar aquí sería alarmar a quien no tiene el problema.
    vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue(
      'Mozilla/5.0 (Linux; Android 15; Pixel 7) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36',
    )
    expect(avisoTraficoSinCifrar('http://192.168.1.50:8080')).toBeNull()
  })

  it('no avisa en el instalador de escritorio, donde http:// tampoco se bloquea', () => {
    ;(window as unknown as Record<string, unknown>).__TAURI_INTERNALS__ = {}
    vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue(
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36',
    )
    expect(avisoTraficoSinCifrar('http://192.168.1.50:8080')).toBeNull()
  })

  it('no avisa con la dirección vacía, que significa «el mismo origen»', () => {
    simularApkAndroid()
    expect(avisoTraficoSinCifrar('')).toBeNull()
  })

  it('reconoce el entorno empaquetado y el sistema operativo por separado', () => {
    expect(enAplicacionEmpaquetada()).toBe(false)
    simularApkAndroid()
    expect(enAplicacionEmpaquetada()).toBe(true)
    expect(enAndroid()).toBe(true)
  })
})
