// Auditoría de los modales en pantalla de teléfono.
// Mismo procedimiento que la auditoría del 16: viewport real, emulación de
// dispositivo, entorno completo y base poblada. No se versiona: se regenera.
import { chromium, devices } from '@playwright/test'

const CUENTAS = {
  ADMINISTRADOR: ['administrador@soy.sena.edu.co', 'Admin123*'],
  GESTION: ['gestion@soy.sena.edu.co', 'Gestion123*'],
}

const MODALES = [
  {
    rol: 'ADMINISTRADOR',
    nombre: 'Crear nuevo supervisor / gestor',
    ruta: '/admin/usuarios',
    abrir: 'text=+ Nuevo usuario',
  },
  { rol: 'ADMINISTRADOR', nombre: 'Restablecer contraseña', ruta: '/admin/usuarios', abrir: 'text=Resetear clave' },
  { rol: 'ADMINISTRADOR', nombre: 'Asignar firma electrónica', ruta: '/admin/firmas', abrir: 'text=+ Asignar firma' },
  { rol: 'ADMINISTRADOR', nombre: 'Cargar formato', ruta: '/admin/documentos', abrir: 'text=+ Cargar formato' },
  { rol: 'GESTION', nombre: 'Cargar nueva ficha', ruta: '/gestion', abrir: '[data-tour="cargar"]' },
]

const medir = () => {
  const dialogo = document.querySelector('[role="dialog"]')
  if (!dialogo) return { error: 'no se abrió ningún diálogo' }
  const anchoV = window.innerWidth
  const altoV = window.innerHeight
  const caja = dialogo.getBoundingClientRect()

  const desc = (el) => {
    const c = typeof el.className === 'string' && el.className ? '.' + el.className.trim().split(/\s+/).join('.') : ''
    const t = (el.textContent || '').trim().slice(0, 26)
    return `${el.tagName.toLowerCase()}${c}${t ? ` «${t}»` : ''}`.slice(0, 64)
  }

  const fuera = []
  const chicos = []
  const zoom = []
  for (const el of dialogo.querySelectorAll('*')) {
    const s = getComputedStyle(el)
    if (s.display === 'none' || s.visibility === 'hidden') continue
    const r = el.getBoundingClientRect()
    if (r.width < 1 || r.height < 1) continue
    if (Math.round(r.right - anchoV) > 1 || Math.round(r.left) < -1)
      fuera.push({ el: desc(el), exceso: Math.round(r.right - anchoV) })
    const interactivo = ['BUTTON', 'A', 'SELECT', 'INPUT', 'TEXTAREA'].includes(el.tagName)
    if (interactivo && (r.height < 44 || r.width < 44))
      chicos.push({ el: desc(el), w: Math.round(r.width), h: Math.round(r.height) })
    if (['INPUT', 'SELECT', 'TEXTAREA'].includes(el.tagName) && parseFloat(s.fontSize) < 16)
      zoom.push({ el: desc(el), px: s.fontSize })
  }

  // ¿Se sale el diálogo por abajo? Con maxHeight en vh y no en dvh, el navegador
  // móvil le permite ser más alto que lo que de verdad se ve.
  const seSalePorAbajo = Math.round(caja.bottom - altoV)

  return {
    viewport: `${anchoV}×${altoV}`,
    dialogo: `${Math.round(caja.width)}×${Math.round(caja.height)}`,
    maxHeightDeclarado: getComputedStyle(dialogo).maxHeight,
    seSalePorAbajo,
    recortados: fuera.length,
    peorRecorte: fuera.sort((a, b) => b.exceso - a.exceso)[0] || null,
    toquesChicos: chicos.length,
    ejemplosToques: chicos.slice(0, 4),
    camposConZoom: zoom.length,
    ejemplosZoom: zoom.slice(0, 3),
  }
}

const b = await chromium.launch()
for (const m of MODALES) {
  const ctx = await b.newContext({
    ...devices['Pixel 7'],
    viewport: { width: 360, height: 800 },
    deviceScaleFactor: 2,
    isMobile: true,
    hasTouch: true,
  })
  const page = await ctx.newPage()
  try {
    const [correo, clave] = CUENTAS[m.rol]
    await page.goto('http://localhost:8443/')
    await page.getByPlaceholder('correo@soy.sena.edu.co').fill(correo)
    await page.getByPlaceholder('••••••••••').fill(clave)
    await page.getByRole('button', { name: /Ingresar/ }).click()
    await page.waitForTimeout(2500)
    await page.goto('http://localhost:8443' + m.ruta)
    await page.waitForTimeout(2000)
    await page.locator(m.abrir).first().click()
    await page.waitForTimeout(1200)
    console.log('\n### ' + m.nombre + '  (' + m.ruta + ')')
    console.log(JSON.stringify(await page.evaluate(medir), null, 1))
  } catch (e) {
    console.log('\n### ' + m.nombre + '  (' + m.ruta + ')')
    console.log(JSON.stringify({ error: String(e).split('\n')[0] }))
  }
  await ctx.close()
}
await b.close()
