import { expect, test } from '@playwright/test'
import { loginComo } from '../../fixtures/auth'

// El botón «atrás» del teléfono a lo largo de un ciclo de sesión.
//
// Esto no se puede comprobar desde una prueba de componente: lo que decide si
// «atrás» cierra la aplicación es cuántas entradas tiene detrás el historial
// del navegador, y hace falta un navegador de verdad para preguntárselo.
//
// **Cómo se afirma aquí.** En un navegador no hay «cerrar la aplicación»: por
// debajo de la primera página que abre Playwright queda siempre un `about:blank`.
// Esa entrada es justamente la que hace de equivalente — salir de SICOT hacia
// ella es lo que en el WebView de Android cierra la actividad. Así que lo que se
// comprueba es a DÓNDE lleva una sola pulsación de «atrás»: fuera de la
// aplicación (`about:blank`), y no a una vista del panel que el guardia de
// sesión devuelve a `/login` dejando la pantalla igual.
//
// No vale comprobar `page.goBack()` a secas: devuelve `null` tanto cuando no
// hay nada detrás como cuando lo que hay es `about:blank`, que no tiene
// respuesta de red. Con el fallo presente la prueba pasaba igual.
//
// Viene del punto 13 de `docs/operacion/PRUEBA_MANUAL_APK.md`, que el recorrido
// del 08-10-2026 dejó a medias: tras cerrar sesión, cada pulsación de «atrás»
// iba a una vista del panel, el guardia de sesión la devolvía a `/login` y la
// pantalla no cambiaba. Hacían falta tantas pulsaciones como vistas se hubieran
// visitado para que la aplicación se cerrara, y mientras tanto el teléfono
// parecía atascado.

/** Vistas del supervisor por las que se pasa, en orden. */
const VISTAS = ['alertas', 'documentos', 'registros'] as const

test('en la pantalla de acceso recién abierta, «atrás» no tiene nada detrás', async ({ page }) => {
  // El arranque limpio es el caso que SÍ funcionaba antes del arreglo, y es la
  // referencia contra la que se compara el de después de cerrar sesión. Además
  // comprueba que la aplicación no apila una entrada de más al redirigir la raíz
  // a `/login`, que es lo que haría fallar la aserción del otro caso sin que el
  // cierre de sesión tuviera nada que ver.
  await page.goto('/')
  await expect(page.getByPlaceholder('correo@soy.sena.edu.co')).toBeVisible({ timeout: 15_000 })
  await expect(page).toHaveURL(/\/login$/)

  await page.goBack()
  await expect(page).toHaveURL('about:blank')
})

test('tras cerrar sesión, «atrás» tampoco tiene nada detrás', async ({ page }) => {
  await loginComo(page, 'SUPERVISOR')
  await expect(page.locator('[data-tour="nav-bandeja"]')).toBeVisible({ timeout: 15_000 })

  // Tres vistas: tres entradas apiladas. Son las que antes quedaban debajo de
  // `/login` al cerrar sesión.
  for (const vista of VISTAS) {
    await page.locator(`[data-tour="nav-${vista}"]`).click()
    await expect(page).toHaveURL(new RegExp(`/supervisor/${vista}$`))
  }

  await page.getByRole('button', { name: /^Abrir menú de/ }).click()
  await page.getByRole('button', { name: 'Cerrar sesión' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByPlaceholder('correo@soy.sena.edu.co')).toBeVisible()

  // La afirmación del punto 13: el historial quedó como en un arranque limpio,
  // así que una sola pulsación de «atrás» sale de SICOT. Con el fallo presente
  // esta misma pulsación llevaba a `/supervisor/registros`, el guardia de sesión
  // la devolvía a `/login` y la prueba terminaba aquí dentro de la aplicación —
  // que es exactamente lo que en el teléfono se veía como un botón muerto.
  await page.goBack()
  await expect(page).toHaveURL('about:blank')
})
