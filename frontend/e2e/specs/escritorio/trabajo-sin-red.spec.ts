import { expect, test, type Page } from '@playwright/test'
import { CUENTAS_DEV, loginComo } from '../../fixtures/auth'

// ADR-015 de extremo a extremo, en un Chromium de verdad y contra el backend
// real: lo que las pruebas unitarias no pueden decir es si PBKDF2 está en el
// navegador, si el corte de red llega como corte y si la pantalla de acceso
// toma el camino sin red.
//
// El caso que se recorre es el de campo: el supervisor entró por la mañana con
// red, su token de 8 horas venció sin que cerrara sesión, y al día siguiente
// abre la aplicación donde no hay señal. No se recarga la página sin red: la
// versión web no tiene sus archivos en el equipo (la de escritorio y el APK sí),
// así que el corte se hace con la aplicación ya abierta.

/** Cambia el token guardado por uno vencido, como si hubieran pasado las 8 horas. */
async function vencerElToken(page: Page) {
  await page.evaluate(() => {
    const sesion = JSON.parse(localStorage.getItem('sicot.session') ?? '{}')
    const carga = btoa(JSON.stringify({ exp: 1 })).replace(/=+$/, '')
    sesion.token = `cabecera.${carga}.firma`
    localStorage.setItem('sicot.session', JSON.stringify(sesion))
  })
}

async function entrarConRedYDejarLaCopia(page: Page) {
  await loginComo(page, 'SUPERVISOR')
  await expect(page.getByText('Panel Supervisor')).toBeVisible({ timeout: 10_000 })
  // La huella para entrar sin red se guarda después de entrar, sin esperar.
  await expect
    .poll(() => page.evaluate(() => localStorage.getItem('sicot.acceso-sin-red')), { timeout: 15_000 })
    .not.toBeNull()
  // Lo que la bandeja pidió al servidor queda en la copia local.
  await page.waitForLoadState('networkidle')
  await vencerElToken(page)
  await page.reload()
  await expect(page.getByPlaceholder('usuario@sena.edu.co')).toBeVisible()
  // La pantalla de acceso precarga el panel del supervisor cuando el equipo
  // puede entrar sin red; se espera a que termine antes de cortar.
  await page.waitForLoadState('networkidle')
}

async function escribirCredenciales(page: Page, password: string) {
  await page.getByPlaceholder('usuario@sena.edu.co').fill(CUENTAS_DEV.SUPERVISOR.email)
  await page.getByPlaceholder('••••••••••').fill(password)
  await page.getByRole('button', { name: /Ingresar/ }).click()
}

test('sin red, el supervisor entra con su contraseña y ve su copia rotulada', async ({ page, context }) => {
  await entrarConRedYDejarLaCopia(page)

  await context.setOffline(true)
  await escribirCredenciales(page, CUENTAS_DEV.SUPERVISOR.password)

  await expect(page.getByText('Panel Supervisor')).toBeVisible({ timeout: 15_000 })
  await expect(page.getByText('Sin conexión con el servidor.')).toBeVisible()
  await expect(page.getByText(/Entró sin conexión con la contraseña recordada/)).toBeVisible()
})

test('sin red, una contraseña equivocada no entra', async ({ page, context }) => {
  await entrarConRedYDejarLaCopia(page)

  await context.setOffline(true)
  await escribirCredenciales(page, 'claveIncorrecta')

  await expect(page.getByText(/Credenciales inválidas\./)).toBeVisible({ timeout: 15_000 })
  await expect(page.getByPlaceholder('usuario@sena.edu.co')).toBeVisible()
})
