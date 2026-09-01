import type { Page } from "@playwright/test"

// Credenciales de las cuentas de perfil "dev" sembradas por DataInitializer
// (backend/src/main/java/co/sena/sicot/config/DataInitializer.java) — las
// mismas que usan los tests de integración del backend (AuthIntegrationTest,
// ContratoIntegrationTest, etc.), para que E2E y backend compartan una sola
// fuente de datos de prueba en vez de inventar una segunda.
export const CUENTAS_DEV = {
  ADMINISTRADOR: {
    email: "administrador@soy.sena.edu.co",
    password: "Admin123*",
  },
  GESTION: { email: "gestion@soy.sena.edu.co", password: "Gestion123*" },
  SUPERVISOR: {
    email: "supervisor@soy.sena.edu.co",
    password: "Supervisor123*",
  },
} as const

export type Rol = keyof typeof CUENTAS_DEV

export async function loginComo(page: Page, rol: Rol) {
  const { email, password } = CUENTAS_DEV[rol]
  await page.goto("/")
  await page.getByPlaceholder("correo@soy.sena.edu.co").fill(email)
  await page.getByPlaceholder("••••••••••").fill(password)
  await page.getByRole("button", { name: /Ingresar/ }).click()
}
