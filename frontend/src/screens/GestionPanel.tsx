// Panel de Gestión y Contratación — carga de fichas de contrato, tabla de
// contratos y flujo de asignación al supervisor.
// Extraído 1:1 desde el App.tsx original de Figma Make — sin cambios visuales.

import { useEffect, useRef, useState } from 'react'
import AppShell, { type NavGroup } from '@/components/AppShell'
import { Chip, Modal, type ChipType } from '@/components/ui'
import {
  IconCheckCircle,
  IconClipboardList,
  IconContract,
  IconFileText,
  IconLoader,
  IconPlay,
  IconUpload,
} from '@/components/icons'
import type { UploadState } from '@/types/domain'
import type { AuthResponse, ContratoResponse, EstadoContrato, ExtraccionContratoResponse } from '@/services/api/types'
import {
  getContratos,
  crearContrato,
  actualizarContrato,
  asignarSupervisor,
  cambiarEstadoContrato,
} from '@/services/contratoService'
import { getUsuarios } from '@/services/usuarioService'
import { extraerDatosContrato, subirDocumento } from '@/services/documentoService'
import { ApiError } from '@/services/api/client'
import { formatCOP, formatFecha } from '@/services/format'

// Tipos de contrato seleccionables. Se persisten en el campo `tipoContrato`
// del contrato; son una etiqueta descriptiva, no cambian el flujo.
// «Compraventa» se añadió el 24-09-2026: un acta que decía COMPRAVENTA
// quedaba registrada como «Suministro de Bienes» por no tener dónde caer. La
// misma lista vive en ExtraccionContratoService.TIPOS del backend.
const CONTRACT_TYPES = ['Suministro de Bienes', 'Compraventa', 'Servicios', 'Obras', 'Arrendamiento'] as const

// Columnas del registro de contratos. Antes eran cinco anchos fijos que
// sumaban 820 px más el objeto con lo que sobrara: en un portátil de 1280 px
// el contenedor mide 976 px y al objeto le quedaban 63 px, una palabra por
// línea (medido el 24-09-2026). Con mínimos y proporciones, el objeto se lleva
// la mayor parte del ancho y ninguna columna baja de lo que necesita.
//
// La séptima columna (ACCIONES) se añadió con la corrección y la activación del
// contrato: hasta entonces la tabla solo se podía mirar. Al entrar se llevó el
// poco ancho libre que quedaba y el objeto bajó a 195 px, así que la prueba de
// extremo a extremo que vigila esos 200 px falló: los mínimos sumaban 900 px más
// 60 px de separaciones sobre los 976 px que mide el contenedor a 1280 px. Se
// recortó lo que cada columna podía ceder sin apretar su contenido y el mínimo
// del objeto subió a 210 px, para que el margen no dependa del ancho sobrante.
const COLUMNAS_REGISTRO =
  'minmax(130px, 1.2fr) minmax(210px, 2.6fr) minmax(110px, 1.2fr) minmax(85px, 0.8fr) minmax(95px, 0.9fr) minmax(115px, 1.1fr) minmax(120px, 1fr)'

// Las 6 etapas reales del procedimiento GCCON-P-010, espejo de
// `GcconP010Plantilla` en el backend.
//
// Antes aquí había una "SECUENCIA RECOMENDADA" distinta por tipo de contrato
// (4 etapas inventadas: Inspección Técnica, Verificación de Canon, etc.). Era
// ficticia: `ContratoService.crear` llama siempre a
// `GcconP010Plantilla.crearEtapas`, que ni siquiera lee `tipoContrato`. Todos
// los contratos reciben exactamente estas 6 etapas, sin excepción.
const ETAPAS_GCCON_P010 = [
  'Inicio — Designación de la supervisión (GCCON-F-032)',
  'Inicio — Acta de Inicio y arranque (GCCON-F-018)',
  'Inspección — Monitoreo y Ejecución',
  'Recepción — Acta de Recibo a Satisfacción',
  'Certificación — Cumplimiento y Trámite de Pago',
  'Cierre — Informe Final y Archivo',
]

// Supervisores disponibles para asignación — se cargan desde /api/usuarios
// (solo visible para ADMINISTRADOR; el rol GESTION recibe 403 y crea sin asignar)
interface SupervisorOption {
  id: number
  nombre: string
}

// Solo el que respaldan los documentos reales del Centro (el GCCON-F-030 del
// CO1.PCCNTR.8426076 lo escribe «36-02-00-005-920510 CENTRO TECNOLOGICO DEL
// MOBILIARIO»). Los «920511 — CTMA Ebanistería» y «920512 — CTMA Tapicería»
// venían del prototipo de Figma y no aparecen en ningún documento: se quitaron
// el 18-10-2026 hasta que el Centro los confirme. Ningún valor va por defecto.
const CENTROS_COSTO = ['920510 — Centro Tecnológico del Mobiliario']

const ESTADO_ROW: Record<EstadoContrato, { label: string; type: ChipType }> = {
  ACTIVO: { label: 'Activo', type: 'vigente' },
  BORRADOR: { label: 'Borrador', type: 'pending' },
  SUSPENDIDO: { label: 'Suspendido', type: 'unassigned' },
  FINALIZADO: { label: 'Finalizado', type: 'finished' },
  CANCELADO: { label: 'Cancelado', type: 'inactive' },
}

const mapContratoRow = (c: ContratoResponse) => ({
  // El contrato tal como lo devolvió la API: lo necesitan las acciones de la
  // fila, que mandan de vuelta al backend campos que la tabla no pinta.
  contrato: c,
  id: c.numeroContrato,
  object: c.objeto,
  supervisor: c.supervisorNombre ?? '— Sin asignar —',
  statusLabel: ESTADO_ROW[c.estado].label,
  statusType: ESTADO_ROW[c.estado].type,
  value: formatCOP(c.valor),
  vigencia: `${formatFecha(c.fechaInicio)} – ${formatFecha(c.fechaFin)}`,
})

// Acepta tanto un número limpio como texto descriptivo con el valor embebido
// (ej. lo que a veces devuelve la extracción con IA: "DIEZ MILLONES DE PESOS
// ($10.000.000 COP)") — se queda solo con los dígitos.
//
// Los centavos no se suman a la cifra: «45900300.5» (como lo devuelve la API)
// y «45.900.300,50» (como se escribe en Colombia) son el mismo valor. Hasta el
// 18-10-2026 se borraba también el separador decimal y la corrección de un
// contrato con centavos multiplicaba su valor por diez o por cien.
const parseValor = (texto: string): number | null => {
  if (!texto) return null
  const limpio = texto.trim()
  let n: number
  if (/^\d+(\.\d{1,2})?$/.test(limpio)) {
    n = Number(limpio)
  } else {
    const soloCifra = (limpio.match(/[\d.,]+/g) ?? []).join('')
    const sinMiles = soloCifra
      .replace(/,(\d{1,2})$/, '#$1')
      .replace(/[.,]/g, '')
      .replace('#', '.')
    if (!sinMiles) return null
    n = Number(sinMiles)
  }
  return Number.isFinite(n) && n > 0 ? n : null
}

// El motivo concreto de un 400 de validación (el campo que falla), no el
// «Error de validación en los datos enviados.» genérico.
const motivoDelServidor = (e: unknown, siNoHay: string): string =>
  e instanceof ApiError ? (Object.values(e.detail?.fieldErrors ?? {})[0] ?? e.message) : siNoHay

const displayAIso = (f: string) => {
  const [d, m, y] = f.split('/')
  return `${y}-${m}-${d}`
}

const parseVigencia = (texto: string): { inicio: string | null; fin: string | null } => {
  // Con un separador, cada lado se lee por su cuenta: el acto de designación
  // trae (vía SECOP) la fecha de terminación pero no la de inicio, que llega
  // después con el acta. Exigir las dos tiraba la que sí se conocía.
  const partes = texto.split(/\s[–-]\s|–/)
  const fecha = (p: string | undefined) => {
    const m = p?.match(/\d{2}\/\d{2}\/\d{4}/)
    return m ? displayAIso(m[0]) : null
  }
  if (partes.length >= 2) return { inicio: fecha(partes[0]), fin: fecha(partes[1]) }
  const fechas = texto.match(/\d{2}\/\d{2}\/\d{4}/g)
  if (!fechas || fechas.length < 2) return { inicio: null, fin: null }
  return { inicio: displayAIso(fechas[0]), fin: displayAIso(fechas[1]) }
}

// ¿El nombre que trae la designación es el de este usuario? «Alex Zapata» y
// «Alex Fernando Zapata Rios» son la misma persona: basta con que todos los
// nombres del más corto estén en el más largo (misma regla que el backend al
// verificar contra el SECOP).
const palabras = (s: string) =>
  new Set(
    s
      .normalize('NFD')
      .replace(/\p{M}/gu, '')
      .toUpperCase()
      .replace(/[^A-Z ]/g, ' ')
      .split(/\s+/)
      .filter(Boolean),
  )
const mismaPersona = (a: string, b: string) => {
  const x = palabras(a)
  const y = palabras(b)
  // Un solo nombre en común («Alex») no identifica a nadie: preseleccionaría a
  // la persona equivocada. Se exigen al menos dos.
  if (Math.min(x.size, y.size) < 2) return false
  const [corto, largo] = x.size <= y.size ? [x, y] : [y, x]
  return [...corto].every((p) => largo.has(p))
}

// Etiquetas de los campos que el backend pudo completar desde el SECOP.
const CAMPO_SECOP: Record<string, string> = {
  objeto: 'objeto',
  proveedor: 'contratista',
  nit: 'NIT',
  representanteLegal: 'representante legal',
  valor: 'valor',
  vigenciaFin: 'fecha de terminación',
  tipoContrato: 'tipo de contrato',
  enlaceSecop: 'enlace del proceso',
  supervisorDesignado: 'supervisor',
  correoContratista: 'correo del contratista',
  telefonoContratista: 'teléfono del contratista',
}

// Formulario de corrección de los datos generales. Todos los campos son texto
// porque vienen de <input>; se convierten al enviar.
//
// Lleva TODOS los campos que acepta el PUT, aunque alguno no se vaya a tocar:
// `ContratoService.actualizar` asigna el contrato completo con lo que llegue,
// así que un campo que no se mande se guardaría como null y la corrección de la
// vigencia borraría, por ejemplo, el NIT del contratista.
interface FormularioDeContrato {
  numeroContrato: string
  objeto: string
  valor: string
  fechaInicio: string
  fechaFin: string
  tipoContrato: string
  contratista: string
  contratistaNit: string
  representanteLegal: string
  lugarEjecucion: string
  numeroRegistroPresupuestal: string
  fechaRegistroPresupuestal: string
  centroCosto: string
  enlaceSecop: string
  apoyoSupervision: string
  fechaDesignacion: string
  correoContratista: string
  telefonoContratista: string
}

const formularioDe = (c: ContratoResponse): FormularioDeContrato => ({
  numeroContrato: c.numeroContrato,
  objeto: c.objeto,
  valor: String(c.valor ?? ''),
  fechaInicio: c.fechaInicio ?? '',
  fechaFin: c.fechaFin ?? '',
  tipoContrato: c.tipoContrato ?? '',
  contratista: c.contratista ?? '',
  contratistaNit: c.contratistaNit ?? '',
  representanteLegal: c.representanteLegal ?? '',
  lugarEjecucion: c.lugarEjecucion ?? '',
  numeroRegistroPresupuestal: c.numeroRegistroPresupuestal ?? '',
  fechaRegistroPresupuestal: c.fechaRegistroPresupuestal ?? '',
  centroCosto: c.centroCosto ?? '',
  enlaceSecop: c.enlaceSecop ?? '',
  apoyoSupervision: c.apoyoSupervision ?? '',
  fechaDesignacion: c.fechaDesignacion ?? '',
  correoContratista: c.correoContratista ?? '',
  telefonoContratista: c.telefonoContratista ?? '',
})

/**
 * Lo que va en el PUT para un campo que se manda solo si cambió: sin cambios,
 * undefined (no viaja, y el backend conserva lo que hay); borrado, cadena
 * vacía (el backend lo borra); si no, el valor nuevo.
 */
function cambio(nuevo: string, antes: string | null | undefined): string | undefined {
  const limpio = nuevo.trim()
  return limpio === (antes ?? '').trim() ? undefined : limpio
}

// El valor guardado puede no estar en la lista de opciones: lo escribió otro
// contrato, otra versión de la lista o la extracción con IA. Se añade como
// opción en vez de descartarlo, porque un <select> que no contiene su propio
// valor lo pierde al guardar sin que nadie lo note.
// Campos de texto de la corrección, en el orden en que los busca quien compara
// el registro contra el documento en papel.
const CAMPOS_DE_TEXTO: { label: string; campo: keyof FormularioDeContrato; mono: boolean }[] = [
  { label: 'Número de contrato', campo: 'numeroContrato', mono: true },
  { label: 'Objeto', campo: 'objeto', mono: false },
  { label: 'Contratista', campo: 'contratista', mono: false },
  { label: 'NIT o CC del contratista', campo: 'contratistaNit', mono: true },
  { label: 'Representante legal', campo: 'representanteLegal', mono: false },
  { label: 'Valor', campo: 'valor', mono: true },
  { label: 'Lugar de ejecución', campo: 'lugarEjecucion', mono: false },
  { label: 'Número de registro presupuestal', campo: 'numeroRegistroPresupuestal', mono: true },
  { label: 'Enlace del proceso en SECOP II', campo: 'enlaceSecop', mono: true },
  { label: 'Apoyo a la supervisión', campo: 'apoyoSupervision', mono: false },
  { label: 'Correo del contratista', campo: 'correoContratista', mono: true },
  { label: 'Teléfono del contratista', campo: 'telefonoContratista', mono: true },
]

// Fechas con <input type="date"> y no con el campo de texto «dd/mm/aaaa –
// dd/mm/aaaa» de la creación: ese formato libre existe porque la extracción con
// IA devuelve texto, y aquí no hay nada que interpretar.
const CAMPOS_DE_FECHA: { label: string; campo: keyof FormularioDeContrato }[] = [
  { label: 'Inicio de la vigencia', campo: 'fechaInicio' },
  { label: 'Fin de la vigencia', campo: 'fechaFin' },
  { label: 'Fecha del registro presupuestal', campo: 'fechaRegistroPresupuestal' },
  { label: 'Fecha de la designación', campo: 'fechaDesignacion' },
]

const opcionesCon = (opciones: readonly string[], valor: string): string[] =>
  valor && !opciones.includes(valor) ? [valor, ...opciones] : [...opciones]

// AAAA-MM-DD (lo que devuelve la IA) -> DD/MM/AAAA (lo que espera el campo de vigencia)
const isoADisplay = (iso: string | null | undefined): string => {
  if (!iso || !/^\d{4}-\d{2}-\d{2}$/.test(iso)) return ''
  const [y, m, d] = iso.split('-')
  return `${d}/${m}/${y}`
}

export default function GestionPanel({
  usuario,
  onLogout,
  onOpenSettings,
  onStartTour,
}: {
  usuario: AuthResponse
  onLogout: () => void
  onOpenSettings: () => void
  onStartTour: () => void
}) {
  const [uploadState, setUploadState] = useState<UploadState>('idle')
  const [showModal, setShowModal] = useState(false)
  const [lastProcessedContract, setLastProcessedContract] = useState<{ id: string; supervisor: string } | null>(null)
  const [extraccion, setExtraccion] = useState<ExtraccionContratoResponse | null>(null)
  const [errorExtraccion, setErrorExtraccion] = useState('')
  const [archivosSeleccionados, setArchivosSeleccionados] = useState<File[]>([])
  const fileInputRef = useRef<HTMLInputElement>(null)
  // Sin valor por defecto: un tipo o un centro que nadie eligió se guardaba
  // como si fuera un dato real del contrato.
  const [tipo, setTipo] = useState('')
  const [centro, setCentro] = useState('')
  const [supervisor, setSupervisor] = useState('')
  const [adjuntosSubidos, setAdjuntosSubidos] = useState(0)

  const [contratos, setContratos] = useState<ReturnType<typeof mapContratoRow>[]>([])
  // Sin esto, una consulta fallida o en curso se veía como «Aún no tiene
  // contratos registrados»: un dato falso presentado como real.
  const [cargandoContratos, setCargandoContratos] = useState(true)
  const [errorContratos, setErrorContratos] = useState('')
  // Asignar o cambiar el supervisor de un contrato ya creado.
  const [contratoParaAsignar, setContratoParaAsignar] = useState<ContratoResponse | null>(null)
  const [supervisorElegido, setSupervisorElegido] = useState('')
  const [busyAsignar, setBusyAsignar] = useState(false)
  const [errorAsignar, setErrorAsignar] = useState('')
  const [supervisores, setSupervisores] = useState<SupervisorOption[]>([])
  const [busyCrear, setBusyCrear] = useState(false)
  const [errorCrear, setErrorCrear] = useState('')

  // Corrección de los datos generales y activación del contrato
  const [contratoEnEdicion, setContratoEnEdicion] = useState<ContratoResponse | null>(null)
  const [edicion, setEdicion] = useState<FormularioDeContrato | null>(null)
  const [busyEditar, setBusyEditar] = useState(false)
  const [errorEditar, setErrorEditar] = useState('')
  const [contratoParaActivar, setContratoParaActivar] = useState<ContratoResponse | null>(null)
  const [busyActivar, setBusyActivar] = useState(false)
  const [errorActivar, setErrorActivar] = useState('')

  // Campos de la ficha (datos que se persisten en el backend)
  const [idContrato, setIdContrato] = useState('')
  const [objeto, setObjeto] = useState('')
  const [proveedor, setProveedor] = useState('')
  const [valor, setValor] = useState('')
  const [vigencia, setVigencia] = useState('')
  const [nit, setNit] = useState('')
  const [representanteLegal, setRepresentanteLegal] = useState('')
  const [lugarEjecucion, setLugarEjecucion] = useState('')
  const [registroPresupuestal, setRegistroPresupuestal] = useState('')
  // Del acto de designación (GCCON-F-032) y del acta de inicio
  const [enlaceSecop, setEnlaceSecop] = useState('')
  const [apoyoSupervision, setApoyoSupervision] = useState('')
  const [fechaDesignacion, setFechaDesignacion] = useState('')
  const [correoContratista, setCorreoContratista] = useState('')
  // Ningún documento del contrato lo trae: la extracción lo completa con el
  // registro de proveedores del SECOP II (paso 5 de la hoja de Alex).
  const [telefonoContratista, setTelefonoContratista] = useState('')
  const [completadoDesdeSecop, setCompletadoDesdeSecop] = useState<string[]>([])

  // Tabla de contratos reales (GET /api/contratos)
  useEffect(() => {
    let cancelado = false
    getContratos()
      .then((lista) => {
        if (!cancelado) setContratos(lista.map(mapContratoRow))
      })
      .catch((err) => {
        console.error('No se pudieron cargar los contratos:', err)
        if (!cancelado)
          setErrorContratos('No se pudieron consultar los contratos. Revise la conexión y vuelva a intentarlo.')
      })
      .finally(() => {
        if (!cancelado) setCargandoContratos(false)
      })
    return () => {
      cancelado = true
    }
  }, [])

  // Supervisores para asignación (solo ADMINISTRADOR tiene acceso)
  useEffect(() => {
    let cancelado = false
    getUsuarios()
      .then((lista) => {
        if (cancelado) return
        const sups = lista
          .filter((u) => u.rol === 'SUPERVISOR' && u.activo)
          .map((u) => ({ id: u.id, nombre: u.nombre }))
        setSupervisores(sups)
        // No se preselecciona el primero de la lista: ese supervisor recibía
        // el aviso de una designación que nadie hizo (auditoría del 17-10-2026).
      })
      .catch((err) => console.error('No se pudieron cargar los supervisores:', err))
    return () => {
      cancelado = true
    }
  }, [])

  const recargarContratos = async () => {
    const lista = await getContratos()
    setContratos(lista.map(mapContratoRow))
    setErrorContratos('')
  }

  const reintentarContratos = () => {
    setCargandoContratos(true)
    setErrorContratos('')
    recargarContratos()
      .catch(() =>
        setErrorContratos('No se pudieron consultar los contratos. Revise la conexión y vuelva a intentarlo.'),
      )
      .finally(() => setCargandoContratos(false))
  }

  // Todo lo que la ficha anterior dejó escrito. Se limpia al abrir la carga y al
  // elegir los archivos: si la extracción fallaba, el formulario manual
  // aparecía con el correo del contratista, el enlace del SECOP y el apoyo del
  // contrato anterior, y se guardaban en el nuevo.
  const limpiarFicha = () => {
    setIdContrato('')
    setObjeto('')
    setProveedor('')
    setValor('')
    setVigencia('')
    setNit('')
    setRepresentanteLegal('')
    setLugarEjecucion('')
    setRegistroPresupuestal('')
    setEnlaceSecop('')
    setApoyoSupervision('')
    setFechaDesignacion('')
    setCorreoContratista('')
    setTelefonoContratista('')
    setCompletadoDesdeSecop([])
    setSupervisor('')
    setTipo('')
    setCentro('')
    setErrorCrear('')
  }

  const openUpload = () => {
    setShowModal(true)
    setUploadState('idle')
    setAdjuntosSubidos(0)
    setExtraccion(null)
    setErrorExtraccion('')
    setArchivosSeleccionados([])
    limpiarFicha()
  }

  const handleFileSelect = () => fileInputRef.current?.click()

  const handleFilesChosen = async (archivos: File[]) => {
    if (archivos.length === 0) return
    // El servidor admite 20 MB por petición, y todos los archivos van juntos.
    const total = archivos.reduce((suma, a) => suma + a.size, 0)
    if (total > 20 * 1024 * 1024) {
      setErrorExtraccion(
        `Entre todos los archivos suman ${(total / 1024 / 1024).toFixed(1)} MB y el límite es 20 MB por carga. ` +
          'Cargue menos archivos o los de menor tamaño (la designación y el acta bastan).',
      )
      return
    }
    limpiarFicha()
    setArchivosSeleccionados(archivos)
    setErrorExtraccion('')
    // Un solo estado de espera: la subida y el análisis van en la misma
    // petición y no hay forma de medir cuánto lleva subido. Antes había un
    // «Subiendo… N%» que React nunca llegaba a pintar y cuya barra se ponía
    // en 100 % sin medir nada.
    setUploadState('analyzing')
    try {
      const resultado = await extraerDatosContrato(archivos)
      setExtraccion(resultado)
      setIdContrato(resultado.idContrato ?? '')
      setObjeto(resultado.objeto ?? '')
      setProveedor(resultado.proveedor ?? '')
      setNit(resultado.nit ?? '')
      setRepresentanteLegal(resultado.representanteLegal ?? '')
      setValor(resultado.valor ?? '')
      const inicio = isoADisplay(resultado.vigenciaInicio)
      const fin = isoADisplay(resultado.vigenciaFin)
      setVigencia(inicio || fin ? `${inicio} – ${fin}`.trim() : '')
      setLugarEjecucion(resultado.lugarEjecucion ?? '')
      setRegistroPresupuestal(resultado.registroPresupuestal ?? '')
      setEnlaceSecop(resultado.enlaceSecop ?? '')
      setApoyoSupervision(resultado.apoyoSupervision ?? '')
      setFechaDesignacion(isoADisplay(resultado.fechaDesignacion))
      setCorreoContratista(resultado.correoContratista ?? '')
      setTelefonoContratista(resultado.telefonoContratista ?? '')
      setCompletadoDesdeSecop(resultado.completadoDesdeSecop ?? [])
      // La designación nombra al supervisor: si es un usuario de SICOT, queda
      // preseleccionado. Si no lo es, no se adivina: Gestión elige.
      const designado = resultado.supervisorDesignado
      const coincide = designado ? supervisores.find((s) => mismaPersona(s.nombre, designado)) : undefined
      if (coincide) setSupervisor(String(coincide.id))
      // Antes se comparaba con Object.keys(CONTRACT_TYPES), que en un arreglo
      // son los índices ('0', '1'…): el tipo extraído nunca se aplicaba y el
      // formulario se quedaba siempre en «Suministro de Bienes».
      if (resultado.tipoContrato && (CONTRACT_TYPES as readonly string[]).includes(resultado.tipoContrato)) {
        setTipo(resultado.tipoContrato)
      }
      setUploadState('detect')
    } catch (e) {
      setErrorExtraccion(motivoDelServidor(e, 'No se pudo analizar el documento. Diligencie los datos a mano.'))
      setUploadState('review')
    }
  }

  const handleAsignar = async () => {
    if (busyCrear) return
    const num = idContrato.trim()
    const obj = objeto.trim()
    const val = parseValor(valor)
    if (!num) {
      setErrorCrear('El ID del contrato es obligatorio.')
      return
    }
    if (!obj) {
      setErrorCrear('El objeto del contrato es obligatorio.')
      return
    }
    if (val == null) {
      setErrorCrear('El valor debe ser un número mayor que cero.')
      return
    }
    // Una vigencia o una fecha escritas en otro formato se descartaban sin aviso.
    const vigLeida = parseVigencia(vigencia)
    if (vigencia.trim() && !vigLeida.inicio && !vigLeida.fin) {
      setErrorCrear('Escriba la vigencia como dd/mm/aaaa – dd/mm/aaaa.')
      return
    }
    if (fechaDesignacion.trim() && !/^\d{2}\/\d{2}\/\d{4}$/.test(fechaDesignacion.trim())) {
      setErrorCrear('Escriba la fecha de la designación como dd/mm/aaaa.')
      return
    }
    setBusyCrear(true)
    setErrorCrear('')
    try {
      const vig = parseVigencia(vigencia)
      const creado = await crearContrato({
        numeroContrato: num,
        objeto: obj,
        valor: val,
        fechaInicio: vig.inicio,
        fechaFin: vig.fin,
        supervisorId: supervisor ? Number(supervisor) : null,
        tipoContrato: tipo || null,
        contratista: proveedor.trim() || null,
        contratistaNit: nit.trim() || null,
        representanteLegal: representanteLegal.trim() || null,
        lugarEjecucion: lugarEjecucion.trim() || null,
        numeroRegistroPresupuestal: registroPresupuestal.trim() || null,
        centroCosto: centro || null,
        enlaceSecop: enlaceSecop.trim() || null,
        apoyoSupervision: apoyoSupervision.trim() || null,
        fechaDesignacion: /^\d{2}\/\d{2}\/\d{4}$/.test(fechaDesignacion.trim())
          ? displayAIso(fechaDesignacion.trim())
          : null,
        correoContratista: correoContratista.trim() || null,
        telefonoContratista: telefonoContratista.trim() || null,
      })
      // Se cuentan los adjuntos que realmente quedaron guardados. Antes se
      // tragaban todos los fallos y luego se informaba "N documentos adjuntos"
      // usando el número de archivos SELECCIONADOS: si todas las cargas
      // fallaban, se le decía al usuario que estaban adjuntos igual. El
      // contrato ya está creado, así que un fallo aquí no es motivo para
      // abortar — pero sí para decirlo.
      let subidos = 0
      if (archivosSeleccionados.length > 0) {
        const resultados = await Promise.all(
          archivosSeleccionados.map((archivo) =>
            subirDocumento(creado.id, archivo)
              .then(() => true)
              .catch(() => false),
          ),
        )
        subidos = resultados.filter(Boolean).length
        setAdjuntosSubidos(subidos)
      } else {
        setAdjuntosSubidos(0)
      }
      setUploadState('done')
      const sup = supervisores.find((s) => String(s.id) === supervisor)
      setLastProcessedContract({ id: creado.numeroContrato, supervisor: sup?.nombre ?? '— Sin asignar —' })
      await recargarContratos()
      // Si algún adjunto no se cargó, la ventana se queda abierta con el aviso:
      // antes se cerraba sola a los 1,2 s, antes de poder leerlo.
      const fallaronAdjuntos = subidos < archivosSeleccionados.length
      if (!fallaronAdjuntos) {
        setTimeout(() => {
          setShowModal(false)
        }, 1200)
      }
    } catch (e) {
      setErrorCrear(motivoDelServidor(e, 'No se pudo crear el contrato.'))
      setUploadState('review')
    } finally {
      setBusyCrear(false)
    }
  }

  const abrirEdicion = (c: ContratoResponse) => {
    setContratoEnEdicion(c)
    setEdicion(formularioDe(c))
    setErrorEditar('')
  }

  const cerrarEdicion = () => {
    setContratoEnEdicion(null)
    setEdicion(null)
    setErrorEditar('')
  }

  const guardarEdicion = async () => {
    if (!contratoEnEdicion || !edicion || busyEditar) return
    const num = edicion.numeroContrato.trim()
    const obj = edicion.objeto.trim()
    const val = parseValor(edicion.valor)
    if (!num) {
      setErrorEditar('El número del contrato es obligatorio.')
      return
    }
    if (!obj) {
      setErrorEditar('El objeto del contrato es obligatorio.')
      return
    }
    if (val == null) {
      setErrorEditar('El valor debe ser un número mayor que cero.')
      return
    }
    setBusyEditar(true)
    setErrorEditar('')
    try {
      // Las fechas van tal cual: el <input type="date"> ya entrega AAAA-MM-DD,
      // que es lo que espera el backend. La coherencia entre inicio y fin la
      // valida él y su mensaje es el que se muestra aquí.
      await actualizarContrato(contratoEnEdicion.id, {
        numeroContrato: num,
        objeto: obj,
        valor: val,
        fechaInicio: edicion.fechaInicio || null,
        fechaFin: edicion.fechaFin || null,
        tipoContrato: edicion.tipoContrato.trim() || null,
        contratista: edicion.contratista.trim() || null,
        contratistaNit: edicion.contratistaNit.trim() || null,
        representanteLegal: edicion.representanteLegal.trim() || null,
        lugarEjecucion: edicion.lugarEjecucion.trim() || null,
        numeroRegistroPresupuestal: edicion.numeroRegistroPresupuestal.trim() || null,
        fechaRegistroPresupuestal: edicion.fechaRegistroPresupuestal || null,
        centroCosto: edicion.centroCosto.trim() || null,
        enlaceSecop: edicion.enlaceSecop.trim() || null,
        apoyoSupervision: edicion.apoyoSupervision.trim() || null,
        fechaDesignacion: edicion.fechaDesignacion || null,
        // Correo y teléfono, solo si Gestión los cambió. Los llena también el
        // supervisor (desde el registro de proveedores del SECOP II), y el
        // formulario sale de la lista cargada al abrir la pantalla: reenviarlos
        // sin tocar borraría lo que él trajo después. Sin el campo, el backend
        // conserva el que había; vacío, lo borra.
        correoContratista: cambio(edicion.correoContratista, contratoEnEdicion.correoContratista),
        telefonoContratista: cambio(edicion.telefonoContratista, contratoEnEdicion.telefonoContratista),
      })
      await recargarContratos()
      cerrarEdicion()
    } catch (e) {
      setErrorEditar(motivoDelServidor(e, 'No se pudo guardar el contrato.'))
    } finally {
      setBusyEditar(false)
    }
  }

  const abrirAsignacion = (c: ContratoResponse) => {
    setContratoParaAsignar(c)
    setSupervisorElegido(c.supervisorId ? String(c.supervisorId) : '')
    setErrorAsignar('')
  }

  const guardarAsignacion = async () => {
    if (!contratoParaAsignar || busyAsignar) return
    if (!supervisorElegido) {
      setErrorAsignar('Elija el supervisor.')
      return
    }
    setBusyAsignar(true)
    setErrorAsignar('')
    try {
      await asignarSupervisor(contratoParaAsignar.id, Number(supervisorElegido))
      await recargarContratos()
      setContratoParaAsignar(null)
    } catch (e) {
      setErrorAsignar(motivoDelServidor(e, 'No se pudo asignar el supervisor.'))
    } finally {
      setBusyAsignar(false)
    }
  }

  const activarContrato = async () => {
    if (!contratoParaActivar || busyActivar) return
    setBusyActivar(true)
    setErrorActivar('')
    try {
      await cambiarEstadoContrato(contratoParaActivar.id, 'ACTIVO')
      await recargarContratos()
      setContratoParaActivar(null)
    } catch (e) {
      setErrorActivar(e instanceof ApiError ? e.message : 'No se pudo activar el contrato.')
    } finally {
      setBusyActivar(false)
    }
  }

  const navGroups: NavGroup[] = [
    {
      label: 'Gestión y contratación',
      items: [
        { id: 'contratos', label: 'Contratos', icon: <IconContract size={17} />, count: contratos.length },
        {
          id: 'cargar',
          label: 'Cargar nueva ficha',
          icon: <IconUpload size={17} />,
          title: 'Cargar una ficha de contrato en PDF',
        },
      ],
    },
  ]

  return (
    <AppShell
      roleBadge="Panel Gestión"
      groups={navGroups}
      activeId="contratos"
      onNavigate={(id) => {
        if (id === 'cargar') openUpload()
      }}
      usuario={usuario}
      avatarColor="#7c3aed"
      avatarTextColor="#ffffff"
      onLogout={onLogout}
      onOpenSettings={onOpenSettings}
      title="Contratos"
      subtitle="Carga de fichas, registro y asignación al supervisor"
      actions={
        <>
          <button
            className="btn-ghost"
            onClick={onStartTour}
            style={{ padding: '7px 13px', fontSize: 12, display: 'inline-flex', alignItems: 'center', gap: 6 }}
          >
            <IconPlay size={10} /> Tutorial
          </button>
          <button
            data-tour="cargar"
            className="btn-green"
            onClick={openUpload}
            style={{ padding: '8px 15px', fontSize: 12.5 }}
          >
            + Cargar nueva ficha
          </button>
        </>
      }
    >
      <div style={{ flex: 1, overflowY: 'auto', padding: '20px 24px', minWidth: 0 }}>
        {/* Ficha procesada card — solo se muestra tras cargar y procesar una ficha real */}
        {lastProcessedContract && (
          <div
            data-tour="ficha"
            className="card"
            style={{
              padding: '14px 16px',
              marginBottom: 20,
              borderLeft: '4px solid var(--accent)',
              display: 'flex',
              alignItems: 'center',
              gap: 16,
            }}
          >
            <div
              style={{
                width: 40,
                height: 40,
                borderRadius: '50%',
                background: 'var(--chip-green-bg)',
                border: '1.5px solid var(--chip-green)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 18,
                flexShrink: 0,
              }}
            >
              ✓
            </div>
            <div style={{ flex: 1 }}>
              <div
                style={{
                  fontSize: 12,
                  fontWeight: 700,
                  color: 'var(--accent)',
                  letterSpacing: '0.06em',
                  marginBottom: 2,
                }}
              >
                FICHA PROCESADA
              </div>
              {/* No se dice "el Copiloto asignó automáticamente": la asignación la
                  hace una persona en el formulario. La IA solo propuso datos a
                  partir del documento cargado, y siempre pasan por confirmación. */}
              <div style={{ fontSize: 13, color: 'var(--text-primary)' }}>
                Contrato <strong>{lastProcessedContract.id}</strong> creado · Supervisor:{' '}
                {lastProcessedContract.supervisor}.
              </div>
            </div>
            {lastProcessedContract.supervisor !== '— Sin asignar —' ? (
              <Chip text="Asignado" type="done" />
            ) : (
              <Chip text="Sin supervisor" type="pending" />
            )}
          </div>
        )}

        {/* Contracts table */}
        <div data-tour="tabla" className="card" style={{ overflow: 'hidden' }}>
          <div
            style={{
              padding: '12px 16px',
              borderBottom: '1px solid var(--border)',
              fontSize: 12,
              fontWeight: 600,
              color: 'var(--text-muted)',
              letterSpacing: '0.06em',
              background: 'var(--bg-surface)',
            }}
          >
            REGISTRO DE CONTRATOS
          </div>
          {/* Header row */}
          <div
            className="tabla-cabecera"
            style={{
              display: 'grid',
              gridTemplateColumns: COLUMNAS_REGISTRO,
              padding: '8px 16px',
              borderBottom: '1px solid var(--border)',
              fontSize: 11,
              fontWeight: 600,
              color: 'var(--text-muted)',
              letterSpacing: '0.04em',
              gap: 12,
              background: 'var(--bg-surface)',
            }}
          >
            <span>CONTRATO</span>
            <span>OBJETO</span>
            <span>SUPERVISOR</span>
            <span>ESTADO</span>
            <span>VALOR</span>
            <span>VIGENCIA</span>
            <span>ACCIONES</span>
          </div>
          {/* Empty state */}
          {contratos.length === 0 && cargandoContratos && (
            <div style={{ padding: '40px 16px', textAlign: 'center', fontSize: 12.5, color: 'var(--text-muted)' }}>
              Consultando los contratos…
            </div>
          )}
          {contratos.length === 0 && !cargandoContratos && errorContratos && (
            <div role="alert" style={{ padding: '32px 16px', textAlign: 'center' }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-primary)', marginBottom: 8 }}>
                {errorContratos}
              </div>
              <button className="btn-ghost" onClick={reintentarContratos} style={{ padding: '6px 14px', fontSize: 12 }}>
                Reintentar
              </button>
            </div>
          )}
          {contratos.length === 0 && !cargandoContratos && !errorContratos && (
            <div style={{ padding: '40px 16px', textAlign: 'center' }}>
              <IconClipboardList size={26} style={{ opacity: 0.5, margin: '0 auto 8px' }} />
              <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-primary)', marginBottom: 4 }}>
                Aún no tiene contratos registrados
              </div>
              <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                Cargue una ficha de contrato en PDF y el Copiloto propondrá los datos para que usted los revise.
              </div>
            </div>
          )}
          {/* Data rows */}
          {contratos.map((c) => (
            <div
              key={c.contrato.id}
              className="tabla-fila"
              style={{
                display: 'grid',
                gridTemplateColumns: COLUMNAS_REGISTRO,
                padding: '12px 16px',
                borderBottom: '1px solid var(--border)',
                fontSize: 13,
                gap: 12,
                alignItems: 'center',
                transition: 'background 0.1s',
                cursor: 'default',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--accent-soft)')}
              onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
            >
              <span
                data-col="Contrato"
                style={{
                  fontFamily: 'var(--font-mono)',
                  fontSize: 11,
                  color: 'var(--accent-tech)',
                  overflowWrap: 'anywhere',
                }}
              >
                {c.id}
              </span>
              <span data-col="Objeto" style={{ color: 'var(--text-primary)', fontSize: 12 }}>
                {c.object}
              </span>
              <span
                data-col="Supervisor"
                style={{
                  color: c.supervisor === '— Sin asignar —' ? 'var(--text-muted)' : 'var(--text-secondary)',
                  fontSize: 12,
                  fontStyle: c.supervisor === '— Sin asignar —' ? 'italic' : 'normal',
                }}
              >
                {c.supervisor}
              </span>
              <span data-col="Estado">
                <Chip text={c.statusLabel} type={c.statusType} />
              </span>
              <span
                data-col="Valor"
                style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-mono)', fontSize: 12 }}
              >
                {c.value}
              </span>
              <span data-col="Vigencia" style={{ color: 'var(--text-muted)', fontSize: 12 }}>
                {c.vigencia}
              </span>
              <span data-col="Acciones" style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                <button
                  className="btn-ghost"
                  onClick={() => abrirEdicion(c.contrato)}
                  style={{ padding: '5px 10px', fontSize: 11.5 }}
                  title={`Corregir los datos generales de ${c.id}`}
                >
                  Editar
                </button>
                {supervisores.length > 0 && (
                  <button
                    className="btn-ghost"
                    onClick={() => abrirAsignacion(c.contrato)}
                    style={{ padding: '5px 10px', fontSize: 11.5 }}
                    title={`Asignar o cambiar el supervisor de ${c.id}`}
                  >
                    {c.contrato.supervisorId ? 'Cambiar supervisor' : 'Asignar supervisor'}
                  </button>
                )}
                {/* Solo desde BORRADOR: es la única transición de contrato que el
                    backend tiene confirmada (TransicionesDeEstado.validarContrato
                    cierra «volver a BORRADOR» y deja el resto sin definir). Lo que
                    hace un contrato activo ya pasado el BORRADOR —suspenderlo,
                    finalizarlo— no se ofrece aquí porque no se sabe con qué
                    trámite del Centro se corresponde. */}
                {c.contrato.estado === 'BORRADOR' && (
                  <button
                    className="btn-green"
                    onClick={() => {
                      setContratoParaActivar(c.contrato)
                      setErrorActivar('')
                    }}
                    style={{ padding: '5px 10px', fontSize: 11.5 }}
                    title={`Activar el contrato ${c.id}`}
                  >
                    Activar
                  </button>
                )}
              </span>
            </div>
          ))}
        </div>
      </div>

      {/* Upload modal */}
      {showModal && (
        <Modal
          title="Cargar nueva ficha de contrato"
          onClose={() => setShowModal(false)}
          width={480}
          hideClose={uploadState === 'done'}
        >
          {uploadState === 'idle' && (
            <div>
              <input
                ref={fileInputRef}
                type="file"
                accept=".pdf,.docx"
                multiple
                style={{ display: 'none' }}
                onChange={(e) => {
                  const f = Array.from(e.target.files ?? [])
                  e.target.value = ''
                  handleFilesChosen(f)
                }}
              />
              <div
                onClick={handleFileSelect}
                style={{
                  border: '2px dashed var(--border)',
                  borderRadius: 10,
                  padding: '40px 20px',
                  textAlign: 'center',
                  cursor: 'pointer',
                  transition: 'border-color 0.15s',
                }}
                onMouseEnter={(e) => (e.currentTarget.style.borderColor = 'var(--accent-dim)')}
                onMouseLeave={(e) => (e.currentTarget.style.borderColor = 'var(--border)')}
              >
                <IconFileText size={34} style={{ color: 'var(--text-muted)', margin: '0 auto 12px' }} />
                <p style={{ color: 'var(--text-secondary)', fontSize: 14, margin: 0 }}>
                  Haga clic para seleccionar uno o varios documentos
                  <br />
                  <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                    PDF o Word (.docx) — hasta 6 archivos y 20 MB entre todos
                  </span>
                </p>
              </div>
              <p style={{ color: 'var(--text-muted)', fontSize: 11, marginTop: 12, textAlign: 'center' }}>
                SICOT lee los documentos que cargue y combina lo que encuentre en todos para proponer los datos del
                contrato — usted los revisa, corrige y confirma antes de crear el contrato. Los archivos quedan adjuntos
                al contrato de todas formas, se analicen o no.
              </p>
              <p style={{ color: 'var(--accent)', fontSize: 11, marginTop: 8, textAlign: 'center', fontWeight: 600 }}>
                Recomendado: cargue el acto de designación (GCCON-F-032) y, si ya lo tiene, el Acta de Inicio firmada:
                de ahí salen el enlace del SECOP, el apoyo, la fecha de inicio, el registro presupuestal y el correo del
                contratista. El Manual de Supervisión y los formatos en blanco no aportan datos.
              </p>
              <p style={{ color: 'var(--text-muted)', fontSize: 11, marginTop: 8, textAlign: 'center' }}>
                Si el documento rotula sus datos (como el Acta de Inicio GCCON-F-018), la lectura es inmediata; si no,
                el Copiloto IA completa el objeto y el tipo, y puede tardar unos minutos. Un PDF escaneado (una imagen)
                todavía no se puede leer: diligencie esos datos a mano.
              </p>
            </div>
          )}

          {uploadState === 'analyzing' && (
            <div style={{ textAlign: 'center', padding: '32px 0' }}>
              <IconLoader size={30} style={{ color: 'var(--accent)', margin: '0 auto 16px' }} />
              <p style={{ color: 'var(--accent)', fontSize: 14, fontWeight: 600 }}>
                Analizando {archivosSeleccionados.length} documento{archivosSeleccionados.length === 1 ? '' : 's'}...
              </p>
              <p style={{ color: 'var(--text-muted)', fontSize: 12 }}>
                El Copiloto IA está extrayendo los datos del contrato — esto corre en un modelo local (sin costo por
                consumo) y puede tardar varios minutos en esta máquina. No cierre esta ventana.
              </p>
              <div style={{ marginTop: 10, fontSize: 11, color: 'var(--text-muted)' }}>
                {archivosSeleccionados.map((f) => (
                  <div key={f.name}>{f.name}</div>
                ))}
              </div>
            </div>
          )}

          {uploadState === 'detect' && (
            <div>
              <div className="card" style={{ padding: 0, overflow: 'hidden', borderColor: 'var(--accent-line)' }}>
                <div
                  style={{
                    padding: '10px 14px',
                    borderBottom: '1px solid var(--border)',
                    fontSize: 11,
                    fontWeight: 700,
                    letterSpacing: '0.1em',
                    color: 'var(--accent)',
                  }}
                >
                  ◆ ANÁLISIS DEL COPILOTO IA — DATOS PROPUESTOS
                </div>
                <div style={{ padding: '14px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
                    <IconCheckCircle size={16} style={{ color: 'var(--accent)' }} />
                    <strong style={{ fontSize: 14 }}>{tipo}</strong>
                  </div>
                  <div
                    style={{
                      fontSize: 10,
                      fontWeight: 700,
                      letterSpacing: '0.1em',
                      color: 'var(--text-muted)',
                      marginBottom: 6,
                    }}
                  >
                    CARACTERÍSTICAS DETECTADAS
                  </div>
                  {[
                    ['Contrato', extraccion?.idContrato],
                    ['Proveedor', extraccion?.proveedor],
                    ['NIT', extraccion?.nit],
                    ['Monto', extraccion?.valor],
                    ['Lugar de ejecución', extraccion?.lugarEjecucion],
                  ].map(([k, v]) => (
                    <div
                      key={k}
                      style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        fontSize: 12.5,
                        padding: '3px 0',
                        color: 'var(--text-secondary)',
                      }}
                    >
                      <span>{k}</span>
                      <span style={{ color: 'var(--text-primary)', fontFamily: 'var(--font-mono)', fontSize: 12 }}>
                        {v || '— no detectado —'}
                      </span>
                    </div>
                  ))}
                  <p style={{ fontSize: 11.5, color: 'var(--text-muted)', margin: '10px 0 0', lineHeight: 1.5 }}>
                    {completadoDesdeSecop.length === 0
                      ? 'Estos datos vienen del documento cargado, no de un catálogo — revise y corrija lo que haga falta en el siguiente paso.'
                      : `Estos datos vienen del documento cargado y, los que el documento no traía (${completadoDesdeSecop
                          .map((c) => CAMPO_SECOP[c] ?? c)
                          .join(
                            ', ',
                          )}), de los datos abiertos del SECOP II — revise y corrija lo que haga falta en el siguiente paso.`}
                  </p>
                  <div
                    style={{
                      fontSize: 10,
                      fontWeight: 700,
                      letterSpacing: '0.1em',
                      color: 'var(--text-muted)',
                      margin: '14px 0 6px',
                    }}
                  >
                    ETAPAS DEL PROCESO (GCCON-P-010)
                  </div>
                  {ETAPAS_GCCON_P010.map((e, i) => (
                    <div
                      key={e}
                      style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 12.5, padding: '3px 0' }}
                    >
                      <span
                        style={{
                          width: 18,
                          height: 18,
                          borderRadius: '50%',
                          background: 'var(--accent-soft)',
                          color: 'var(--accent)',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontSize: 10,
                          fontWeight: 700,
                        }}
                      >
                        {i + 1}
                      </span>
                      {e}
                    </div>
                  ))}
                </div>
              </div>
              <button
                className="btn-green"
                onClick={() => setUploadState('review')}
                style={{ width: '100%', padding: '10px 0', fontSize: 13, marginTop: 16 }}
              >
                Continuar a confirmación →
              </button>
            </div>
          )}

          {uploadState === 'review' && (
            <div>
              {errorExtraccion ? (
                <div
                  style={{
                    marginBottom: 16,
                    padding: '10px 14px',
                    border: '1px solid var(--chip-red)',
                    background: 'var(--chip-red-bg)',
                    borderRadius: 8,
                    fontSize: 13,
                    color: 'var(--text-primary)',
                  }}
                >
                  El Copiloto IA no pudo analizar los documentos: {errorExtraccion} Diligencie los datos manualmente.
                </div>
              ) : (
                <div
                  style={{
                    marginBottom: 16,
                    padding: '10px 14px',
                    background: 'var(--accent-soft)',
                    border: '1px solid var(--accent-line)',
                    borderRadius: 8,
                    fontSize: 13,
                    color: 'var(--accent)',
                  }}
                >
                  ✓ Documento analizado. Revise, corrija y confirme los datos extraídos.
                  {completadoDesdeSecop.length > 0 && (
                    <div style={{ color: 'var(--text-secondary)', marginTop: 4, fontSize: 12 }}>
                      El documento no traía {completadoDesdeSecop.map((c) => CAMPO_SECOP[c] ?? c).join(', ')}: se
                      tomaron de los datos abiertos del SECOP II para el mismo número de contrato.
                    </div>
                  )}
                </div>
              )}
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {[
                  { label: 'ID Contrato', value: idContrato, onChange: setIdContrato, mono: true },
                  { label: 'Objeto', value: objeto, onChange: setObjeto, mono: false },
                  { label: 'Proveedor / Contratista', value: proveedor, onChange: setProveedor, mono: false },
                  { label: 'NIT o CC del contratista', value: nit, onChange: setNit, mono: true },
                  {
                    label: 'Representante legal',
                    value: representanteLegal,
                    onChange: setRepresentanteLegal,
                    mono: false,
                  },
                  { label: 'Valor', value: valor, onChange: setValor, mono: true },
                  { label: 'Vigencia (dd/mm/aaaa – dd/mm/aaaa)', value: vigencia, onChange: setVigencia, mono: false },
                  { label: 'Lugar de ejecución', value: lugarEjecucion, onChange: setLugarEjecucion, mono: false },
                  {
                    label: 'Número de registro presupuestal',
                    value: registroPresupuestal,
                    onChange: setRegistroPresupuestal,
                    mono: true,
                  },
                  {
                    label: 'Correo del contratista',
                    value: correoContratista,
                    onChange: setCorreoContratista,
                    mono: false,
                  },
                  {
                    label: 'Teléfono del contratista',
                    value: telefonoContratista,
                    onChange: setTelefonoContratista,
                    mono: true,
                  },
                  {
                    label: 'Apoyo a la supervisión',
                    value: apoyoSupervision,
                    onChange: setApoyoSupervision,
                    mono: false,
                  },
                  {
                    label: 'Fecha de la designación (dd/mm/aaaa)',
                    value: fechaDesignacion,
                    onChange: setFechaDesignacion,
                    mono: true,
                  },
                  { label: 'Enlace del proceso en SECOP II', value: enlaceSecop, onChange: setEnlaceSecop, mono: true },
                ].map((f) => (
                  <div key={f.label}>
                    <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 4 }}>{f.label}</div>
                    <input
                      type="text"
                      value={f.value}
                      onChange={(e) => f.onChange(e.target.value)}
                      style={{
                        width: '100%',
                        padding: '8px 12px',
                        fontFamily: f.mono ? 'var(--font-mono)' : 'inherit',
                        fontSize: 13,
                      }}
                    />
                  </div>
                ))}
                <div>
                  <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 4 }}>Tipo de contrato</div>
                  <select value={tipo} onChange={(e) => setTipo(e.target.value)}>
                    <option value="">— Sin especificar —</option>
                    {CONTRACT_TYPES.map((t) => (
                      <option key={t}>{t}</option>
                    ))}
                  </select>
                  <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 5 }}>
                    Todos los contratos siguen las mismas 6 etapas; la 1 y la 2 son las del proceso real del supervisor.
                  </div>
                </div>
                <div>
                  <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 4 }}>Centro de costo</div>
                  <select value={centro} onChange={(e) => setCentro(e.target.value)}>
                    <option value="">— Sin especificar —</option>
                    {CENTROS_COSTO.map((c) => (
                      <option key={c}>{c}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 4 }}>Supervisor asignado</div>
                  <select value={supervisor} onChange={(e) => setSupervisor(e.target.value)}>
                    <option value="">
                      {supervisores.length === 0 ? '— Sin asignar —' : '— Elija el supervisor —'}
                    </option>
                    {supervisores.map((s) => (
                      <option key={s.id} value={String(s.id)}>
                        {s.nombre}
                      </option>
                    ))}
                  </select>
                  {extraccion?.supervisorDesignado && (
                    <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 5 }}>
                      La designación nombra a: <strong>{extraccion.supervisorDesignado}</strong>
                      {!supervisores.some((s) => mismaPersona(s.nombre, extraccion.supervisorDesignado ?? '')) &&
                        ' — no coincide con ningún supervisor de SICOT; elíjalo a mano o pídale al Administrador que le cree la cuenta.'}
                    </div>
                  )}
                </div>
              </div>

              {errorCrear && (
                <div
                  style={{
                    marginTop: 12,
                    padding: '8px 12px',
                    border: '1px solid var(--chip-red)',
                    background: 'var(--chip-red-bg)',
                    borderRadius: 8,
                    fontSize: 12.5,
                    color: 'var(--text-primary)',
                  }}
                >
                  {errorCrear}
                </div>
              )}

              <div style={{ display: 'flex', gap: 10, marginTop: 20, flexWrap: 'wrap' }}>
                <button
                  className="btn-ghost"
                  onClick={openUpload}
                  style={{ flex: 1, padding: '10px 0', fontSize: 13, minWidth: 110 }}
                >
                  ✗ Rechazar
                </button>
                {/* El botón "Revisión IA" se retiró: no llamaba a ninguna IA — solo
                      desplegaba una lista fija de documentos por tipo de contrato. La
                      detección de inconsistencias sigue sin implementarse. */}
                <button
                  className="btn-green"
                  onClick={handleAsignar}
                  disabled={busyCrear}
                  style={{
                    flex: 2,
                    padding: '10px 0',
                    fontSize: 13,
                    minWidth: 170,
                    opacity: busyCrear ? 0.6 : 1,
                    cursor: busyCrear ? 'default' : 'pointer',
                  }}
                >
                  {busyCrear ? 'Guardando…' : '✓ Confirmar y cargar'}
                </button>
              </div>
            </div>
          )}

          {uploadState === 'done' && (
            <div style={{ textAlign: 'center', padding: '24px 0' }}>
              <IconCheckCircle size={44} style={{ color: 'var(--accent)', margin: '0 auto 12px' }} />
              <p style={{ color: 'var(--accent)', fontSize: 15, fontWeight: 600, margin: 0 }}>Contrato creado.</p>
              {/* No se dice "ha sido notificado": SICOT no envía ningún aviso al
                    crear o asignar un contrato — solo escribe un registro de
                    auditoría. Prometer una notificación inexistente haría que el
                    supervisor no se enterara y nadie lo supiera. */}
              <p style={{ color: 'var(--text-muted)', fontSize: 13, marginTop: 8 }}>
                {tipo} · {centro.split(' — ')[0]} · Supervisor: {lastProcessedContract?.supervisor ?? '—'}
              </p>
              {archivosSeleccionados.length > 0 && (
                <p
                  style={{
                    color: adjuntosSubidos < archivosSeleccionados.length ? 'var(--chip-red)' : 'var(--text-muted)',
                    fontSize: 12,
                    marginTop: 6,
                  }}
                >
                  {adjuntosSubidos === archivosSeleccionados.length
                    ? `${adjuntosSubidos} documento${adjuntosSubidos === 1 ? '' : 's'} adjunto${adjuntosSubidos === 1 ? '' : 's'} al contrato.`
                    : `Se adjuntaron ${adjuntosSubidos} de ${archivosSeleccionados.length} documentos. El contrato quedó creado; los demás no se pudieron cargar (revise su tamaño y su tipo).`}
                </p>
              )}
            </div>
          )}
        </Modal>
      )}

      {/* Corrección de los datos generales (PUT /api/contratos/{id}) */}
      {contratoEnEdicion && edicion && (
        <Modal title={`Corregir ${contratoEnEdicion.numeroContrato}`} onClose={cerrarEdicion} width={520}>
          {/* Se dice que lo vacío queda vacío porque estos campos alimentan los
              documentos del supervisor: un campo en blanco sale como pendiente
              en el documento, no lo rellena nadie. */}
          <p style={{ fontSize: 12, color: 'var(--text-muted)', margin: '0 0 14px', lineHeight: 1.5 }}>
            Se guarda exactamente lo que escriba aquí. Lo que deje vacío queda vacío en el contrato y aparecerá como
            pendiente en los documentos que se generen con él. El supervisor asignado no se cambia desde esta ventana.
          </p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {CAMPOS_DE_TEXTO.map((f) => (
              <div key={f.campo}>
                <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 4 }}>{f.label}</div>
                <input
                  type="text"
                  value={edicion[f.campo]}
                  onChange={(e) => setEdicion({ ...edicion, [f.campo]: e.target.value })}
                  style={{
                    width: '100%',
                    padding: '8px 12px',
                    fontFamily: f.mono ? 'var(--font-mono)' : 'inherit',
                    fontSize: 13,
                  }}
                />
              </div>
            ))}
            {CAMPOS_DE_FECHA.map((f) => (
              <div key={f.campo}>
                <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 4 }}>{f.label}</div>
                <input
                  type="date"
                  value={edicion[f.campo]}
                  onChange={(e) => setEdicion({ ...edicion, [f.campo]: e.target.value })}
                  style={{ width: '100%', padding: '8px 12px', fontFamily: 'var(--font-mono)', fontSize: 13 }}
                />
              </div>
            ))}
            <div>
              <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 4 }}>Tipo de contrato</div>
              <select
                value={edicion.tipoContrato}
                onChange={(e) => setEdicion({ ...edicion, tipoContrato: e.target.value })}
              >
                <option value="">— Sin especificar —</option>
                {opcionesCon(CONTRACT_TYPES, edicion.tipoContrato).map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 4 }}>Centro de costo</div>
              <select
                value={edicion.centroCosto}
                onChange={(e) => setEdicion({ ...edicion, centroCosto: e.target.value })}
              >
                <option value="">— Sin especificar —</option>
                {opcionesCon(CENTROS_COSTO, edicion.centroCosto).map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {errorEditar && (
            <div
              style={{
                marginTop: 12,
                padding: '8px 12px',
                border: '1px solid var(--chip-red)',
                background: 'var(--chip-red-bg)',
                borderRadius: 8,
                fontSize: 12.5,
                color: 'var(--text-primary)',
              }}
            >
              {errorEditar}
            </div>
          )}

          <div style={{ display: 'flex', gap: 10, marginTop: 20, flexWrap: 'wrap' }}>
            <button
              className="btn-ghost"
              onClick={cerrarEdicion}
              style={{ flex: 1, padding: '10px 0', fontSize: 13, minWidth: 110 }}
            >
              Cancelar
            </button>
            <button
              className="btn-green"
              onClick={guardarEdicion}
              disabled={busyEditar}
              style={{
                flex: 2,
                padding: '10px 0',
                fontSize: 13,
                minWidth: 170,
                opacity: busyEditar ? 0.6 : 1,
                cursor: busyEditar ? 'default' : 'pointer',
              }}
            >
              {busyEditar ? 'Guardando…' : 'Guardar los cambios'}
            </button>
          </div>
        </Modal>
      )}

      {/* Activación del contrato (PATCH /api/contratos/{id}/estado) */}
      {/* Hasta el 18-10-2026 no había dónde asignar o cambiar el supervisor de
          un contrato ya creado: uno que nació sin él se quedaba para siempre en
          la etapa 1, y un supervisor que se iba no se podía desactivar. */}
      {contratoParaAsignar && (
        <Modal
          title={contratoParaAsignar.supervisorId ? 'Cambiar el supervisor' : 'Asignar el supervisor'}
          onClose={() => setContratoParaAsignar(null)}
          width={460}
        >
          <p style={{ fontSize: 13, color: 'var(--text-primary)', margin: '0 0 10px', lineHeight: 1.6 }}>
            Contrato <strong>{contratoParaAsignar.numeroContrato}</strong>
            {contratoParaAsignar.supervisorNombre
              ? `, hoy a cargo de ${contratoParaAsignar.supervisorNombre}.`
              : ', todavía sin supervisor.'}
          </p>
          <p style={{ fontSize: 12.5, color: 'var(--text-secondary)', margin: '0 0 12px', lineHeight: 1.6 }}>
            SICOT le avisa al supervisor que elija y, si el contrato no tenía, da por hecha la designación (paso 1).
          </p>
          <label style={{ display: 'block', fontSize: 11, color: 'var(--text-muted)', marginBottom: 4 }}>
            Supervisor
            <select
              value={supervisorElegido}
              onChange={(e) => setSupervisorElegido(e.target.value)}
              style={{ display: 'block', width: '100%', marginTop: 4 }}
            >
              <option value="">— Elija el supervisor —</option>
              {supervisores.map((s) => (
                <option key={s.id} value={String(s.id)}>
                  {s.nombre}
                </option>
              ))}
            </select>
          </label>

          {errorAsignar && (
            <div
              role="alert"
              style={{
                marginTop: 12,
                padding: '8px 12px',
                border: '1px solid var(--chip-red)',
                background: 'var(--chip-red-bg)',
                borderRadius: 8,
                fontSize: 12.5,
                color: 'var(--text-primary)',
              }}
            >
              {errorAsignar}
            </div>
          )}

          <div style={{ display: 'flex', gap: 10, marginTop: 20, flexWrap: 'wrap' }}>
            <button
              className="btn-ghost"
              onClick={() => setContratoParaAsignar(null)}
              style={{ flex: 1, padding: '10px 0', fontSize: 13, minWidth: 110 }}
            >
              Cancelar
            </button>
            <button
              className="btn-green"
              onClick={guardarAsignacion}
              disabled={busyAsignar}
              style={{ flex: 1, padding: '10px 0', fontSize: 13, minWidth: 110 }}
            >
              {busyAsignar ? 'Asignando…' : 'Guardar'}
            </button>
          </div>
        </Modal>
      )}

      {contratoParaActivar && (
        <Modal title="Activar el contrato" onClose={() => setContratoParaActivar(null)} width={460}>
          <p style={{ fontSize: 13, color: 'var(--text-primary)', margin: '0 0 10px', lineHeight: 1.6 }}>
            El contrato <strong>{contratoParaActivar.numeroContrato}</strong> pasa de borrador a activo.
          </p>
          {/* Lo que se afirma aquí es lo que el motor hace de verdad: las cuatro
              reglas de calendario parten de LectorDeContratos.vigentes(), que
              solo devuelve los ACTIVO. Los umbrales son los de
              sicot.automatizacion.dias-de-aviso-previo, configurables. */}
          <p style={{ fontSize: 12.5, color: 'var(--text-secondary)', margin: '0 0 10px', lineHeight: 1.6 }}>
            Desde ese momento SICOT vigila su plazo: los avisos de vencimiento próximo (por defecto 30, 15 y 7 días
            antes del fin de la vigencia), el de contrato vencido y el de cronograma atrasado solo miran los contratos
            activos. Mientras siga en borrador, SICOT no emite ninguno.
          </p>
          <p style={{ fontSize: 12.5, color: 'var(--text-muted)', margin: '0 0 4px', lineHeight: 1.6 }}>
            Cuándo corresponde activarlo lo decide usted: SICOT no supone en qué paso del GCCON-P-010 ocurre. Volver a
            borrador no es posible.
          </p>

          {errorActivar && (
            <div
              style={{
                marginTop: 12,
                padding: '8px 12px',
                border: '1px solid var(--chip-red)',
                background: 'var(--chip-red-bg)',
                borderRadius: 8,
                fontSize: 12.5,
                color: 'var(--text-primary)',
              }}
            >
              {errorActivar}
            </div>
          )}

          <div style={{ display: 'flex', gap: 10, marginTop: 20, flexWrap: 'wrap' }}>
            <button
              className="btn-ghost"
              onClick={() => setContratoParaActivar(null)}
              style={{ flex: 1, padding: '10px 0', fontSize: 13, minWidth: 110 }}
            >
              Cancelar
            </button>
            <button
              className="btn-green"
              onClick={activarContrato}
              disabled={busyActivar}
              style={{
                flex: 2,
                padding: '10px 0',
                fontSize: 13,
                minWidth: 170,
                opacity: busyActivar ? 0.6 : 1,
                cursor: busyActivar ? 'default' : 'pointer',
              }}
            >
              {busyActivar ? 'Activando…' : 'Activar el contrato'}
            </button>
          </div>
        </Modal>
      )}
    </AppShell>
  )
}
