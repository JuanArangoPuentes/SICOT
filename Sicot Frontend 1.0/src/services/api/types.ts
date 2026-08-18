// Tipos de la API del backend SICOT — espejo de los DTO de co.sena.sicot.dto.
// Fuente de verdad: sicot-backend (Spring Boot 3.5.3, puerto 8080).

export type Rol = 'ADMINISTRADOR' | 'GESTION' | 'SUPERVISOR'
export type EstadoContrato = 'BORRADOR' | 'ACTIVO' | 'SUSPENDIDO' | 'FINALIZADO' | 'CANCELADO'
export type EstadoEtapa = 'PENDIENTE' | 'EN_CURSO' | 'COMPLETADA'
export type EstadoSubetapa = 'PENDIENTE' | 'EN_CURSO' | 'COMPLETADA'
export type TipoDocumento = 'PDF' | 'DOCX' | 'XLSX' | 'IMAGEN' | 'OTRO'
export type EstadoDocumento = 'PENDIENTE' | 'APROBADO' | 'RECHAZADO'
export type PrioridadAlerta = 'ALTA' | 'MEDIA' | 'BAJA'
export type TipoAlerta =
  | 'VENCIMIENTO' | 'DOCUMENTO' | 'FACTURA' | 'FIRMA' | 'IA'
  | 'SECOP' | 'RECORDATORIO' | 'SOLICITUD' | 'RECHAZADO' | 'CRONOGRAMA'

// ─── Auth ────────────────────────────────────────────────────────────────────

export interface LoginRequest {
  email: string
  password: string
}

export interface AuthResponse {
  token: string
  usuarioId: number
  nombre: string
  email: string
  rol: Rol
}

// ─── Usuarios ────────────────────────────────────────────────────────────────

export interface CrearUsuarioRequest {
  nombre: string
  email: string
  password: string
  rol: Rol
}

export interface CambiarEstadoUsuarioRequest {
  activo: boolean
}

export interface UsuarioResponse {
  id: number
  nombre: string
  email: string
  rol: Rol
  activo: boolean
  fechaCreacion: string
}

// ─── Contratos ───────────────────────────────────────────────────────────────

export interface CrearContratoRequest {
  numeroContrato: string
  objeto: string
  valor: number
  fechaInicio: string | null
  fechaFin: string | null
  supervisorId: number | null
}

export interface ContratoResponse {
  id: number
  numeroContrato: string
  objeto: string
  valor: number
  fechaInicio: string
  fechaFin: string
  estado: EstadoContrato
  supervisorId: number | null
  supervisorNombre: string | null
  supervisorEmail: string | null
  fechaCreacion: string
}

// ─── Etapas / Subetapas ──────────────────────────────────────────────────────

export interface ActualizarEstadoSubetapaRequest {
  estado: EstadoSubetapa
}

export interface SubetapaResponse {
  id: number
  codigo: string
  nombre: string
  descripcion: string
  estado: EstadoSubetapa
  responsable: string
}

export interface EtapaResponse {
  id: number
  numero: number
  nombre: string
  estado: EstadoEtapa
  porcentaje: number
  subEtapas: SubetapaResponse[]
}

// ─── Documentos ──────────────────────────────────────────────────────────────

export interface DocumentoResponse {
  id: number
  contratoId: number
  subetapaId: number | null
  nombre: string
  tipo: TipoDocumento
  rutaArchivo: string
  estado: EstadoDocumento
  fechaSubida: string
}

// ─── Alertas ─────────────────────────────────────────────────────────────────

export interface AlertaResponse {
  id: number
  contratoId: number
  tipo: TipoAlerta
  prioridad: PrioridadAlerta
  mensaje: string
  leida: boolean
  fechaCreacion: string
}

// ─── Registros ───────────────────────────────────────────────────────────────

export interface RegistroResponse {
  id: number
  contratoId: number
  usuarioId: number | null
  usuarioNombre: string | null
  accion: string
  descripcion: string | null
  fecha: string
}

// ─── Formatos documentales (catálogo del Administrador) ──────────────────────

export type EstadoFormato = 'VIGENTE' | 'OBSOLETO'

export interface FormatoDocumentalResponse {
  id: number
  codigo: string
  nombre: string
  version: string
  tipoArchivo: TipoDocumento
  nombreArchivo: string
  tamanioBytes: number
  estado: EstadoFormato
  subidoPorNombre: string | null
  fechaActualizacion: string
}

// ─── Errores (ErrorResponse del backend) ─────────────────────────────────────

export interface ErrorResponse {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  fieldErrors: Record<string, string> | null
}
