// Configuración común de las pruebas de Vitest.
//
// `jest-dom` añade aserciones que describen intención en vez de estructura
// (`toBeInTheDocument`, `toBeDisabled`, `toHaveAccessibleName`), lo que hace
// que una prueba fallida diga qué dejó de funcionar para el usuario y no qué
// nodo del DOM cambió.

import "@testing-library/jest-dom/vitest"
import { cleanup } from "@testing-library/react"
import { afterEach, beforeEach, vi } from "vitest"

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
