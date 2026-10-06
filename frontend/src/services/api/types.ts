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
  | 'VENCIMIENTO'
  | 'DOCUMENTO'
  | 'FACTURA'
  | 'FIRMA'
  | 'IA'
  | 'SECOP'
  | 'RECORDATORIO'
  | 'SOLICITUD'
  | 'RECHAZADO'
  | 'CRONOGRAMA'

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

/**
 * Cuerpo de `PUT /api/contratos/{id}` — la corrección de los datos generales.
 *
 * Es un reemplazo completo, no un parche: el backend asigna todos estos campos
 * con lo que llegue, así que un campo omitido se guarda como null. Quien lo use
 * tiene que mandar siempre el contrato entero, no solo lo que cambió.
 *
 * No lleva `supervisorId`: el supervisor se cambia con
 * `PATCH /api/contratos/{id}/supervisor`, que es lo único que avisa al
 * supervisor nuevo.
 */
export interface ActualizarContratoRequest {
  numeroContrato: string
  objeto: string
  valor: number
  fechaInicio: string | null
  fechaFin: string | null
  tipoContrato?: string | null
  contratista?: string | null
  contratistaNit?: string | null
  representanteLegal?: string | null
  lugarEjecucion?: string | null
  numeroRegistroPresupuestal?: string | null
  fechaRegistroPresupuestal?: string | null
  centroCosto?: string | null
}

export interface CambiarEstadoContratoRequest {
  estado: EstadoContrato
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
  /** Entrada del catálogo institucional que el documento representa. null si no es la instancia de un formato oficial. */
  formatoId: number | null
  /** Código del formato institucional (GCCON-F-031, GCCON-F-018…). null si no tiene formato asociado. */
  formatoCodigo: string | null
  /** Nombre del formato institucional. null si no tiene formato asociado. */
  formatoNombre: string | null
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
  /** Cuándo se tomó la foto, según su EXIF. null si no es una foto o no trae el dato. */
  capturaFecha: string | null
  /** Dónde se tomó la foto, según su GPS. Las dos o ninguna; null si no trae el dato. */
  capturaLatitud: number | null
  capturaLongitud: number | null
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
  /**
   * Lo que el supervisor escribió sobre lo que hizo en el paso. El backend lo
   * usa para el apartado de observaciones del documento; sin notas, ese
   * apartado queda marcado como pendiente en vez de inventarse.
   */
  notas?: string | null
  /**
   * Datos del documento que el contrato no tiene —factura, póliza, cédulas…—,
   * por clave (ver `PlantillaDocumento.campos`). Los que falten salen en el PDF
   * como «[dato pendiente…]», en rojo, en vez de inventarse.
   */
  datos?: Record<string, string>
  /**
   * false: las observaciones van tal como el supervisor las escribió, sin
   * pasar por el Copiloto («Usar mis notas tal cual»). Sin valor, se redactan.
   */
  redactarConIa?: boolean
  /**
   * Filas de las tablas del formato (ver `PlantillaDocumento.tablas`). Una
   * tabla sin filas sale en el PDF como «[dato pendiente…]».
   */
  tablas?: TablasDelDocumento
}

/**
 * El documento recién generado y cómo quedaron sus observaciones. El panel
 * muestra la redacción del Copiloto antes de firmar: ninguna comprobación
 * automática ve que cambie el sujeto de una frase, y quien firma sí.
 */
export interface DocumentoGeneradoResponse extends DocumentoResponse {
  /** El texto que quedó en el apartado de observaciones, o null si no hay. */
  observaciones: string | null
  /** Si ese texto lo redactó el Copiloto (false: son las notas tal cual). */
  observacionesRedactadasConIa: boolean
  /** Por qué van las notas tal cual cuando se pidió la redacción, o null. */
  motivoNotasTalCual: string | null
  /**
   * SHA-256 del borrador tal como quedó. Se devuelve al firmar para que se
   * firme exactamente lo que el supervisor leyó.
   */
  huellaDelBorrador: string
}

/** Un turno de la conversación previa que se le manda al Copiloto. */
export interface ChatTurno {
  rol: 'user' | 'ai'
  texto: string
}

/** Pregunta al Copiloto (POST /api/contratos/{id}/copiloto/chat). */
export interface ChatRequest {
  /** Lo que escribió el supervisor; en una revisión del paso, solo su descripción. */
  pregunta: string
  historial?: ChatTurno[]
  /**
   * Uno por pregunta, generado aquí. El reintento automático al volver de
   * segundo plano repite el mismo: el servidor reconoce la pregunta y devuelve
   * la inferencia que sigue en curso o la ya calculada, en vez de lanzar otra.
   */
  idSolicitud?: string
  /**
   * Paso (1..6) que el supervisor quiere cerrar: la petición es la revisión
   * consultiva de ese paso. Las instrucciones de la revisión las arma el
   * servidor; el cliente no las escribe.
   */
  revisarPaso?: number
}

/** Quién escribió la respuesta: SICOT con datos fijos, sin modelo, o el modelo de IA. */
export type FuenteRespuestaCopiloto = 'SISTEMA' | 'MODELO'

export type TipoAccionCopiloto =
  | 'IR_A_PASO'
  | 'IR_A_SUBPASO'
  | 'ABRIR_DOCUMENTO'
  | 'ABRIR_EVIDENCIA'
  | 'MOSTRAR_ALERTAS'
  | 'MOSTRAR_DOCUMENTOS'
  | 'DESCARGAR_DOCUMENTO'
  | 'IR_A_CONFIGURACION'

/**
 * La pantalla que el Copiloto ofrece abrir junto a su respuesta. Ninguna
 * acción firma, marca ni genera nada: solo lleva a donde el supervisor decide.
 * La interfaz la pinta como un botón con `etiqueta` y la ejecuta solo cuando
 * él lo pulsa, nunca al llegar la respuesta.
 */
export interface AccionCopiloto {
  tipo: TipoAccionCopiloto
  paso: number | null
  /** Código de la subetapa, «N.M». */
  subpaso: string | null
  /** Clave del catálogo de documentos del backend (ACTA_INICIO…). */
  documentoTipo: string | null
  /** Solo en DESCARGAR_DOCUMENTO, y de un documento que existe. */
  documentoId: number | null
  etiqueta: string
}

export interface ChatResponse {
  respuesta: string
  // Opcionales a propósito: el APK y el instalador de escritorio pueden hablar
  // con un servidor anterior al 02-10-2026, que solo manda `respuesta`.
  fuente?: FuenteRespuestaCopiloto
  accion?: AccionCopiloto | null
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

export type OrigenRegistro = 'USUARIO' | 'SISTEMA'

export interface RegistroResponse {
  id: number
  contratoId: number
  usuarioId: number | null
  usuarioNombre: string | null
  accion: string
  descripcion: string | null
  fecha: string
  // Quién produjo la entrada. Existe porque `usuarioNombre` nulo es ambiguo:
  // puede ser el sistema, o una persona cuya cuenta se borró — `usuario_id`
  // es ON DELETE SET NULL. Sin este campo, el panel atribuía al "Sistema"
  // acciones de personas que ya no están en la organización.
  origen: OrigenRegistro
}

// ─── Cronograma ──────────────────────────────────────────────────────────────

export type SemaforoCronograma = 'SIN_DATOS' | 'VERDE' | 'AMARILLO' | 'ROJO'

/**
 * Estado de cronograma calculado POR EL BACKEND.
 *
 * Antes el panel lo calculaba por su cuenta, con un criterio distinto al de la
 * alerta persistida: SICOT podía decir "va a tiempo" en la pantalla y
 * "atrasado 39 puntos" en la bandeja del mismo contrato. El cálculo vive ahora
 * una sola vez, del lado que FR-002 declara autoridad de las reglas de negocio.
 */
export interface CronogramaResponse {
  semaforo: SemaforoCronograma
  fraccionDePlazo: number | null
  fraccionDeAvance: number | null
  brecha: number | null
  etapaActual: number | null
  cierreEsperado: string | null
  diasDeAtraso: number | null
  mensaje: string
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

// ─── Seguimiento de supervisores (Administrador) ─────────────────────────────
//
// GET /api/seguimiento/supervisores. El backend lo resuelve en un puñado de
// consultas agrupadas; armarlo aquí con los endpoints por contrato serían seis
// peticiones por contrato abierto cada vez que se abre la pestaña.

export interface DocumentoResumen {
  id: number
  contratoId: number
  /** Código de la subetapa («2.7»), o null si el documento se cargó sin subetapa. */
  subetapaCodigo: string | null
  nombre: string
  estado: EstadoDocumento
  generadoPorIa: boolean
  firmado: boolean
  fechaSubida: string
  fechaFirma: string | null
}

export interface UltimaActividad {
  contratoId: number
  fecha: string
  accion: string
  descripcion: string | null
}

export interface ContratoSeguimiento {
  id: number
  numeroContrato: string
  objeto: string
  contratista: string | null
  estado: EstadoContrato
  valor: number
  fechaInicio: string | null
  fechaFin: string | null
  cronograma: CronogramaResponse | null
  subetapasCompletadas: number
  subetapasTotales: number
  etapaActual: number | null
  etapaActualNombre: string | null
  subetapaEnCurso: SubetapaResponse | null
  etapas: EtapaResponse[]
  documentos: DocumentoResumen[]
  alertasSinLeer: number
  ultimaActividad: UltimaActividad | null
}

export interface SupervisorSeguimiento {
  id: number
  nombre: string
  email: string
  activo: boolean
  firmaVigente: boolean
  contratosFinalizados: number
  contratos: ContratoSeguimiento[]
}

export interface SeguimientoResponse {
  supervisores: SupervisorSeguimiento[]
  contratosSinSupervisor: ContratoSeguimiento[]
  generadoEn: string
}

/** Un documento formal que SICOT arma y los datos que pide que el contrato no tiene (GET /api/ia/plantillas). */
export interface PlantillaDocumento {
  tipo: string
  codigo: string
  nombre: string
  /** Si el formato tiene un apartado donde van las notas del supervisor. */
  llevaObservaciones: boolean
  /**
   * `opcional`: si falta no deja nada pendiente (se deduce de otros datos o no
   * siempre aplica). `dependeDe`: el opcional pasa a obligatorio cuando ese
   * otro dato dice que sí (con una adición, el valor actualizado). `porDocumento`:
   * cambia en cada documento (número de informe, factura…), así que no se
   * recuerda para el siguiente. `largo`: es un párrafo (el cumplimiento del
   * SIGA), no un dato de una línea.
   */
  campos: Array<{
    clave: string
    etiqueta: string
    ejemplo: string
    opcional: boolean
    dependeDe: string | null
    porDocumento: boolean
    largo?: boolean
  }>
  /**
   * Tablas del formato que se llenan fila por fila (obligaciones, amparos,
   * órdenes de pago). En cada columna, `delContrato` dice si lo escrito es del
   * contrato (el texto de la obligación) y se ofrece en el siguiente documento,
   * o de este (lo hecho en el periodo) y no se arrastra. Opcional para tolerar
   * un backend anterior al 30-09-2026, que no las enviaba.
   */
  tablas?: Array<{
    clave: string
    etiqueta: string
    ayuda: string
    columnas: Array<{ etiqueta: string; ejemplo: string; delContrato: boolean }>
  }>
}

/** Filas de las tablas de un documento, por clave de tabla; cada fila, sus celdas en el orden de las columnas. */
export type TablasDelDocumento = Record<string, string[][]>
