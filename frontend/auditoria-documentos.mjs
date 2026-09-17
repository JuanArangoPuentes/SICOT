import { chromium, devices } from '@playwright/test'
const b = await chromium.launch()
const ctx = await b.newContext({
  ...devices['Pixel 7'],
  viewport: { width: 360, height: 800 },
  deviceScaleFactor: 2,
  isMobile: true,
  hasTouch: true,
})
const page = await ctx.newPage()
await page.goto('http://localhost:8443/')
await page.getByPlaceholder('correo@soy.sena.edu.co').fill('supervisor@soy.sena.edu.co')
await page.getByPlaceholder('••••••••••').fill('Supervisor123*')
await page.getByRole('button', { name: /Ingresar/ }).click()
await page.waitForTimeout(3000)
await page.goto('http://localhost:8443/supervisor/documentos')
await page.waitForTimeout(3500)
console.log(
  JSON.stringify(
    await page.evaluate(() => {
      const ancho = window.innerWidth
      const desc = (el) => {
        const c =
          typeof el.className === 'string' && el.className ? '.' + el.className.trim().split(/\s+/).join('.') : ''
        return `${el.tagName.toLowerCase()}${c}`.slice(0, 50)
      }
      // Las tarjetas de "DOCUMENTOS DEL CONTRATO" son .card en flex con un bloque
      // de acciones flexShrink:0 a la derecha.
      const tarjetas = Array.from(document.querySelectorAll('.card')).filter(
        (c) => getComputedStyle(c).display === 'flex',
      )
      const medidas = tarjetas.map((c) => {
        const izq = c.children[0],
          der = c.children[1]
        const nombre = izq?.querySelector('div')
        return {
          anchoTexto: izq ? Math.round(izq.getBoundingClientRect().width) : null,
          anchoAcciones: der ? Math.round(der.getBoundingClientRect().width) : null,
          altoTarjeta: Math.round(c.getBoundingClientRect().height),
          nombreVisible: nombre ? nombre.textContent.trim().slice(0, 40) : null,
          // ¿Cuántas líneas ocupa el nombre? Si el texto se parte palabra por
          // palabra, el alto del bloque se dispara.
          lineasNombre: nombre
            ? Math.round(
                nombre.getBoundingClientRect().height / parseFloat(getComputedStyle(nombre).lineHeight || '18'),
              )
            : null,
          desbordaNombre: nombre ? nombre.scrollWidth > nombre.clientWidth + 1 : null,
        }
      })
      const fuera = []
      for (const el of document.body.querySelectorAll('*')) {
        const s = getComputedStyle(el)
        if (s.display === 'none' || s.visibility === 'hidden' || s.opacity === '0') continue
        const r = el.getBoundingClientRect()
        if (r.width < 1 || r.height < 1) continue
        if (Math.round(r.right - ancho) > 1) fuera.push({ el: desc(el), exceso: Math.round(r.right - ancho) })
      }
      return {
        ancho,
        tarjetas: tarjetas.length,
        fueraDeAncho: fuera.length,
        peor: fuera.sort((a, b) => b.exceso - a.exceso)[0] || null,
        medidas: medidas.slice(0, 4),
      }
    }),
    null,
    1,
  ),
)
await b.close()
