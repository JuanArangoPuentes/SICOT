import { expect, test } from '@playwright/test'
import { loginComo } from '../../fixtures/auth'

// Armazón en pantalla de teléfono.
//
// Estas pruebas no comprueban que algo «se vea bien» —eso no se puede
// afirmar desde una prueba— sino tres hechos medibles que la auditoría del 16
// de septiembre de 2026 encontró rotos, y que al romperse no dan ningún error:
// la página sigue cargando, simplemente hay partes a las que no se llega.

/** Rutas del Supervisor, que es el rol para el que se piensa el teléfono. */
const RUTAS_SUPERVISOR = [
  '/supervisor/bandeja',
  '/supervisor/alertas',
  '/supervisor/documentos',
  '/supervisor/registros',
]

test.describe('armazón en pantalla estrecha', () => {
  test('se puede iniciar sesión desde un teléfono', async ({ page }) => {
    // Antes del 16 de septiembre de 2026 esto era imposible: el botón nacía
    // entre los píxeles 475 y 566 de una pantalla de 360, sin desplazamiento
    // horizontal que lo alcanzara. `loginComo` pulsa el botón de verdad, así
    // que si vuelve a salirse, esta prueba se agota esperando y lo dice.
    await loginComo(page, 'SUPERVISOR')
    // El marcador no es el mismo que usan las pruebas de escritorio: la
    // etiqueta de rol de la cabecera se oculta en estrecho porque no cabe. Lo
    // que confirma que se entró es la navegación del panel, que en un teléfono
    // es la fila inferior.
    await expect(page.locator('[data-tour="nav-bandeja"]')).toBeVisible({ timeout: 15_000 })
  })

  test('la navegación queda al alcance y ninguna vista se desborda', async ({ page }) => {
    await loginComo(page, 'SUPERVISOR')
    await expect(page.locator('[data-tour="nav-bandeja"]')).toBeVisible({ timeout: 15_000 })

    for (const ruta of RUTAS_SUPERVISOR) {
      await page.goto(ruta)
      await page.waitForTimeout(1200)

      // 1. Nada se sale del ancho. Se mide sobre el documento y no sobre un
      //    elemento concreto para que sirva igual cuando se añada una pantalla
      //    que hoy no existe.
      const { visible, desplazable } = await page.evaluate(() => ({
        visible: window.innerWidth,
        desplazable: document.documentElement.scrollWidth,
      }))
      expect(desplazable, `${ruta} se desborda en horizontal`).toBeLessThanOrEqual(visible + 1)

      // 2. Las entradas de navegación existen y se pueden pulsar. El recorrido
      //    guiado se ancla a estos mismos selectores, así que la prueba protege
      //    de paso al tutorial.
      const bandeja = page.locator('[data-tour="nav-bandeja"]')
      await expect(bandeja).toBeVisible()
      const caja = await bandeja.boundingBox()
      expect(caja, `${ruta}: la entrada de navegación no tiene caja`).not.toBeNull()
      expect(caja!.height, `${ruta}: objetivo táctil demasiado bajo`).toBeGreaterThanOrEqual(44)
    }
  })

  test('se puede cerrar sesión desde un teléfono', async ({ page }) => {
    // El menú de usuario nacía hasta 270 px fuera del borde derecho en las once
    // pantallas: la sesión no se podía cerrar, que en un sistema institucional
    // abierto en dispositivos compartidos no es un detalle de comodidad.
    await loginComo(page, 'SUPERVISOR')
    await expect(page.locator('[data-tour="nav-bandeja"]')).toBeVisible({ timeout: 15_000 })

    await page.locator('.usermenu-avatar').click()
    await page.getByRole('button', { name: /Cerrar sesión/ }).click()
    await expect(page.getByPlaceholder('correo@soy.sena.edu.co')).toBeVisible()
  })
})
