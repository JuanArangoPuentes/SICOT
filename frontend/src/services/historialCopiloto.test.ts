import { beforeEach, describe, expect, it, vi } from 'vitest'
import { preguntarCopiloto } from './documentoService'
import { apiFetch } from './api/client'
import { historialParaElCopiloto, TURNOS_DE_HISTORIAL } from './historialCopiloto'
import type { ChatMsg } from '@/types/domain'

vi.mock('./api/client', () => ({
  apiFetch: vi.fn(),
  apiFetchBlob: vi.fn(),
}))

/** Turnos que el backend recibió en la última llamada al chat. */
function historialEnviado(): Array<{ rol: string; texto: string }> {
  const [, opciones] = vi.mocked(apiFetch).mock.calls.at(-1) ?? []
  return JSON.parse(String(opciones?.body)).historial
}

/** Una conversación real de `n` preguntas, cada una con su respuesta del modelo. */
function conversacion(n: number): ChatMsg[] {
  return Array.from({ length: n }, (_, i): ChatMsg[] => [
    { role: 'user', text: `Pregunta ${i + 1}` },
    { role: 'ai', text: `Respuesta ${i + 1}`, origen: 'modelo' },
  ]).flat()
}

describe('preguntarCopiloto — el historial que sale hacia el backend', () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset()
    vi.mocked(apiFetch).mockResolvedValue({ respuesta: 'ok' })
  })

  /**
   * ChatRequest rechaza con 400 un historial de más de 80 turnos, y el panel
   * mandaba el chat entero: bienvenida, la guía de cada sub-paso, los cierres
   * de paso y los avisos. Un supervisor que recorre el contrato con el
   * tutorial pasa de 80 mensajes, y desde ahí cada pregunta fallaba.
   */
  it('nunca manda más turnos de los que el backend usa', async () => {
    await preguntarCopiloto(1, '¿Y ahora?', conversacion(60))

    expect(historialEnviado().map((t) => t.texto)).toEqual([
      'Pregunta 57',
      'Respuesta 57',
      'Pregunta 58',
      'Respuesta 58',
      'Pregunta 59',
      'Respuesta 59',
      'Pregunta 60',
      'Respuesta 60',
    ])
  })
})

describe('historialParaElCopiloto', () => {
  const bienvenida: ChatMsg = { role: 'ai', text: 'Este es su panel de supervisión…' }
  const guia = (id: string): ChatMsg => ({ role: 'ai', text: `Sub-paso ${id} — …`, origen: 'guia' })
  const aviso: ChatMsg = { role: 'ai', text: 'Generé y firmé «Acta de Inicio»…' }
  const error: ChatMsg = { role: 'ai', text: 'No pude responder: No se pudo conectar con el Copiloto IA (Ollama).' }

  /**
   * Lo que SICOT escribe por su cuenta no es conversación. Un «No pude
   * responder» en el historial, además, le haría creer al modelo que fue él
   * quien lo dijo.
   */
  it('deja fuera la bienvenida, los avisos y los errores', () => {
    const chat: ChatMsg[] = [
      bienvenida,
      { role: 'user', text: '¿Qué es un CDP?' },
      error,
      { role: 'user', text: '¿Qué es un CDP?' },
      { role: 'ai', text: 'El certificado de disponibilidad presupuestal.', origen: 'modelo' },
      aviso,
    ]

    expect(historialParaElCopiloto(chat).map((m) => m.text)).toEqual([
      '¿Qué es un CDP?',
      '¿Qué es un CDP?',
      'El certificado de disponibilidad presupuestal.',
    ])
  })

  /**
   * La guía termina con «Si necesita más detalle, pregúnteme abajo»: la
   * pregunta que sigue («¿y eso quién lo firma?») no se entiende sin ella. Las
   * guías de sub-pasos ya cerrados solo ocuparían turnos.
   */
  it('conserva solo la guía del sub-paso más reciente, en su sitio', () => {
    const chat: ChatMsg[] = [
      guia('2.1'),
      guia('2.2'),
      { role: 'user', text: '¿Quién hace esto?' },
      { role: 'ai', text: 'Gestión.', origen: 'modelo' },
      guia('2.3'),
      { role: 'user', text: '¿Y eso quién lo firma?' },
    ]

    expect(historialParaElCopiloto(chat).map((m) => m.text)).toEqual([
      '¿Quién hace esto?',
      'Gestión.',
      'Sub-paso 2.3 — …',
      '¿Y eso quién lo firma?',
    ])
  })

  /** Se recorta lo que queda después de filtrar: los avisos no le quitan sitio a la conversación. */
  it('cuenta los turnos sobre la conversación, no sobre lo que se ve en pantalla', () => {
    const chat = conversacion(4).flatMap((m) => [m, aviso, error])

    const historial = historialParaElCopiloto(chat)

    expect(historial).toHaveLength(TURNOS_DE_HISTORIAL)
    expect(historial.every((m) => m.role === 'user' || m.origen === 'modelo')).toBe(true)
  })
})
