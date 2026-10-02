// Qué parte del chat se le manda al Copiloto como conversación previa.
//
// El panel mandaba el chat entero en cada pregunta. Eso tenía dos problemas.
// El primero era un fallo seguro: ChatRequest rechaza con 400 un historial de
// más de 80 turnos, y el tutorial solo ya deja 57 mensajes al recorrer los
// seis pasos: la bienvenida, la guía de cada uno de los 27 sub-pasos, la
// revisión de cada paso (petición, descripción y respuesta), los cinco avisos
// de documento firmado y los seis cierres. Con una docena de preguntas más se
// pasa de 80, y desde ahí ninguna pregunta llegaba al modelo. El segundo era
// de calidad: el backend solo usa los últimos turnos (CopilotoChatService), y
// si esos son guías y avisos, la conversación de verdad queda fuera.
//
// Se manda lo que el supervisor escribió y lo que el modelo le respondió. De
// lo que SICOT escribe por su cuenta solo va la guía del sub-paso más
// reciente: termina con «Si necesita más detalle, pregúnteme abajo», y la
// pregunta que sigue («¿y eso quién lo firma?») no se entiende sin ella. Las
// guías anteriores, la bienvenida, los avisos y los errores se quedan en
// pantalla: el modelo ya recibe el procedimiento y el estado real de las
// etapas en su prompt, y un «No pude responder…» en el historial le haría
// creer que fue él quien lo dijo.
//
// Tampoco van las respuestas que el servidor armó sin modelo (origen
// 'sistema': fichas de documento, guía del paso, órdenes), ni la pregunta que
// las pidió. Son textos fijos de unos cientos de tokens que nunca pasaron por
// Ollama, así que no están en su caché: cuatro fichas tocadas en las
// sugerencias le sumaban más de un minuto de CPU a la siguiente pregunta
// abierta (auditoría del 02-10-2026). Y la pregunta sola, sin su respuesta,
// el modelo la tomaría por pendiente y la contestaría otra vez.

import type { ChatMsg } from '@/types/domain'

// Los mismos turnos que usa el backend (MAX_TURNOS_HISTORIAL en
// CopilotoChatService.java): mandar más no le da más memoria al Copiloto, solo
// acerca el tope de 80 de ChatRequest. Si cambia allí, cambia aquí.
export const TURNOS_DE_HISTORIAL = 8

export function historialParaElCopiloto(mensajes: readonly ChatMsg[]): ChatMsg[] {
  let ultimaGuia = -1
  mensajes.forEach((m, i) => {
    if (m.role === 'ai' && m.origen === 'guia') ultimaGuia = i
  })
  return mensajes
    .filter(
      (m, i) => (m.role === 'user' && !laRespondioElSistema(mensajes, i)) || m.origen === 'modelo' || i === ultimaGuia,
    )
    .slice(-TURNOS_DE_HISTORIAL)
}

/**
 * ¿La pregunta `i` la contestó el servidor sin modelo? Su respuesta es el
 * primer mensaje con origen de respuesta antes de la pregunta siguiente; los
 * avisos de SICOT que haya en medio («la respuesta se cortó…») no cuentan.
 */
function laRespondioElSistema(mensajes: readonly ChatMsg[], i: number): boolean {
  for (const m of mensajes.slice(i + 1)) {
    if (m.role === 'user') return false
    if (m.origen === 'sistema') return true
    if (m.origen === 'modelo') return false
  }
  return false
}
