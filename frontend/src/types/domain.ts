// Tipos de dominio compartidos por toda la aplicación SICOT.
// Extraído 1:1 desde el App.tsx original de Figma Make — sin cambios de lógica.

import type { AccionCopiloto } from '@/services/api/types'

// Vistas de cada panel. Son los segmentos que aparecen en la URL
// (/supervisor/alertas, /admin/usuarios) — ver docs/decisiones/ADR-007.
//
// Ya no existe un tipo `Screen`: qué pantalla se ve lo decide la ruta, no una
// variable de estado.
/** Vistas del panel del Supervisor — una por entrada de la barra lateral. */
export type Tab = 'bandeja' | 'contrato' | 'alertas' | 'documentos' | 'registros'

export type AdminTab = 'dashboard' | 'seguimiento' | 'documentos' | 'usuarios' | 'firmas'
export type UploadState = 'idle' | 'analyzing' | 'detect' | 'review' | 'done'

export interface SubStep {
  id: string
  label: string
  responsible: string
  document: string
  completed: boolean
  // Descripción de la subetapa en la plantilla GCCON-P-010 del backend; la usa
  // la guía del tutorial (data/guiaSubPaso.ts) sin tener que preguntarle al modelo.
  description?: string
  // true = the copiloto generates this document and the supervisor only signs it
  aiGenerated?: boolean
  // id numérico de la subetapa en el backend (para PATCH /api/subetapas/{id}/estado)
  apiId?: number
}

export interface Step {
  id: number
  title: string
  subSteps: SubStep[]
  status: 'completed' | 'active' | 'pending'
}

export interface ChatMsg {
  role: 'ai' | 'user'
  text: string
  /**
   * De dónde sale un mensaje del Copiloto. Decide cuáles le llegan al modelo
   * como conversación previa (ver services/historialCopiloto.ts) y cuáles
   * llevan en pantalla la marca «Respuesta del sistema».
   *
   * - `'modelo'`: la respuesta del modelo a una pregunta.
   * - `'sistema'`: la respuesta que el servidor armó sin modelo (fichas de
   *   documento, guía del paso, órdenes). Se marca, para no presentar como IA
   *   lo que es texto fijo, y no se le reenvía al modelo.
   * - `'guia'`: la guía de un sub-paso, armada con el procedimiento.
   * - sin origen: lo que SICOT escribe por su cuenta (bienvenida, avisos,
   *   errores, cierres de paso). No es conversación y no se le manda.
   */
  origen?: 'modelo' | 'sistema' | 'guia'
  /** Lo que la respuesta ofrece abrir: se pinta como botón y solo se ejecuta al pulsarlo. */
  accion?: AccionCopiloto
}
