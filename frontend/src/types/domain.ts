// Tipos de dominio compartidos por toda la aplicación SICOT.
// Extraído 1:1 desde el App.tsx original de Figma Make — sin cambios de lógica.

// Vistas de cada panel. Son los segmentos que aparecen en la URL
// (/supervisor/alertas, /admin/usuarios) — ver docs/decisiones/ADR-007.
//
// Ya no existe un tipo `Screen`: qué pantalla se ve lo decide la ruta, no una
// variable de estado.
/** Vistas del panel del Supervisor — una por entrada de la barra lateral. */
export type Tab = 'bandeja' | 'contrato' | 'alertas' | 'documentos' | 'registros'

export type AdminTab = 'dashboard' | 'documentos' | 'usuarios' | 'firmas'
export type UploadState = 'idle' | 'uploading' | 'analyzing' | 'detect' | 'review' | 'done'

export interface SubStep {
  id: string
  label: string
  responsible: string
  document: string
  completed: boolean
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

export interface ChatMsg { role: 'ai' | 'user'; text: string }
