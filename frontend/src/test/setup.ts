// Configuración común de las pruebas de Vitest.
//
// `jest-dom` añade aserciones que describen intención en vez de estructura
// (`toBeInTheDocument`, `toBeDisabled`, `toHaveAccessibleName`), lo que hace
// que una prueba fallida diga qué dejó de funcionar para el usuario y no qué
// nodo del DOM cambió.

import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, beforeEach, vi } from 'vitest'

afterEach(() => {
  // Desmonta lo montado en la prueba anterior. Sin esto, dos pruebas que
  // busquen el mismo texto encuentran dos coincidencias y fallan por un motivo
  // que no tiene nada que ver con lo que estaban comprobando.
  cleanup()
  vi.restoreAllMocks()
})

beforeEach(() => {
  // Cada prueba arranca sin sesión guardada: el cliente HTTP y session.ts leen
  // localStorage, y una sesión heredada de otra prueba cambiaría el resultado.
  localStorage.clear()
})

// jsdom no implementa scrollIntoView: no tiene disposición ni ventana real que
// desplazar, así que el método sencillamente no existe en sus elementos.
//
// Se define aquí, y no en la prueba que se tropezó con él, porque no es una
// particularidad de esa prueba: cualquier vista que lleve el copiloto —o
// cualquier otra lista que se autodesplace— revienta igual en cuanto se monte.
// El panel del Supervisor lo usa para mantener a la vista el último mensaje del
// chat. Es una carencia del entorno de pruebas, no del componente.
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = function scrollIntoView() {}
}

// jsdom tampoco implementa matchMedia, por el mismo motivo: no tiene una
// ventana real cuyo ancho consultar. El armazón lo usa para saber si está en un
// teléfono, porque el rótulo de cada entrada de navegación no se oculta con CSS
// sino que no se renderiza, y esa decisión hay que tomarla en JavaScript.
//
// Se declara "no coincide": las pruebas de componente describen el
// comportamiento de escritorio, que es el que todas ellas afirman. El
// comportamiento en pantalla estrecha se comprueba en las pruebas de extremo a
// extremo con un viewport de teléfono de verdad, que es donde tiene sentido —
// una media query simulada no prueba que algo quepa en la pantalla.
if (!window.matchMedia) {
  window.matchMedia = (query: string): MediaQueryList =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false,
    }) as MediaQueryList
}
