import { expect, test, type Page } from '@playwright/test'
import { loginComo, type Rol } from '../../fixtures/auth'

// Armazón en pantalla de teléfono.
//
// Estas pruebas no comprueban que algo «se vea bien» —eso no se puede
// afirmar desde una prueba— sino hechos medibles que la auditoría del 16 de
// septiembre de 2026 encontró rotos, y que al romperse no dan ningún error:
// la página sigue cargando, simplemente hay partes a las que no se llega.
//
// Cubren los tres roles y no solo el Supervisor. La hipótesis de trabajo de
// ADR-012 es que el teléfono es para el Supervisor, pero la adaptación se hizo
// para los tres porque era lo barato y lo honesto; dejar a Gestión y
// Administración sin vigilancia significaría que la pantalla con PEOR medida de
// toda la auditoría —el registro de contratos, con 41 elementos recortados y el
// peor 713 px fuera del borde— es justo la que nada protege.

/** Pantallas de cada rol, por su ruta. */
const PANTALLAS: Record<Rol, readonly string[]> = {
  SUPERVISOR: [
    '/supervisor/bandeja',
    '/supervisor/contrato',
    '/supervisor/alertas',
    '/supervisor/documentos',
    '/supervisor/registros',
  ],
  GESTION: ['/gestion'],
  ADMINISTRADOR: ['/admin/dashboard', '/admin/documentos', '/admin/usuarios', '/admin/firmas'],
}

/**
 * Entrada de navegación que sirve de ancla para cada rol: la primera de su
 * panel. Son los mismos selectores a los que se engancha el recorrido guiado,
 * así que comprobarlos protege de paso al tutorial.
 */
const ANCLA_DE_NAVEGACION: Record<Rol, string> = {
  SUPERVISOR: '[data-tour="nav-bandeja"]',
  GESTION: '[data-tour="nav-contratos"]',
  ADMINISTRADOR: '[data-tour="nav-dashboard"]',
}

/** Mínimo cómodo para un dedo. Un objetivo de 24 px es cómodo con un ratón. */
const MINIMO_TACTIL = 44

async function entrarComo(page: Page, rol: Rol) {
  await loginComo(page, rol)
  // El marcador no es el mismo que usan las pruebas de escritorio: la etiqueta
  // de rol de la cabecera se oculta en estrecho porque no cabe. Lo que confirma
  // que se entró es la navegación del panel, que en un teléfono es la fila
  // inferior.
  await expect(page.locator(ANCLA_DE_NAVEGACION[rol])).toBeVisible({ timeout: 15_000 })
}

/**
 * Elementos cuyo borde derecho cae fuera del ancho visible.
 *
 * **Por qué no se mide `document.documentElement.scrollWidth`**, que es lo que
 * hacía la primera versión de esta prueba: el armazón de SICOT recorta con
 * `overflow: hidden`, así que el documento NUNCA declara desbordamiento por
 * mucho que sus hijos se salgan. Es el hallazgo 0 de la auditoría del 16 de
 * septiembre de 2026 —«en las once pantallas el ancho desplazable era
 * exactamente 360 px, el mismo que el visible»— y significa que aquella
 * comprobación habría pasado en verde sobre la aplicación rota que vino a
 * proteger. Se descubrió deshaciendo a propósito la conversión de tabla a
 * tarjetas y viendo que la compuerta no se enteraba.
 *
 * Se mide por tanto lo mismo que midió la auditoría: la caja de cada elemento
 * visible contra el ancho de la ventana.
 *
 * Se excluye lo que está dentro de un contenedor con desplazamiento horizontal
 * explícito, porque ahí salirse del borde no es un defecto sino la forma
 * prevista de leerlo — y es justo la salida que puede acabar adoptando la barra
 * de etapas.
 */
async function medirDesborde(page: Page) {
  return page.evaluate(() => {
    const ancho = window.innerWidth
    const enContenedorDesplazable = (el: Element) => {
      for (let p = el.parentElement; p; p = p.parentElement) {
        const overflowX = getComputedStyle(p).overflowX
        if (overflowX !== 'auto' && overflowX !== 'scroll') continue
        // No basta con que el contenedor DECLARE desplazamiento: hay que
        // comprobar que de verdad se desplace. El armazón pone `overflow-y:
        // auto`, y el navegador convierte entonces el `overflow-x: visible` en
        // `auto` por sí solo — con lo cual media aplicación queda anidada bajo
        // un contenedor «desplazable» que no se desplaza. Con la comprobación
        // anterior eso excluía de la medida a casi todos los elementos, y la
        // prueba pasaba en verde sobre una pantalla rota.
        if (p.scrollWidth > p.clientWidth + 1) return true
      }
      return false
    }
    const describir = (el: Element) => {
      const clases =
        typeof el.className === 'string' && el.className ? `.${el.className.trim().split(/\s+/).join('.')}` : ''
      return `${el.tagName.toLowerCase()}${el.id ? `#${el.id}` : ''}${clases}`.slice(0, 90)
    }

    const fuera: { etiqueta: string; exceso: number }[] = []
    for (const el of Array.from(document.body.querySelectorAll('*'))) {
      const estilo = getComputedStyle(el)
      if (estilo.display === 'none' || estilo.visibility === 'hidden' || estilo.opacity === '0') continue
      const caja = el.getBoundingClientRect()
      if (caja.width < 1 || caja.height < 1) continue
      // Un píxel de tolerancia: el redondeo subpíxel del navegador produce
      // excesos de 0,5 px que no ve nadie y que harían la prueba inestable.
      const exceso = Math.round(caja.right - ancho)
      if (exceso <= 1) continue
      if (enContenedorDesplazable(el)) continue
      fuera.push({ etiqueta: describir(el), exceso })
    }
    fuera.sort((a, b) => b.exceso - a.exceso)
    return { ancho, total: fuera.length, peores: fuera.slice(0, 5) }
  })
}

test.describe('armazón en pantalla estrecha', () => {
  test('se puede iniciar sesión desde un teléfono', async ({ page }) => {
    // Antes del 16 de septiembre de 2026 esto era imposible: el botón nacía
    // entre los píxeles 475 y 566 de una pantalla de 360, sin desplazamiento
    // horizontal que lo alcanzara. `loginComo` pulsa el botón de verdad, así
    // que si vuelve a salirse, esta prueba se agota esperando y lo dice.
    await entrarComo(page, 'SUPERVISOR')
  })

  test('se puede cerrar sesión desde un teléfono', async ({ page }) => {
    // El menú de usuario nacía hasta 270 px fuera del borde derecho en las once
    // pantallas: la sesión no se podía cerrar, que en un sistema institucional
    // abierto en dispositivos compartidos no es un detalle de comodidad.
    await entrarComo(page, 'SUPERVISOR')

    await page.locator('.usermenu-avatar').click()
    await page.getByRole('button', { name: /Cerrar sesión/ }).click()
    await expect(page.getByPlaceholder('correo@soy.sena.edu.co')).toBeVisible()
  })
})

for (const rol of Object.keys(PANTALLAS) as Rol[]) {
  test.describe(`armazón en pantalla estrecha · ${rol}`, () => {
    test('la navegación queda al alcance y ninguna vista se desborda', async ({ page }) => {
      // El tope por omisión de Playwright son 30 s para el test entero, y este
      // recorre varias pantallas con una espera de asentamiento en cada una. Al
      // pasar el Supervisor de cuatro rutas a cinco, el tope se agotaba a mitad
      // del recorrido y el fallo aparecía como «no encuentro la navegación» en
      // la última ruta — un síntoma que señala a la aplicación cuando el
      // problema era el reloj. Se calcula desde el número de pantallas para que
      // añadir una más no vuelva a romperlo.
      test.setTimeout(20_000 + 15_000 * PANTALLAS[rol].length)

      await entrarComo(page, rol)

      for (const ruta of PANTALLAS[rol]) {
        await page.goto(ruta)
        await page.waitForTimeout(1200)

        // 1. Nada se sale del ancho visible.
        const desborde = await medirDesborde(page)
        expect(
          desborde.total,
          `${ruta}: ${desborde.total} elemento(s) fuera del ancho visible. ` +
            `Los peores: ${desborde.peores.map((e) => `${e.etiqueta} (+${e.exceso} px)`).join(', ')}`,
        ).toBe(0)

        // 2. La entrada de navegación existe y se puede pulsar con un dedo.
        const ancla = page.locator(ANCLA_DE_NAVEGACION[rol])
        await expect(ancla).toBeVisible()
        const caja = await ancla.boundingBox()
        expect(caja, `${ruta}: la entrada de navegación no tiene caja`).not.toBeNull()
        expect(caja!.height, `${ruta}: objetivo táctil demasiado bajo`).toBeGreaterThanOrEqual(MINIMO_TACTIL)
      }
    })
  })
}
