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
