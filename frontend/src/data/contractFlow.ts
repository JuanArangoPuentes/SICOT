// Datos del flujo de supervisión GCCON-P-010 que la interfaz necesita y el
// backend no le da: el documento de cada subetapa, qué documentos formales arma
// SICOT, dónde se piden fotos de la entrega y los mensajes de cierre de cada
// paso del tutorial. Es la base de conocimiento local del proceso — NO son
// datos de un contrato de ejemplo. Las etapas reales de un contrato vienen del
// backend.

// Sub-pasos cuyo documento formal arma SICOT con los datos del contrato; el supervisor lo revisa y lo firma.
// Corregido contra fuentes reales (Datos SICOT, ver memoria de proyecto
// project_sicot_gccon_p010_grounded): el Oficio de Pago GRF-F-089 ("SCM") lo
// firma el Ordenador del gasto, no el supervisor — no se incluye aquí.
// El Acta de Inicio (antes 2.7) salió de esta lista el 15-10-2026: según el
// proceso real que documentó el supervisor, la elabora Contratación y llega por
// correo, así que SICOT la recibe firmada en el paso 2.1 en vez de generarla.
export const AI_GENERATED_DOCS = new Set(['3.4', '4.3', '5.3', '6.3'])

// Sub-pasos donde el supervisor aporta fotos de la entrega en el Centro: la
// verificación física en bodega (3.1) y la evidencia fotográfica que el propio
// flujo pide (3.2). En los demás sub-pasos no se ofrece la cámara, porque
// ofrecerla en todos invita a llenar el expediente de fotos sueltas que nadie
// va a mirar.
export const SUBETAPAS_CON_EVIDENCIA_FOTOGRAFICA = new Set(['3.1', '3.2'])

// Lo que el paso del acta exige antes de cerrarse. Coincide con
// Subetapa.documentosRequeridos en el backend, que es quien lo hace cumplir.
export const ACTA_FIRMADA_Y_ENVIO = 'ACTA_FIRMADA_Y_ENVIO'

// Los pasos 2.2 a 2.4 del proceso real traen una herramienta en el panel. Se
// reconocen por el nombre de la subetapa y no por el código: los contratos
// creados antes del 15-10-2026 tienen otras subetapas con esos mismos códigos
// («2.2 Verificación de datos del contratista y NIT»…) y no deben mostrarla.
export type HerramientaDelPaso = 'VERIFICACION_SECOP' | 'CORREO_SST' | 'CORREO_CONTRATISTA'
export const HERRAMIENTA_POR_PASO: Record<string, HerramientaDelPaso> = {
  'Aviso a los profesionales SST y ambiental': 'CORREO_SST',
  'Verificación de especificaciones en SECOP II': 'VERIFICACION_SECOP',
  'Contacto con el contratista y acuerdo de entregas': 'CORREO_CONTRATISTA',
}

// Documento que se maneja en cada subetapa, por su código.
//
// Es lo único de la definición del proceso que la plantilla del backend
// (GcconP010Plantilla.java) no trae: títulos, rótulos, responsables y estado de
// cada subetapa llegan del servidor en mapEtapas. Aquí había una copia completa
// de todas las subetapas (STEPS_INITIAL) de la que solo se leía este campo, y
// quien la editaba creía estar cambiando lo que se ve en pantalla.
//
// Las etapas 1 y 2 siguen el proceso real que documentó el supervisor Alex
// Zapata el 15-10-2026 (docs/producto/LISTA_CHEQUEO_ALEX.md); las 3 a 6 siguen
// GCCON-P-010 hasta que él documente esa parte. Un contrato anterior a esa fecha
// con un código que ya no existe (1.4 a 1.6, 2.5 a 2.7) cae en la descripción
// de su subetapa, que trae el servidor.
export const DOCUMENTO_POR_SUBETAPA: Record<string, string> = {
  '1.1': 'Contrato SECOP II',
  '1.2': 'GCCON-F-032',
  '1.3': 'Registro en SICOT',
  '2.1': 'Acta firmada + evidencia del envío',
  '2.2': 'Correo a SST y ambiental',
  '2.3': 'SECOP II',
  '2.4': 'Correo al contratista',
  '3.1': 'Acta de visita',
  '3.2': 'Registro fotográfico',
  '3.3': 'Lista de chequeo',
  '3.4': 'GCCON-F-031',
  '4.1': 'Planilla PILA',
  '4.2': 'FEV DIAN',
  '4.3': 'GIL-F-010',
  '5.1': 'Póliza vigencia',
  '5.2': 'CRP',
  '5.3': 'Certificación de cumplimiento',
  '6.1': 'Informe final',
  '6.2': 'Adición / prórroga SECOP II',
  '6.3': 'GCCON-F-030',
  '6.4': 'Expediente SIGEP',
}

export const TUTORIAL: Record<string, string> = {
  welcome: `Este es su panel de supervisión. Cuando se le asigne un contrato, aquí verá sus 6 etapas. La designación ya queda registrada por el apoyo a la supervisión, así que usted empieza en el Acta de Inicio. Yo lo voy a guiar paso a paso — en cada etapa activa le explico qué hacer; el acta la recibe de Contratación, y los demás documentos formales los arma SICOT con los datos del contrato para que usted los revise y los firme. Haga clic en "Iniciar Paso" cuando esté listo para empezar.`,

  // La guía de cada sub-paso no vive aquí: la arma data/guiaSubPaso.ts con la
  // descripción y el responsable de la plantilla GCCON-P-010, sin llamar al
  // modelo (antes se le preguntaba a Ollama en cada sub-paso y en un portátil
  // la respuesta se cortaba a los 240 s). Aquí solo quedan los mensajes de
  // cierre de cada paso, que son un resumen de transición.
  step1done:
    'La designación quedó registrada. Ahora empieza su participación activa: en el Paso 2 firma y devuelve el Acta de Inicio que le envía Contratación, avisa a los profesionales SST y ambiental, verifica el contrato en el SECOP II y acuerda las entregas con el contratista.',
  step2done:
    'Ha completado el Paso 2 — Inicio. El Acta de Inicio firmada y la evidencia de su envío quedaron en el expediente, y el contrato quedó verificado contra el SECOP II. Ahora avanzamos al Paso 3: Inspección, donde verificará la entrega física en bodega.',
  step3done:
    'Ha completado el Paso 3 — Inspección. El Informe GCCON-F-031 quedó firmado y registrado en el expediente. Ahora avanzamos al Paso 4: Recepción formal con el Acta GIL-F-010.',
  step4done:
    'Ha completado el Paso 4 — Recepción. El Acta GIL-F-010 quedó firmada y registrada. Ahora avanzamos al Paso 5: Certificación de cumplimiento y trámite de pago.',
  step5done:
    'Ha completado el Paso 5 — Certificación. Su certificación quedó firmada; el trámite de pago ahora sigue con el Ordenador del gasto. Avanzamos al Paso 6: Cierre, el último de su supervisión.',
  step6done:
    'Ha completado el Paso 6 — Cierre. Su supervisión del contrato quedó formalmente cerrada: el Acta de Inicio firmada, el Informe de Supervisión, el Acta de Recibo, la Certificación de cumplimiento y el Informe Final quedaron registrados en el expediente.',
}

// NOTA: la coincidencia de palabras clave (CHAT_RESPONSES) que vivía aquí se
// eliminó — el chat del Copiloto ahora es una IA real (Ollama, vía
// CopilotoChatService en el backend, POST /api/contratos/{id}/copiloto/chat),
// anclada a los datos reales del contrato y al estado real de sus etapas.
// Ver services/documentoService.ts#preguntarCopiloto.

// Documentos formales que arma SICOT — se siguen en la pestaña Documentos
// (GCCON-P-010 / GCCON-M-002). Claves de generación (`tipo`) coinciden con
// PlantillaDocumentoIA.CATALOGO en el backend, y `llevaObservaciones` con su
// campo del mismo nombre: la Certificación no tiene apartado de observaciones
// en el formato oficial. El Acta de Inicio ya no está: la elabora Contratación
// y el supervisor la carga firmada en el paso 2.1.
export const FORMAL_DOCS = [
  {
    subStepId: '3.4',
    llevaObservaciones: true,
    tipo: 'INFORME_SUPERVISION',
    name: 'Informe de Supervisión',
    code: 'GCCON-F-031',
    step: 3,
    desc: 'Control de ejecución, inspección física, novedades y avances.',
  },
  {
    subStepId: '4.3',
    llevaObservaciones: true,
    tipo: 'ACTA_RECIBO',
    name: 'Acta de Recibo a Satisfacción',
    code: 'GIL-F-010',
    step: 4,
    desc: 'Recepción formal de bienes — cantidad, calidad y especificaciones técnicas.',
  },
  {
    subStepId: '5.3',
    llevaObservaciones: false,
    tipo: 'CERTIFICACION_CUMPLIMIENTO',
    name: 'Certificación de cumplimiento',
    code: 'PENDIENTE_DE_DEFINIR',
    step: 5,
    desc: 'Certificación del supervisor que respalda el trámite de pago ("ESUCON" en el CTMA; sin código de formato oficial confirmado).',
  },
  {
    subStepId: '6.3',
    llevaObservaciones: true,
    tipo: 'INFORME_FINAL',
    name: 'Informe Final de Supervisión',
    code: 'GCCON-F-030',
    step: 6,
    desc: 'Cumplimiento de obligaciones, aspectos financieros y conclusión del contrato (no es el acta de liquidación).',
  },
]
