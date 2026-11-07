import { test, expect } from '@playwright/test'
import { loginComo } from '../../fixtures/auth'

/**
 * El panel del Administrador en un portátil corriente.
 *
 * Las tablas de Usuarios y de Firmas necesitan unos 920 y 1050 px y su tarjeta
 * recorta lo que se sale: a 1024 px, con la barra lateral abierta, «Asignar
 * firma», «Resetear clave» y «Desactivar» quedaban fuera, sin desplazamiento
 * (06-11-2026). Gestionar a los supervisores es lo único que el Administrador
 * hace aparte de mirar su avance, así que esto no puede volver a pasar callado.
 */
test('a 1024 px las acciones sobre cada cuenta quedan dentro de la tarjeta', async ({ page }) => {
  await page.setViewportSize({ width: 1024, height: 768 })
  await loginComo(page, 'ADMINISTRADOR')
  // Por la barra lateral y no con goto: navegar antes de que el acceso termine
  // de guardar la sesión devuelve a la pantalla de acceso.
  const usuarios = page.locator('[data-tour="nav-usuarios"]')
  await expect(usuarios).toBeVisible({ timeout: 15_000 })
  await usuarios.click()
  const desactivar = page.getByRole('button', { name: /^(Desactivar|Reactivar)$/ }).first()
  await expect(desactivar).toBeVisible({ timeout: 15_000 })

  const tarjeta = await page.locator('.tabla-usuarios').boundingBox()
  const borde = (tarjeta?.x ?? 0) + (tarjeta?.width ?? 0)
  for (const boton of await page.locator('.tabla-usuarios button').all()) {
    const caja = await boton.boundingBox()
    expect((caja?.x ?? 0) + (caja?.width ?? 0), await boton.innerText()).toBeLessThanOrEqual(borde)
  }
})
