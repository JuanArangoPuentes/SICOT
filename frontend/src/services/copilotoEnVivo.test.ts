import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, setAuthToken } from './api/client'
import { preguntarCopilotoEnVivo } from './documentoService'
import { repetirSiSeCorta } from './segundoPlano'

/**
 * La respuesta en vivo del Copiloto y el reintento ante cortes (ADR-006,
 * revisión del 18-10-2026). En el portátil del Centro el modelo escribe a unos
 * 3 tokens por segundo: el supervisor ve la respuesta formarse en vez de mirar
 * «Pensando…», y un corte del camino ya no se presenta como «Ollama no está».
 */

/** Una respuesta NDJSON que llega en los trozos dados. */
function enTrozos(trozos: string[], status = 200): Response {
  const codificador = new TextEncoder()
  const cuerpo = new ReadableStream<Uint8Array>({
    start(control) {
      for (const t of trozos) control.enqueue(codificador.encode(t))
      control.close()
    },
  })
  return new Response(cuerpo, { status, headers: { 'Content-Type': 'application/x-ndjson' } })
}

describe('preguntarCopilotoEnVivo', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
    setAuthToken('token')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    setAuthToken(null)
  })

  it('va entregando el texto acumulado y se resuelve con la respuesta completa del evento final', async () => {
    fetchMock.mockResolvedValue(
      enTrozos([
        '{"tipo":"inicio"}\n{"tipo":"espera"}\n',
        '{"tipo":"texto","t":"La póliza "}\n{"tipo":"te',
        'xto","t":"se verifica en SECOP II."}\n',
        '{"tipo":"fin","respuesta":{"respuesta":"La póliza se verifica en SECOP II.","fuente":"MODELO"}}\n',
      ]),
    )
    const vistos: string[] = []

    const r = await preguntarCopilotoEnVivo(1, '¿la póliza?', undefined, { idSolicitud: 'abc' }, (t) => vistos.push(t))

    expect(vistos).toEqual(['La póliza ', 'La póliza se verifica en SECOP II.'])
    expect(r).toEqual({ respuesta: 'La póliza se verifica en SECOP II.', fuente: 'MODELO' })
    const [url, opciones] = fetchMock.mock.calls[0]
    expect(url).toMatch(/\/api\/contratos\/1\/copiloto\/chat\/en-vivo$/)
    expect(JSON.parse(opciones.body)).toMatchObject({ pregunta: '¿la póliza?', idSolicitud: 'abc' })
  })

  it('un evento de error sale como ApiError con su código y su mensaje', async () => {
    fetchMock.mockResolvedValue(
      enTrozos([
        '{"tipo":"inicio"}\n{"tipo":"error","status":503,"mensaje":"El servicio de IA no está disponible."}\n',
      ]),
    )

    await expect(preguntarCopilotoEnVivo(1, 'hola', undefined, undefined, () => {})).rejects.toMatchObject({
      status: 503,
      message: 'El servicio de IA no está disponible.',
    })
  })

  it('si la respuesta se corta antes del evento final, sale como fallo de red para repetirla', async () => {
    fetchMock.mockResolvedValue(enTrozos(['{"tipo":"inicio"}\n{"tipo":"texto","t":"La pó"}\n']))

    await expect(preguntarCopilotoEnVivo(1, 'hola', undefined, undefined, () => {})).rejects.toBeInstanceOf(TypeError)
  })

  it('con un servidor sin respuesta en vivo (404 de ruta) hace la pregunta de siempre', async () => {
    fetchMock
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ status: 404, message: 'La ruta solicitada no existe.' }), {
          status: 404,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ respuesta: 'Hola.', fuente: 'MODELO' }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )

    const r = await preguntarCopilotoEnVivo(1, 'hola', undefined, { idSolicitud: 'x1' }, () => {})

    expect(r.respuesta).toBe('Hola.')
    expect(fetchMock.mock.calls[1][0]).toMatch(/\/copiloto\/chat$/)
    expect(JSON.parse(fetchMock.mock.calls[1][1].body).idSolicitud).toBe('x1')
  })

  it('un contrato ajeno (404 del recurso) no se reintenta por la otra ruta', async () => {
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({ status: 404, message: 'El recurso solicitado no existe o no tiene acceso a él.' }),
        {
          status: 404,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    )

    await expect(preguntarCopilotoEnVivo(1, 'hola', undefined, undefined, () => {})).rejects.toMatchObject({
      status: 404,
    })
    expect(fetchMock).toHaveBeenCalledOnce()
  })
})

describe('repetirSiSeCorta', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  const avisos = () => ({ alCortarsePorSegundoPlano: vi.fn(), alCortarseLaConexion: vi.fn() })

  it('un corte del camino tras una espera larga se repite con la misma petición y avisa una vez', async () => {
    vi.useFakeTimers()
    const a = avisos()
    let llamadas = 0
    const peticion = vi.fn(async () => {
      llamadas++
      if (llamadas === 1) {
        // El intermediario cortó después de 100 s esperando.
        vi.setSystemTime(Date.now() + 100_000)
        throw new ApiError(524, 'El servidor de SICOT no respondió a tiempo (error 524 del intermediario).')
      }
      return 'respuesta'
    })

    const promesa = repetirSiSeCorta(peticion, a)
    await vi.runAllTimersAsync()

    await expect(promesa).resolves.toBe('respuesta')
    expect(peticion).toHaveBeenCalledTimes(2)
    expect(a.alCortarseLaConexion).toHaveBeenCalledOnce()
  })

  it('una respuesta del servidor (503 de Ollama, con su JSON) no se repite', async () => {
    const a = avisos()
    const error = new ApiError(503, 'El servicio de IA no está disponible.', {
      timestamp: '',
      status: 503,
      error: 'Service Unavailable',
      message: 'El servicio de IA no está disponible.',
      path: '/api/contratos/1/copiloto/chat',
      fieldErrors: null,
    })
    const peticion = vi.fn(async () => {
      throw error
    })

    await expect(repetirSiSeCorta(peticion, a)).rejects.toBe(error)
    expect(peticion).toHaveBeenCalledOnce()
    expect(a.alCortarseLaConexion).not.toHaveBeenCalled()
  })

  it('si el servidor no está al alcance desde el primer intento, lo dice tras dos reintentos y sin esperar minutos', async () => {
    vi.useFakeTimers()
    const a = avisos()
    const peticion = vi.fn(async () => {
      throw new TypeError('Failed to fetch')
    })

    const promesa = repetirSiSeCorta(peticion, a)
    const resultado = expect(promesa).rejects.toBeInstanceOf(TypeError)
    await vi.runAllTimersAsync()
    await resultado

    expect(peticion).toHaveBeenCalledTimes(3)
    expect(a.alCortarseLaConexion).not.toHaveBeenCalled()
  })
})
