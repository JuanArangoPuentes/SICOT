// Tipos de la API del backend SICOT — espejo de los DTO de co.sena.sicot.dto.
// Fuente de verdad: el backend (Spring Boot 3.5.3, puerto 8080).

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
  telefono: string
  rol: Rol
}

export interface ActualizarUsuarioRequest {
  nombre: string
  email: string
  /** Opcional: si viene, reemplaza la contraseña actual (8 a 100 caracteres). */
  password?: string
  telefono: string
  rol: Rol
}

export interface CambiarEstadoUsuarioRequest {
  activo: boolean
}

export interface EnviarCredencialesRequest {
  password: string
}

export interface EnviarCredencialesResponse {
  enviado: boolean
  error: string | null
}

export interface UsuarioResponse {
  id: number
  nombre: string
  email: string
  telefono: string | null
  rol: Rol
  activo: boolean
  fechaCreacion: string
}

// ─── Firmas electrónicas ─────────────────────────────────────────────────────

export interface CrearFirmaRequest {
  usuarioId: number
}

export interface CambiarEstadoFirmaRequest {
  activa: boolean
}

export interface MiFirmaResponse {
  tieneFirmaActiva: boolean
  firmaId: string | null
}

export interface FirmaResponse {
  id: number
  usuarioId: number
  usuarioNombre: string
  usuarioEmail: string
  firmaId: string
  activa: boolean
  asignadoPorId: number | null
  asignadoPorNombre: string | null
  fechaAsignacion: string
}

// ─── Contratos ───────────────────────────────────────────────────────────────

export interface CrearContratoRequest {
  numeroContrato: string
  objeto: string
  valor: number
  fechaInicio: string | null
  fechaFin: string | null
  supervisorId: number | null
  // Identificación real (Acta de Inicio / Informe de Supervisión SENA) — opcionales
  tipoContrato?: string | null
  contratista?: string | null
  contratistaNit?: string | null
  representanteLegal?: string | null
  lugarEjecucion?: string | null
  numeroRegistroPresupuestal?: string | null
  fechaRegistroPresupuestal?: string | null
  centroCosto?: string | null
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
  tipoContrato: string | null
  contratista: string | null
  contratistaNit: string | null
  representanteLegal: string | null
  lugarEjecucion: string | null
  numeroRegistroPresupuestal: string | null
  fechaRegistroPresupuestal: string | null
  centroCosto: string | null
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
  tamanioBytes: number | null
  generadoPorIa: boolean
  firmaId: string | null
  fechaFirma: string | null
  /** Huella SHA-256 registrada al firmar. null si el documento no está firmado. */
  firmaHashSha256: string | null
  /** Quién firmó. null si no está firmado. */
  firmadoPorNombre: string | null
  subidoPorNombre: string | null
  fechaSubida: string
}

/**
 * Estado de integridad de un documento firmado — el backend recalcula el
 * SHA-256 del contenido y lo compara con la huella registrada al firmar.
 *
 * NO_VERIFICABLE no significa "está bien": son documentos firmados antes de que
 * el sistema registrara la huella, y su integridad no se puede confirmar ni
 * descartar. Debe mostrarse distinto de INTEGRO.
 */
export type EstadoIntegridad = 'INTEGRO' | 'ALTERADO' | 'SIN_FIRMA' | 'NO_VERIFICABLE'

export interface VerificacionIntegridadResponse {
  documentoId: number
  nombre: string
  estado: EstadoIntegridad
  hashRegistrado: string | null
  hashActual: string | null
  firmaId: string | null
  fechaFirma: string | null
  firmadoPorNombre: string | null
  mensaje: string
}

// ─── IA (Ollama local) ────────────────────────────────────────────────────────

export interface ExtraccionContratoResponse {
  idContrato: string | null
  objeto: string | null
  proveedor: string | null
  nit: string | null
  representanteLegal: string | null
  valor: string | null
  vigenciaInicio: string | null
  vigenciaFin: string | null
  lugarEjecucion: string | null
  registroPresupuestal: string | null
  tipoContrato: string | null
}

export interface GenerarDocumentoRequest {
  tipo: string
  subetapaId: number | null
}

export interface ChatResponse {
  respuesta: string
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
