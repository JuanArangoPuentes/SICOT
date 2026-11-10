import { describe, expect, it } from 'vitest'
import { esMasNueva } from './autoactualizacion'

// La comparación de versiones es lo único de este módulo que se puede probar
// sin un WebView de Tauri, y es también donde está el error que de verdad
// cuesta: si se compara como texto, «0.10.0» es menor que «0.9.0» y el aviso
// de actualización deja de salir justo cuando el proyecto pasa de la 0.9.
describe('esMasNueva', () => {
  it('reconoce una versión posterior', () => {
    expect(esMasNueva('0.9.1', '0.9.0')).toBe(true)
    expect(esMasNueva('0.10.0', '0.9.0')).toBe(true)
    expect(esMasNueva('1.0.0', '0.9.9')).toBe(true)
  })

  it('no avisa cuando ya está al día o es anterior', () => {
    expect(esMasNueva('0.9.0', '0.9.0')).toBe(false)
    expect(esMasNueva('0.8.0', '0.9.0')).toBe(false)
    expect(esMasNueva('0.9.0', '0.10.0')).toBe(false)
  })

  it('admite la «v» de la etiqueta de GitHub', () => {
    expect(esMasNueva('v0.9.1', '0.9.0')).toBe(true)
    expect(esMasNueva('v0.9.0', 'v0.9.0')).toBe(false)
  })

  it('completa con ceros lo que no está escrito', () => {
    expect(esMasNueva('0.9', '0.9.0')).toBe(false)
    expect(esMasNueva('0.9.1', '0.9')).toBe(true)
    expect(esMasNueva('1', '0.9.9')).toBe(true)
  })

  it('no avisa cuando alguna versión no se entiende', () => {
    // Un descriptor servido a medias, o un `latest.json` de otra cosa: callar
    // es lo correcto. Mandar a descargar algo que no hace falta gasta el
    // tiempo de un supervisor y la credibilidad del aviso.
    expect(esMasNueva('', '0.9.0')).toBe(false)
    expect(esMasNueva('0.9.1-beta', '0.9.0')).toBe(false)
    expect(esMasNueva('última', '0.9.0')).toBe(false)
    expect(esMasNueva('0.9.1', 'desconocida')).toBe(false)
  })
})
