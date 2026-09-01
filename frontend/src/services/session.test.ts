import { beforeEach, describe, expect, it } from "vitest"
import { clearSession, getSession, saveSession } from "./session"
import type { AuthResponse } from "./api/types"

/**
 * La sesión vive en localStorage y decide si la persona entra o vuelve al
 * login. Un fallo aquí no rompe una pantalla: expulsa a todo el mundo, o deja
 * dentro a quien ya no debería estar.
 */
describe("session", () => {
  beforeEach(() => {
    localStorage.clear()
  })

  /** Construye un JWT con la fecha de expiración pedida. Solo el payload importa. */
  function tokenQueExpiraEn(segundosDesdeAhora: number): string {
    const exp = Math.floor(Date.now() / 1000) + segundosDesdeAhora
    const payload = btoa(
      JSON.stringify({ sub: "supervisor@soy.sena.edu.co", exp }),
    )
      .replace(/\+/g, "-")
      .replace(/\//g, "_")
    return `cabecera.${payload}.firma`
  }

  function sesion(token: string): AuthResponse {
    return {
      token,
      usuarioId: 3,
      nombre: "Alex Fernando Zapata",
      email: "supervisor@soy.sena.edu.co",
      rol: "SUPERVISOR",
    }
  }

  it("guarda y recupera una sesión vigente", () => {
    saveSession(sesion(tokenQueExpiraEn(3600)))

    expect(getSession()?.email).toBe("supervisor@soy.sena.edu.co")
  })

  /**
   * Sin esto, al recargar con un token vencido la aplicación pintaba el panel,
   * lanzaba peticiones que morían en 401 y solo entonces devolvía al login.
   */
  it("descarta una sesión con el token ya vencido", () => {
    saveSession(sesion(tokenQueExpiraEn(-60)))

    expect(getSession()).toBeNull()
    expect(localStorage.getItem("sicot.session")).toBeNull()
  })

  it("no hay sesión cuando no se ha guardado nada", () => {
    expect(getSession()).toBeNull()
  })

  it("un contenido corrupto en localStorage no rompe el arranque", () => {
    // Puede pasar por una versión anterior del formato o por edición manual.
    // Debe comportarse como "no hay sesión", nunca lanzar durante el render.
    localStorage.setItem("sicot.session", "no-es-json{{{")

    expect(() => getSession()).not.toThrow()
    expect(getSession()).toBeNull()
  })

  it("un token sin fecha de expiración se acepta y lo valida el backend", () => {
    const payload = btoa(JSON.stringify({ sub: "x@y.co" }))
    saveSession(sesion(`cabecera.${payload}.firma`))

    // La comprobación local es solo una optimización de experiencia de uso; la
    // autoridad sobre la validez del token siempre es el backend.
    expect(getSession()).not.toBeNull()
  })

  it("cerrar sesión la borra de localStorage", () => {
    saveSession(sesion(tokenQueExpiraEn(3600)))

    clearSession()

    expect(getSession()).toBeNull()
  })
})
