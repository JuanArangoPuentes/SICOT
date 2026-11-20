import { test, expect } from '@playwright/test'
import { loginComo } from '../../fixtures/auth'
import { writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

/**
 * El proceso GCCON-P-010 recorrido **por la interfaz**, de principio a fin de
 * un paso: ver el expediente, aportar una evidencia real, que quede en los
 * documentos del contrato y en la bitácora, y que siga ahí al recargar.
 *
 * <h2>Por qué hacía falta esta prueba</h2>
 * Hasta el 20-11-2026 la suite de extremo a extremo eran seis archivos sobre
 * **maquetación y acceso**: iniciar y cerrar sesión, que un modal quepa en un
 * teléfono, que a 1024 px no se corte una columna. Todo eso es necesario —nació
 * de la auditoría móvil del 16-09-2026 y atrapa regresiones reales— y ninguna
 * de esas pruebas recorría el proceso que es el producto.
 *
 * El backend sí lo cubre ({@code DocumentoFormalDeExtremoAExtremoIntegrationTest},
 * {@code ArranqueDelContratoIntegrationTest}). El problema era otro: la
 * orquestación de los pasos —qué se ve, qué se habilita, qué se recuerda entre
 * sub-pasos, cuándo se puede cargar o firmar— vive en `SupervisorPanel.tsx`,
 * 2 175 líneas, y allí se validaba solo con servicios simulados. El archivo más
 * complejo y de mayor valor del repositorio nunca se ejecutaba contra el
 * backend de verdad.
 *
 * <h2>Lo que esta prueba toca de punta a punta</h2>
 * Autenticación → carga del contrato asignado → etapas y sub-etapas de la
 * plantilla GCCON-P-010 → carga de un archivo real (multipart, validación por
 * magic bytes, lectura de EXIF) → listado de documentos → bitácora de auditoría
 * → persistencia. Si cualquiera de esos eslabones se rompe, falla aquí.
 *
 * <h2>Por qué la evidencia fotográfica y no la firma de un documento</h2>
 * Porque la firma exige que SICOT redacte el borrador, y eso exige Ollama. El
 * CI no lo tiene y una compuerta que depende de un modelo de 5 GB no es una
 * compuerta. La carga de evidencia del sub-paso 3.2 es el otro camino real del
 * supervisor —la foto de la entrega en bodega, tomada con el teléfono— y no
 * necesita nada más que el backend.
 */

/** Un JPEG de 1x1 válido: lo que importa es que pase la validación por contenido. */
const JPEG_1X1 = Buffer.from(
  '/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0a' +
    'HBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPDWy/9sAQwEJCQkMCwwYDQ0YMiEcITIyMjIyMjIy' +
    'MjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEB' +
    'AxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9' +
    'AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6' +
    'Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ip' +
    'qrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/9oACAEBAAA/' +
    'APn+iiigD//Z',
  'base64',
)

/**
 * Nombre distinto en cada ejecución.
 *
 * En el CI la base nace vacía, pero en una máquina de desarrollo lleva lo de
 * los días anteriores: con un nombre fijo, las comprobaciones podrían pasar por
 * un documento que dejó otra ejecución y la prueba diría «verde» sin haber
 * cargado nada.
 */
function fotoDeEvidencia(): string {
  const ruta = join(tmpdir(), `evidencia-e2e-${Date.now()}.jpg`)
  writeFileSync(ruta, JPEG_1X1)
  return ruta
}

test.describe('el supervisor recorre el proceso GCCON-P-010', () => {
  test('ve su expediente, aporta evidencia y queda en el contrato y en la bitácora', async ({ page }) => {
    // El tope por defecto no alcanza: esto arranca sesión, lee el contrato con
    // sus seis etapas, sube un archivo y vuelve a leer tres pantallas.
    test.setTimeout(90_000)

    await loginComo(page, 'SUPERVISOR')
    await expect(page.locator('[data-tour="nav-contrato"]')).toBeVisible({ timeout: 20_000 })

    // ── 1. El expediente es el de la plantilla GCCON-P-010, no una maqueta ──
    await page.locator('[data-tour="nav-contrato"]').click()
    const pasos = page.locator('button.step-row')
    await expect(pasos).toHaveCount(6, { timeout: 20_000 })
    await expect(pasos.nth(0)).toContainText('Designación de la supervisión')
    await expect(pasos.nth(1)).toContainText('Acta de Inicio')
    await expect(pasos.nth(5)).toContainText('Informe Final')
    // La etapa 1 la cierra el servidor al asignar el supervisor, así que el
    // supervisor entra con el paso 1 hecho y el 2 en curso. Si esto se rompe,
    // el proceso arranca en el sitio equivocado.
    await expect(pasos.nth(0)).toContainText('3/3 — 100%')
    await expect(pasos.nth(1)).toContainText('ACTIVO')

    // ── 2. Desplegar un paso, CON EL TECLADO ───────────────────────────────
    // Desde el 20-11-2026 la fila del paso es un <button> y no un <div
    // onClick>: antes no se alcanzaba con el teclado y quien no usa ratón no
    // podía abrir ningún paso del proceso. Esta comprobación es lo que impide
    // que vuelva a ser un div.
    const paso3 = pasos.nth(2)
    await expect(paso3).toHaveAttribute('aria-expanded', 'false')
    await paso3.focus()
    await page.keyboard.press('Enter')
    await expect(paso3).toHaveAttribute('aria-expanded', 'true')

    const sub32 = page.locator('.substep-row').filter({ hasText: '3.2' })
    await expect(sub32).toContainText('Carga de evidencia fotográfica georreferenciada')

    // ── 3. Aportar la evidencia real de la entrega en bodega ───────────────
    // El <input type="file"> está oculto detrás de «Elegir una foto»; se le
    // pasan los bytes directamente, que es lo que haría el selector del
    // sistema. El segundo es el de la galería (el primero abre la cámara).
    await sub32.locator('input[type=file]').nth(1).setInputFiles(fotoDeEvidencia())
    const cargar = sub32.getByRole('button', { name: /Cargar evidencia/ })
    await expect(cargar).toBeVisible({ timeout: 15_000 })
    await cargar.click()

    // El acuse nombra el documento que quedó, y avisa de que la foto no trae
    // fecha ni lugar: es la mitad del valor de una evidencia, y callarlo haría
    // pasar por georreferenciada una foto que no lo está.
    await expect(sub32).toContainText('Evidencia cargada', { timeout: 30_000 })
    await expect(sub32).toContainText('Evidencia fotográfica 3.2')
    await expect(sub32).toContainText(/no trae fecha|no trae ubicación/)

    // ── 4. Está en los documentos del contrato ─────────────────────────────
    await page.locator('[data-tour="nav-documentos"]').click()
    const documentos = page.locator('body')
    await expect(documentos).toContainText('DOCUMENTOS DEL CONTRATO', { timeout: 20_000 })
    await expect(documentos).toContainText('Evidencia fotográfica 3.2')
    // Reconocido como imagen (por su contenido, no por la extensión) y todavía
    // sin firmar: las dos cosas que la pantalla tiene que decir de él.
    await expect(documentos).toContainText('IMAGEN')

    // ── 5. Y en la bitácora, con quién lo hizo ─────────────────────────────
    await page.locator('[data-tour="nav-registros"]').click()
    const registros = page.locator('body')
    await expect(registros).toContainText('Documento cargado', { timeout: 20_000 })
    await expect(registros).toContainText('cargado en la subetapa 3.2')
    // El nombre de la persona, no «Sistema»: es un expediente de contratación
    // pública y quién hizo cada cosa es su razón de ser.
    await expect(registros).toContainText('Alex Fernando Zapata')

    // ── 6. La autoridad es el servidor, no la pantalla ─────────────────────
    // Recargar tira todo el estado de React. Si lo anterior se hubiera quedado
    // en memoria —el fallo clásico de una interfaz que «parece» guardar— aquí
    // desaparecería.
    await page.reload()
    await expect(page.locator('[data-tour="nav-documentos"]')).toBeVisible({ timeout: 20_000 })
    await page.locator('[data-tour="nav-documentos"]').click()
    await expect(page.locator('body')).toContainText('Evidencia fotográfica 3.2', { timeout: 20_000 })
  })

  test('los seis pasos se pueden abrir y cerrar sin ratón', async ({ page }) => {
    // El recorrido completo del teclado sobre la navegación principal del
    // panel. Va aparte de la prueba de arriba porque no necesita escribir nada:
    // si falla, lo que está roto es la accesibilidad, no el proceso.
    await loginComo(page, 'SUPERVISOR')
    await page.locator('[data-tour="nav-contrato"]').click()
    const pasos = page.locator('button.step-row')
    await expect(pasos).toHaveCount(6, { timeout: 20_000 })

    for (let i = 0; i < 6; i++) {
      const paso = pasos.nth(i)
      const abiertoAlEntrar = (await paso.getAttribute('aria-expanded')) === 'true'
      await paso.focus()
      // Espacio además de Enter: un <button> responde a los dos y un div no
      // responde a ninguno.
      await page.keyboard.press(abiertoAlEntrar ? 'Enter' : 'Space')
      await expect(paso).toHaveAttribute('aria-expanded', String(!abiertoAlEntrar))
    }
  })
})
