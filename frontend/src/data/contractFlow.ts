// Datos y contenidos del flujo de supervisión GCCON-P-010.
// Esta es la configuración/base de conocimiento del proceso (etapas, textos del
// tutorial, respuestas del copiloto, catálogo de documentos formales) — NO son
// datos de un contrato de ejemplo.
// Extraído 1:1 desde el App.tsx original de Figma Make — sin cambios de lógica.

import type { Step } from '@/types/domain'

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

// Etapas de supervisión — espejo de GcconP010Plantilla en el backend. Las
// etapas 1 y 2 siguen el proceso real que documentó el supervisor Alex Zapata
// el 15-10-2026 (docs/producto/LISTA_CHEQUEO_ALEX.md); las 3 a 6 siguen
// GCCON-P-010 hasta que él documente esa parte.
export const STEPS_INITIAL: Step[] = [
  {
    id: 1,
    title: 'INICIO — Designación de la supervisión (GCCON-F-032)',
    status: 'active',
    subSteps: [
      {
        id: '1.1',
        label: 'Suscripción y publicación del contrato en SECOP II',
        responsible: 'Unidad de Contratación',
        document: 'Contrato SECOP II',
        completed: false,
      },
      {
        id: '1.2',
        label: 'Designación del supervisor y del apoyo a la supervisión (GCCON-F-032)',
        responsible: 'Ordenador del gasto',
        document: 'GCCON-F-032',
        completed: false,
      },
      {
        id: '1.3',
        label: 'Registro del contrato en SICOT y aviso al supervisor',
        responsible: 'Apoyo a la supervisión',
        document: 'Registro en SICOT',
        completed: false,
      },
    ],
  },
  {
    id: 2,
    title: 'INICIO — Acta de Inicio y arranque (GCCON-F-018)',
    status: 'pending',
    subSteps: [
      {
        id: '2.1',
        label: 'Firma y devolución del Acta de Inicio (GCCON-F-018)',
        responsible: 'Supervisor',
        document: 'Acta firmada + evidencia del envío',
        completed: false,
      },
      {
        id: '2.2',
        label: 'Aviso a los profesionales SST y ambiental',
        responsible: 'Supervisor',
        document: 'Correo a SST y ambiental',
        completed: false,
      },
      {
        id: '2.3',
        label: 'Verificación de especificaciones en SECOP II',
        responsible: 'Supervisor',
        document: 'SECOP II',
        completed: false,
      },
      {
        id: '2.4',
        label: 'Contacto con el contratista y acuerdo de entregas',
        responsible: 'Supervisor',
        document: 'Correo al contratista',
        completed: false,
      },
    ],
  },
  {
    id: 3,
    title: 'INSPECCIÓN — Monitoreo y Ejecución',
    status: 'pending',
    subSteps: [
      {
        id: '3.1',
        label: 'Verificación física de la entrega en bodega',
        responsible: 'Supervisor',
        document: 'Acta de visita',
        completed: false,
      },
      {
        id: '3.2',
        label: 'Carga de evidencia fotográfica georreferenciada',
        responsible: 'Supervisor',
        document: 'Registro fotográfico',
        completed: false,
      },
      {
        id: '3.3',
        label: 'Comparación cantidad/calidad vs. ficha técnica',
        responsible: 'Supervisor',
        document: 'Lista de chequeo',
        completed: false,
      },
      {
        id: '3.4',
        label: 'Firma del Informe de Supervisión (GCCON-F-031)',
        responsible: 'Supervisor',
        document: 'GCCON-F-031',
        completed: false,
        aiGenerated: true,
      },
    ],
  },
  {
    id: 4,
    title: 'RECEPCIÓN — Acta de Recibo a Satisfacción',
    status: 'pending',
    subSteps: [
      {
        id: '4.1',
        label: 'Verificación de aportes a seguridad social (PILA)',
        responsible: 'Supervisor',
        document: 'Planilla PILA',
        completed: false,
      },
      {
        id: '4.2',
        label: 'Verificación de factura electrónica DIAN (FEV)',
        responsible: 'Supervisor',
        document: 'FEV DIAN',
        completed: false,
      },
      {
        id: '4.3',
        label: 'Firma del Acta de Recibo a Satisfacción (GIL-F-010)',
        responsible: 'Supervisor',
        document: 'GIL-F-010',
        completed: false,
        aiGenerated: true,
      },
    ],
  },
  {
    // "ESUCON" no es un código de formato oficial confirmado — se mantiene
    // en el título por ser el término que usa el CTMA, pero el documento en
    // sí se modela como "Certificación de cumplimiento" (PENDIENTE_DE_DEFINIR).
    // El Oficio de Pago (GRF-F-089) lo firma el Ordenador del gasto, no el
    // supervisor, así que no aparece como sub-paso de firma aquí.
    id: 5,
    title: 'CERTIFICACIÓN — Cumplimiento y Trámite de Pago',
    status: 'pending',
    subSteps: [
      {
        id: '5.1',
        label: 'Verificación de vigencia de garantías',
        responsible: 'Supervisor',
        document: 'Póliza vigencia',
        completed: false,
      },
      {
        id: '5.2',
        label: 'Revisión de orden de pago y CRP',
        responsible: 'Supervisor',
        document: 'CRP',
        completed: false,
      },
      {
        id: '5.3',
        label: 'Firma de la Certificación de cumplimiento',
        responsible: 'Supervisor',
        document: 'Certificación de cumplimiento',
        completed: false,
        aiGenerated: true,
      },
    ],
  },
  {
    id: 6,
    title: 'CIERRE — Informe Final y Archivo (GCCON-F-030)',
    status: 'pending',
    subSteps: [
      {
        id: '6.1',
        label: 'Verificación de cumplimiento total del objeto contractual',
        responsible: 'Supervisor',
        document: 'Informe final',
        completed: false,
      },
      {
        id: '6.2',
        label: 'Evaluación de modificaciones o adiciones (si aplica)',
        responsible: 'Supervisor',
        document: 'Adición / prórroga SECOP II',
        completed: false,
      },
      {
        id: '6.3',
        label: 'Firma del Informe Final de Supervisión (GCCON-F-030)',
        responsible: 'Supervisor',
        document: 'GCCON-F-030',
        completed: false,
        aiGenerated: true,
      },
      {
        id: '6.4',
        label: 'Cierre y archivo del expediente digital en SIGEP',
        responsible: 'Unidad de Contratación',
        document: 'Expediente SIGEP',
        completed: false,
      },
    ],
  },
]

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
