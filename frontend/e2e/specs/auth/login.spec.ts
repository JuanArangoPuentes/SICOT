import { test, expect } from '@playwright/test'
import { CUENTAS_DEV, loginComo } from '../../fixtures/auth'

// Marcador visible que confirma cuál panel cargó — App.tsx no usa rutas
// (elige el panel por el rol de la sesión), así que la aserción es sobre
// contenido, no sobre la URL. Ver TopBar badge en cada panel.
const MARCADOR_POR_ROL = {
  ADMINISTRADOR: 'Interfaz de Administrador',
  GESTION: 'Panel Gestión',
  SUPERVISOR: 'Panel Supervisor',
} as const

for (const rol of Object.keys(CUENTAS_DEV) as (keyof typeof CUENTAS_DEV)[]) {
  test(`login como ${rol} muestra el panel correcto`, async ({ page }) => {
    await loginComo(page, rol)
    await expect(page.getByText(MARCADOR_POR_ROL[rol])).toBeVisible({ timeout: 10_000 })
  })
}

test('contraseña incorrecta muestra el error y no navega', async ({ page }) => {
  await page.goto('/')
  await page.getByPlaceholder('correo@soy.sena.edu.co').fill(CUENTAS_DEV.GESTION.email)
  await page.getByPlaceholder('••••••••••').fill('claveIncorrecta')
  await page.getByRole('button', { name: /Ingresar/ }).click()

  // El backend devuelve exactamente este mensaje (ver
  // AuthIntegrationTest.loginConContrasenaIncorrectaDevuelveError) — probarlo
  // end-to-end detecta tanto una regresión del backend como del mapeo de
  // errores del frontend.
  await expect(page.getByText('Credenciales inválidas.')).toBeVisible()
  await expect(page.getByPlaceholder('correo@soy.sena.edu.co')).toBeVisible()
})
