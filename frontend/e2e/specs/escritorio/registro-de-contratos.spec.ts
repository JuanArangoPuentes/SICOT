import { test, expect } from '@playwright/test'
import { loginComo } from '../../fixtures/auth'

/**
 * El registro de contratos de Gestión en un portátil corriente.
 *
 * Las columnas eran cinco anchos fijos (820 px) y el objeto se quedaba con lo
 * que sobrara: a 1280 px de ventana el contenedor mide unos 976 px y al objeto
 * le quedaban 63 px, una palabra por línea (prueba integral del 24-09-2026).
 * La suite móvil no lo veía —en un teléfono la tabla se vuelve tarjetas— y la
 * de escritorio no medía esta pantalla.
 */
/**
 * Entre 768 y unos 1250 px la tabla no cabía en su tarjeta, que recorta, y la
 * columna ACCIONES quedaba fuera sin desplazamiento posible: a 1024 px, con la
 * barra lateral abierta, Gestión no podía asignar el supervisor ni activar el
 * contrato (recorrido del 06-11-2026). Ahí la tabla pasa a tarjetas.
 */
test('a 1024 px las acciones de cada contrato quedan a la vista', async ({ page }) => {
  await page.setViewportSize({ width: 1024, height: 768 })
  await loginComo(page, 'GESTION')
  const asignar = page.getByTitle(/Asignar o cambiar el supervisor de/).first()
  await expect(asignar).toBeVisible({ timeout: 15_000 })

  const boton = await asignar.boundingBox()
  const tarjeta = await page.locator('[data-tour="tabla"]').boundingBox()
  // Dentro de la tarjeta (que recorta lo que se sale) y de la ventana.
  expect((boton?.x ?? 0) + (boton?.width ?? 0)).toBeLessThanOrEqual((tarjeta?.x ?? 0) + (tarjeta?.width ?? 0))
  expect((boton?.x ?? 0) + (boton?.width ?? 0)).toBeLessThanOrEqual(1024)
})

for (const ancho of [1280, 1366]) {
  test(`a ${ancho} px el objeto del contrato tiene espacio para leerse`, async ({ page }) => {
    await page.setViewportSize({ width: ancho, height: 800 })
    await loginComo(page, 'GESTION')
    const objeto = page.locator('.tabla-fila [data-col="Objeto"]').first()
    await expect(objeto).toBeVisible({ timeout: 15_000 })

    const caja = await objeto.boundingBox()
    expect(caja?.width ?? 0).toBeGreaterThanOrEqual(200)

    // Y la fila entera cabe: nada se corta por la derecha.
    const fila = await page.locator('.tabla-fila').first().boundingBox()
    expect((fila?.x ?? 0) + (fila?.width ?? 0)).toBeLessThanOrEqual(ancho)
  })
}
