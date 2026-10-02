import { afterEach, describe, expect, it, vi } from 'vitest'

const save = vi.fn()
const writeFile = vi.fn()
vi.mock('@tauri-apps/plugin-dialog', () => ({ save }))
vi.mock('@tauri-apps/plugin-fs', () => ({ writeFile }))

import { abrirArchivo, ErrorAlGuardar, guardarArchivo } from './guardarArchivo'

// jsdom no implementa dos cosas que el WebView de Android (Chrome 124) sí tiene:
// `Blob.prototype.arrayBuffer` y `URL.createObjectURL`. Se completan aquí, en la
// prueba, para no meter en el código de producción caminos que solo existirían
// para contentar al entorno de pruebas.
if (!Blob.prototype.arrayBuffer) {
  Blob.prototype.arrayBuffer = function (this: Blob) {
    return new Promise<ArrayBuffer>((resolver) => {
      const lector = new FileReader()
      lector.onload = () => resolver(lector.result as ArrayBuffer)
      lector.readAsArrayBuffer(this)
    })
  }
}
if (!('createObjectURL' in URL)) {
  Object.assign(URL, { createObjectURL: () => '', revokeObjectURL: () => {} })
}

const UA_ANDROID =
  'Mozilla/5.0 (Linux; Android 15; Pixel 7; wv) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36'

function simularApk() {
  ;(window as unknown as Record<string, unknown>).__TAURI_INTERNALS__ = {}
  vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue(UA_ANDROID)
}

afterEach(() => {
  delete (window as unknown as Record<string, unknown>).__TAURI_INTERNALS__
  vi.restoreAllMocks()
  save.mockReset()
  writeFile.mockReset()
})

describe('guardarArchivo', () => {
  it('en el APK abre el «Guardar como» del sistema y escribe los bytes donde el usuario elige', async () => {
    simularApk()
    save.mockResolvedValue('content://com.android.providers.downloads.documents/document/42')
    const pdf = new Blob([new Uint8Array([0x25, 0x50, 0x44, 0x46])], { type: 'application/pdf' })

    const resultado = await guardarArchivo(pdf, 'Acta de Inicio.pdf')

    expect(resultado).toBe('guardado')
    expect(save).toHaveBeenCalledWith({
      defaultPath: 'Acta de Inicio.pdf',
      filters: [{ name: 'Documento PDF', extensions: ['pdf'] }],
    })
    const [destino, bytes] = writeFile.mock.calls[0]
    expect(destino).toBe('content://com.android.providers.downloads.documents/document/42')
    expect(Array.from(bytes as Uint8Array)).toEqual([0x25, 0x50, 0x44, 0x46])
  })

  it('si el usuario cierra el diálogo no escribe nada y no lo trata como error', async () => {
    simularApk()
    save.mockResolvedValue(null)

    await expect(guardarArchivo(new Blob(['x']), 'registros.csv')).resolves.toBe('cancelado')
    expect(writeFile).not.toHaveBeenCalled()
  })

  // Auditoría del 02-10-2026: los plugins rechazan con un texto, y el panel lo
  // tomaba por un fallo pasajero y pedía «intente de nuevo en un momento».
  it('si el teléfono no deja escribir, lo dice con el motivo del sistema', async () => {
    simularApk()
    save.mockResolvedValue('content://descargas/42')
    writeFile.mockRejectedValue('No queda espacio en el dispositivo')

    const intento = guardarArchivo(new Blob(['x']), 'acta.pdf')

    await expect(intento).rejects.toBeInstanceOf(ErrorAlGuardar)
    await expect(intento).rejects.toThrow('No queda espacio en el dispositivo')
  })

  it('en el navegador conserva el gestor de descargas y no toca los plugins', async () => {
    // Sin la marca de Tauri: es la aplicación web, donde el patrón de siempre
    // funciona. Cambiarlo aquí sería añadir riesgo sin ningún beneficio.
    const crear = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:prueba')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    const clic = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    await expect(guardarArchivo(new Blob(['x']), 'formato.docx')).resolves.toBe('navegador')
    expect(crear).toHaveBeenCalled()
    expect(clic).toHaveBeenCalled()
    expect(save).not.toHaveBeenCalled()
  })

  it('en el instalador de escritorio también conserva el patrón de navegador', async () => {
    // El WebView de escritorio sí descarga por sí mismo.
    ;(window as unknown as Record<string, unknown>).__TAURI_INTERNALS__ = {}
    vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue(
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36',
    )
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:prueba')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    await expect(guardarArchivo(new Blob(['x']), 'acta.pdf')).resolves.toBe('navegador')
    expect(save).not.toHaveBeenCalled()
  })
})

describe('abrirArchivo', () => {
  it('en el navegador abre el borrador en otra pestaña en vez de descargarlo', async () => {
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:borrador')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    const abrir = vi.spyOn(window, 'open').mockReturnValue({} as Window)
    const clic = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    await expect(abrirArchivo(new Blob(['%PDF']), 'BORRADOR Acta.pdf')).resolves.toBe('abierto')
    expect(abrir).toHaveBeenCalledWith('blob:borrador', '_blank')
    expect(clic).not.toHaveBeenCalled()
  })

  it('si el navegador bloquea la pestaña, lo descarga', async () => {
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:borrador')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    vi.spyOn(window, 'open').mockReturnValue(null)
    const clic = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    await expect(abrirArchivo(new Blob(['%PDF']), 'BORRADOR Acta.pdf')).resolves.toBe('navegador')
    expect(clic).toHaveBeenCalled()
  })

  it('en el APK lo guarda con el «Guardar como» del sistema', async () => {
    simularApk()
    save.mockResolvedValue('content://descargas/43')
    const abrir = vi.spyOn(window, 'open')

    await expect(abrirArchivo(new Blob(['%PDF']), 'BORRADOR Acta.pdf')).resolves.toBe('guardado')
    expect(abrir).not.toHaveBeenCalled()
  })
})
