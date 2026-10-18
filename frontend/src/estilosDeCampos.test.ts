import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

// Ningún campo de texto puede quedarse sin el estilo del tema oscuro.
//
// POR QUÉ EXISTE ESTA PRUEBA. La regla de `index.css` enumeraba los tipos que
// SÍ se pintan —text, email, password, date, number—, así que cualquier campo
// con otro tipo se quedaba con el estilo del navegador: caja blanca y texto
// casi del mismo color encima. Ocurría en dos sitios, y el peor era el campo
// `type="url"` de la dirección del servidor en Configuración: es lo primero
// que escribe un supervisor al instalar el APK, y no se leía lo escrito. No lo
// vio ninguna prueba porque jsdom no aplica hojas de estilo y las pruebas de
// extremo a extremo no miran el color de un campo; se encontró a ojo en el
// emulador el 07-10-2026.
//
// La regla ahora enumera las EXCEPCIONES, que es lo que esta prueba sostiene:
// un campo nuevo nace con el estilo correcto, y dejar uno fuera obliga a
// escribirlo aquí. No comprueba cómo se ve —eso no se puede desde jsdom—, sino
// que el selector alcanza a todos los tipos que el código usa de verdad.

const RAIZ = join(__dirname)

/** Tipos con interfaz propia del sistema: pintarlos los rompería. */
const CON_INTERFAZ_PROPIA = ['file', 'range', 'color', 'checkbox', 'radio', 'submit', 'reset', 'button', 'image']

function archivosFuente(dir: string): string[] {
  return readdirSync(dir).flatMap((entrada) => {
    const ruta = join(dir, entrada)
    if (statSync(ruta).isDirectory()) return archivosFuente(ruta)
    return /\.tsx?$/.test(entrada) && !/\.test\.tsx?$/.test(entrada) ? [ruta] : []
  })
}

/** El selector de la regla que pinta los campos, tal como está escrito hoy. */
function selectorDeCampos(): string {
  const css = readFileSync(join(RAIZ, 'index.css'), 'utf8')
  const regla = css.match(/\n(input:not\(\[type[^{]*)\{\s*\n\s*background: var\(--bg-input\)/)
  expect(regla, 'no se encontró en index.css la regla que pinta los campos de texto').not.toBeNull()
  // oxfmt parte el selector en varias líneas cuando es largo.
  return regla![1].replace(/\s+/g, '')
}

/** Los `type` que el código usa de verdad en sus `<input>`. */
function tiposUsados(): string[] {
  const tipos = new Set<string>()
  for (const ruta of archivosFuente(RAIZ)) {
    const fuente = readFileSync(ruta, 'utf8')
    for (const etiqueta of fuente.matchAll(/<input\b[^>]*/g)) {
      const tipo = etiqueta[0].match(/type=["']([a-z-]+)["']/)
      // Un `<input>` sin `type` es de texto para el navegador, pero
      // `input[type='text']` no lo alcanza: cuenta como caso a cubrir.
      tipos.add(tipo ? tipo[1] : 'sin-type')
    }
  }
  return [...tipos]
}

describe('estilos de los campos de formulario', () => {
  const selector = selectorDeCampos()

  it('alcanza a todos los tipos de campo que usa la aplicación', () => {
    const sinEstilo = tiposUsados()
      .filter((tipo) => !CON_INTERFAZ_PROPIA.includes(tipo))
      .filter((tipo) => {
        const campo = document.createElement('input')
        if (tipo !== 'sin-type') campo.setAttribute('type', tipo)
        return !campo.matches(selector)
      })
    expect(
      sinEstilo,
      `estos tipos de <input> quedarían con el estilo claro del navegador: ${sinEstilo.join(', ')}`,
    ).toEqual([])
  })

  it('deja fuera los campos con interfaz propia del sistema', () => {
    for (const tipo of CON_INTERFAZ_PROPIA) {
      const campo = document.createElement('input')
      campo.setAttribute('type', tipo)
      expect(campo.matches(selector), `type="${tipo}" no debería recibir el estilo de los campos de texto`).toBe(false)
    }
  })
})
