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
export const AI_GENERATED_DOCS = new Set(['2.7', '3.4', '4.3', '5.3', '6.3'])

// Sub-pasos donde el supervisor aporta fotos de la entrega en el Centro: la
// verificación física en bodega (3.1) y la evidencia fotográfica que el propio
// flujo pide (3.2). En los demás sub-pasos no se ofrece la cámara, porque
// ofrecerla en todos invita a llenar el expediente de fotos sueltas que nadie
// va a mirar.
export const SUBETAPAS_CON_EVIDENCIA_FOTOGRAFICA = new Set(['3.1', '3.2'])

// Documento que se maneja en cada subetapa del GCCON-P-010, por su código.
//
// Es lo único de la definición del proceso que la plantilla del backend
// (GcconP010Plantilla.java) no trae: títulos, rótulos, responsables y estado de
// cada subetapa llegan del servidor en mapEtapas. Aquí había una copia completa
// de las 27 subetapas (STEPS_INITIAL) de la que solo se leía este campo, y quien
// la editaba creía estar cambiando lo que se ve en pantalla.
export const DOCUMENTO_POR_SUBETAPA: Record<string, string> = {
  '1.1': 'Ficha de necesidad',
  '1.2': 'Acto administrativo',
  '1.3': 'GCCON-F-046',
  '1.4': 'CDP + Póliza',
  '1.5': 'Contrato SECOP II',
  '1.6': 'C.I. Supervisión',
  '2.1': 'Contrato SECOP II',
  '2.2': 'RUT / Cámara comercio',
  '2.3': 'Cronograma',
  '2.4': 'Matriz de control',
  '2.5': 'Certificado PILA',
  '2.6': 'Póliza de cumplimiento',
  '2.7': 'GCCON-F-018',
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
  welcome: `Este es su panel de supervisión. Cuando se le asigne un contrato, aquí verá las 6 etapas del proceso GCCON-P-010. Yo lo voy a guiar paso a paso — en cada etapa activa le explico qué hacer, y los documentos formales los arma SICOT con los datos del contrato; usted los revisa y los firma. Haga clic en "Iniciar Paso" cuando esté listo para empezar.`,

  // La guía de cada sub-paso no vive aquí: la arma data/guiaSubPaso.ts con la
  // descripción y el responsable de la plantilla GCCON-P-010, sin llamar al
  // modelo (antes se le preguntaba a Ollama en cada sub-paso y en un portátil
  // la respuesta se cortaba a los 240 s). Aquí solo quedan los mensajes de
  // cierre de cada paso, que son un resumen de transición.
  step1done:
    'Ha completado el Paso 1 — Inicio. Ahora empieza su participación activa: en el Paso 2 usted revisa los datos del contratista, confirma el cronograma y firma el Acta de Inicio GCCON-F-018, que SICOT arma con los datos del contrato.',
  step2done:
    'Ha completado el Paso 2 — Inicio. El Acta GCCON-F-018 quedó firmada y registrada. Ahora avanzamos al Paso 3: Inspección, donde verificará la entrega física en bodega.',
  step3done:
    'Ha completado el Paso 3 — Inspección. El Informe GCCON-F-031 quedó firmado y registrado en el expediente. Ahora avanzamos al Paso 4: Recepción formal con el Acta GIL-F-010.',
  step4done:
    'Ha completado el Paso 4 — Recepción. El Acta GIL-F-010 quedó firmada y registrada. Ahora avanzamos al Paso 5: Certificación de cumplimiento y trámite de pago.',
  step5done:
    'Ha completado el Paso 5 — Certificación. Su certificación quedó firmada; el trámite de pago ahora sigue con el Ordenador del gasto. Avanzamos al Paso 6: Cierre, el último de su supervisión.',
  step6done:
    'Ha completado el Paso 6 — Cierre. Su supervisión del contrato quedó formalmente cerrada: Acta de Inicio, Informe de Supervisión, Acta de Recibo, Certificación de cumplimiento e Informe Final quedaron firmados y registrados en el expediente.',
}

// NOTA: la coincidencia de palabras clave (CHAT_RESPONSES) que vivía aquí se
// eliminó — el chat del Copiloto ahora es una IA real (Ollama, vía
// CopilotoChatService en el backend, POST /api/contratos/{id}/copiloto/chat),
// anclada a los datos reales del contrato y al estado real de sus etapas.
// Ver services/documentoService.ts#preguntarCopiloto.

// Documentos formales que arma SICOT — se siguen en la pestaña Documentos
// (GCCON-P-010 / GCCON-M-002). Claves de generación (`tipo`) coinciden con
// PlantillaDocumentoIA.CATALOGO en el backend, y `llevaObservaciones` con su
// campo del mismo nombre: el Acta de Inicio y la Certificación no tienen
// apartado de observaciones en el formato oficial.
export const FORMAL_DOCS = [
  {
    subStepId: '2.7',
    llevaObservaciones: false,
    tipo: 'ACTA_INICIO',
    name: 'Acta de Inicio',
    code: 'GCCON-F-018',
    step: 2,
    desc: 'Fecha de inicio, duración, alcance y responsabilidades del supervisor.',
  },
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
