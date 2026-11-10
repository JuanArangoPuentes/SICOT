import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { setAuthToken } from './api/client'
import {
  alEnviarCambios,
  CABECERA_HECHO_SIN_CONEXION,
  cambiosDeLaCola,
  colaDe,
  descartarCambio,
  enviarOEncolar,
  reenviarLaCola,
  reiniciarLaColaParaPruebas,
  reintentarCambio,
} from './colaDeCambios'
import { conCopiaLocal, copiaLocalDe, reiniciarCopiaLocalParaPruebas } from './copiaLocal'
import { getEtapasContrato } from './etapaService'
import { guardarEntregasAcordadas, obtenerEntregasAcordadas } from './contratoService'
import type { EtapaResponse } from './api/types'

/**
 * La cola es lo que convierte la copia de lectura en «trabajar sin red» de
 * verdad (ADR-015, punto 3). Estas pruebas la ponen delante de un servidor
 * falso que se puede desenchufar, y miran las tres cosas que no pueden fallar:
 * que nada se pierda, que salga en el orden en que se hizo, y que no pise lo
 * que otro cambió mientras tanto.
 */

type Peticion = { metodo: string; ruta: string; cuerpo: unknown; horaDeclarada: string | null }

const ETAPAS: EtapaResponse[] = [
  {
    id: 2,
    numero: 2,
    nombre: 'Inicio',
    estado: 'EN_CURSO',
    porcentajeAvance: 25,
    subEtapas: [
      { id: 22, codigo: '2.2', nombre: 'SST', descripcion: '', estado: 'EN_CURSO', responsable: 'Supervisor' },
      {
        id: 23,
        codigo: '2.3',
        nombre: 'Especificaciones',
        descripcion: '',
        estado: 'PENDIENTE',
        responsable: 'Supervisor',
      },
    ],
  } as unknown as EtapaResponse,
]

function servidorFalso() {
  const estado = {
    conectado: true,
    peticiones: [] as Peticion[],
    entregas: [] as Array<{ numero: number; fecha: string; descripcion: string }>,
    /** Respuesta forzada para una ruta: `{ status, message }`. */
    forzar: new Map<string, { status: number; message: string }>(),
  }
  const json = (status: number, cuerpo?: unknown) =>
    new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), {
      status,
      headers: { 'Content-Type': 'application/json' },
    })

  const fetchFalso = vi.fn(async (url: string, init: RequestInit = {}) => {
    if (!estado.conectado) throw new TypeError('Failed to fetch')
    const ruta = new URL(url).pathname
    const metodo = (init.method ?? 'GET').toUpperCase()
    const cuerpo = typeof init.body === 'string' ? JSON.parse(init.body) : null
    const horaDeclarada = new Headers(init.headers).get(CABECERA_HECHO_SIN_CONEXION)
    estado.peticiones.push({ metodo, ruta, cuerpo, horaDeclarada })

    const forzada = estado.forzar.get(`${metodo} ${ruta}`)
    if (forzada) return json(forzada.status, { status: forzada.status, message: forzada.message })

    if (metodo === 'GET' && ruta === '/api/contratos/1/etapas') return json(200, ETAPAS)
    if (metodo === 'GET' && ruta === '/api/contratos/1/entregas-acordadas')
      return json(200, { entregas: estado.entregas, inicioDelContrato: '2026-10-01', finDelContrato: '2026-12-31' })
    if (metodo === 'PUT' && ruta === '/api/contratos/1/entregas-acordadas') {
      const lista = (cuerpo as { entregas: Array<{ fecha: string; descripcion: string }> }).entregas
      estado.entregas = lista.map((e, i) => ({ numero: i + 1, ...e }))
      return json(200, { entregas: estado.entregas, inicioDelContrato: '2026-10-01', finDelContrato: '2026-12-31' })
    }
    if (metodo === 'PATCH' && ruta.startsWith('/api/subetapas/')) return json(200, { id: 0, estado: 'COMPLETADA' })
    if (metodo === 'PATCH' && ruta.startsWith('/api/alertas/')) return json(200, {})
    return json(404, { status: 404, message: 'No existe' })
  })
  return { estado, fetchFalso }
}

const enviadas = (s: ReturnType<typeof servidorFalso>['estado']) =>
  s.peticiones.filter((p) => p.metodo !== 'GET').map((p) => `${p.metodo} ${p.ruta}`)

describe('colaDeCambios', () => {
  let servidor: ReturnType<typeof servidorFalso>

  beforeEach(async () => {
    servidor = servidorFalso()
    vi.stubGlobal('fetch', servidor.fetchFalso)
    reiniciarCopiaLocalParaPruebas()
    await reiniciarLaColaParaPruebas()
    await copiaLocalDe('42')
    await colaDe('42')
    setAuthToken('token-valido')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    setAuthToken(null)
  })

  const cerrar = (subetapaId: number, codigo: string) =>
    enviarOEncolar({ tipo: 'estado-subetapa', contratoId: 1, subetapaId, codigo, estado: 'COMPLETADA' })

  it('con red envía en el acto y no guarda nada', async () => {
    const r = await cerrar(22, '2.2')

    expect(r.enCola).toBe(false)
    expect(cambiosDeLaCola()).toHaveLength(0)
    expect(enviadas(servidor.estado)).toEqual(['PATCH /api/subetapas/22/estado'])
  })

  it('sin red guarda el cambio, y la copia lo muestra encima rotulado como pendiente', async () => {
    await getEtapasContrato(1) // con red: queda la copia
    servidor.estado.conectado = false

    const r = await cerrar(22, '2.2')
    expect(r.enCola).toBe(true)
    expect(cambiosDeLaCola()).toHaveLength(1)

    const etapas = await getEtapasContrato(1)
    const sub = etapas[0].subEtapas[0] as (typeof etapas)[0]['subEtapas'][0] & { pendienteDeEnviar?: boolean }
    expect(sub.estado).toBe('COMPLETADA')
    expect(sub.pendienteDeEnviar).toBe(true)
    // El que no se tocó sigue como lo dejó el servidor, sin rótulo.
    expect(etapas[0].subEtapas[1]).not.toHaveProperty('pendienteDeEnviar')
  })

  it('al volver la red los envía en el orden en que se hicieron, con la hora del equipo', async () => {
    servidor.estado.conectado = false
    await cerrar(22, '2.2')
    await cerrar(23, '2.3')
    const horas = cambiosDeLaCola().map((c) => c.hechoEn)
    const avisado = vi.fn()
    alEnviarCambios(avisado)

    servidor.estado.conectado = true
    const r = await reenviarLaCola()

    expect(r).toEqual({ enviados: 2, rechazados: 0, detenido: null })
    expect(enviadas(servidor.estado)).toEqual(['PATCH /api/subetapas/22/estado', 'PATCH /api/subetapas/23/estado'])
    expect(servidor.estado.peticiones.map((p) => p.horaDeclarada)).toEqual(horas)
    expect(cambiosDeLaCola()).toHaveLength(0)
    expect(avisado).toHaveBeenCalledTimes(1)
  })

  it('un cambio que entra mientras se envía sale en ese mismo envío, detrás de los anteriores', async () => {
    servidor.estado.conectado = false
    await cerrar(22, '2.2')
    servidor.estado.conectado = true

    const enCurso = reenviarLaCola()
    // Llega mientras el 2.2 todavía está en camino.
    await cerrar(23, '2.3')
    const r = await enCurso

    expect(r.enviados).toBe(2)
    expect(enviadas(servidor.estado)).toEqual(['PATCH /api/subetapas/22/estado', 'PATCH /api/subetapas/23/estado'])
    expect(cambiosDeLaCola()).toHaveLength(0)
  })

  it('con cambios esperando, uno nuevo va detrás aunque ya haya red', async () => {
    servidor.estado.conectado = false
    await cerrar(22, '2.2')
    servidor.estado.conectado = true

    const r = await cerrar(23, '2.3')
    await reenviarLaCola()

    expect(r.enCola).toBe(true)
    expect(enviadas(servidor.estado)).toEqual(['PATCH /api/subetapas/22/estado', 'PATCH /api/subetapas/23/estado'])
  })

  it('lo que el servidor rechaza queda a la vista con su motivo, y lo siguiente sigue su camino', async () => {
    servidor.estado.conectado = false
    await cerrar(22, '2.2')
    await cerrar(23, '2.3')
    servidor.estado.conectado = true
    servidor.estado.forzar.set('PATCH /api/subetapas/22/estado', {
      status: 400,
      message: 'No se puede cerrar: faltan los contactos de SST.',
    })

    const r = await reenviarLaCola()

    expect(r.rechazados).toBe(1)
    expect(r.enviados).toBe(1)
    const [rechazado] = cambiosDeLaCola()
    expect(rechazado.estado).toBe('rechazado')
    expect(rechazado.motivo).toBe('No se puede cerrar: faltan los contactos de SST.')
    // Rechazado ya no se pinta encima de la copia: lo que vale es lo del servidor.
    const etapas = await getEtapasContrato(1)
    expect(etapas[0].subEtapas[0].estado).toBe('EN_CURSO')
  })

  it('un error del servidor al enviar con red no se encola: se le dice al supervisor como siempre', async () => {
    servidor.estado.forzar.set('PATCH /api/subetapas/22/estado', { status: 400, message: 'Transición no permitida' })

    await expect(cerrar(22, '2.2')).rejects.toThrow('Transición no permitida')
    expect(cambiosDeLaCola()).toHaveLength(0)
  })

  it('una sesión sin token no envía: espera a que se entre con conexión', async () => {
    servidor.estado.conectado = false
    await cerrar(22, '2.2')
    servidor.estado.conectado = true
    setAuthToken(null)

    const r = await reenviarLaCola()

    expect(r.detenido).toBe('sin-sesion')
    expect(enviadas(servidor.estado)).toEqual([])
    expect(cambiosDeLaCola()).toHaveLength(1)
  })

  describe('las listas enteras no pisan lo que otro cambió', () => {
    const PRIMERA = { fecha: '2026-11-20', descripcion: 'Primera entrega' }

    it('si la lista del servidor es la que el supervisor vio, se envía la suya', async () => {
      await obtenerEntregasAcordadas(1) // la base: vacía
      servidor.estado.conectado = false
      const vista = await guardarEntregasAcordadas(1, [PRIMERA])
      expect(vista.pendienteDeEnviar).toBe(true)
      expect(vista.entregas).toEqual([{ numero: 1, ...PRIMERA }])
      expect(vista.finDelContrato).toBe('2026-12-31') // el plazo sale de la copia

      servidor.estado.conectado = true
      await reenviarLaCola()

      expect(enviadas(servidor.estado)).toEqual(['PUT /api/contratos/1/entregas-acordadas'])
      expect(servidor.estado.entregas).toEqual([{ numero: 1, ...PRIMERA }])
    })

    it('si otro la cambió mientras tanto, no se envía y quedan las dos a la vista', async () => {
      await obtenerEntregasAcordadas(1) // la base: vacía
      servidor.estado.conectado = false
      await guardarEntregasAcordadas(1, [PRIMERA])
      // Gestión registra otra fecha mientras el supervisor está sin red.
      servidor.estado.entregas = [{ numero: 1, fecha: '2026-11-25', descripcion: 'La de Gestión' }]

      servidor.estado.conectado = true
      const r = await reenviarLaCola()

      expect(r.rechazados).toBe(1)
      expect(enviadas(servidor.estado)).toEqual([])
      const [conflicto] = cambiosDeLaCola()
      expect(conflicto.motivo).toMatch(/alguien cambió esta lista/)
      expect(conflicto.enElServidor).toMatchObject({ entregas: [{ descripcion: 'La de Gestión' }] })

      // «Enviar la mía de todos modos»: ahora sí sale, sobre lo que vio.
      await reintentarCambio(conflicto.id)
      await reenviarLaCola()
      expect(enviadas(servidor.estado)).toEqual(['PUT /api/contratos/1/entregas-acordadas'])
      expect(servidor.estado.entregas).toEqual([{ numero: 1, ...PRIMERA }])
    })

    it('si el servidor ya tiene exactamente lo suyo, se da por enviado sin volver a mandarlo', async () => {
      await obtenerEntregasAcordadas(1)
      servidor.estado.conectado = false
      await guardarEntregasAcordadas(1, [PRIMERA])
      // La respuesta se perdió por el camino, pero el cambio sí llegó.
      servidor.estado.entregas = [{ numero: 1, ...PRIMERA }]

      servidor.estado.conectado = true
      const r = await reenviarLaCola()

      expect(r.enviados).toBe(1)
      expect(enviadas(servidor.estado)).toEqual([])
      expect(cambiosDeLaCola()).toHaveLength(0)
    })
  })

  it('una alerta que ya no existe no deja la cola atascada', async () => {
    servidor.estado.conectado = false
    await enviarOEncolar({ tipo: 'alerta-leida', contratoId: 1, alertaId: 7 })
    servidor.estado.conectado = true
    servidor.estado.forzar.set('PATCH /api/alertas/7/leida', { status: 404, message: 'Alerta no encontrada' })

    await reenviarLaCola()

    expect(cambiosDeLaCola()).toHaveLength(0)
  })

  it('un fallo del servidor detiene el envío y, si se repite, aparta el cambio', async () => {
    servidor.estado.conectado = false
    await cerrar(22, '2.2')
    servidor.estado.conectado = true
    servidor.estado.forzar.set('PATCH /api/subetapas/22/estado', { status: 500, message: 'Error interno' })

    expect((await reenviarLaCola()).detenido).toBe('servidor')
    expect((await reenviarLaCola()).detenido).toBe('servidor')
    const tercera = await reenviarLaCola()

    expect(tercera.rechazados).toBe(1)
    expect(cambiosDeLaCola()[0].estado).toBe('rechazado')
  })

  it('la cola es de cada cuenta y sobrevive a cerrar la sesión', async () => {
    servidor.estado.conectado = false
    await cerrar(22, '2.2')

    await colaDe(null) // cierre de sesión
    expect(cambiosDeLaCola()).toHaveLength(0)
    await colaDe('43') // entra otra persona en el mismo portátil
    expect(cambiosDeLaCola()).toHaveLength(0)
    await colaDe('42') // vuelve el dueño
    expect(cambiosDeLaCola()).toHaveLength(1)
  })

  it('descartar quita el cambio y la copia vuelve a mostrar lo del servidor', async () => {
    await getEtapasContrato(1)
    servidor.estado.conectado = false
    await cerrar(22, '2.2')

    await descartarCambio(cambiosDeLaCola()[0].id)

    const etapas = await conCopiaLocal('/api/contratos/1/etapas', () =>
      Promise.reject(new TypeError('Failed to fetch')),
    )
    expect((etapas as EtapaResponse[])[0].subEtapas[0].estado).toBe('EN_CURSO')
  })
})
