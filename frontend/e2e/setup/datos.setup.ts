import { expect, test as setup } from '@playwright/test'
import { CUENTAS_DEV } from '../fixtures/auth'

// Siembra de datos para la suite de extremo a extremo.
//
// **Por qué existe.** La regla que la auditoría del 16 de septiembre de 2026 dejó
// escrita es que una tabla sin filas cabe en cualquier ancho y no revela nada.
// Sin esta siembra, la compuerta del CI recorría pantallas vacías: la base que
// levanta el job solo tiene las tres cuentas de `DataInitializer`, ningún
// contrato y ninguna etapa. O sea que habría estado midiendo exactamente el caso
// que la auditoría descartó por inútil, y la propia regresión con la que se
// verificó la compuerta —41 filas recortadas en el registro de contratos— no
// habría tenido ni una fila que recortar.
//
// **Por qué por la API y no con SQL.** Crear un contrato dispara la plantilla
// GCCON-P-010 en el backend, que genera sus etapas y subetapas. Un fichero de
// SQL tendría que reproducir eso a mano y quedaría desfasado en cuanto la
// plantilla cambiara, sin que nada avisara.
//
// Es idempotente: en una máquina de desarrollo la base ya está poblada de días
// anteriores, y esto no debe duplicar contratos en cada ejecución.

const API = process.env.E2E_API_URL || 'http://localhost:8080'

/** Contratos mínimos para que ninguna pantalla se mida vacía. */
const CONTRATOS = [
  {
    numeroContrato: 'CO1.PCCNTR.E2E-SEMILLA-1',
    objeto:
      'Prestación de servicios profesionales para el acompañamiento técnico y pedagógico de los programas de formación del Centro Tecnológico del Mobiliario, incluyendo la elaboración de material didáctico.',
    valor: 48000000,
    tipoContrato: 'Prestación de servicios profesionales',
    contratista: 'María Fernanda Ospina Restrepo',
    contratistaNit: '43.567.890-1',
    lugarEjecucion: 'Centro Tecnológico del Mobiliario — Itagüí, Antioquia',
    centroCosto: 'CTM-2026-FORMACION',
  },
  {
    numeroContrato: 'CO1.PCCNTR.E2E-SEMILLA-2',
    objeto: 'Suministro e instalación de maquinaria para el taller de ebanistería del Centro.',
    valor: 126500000,
    tipoContrato: 'Suministro',
    contratista: 'Maderas y Equipos del Norte S.A.S.',
    contratistaNit: '900.456.123-7',
    lugarEjecucion: 'Taller de ebanistería — Sede principal',
    centroCosto: 'CTM-2026-DOTACION',
  },
]

setup('sembrar contratos para que ninguna pantalla se mida vacía', async ({ request }) => {
  const { email, password } = CUENTAS_DEV.GESTION
  const acceso = await request.post(`${API}/api/auth/login`, { data: { email, password } })
  expect(acceso.ok(), 'no se pudo iniciar sesión como GESTION para sembrar').toBeTruthy()
  const { token } = (await acceso.json()) as { token: string }
  const cabeceras = { Authorization: `Bearer ${token}` }

  const listado = await request.get(`${API}/api/contratos?size=200`, { headers: cabeceras })
  expect(listado.ok(), 'no se pudo leer el listado de contratos').toBeTruthy()
  const cuerpo = (await listado.json()) as { content?: { numeroContrato: string }[] } | { numeroContrato: string }[]
  const existentes = new Set((Array.isArray(cuerpo) ? cuerpo : (cuerpo.content ?? [])).map((c) => c.numeroContrato))

  const hoy = new Date()
  const enUnAnio = new Date(hoy.getTime() + 365 * 24 * 60 * 60 * 1000)

  for (const contrato of CONTRATOS) {
    if (existentes.has(contrato.numeroContrato)) continue
    const creado = await request.post(`${API}/api/contratos`, {
      headers: cabeceras,
      data: {
        ...contrato,
        fechaInicio: hoy.toISOString().slice(0, 10),
        fechaFin: enUnAnio.toISOString().slice(0, 10),
        // El supervisor de las cuentas sembradas es el tercero; se busca por
        // correo en vez de fijar un identificador, que cambia según el orden de
        // inserción de la base.
        supervisorId: await idDelSupervisor(request, cabeceras, API),
      },
    })
    expect(
      creado.ok(),
      `no se pudo crear ${contrato.numeroContrato}: ${creado.status()} ${await creado.text()}`,
    ).toBeTruthy()
  }
})

async function idDelSupervisor(
  request: Parameters<Parameters<typeof setup>[1]>[0]['request'],
  headers: Record<string, string>,
  api: string,
): Promise<number> {
  const respuesta = await request.get(`${api}/api/usuarios?size=200`, { headers })
  const cuerpo = (await respuesta.json()) as
    | { content?: { id: number; email: string }[] }
    | { id: number; email: string }[]
  const usuarios = Array.isArray(cuerpo) ? cuerpo : (cuerpo.content ?? [])
  const supervisor = usuarios.find((u) => u.email === CUENTAS_DEV.SUPERVISOR.email)
  expect(supervisor, 'no se encontró la cuenta de supervisor sembrada por DataInitializer').toBeTruthy()
  return supervisor!.id
}
