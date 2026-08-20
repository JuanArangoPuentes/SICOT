// Datos y contenidos del flujo de supervisión GCCON-P-010.
// Esta es la configuración/base de conocimiento del proceso (etapas, textos del
// tutorial, respuestas del copiloto, catálogo de documentos formales) — NO son
// datos de un contrato de ejemplo. Los valores de instancia (CONTRACT) llegan
// vacíos: deben poblarse desde la API/base de datos real.
// Extraído 1:1 desde el App.tsx original de Figma Make — sin cambios de lógica.

import type { Step } from '@/types/domain'

export const CONTRACT = {
  id: '',
  object: '',
  value: '',
  startDate: '',
  endDate: '',
  supervisor: '',
  supervisorEmail: 'supervisor@soy.sena.edu.co',
}

// Sub-steps where the Copiloto generates the document; supervisor only signs.
// Corregido contra fuentes reales (Datos SICOT, ver memoria de proyecto
// project_sicot_gccon_p010_grounded): el Oficio de Pago GRF-F-089 ("SCM") lo
// firma el Ordenador del gasto, no el supervisor — no se incluye aquí.
export const AI_GENERATED_DOCS = new Set(['2.7', '3.4', '4.3', '5.3', '6.3'])

// GCCON-P-010 — Etapas de supervisión de contratos
export const STEPS_INITIAL: Step[] = [
  {
    id: 1, title: 'INICIO — Estudios y Suscripción', status: 'active',
    subSteps: [
      { id: '1.1', label: 'Identificación de la necesidad institucional', responsible: 'Área requirente', document: 'Ficha de necesidad', completed: false },
      { id: '1.2', label: 'Conformación de la Unidad de Contratación', responsible: 'Ordenador del gasto', document: 'Acto administrativo', completed: false },
      { id: '1.3', label: 'Elaboración de estudios previos (GCCON-F-046)', responsible: 'Unidad de Contratación', document: 'GCCON-F-046', completed: false },
      { id: '1.4', label: 'Expedición del CDP y aprobación de garantías', responsible: 'Unidad de Contratación', document: 'CDP + Póliza', completed: false },
      { id: '1.5', label: 'Suscripción y publicación en SECOP II', responsible: 'Unidad de Contratación', document: 'Contrato SECOP II', completed: false },
      { id: '1.6', label: 'Designación formal del supervisor', responsible: 'Ordenador del gasto', document: 'C.I. Supervisión', completed: false },
    ],
  },
  {
    id: 2, title: 'INICIO — Acta de Inicio (GCCON-F-018)', status: 'pending',
    subSteps: [
      { id: '2.1', label: 'Revisión del objeto y alcance del contrato', responsible: 'Supervisor', document: 'Contrato SECOP II', completed: false },
      { id: '2.2', label: 'Verificación de datos del contratista y NIT', responsible: 'Supervisor', document: 'RUT / Cámara comercio', completed: false },
      { id: '2.3', label: 'Confirmación de fecha de inicio y duración', responsible: 'Supervisor', document: 'Cronograma', completed: false },
      { id: '2.4', label: 'Establecimiento de responsabilidades y puntos de control', responsible: 'Supervisor', document: 'Matriz de control', completed: false },
      { id: '2.5', label: 'Verificación de afiliaciones a seguridad social', responsible: 'Supervisor', document: 'Certificado PILA', completed: false },
      { id: '2.6', label: 'Registro de garantías vigentes', responsible: 'Unidad de Contratación', document: 'Póliza de cumplimiento', completed: false },
      { id: '2.7', label: 'Firma del Acta de Inicio (GCCON-F-018)', responsible: 'Supervisor', document: 'GCCON-F-018', completed: false, aiGenerated: true },
    ],
  },
  {
    id: 3, title: 'INSPECCIÓN — Monitoreo y Ejecución', status: 'pending',
    subSteps: [
      { id: '3.1', label: 'Verificación física de la entrega en bodega', responsible: 'Supervisor', document: 'Acta de visita', completed: false },
      { id: '3.2', label: 'Carga de evidencia fotográfica georreferenciada', responsible: 'Supervisor', document: 'Registro fotográfico', completed: false },
      { id: '3.3', label: 'Comparación cantidad/calidad vs. ficha técnica', responsible: 'Supervisor', document: 'Lista de chequeo', completed: false },
      { id: '3.4', label: 'Firma del Informe de Supervisión (GCCON-F-031)', responsible: 'Supervisor', document: 'GCCON-F-031', completed: false, aiGenerated: true },
    ],
  },
  {
    id: 4, title: 'RECEPCIÓN — Acta de Recibo a Satisfacción', status: 'pending',
    subSteps: [
      { id: '4.1', label: 'Verificación de aportes a seguridad social (PILA)', responsible: 'Supervisor', document: 'Planilla PILA', completed: false },
      { id: '4.2', label: 'Verificación de factura electrónica DIAN (FEV)', responsible: 'Supervisor', document: 'FEV DIAN', completed: false },
      { id: '4.3', label: 'Firma del Acta de Recibo a Satisfacción (GIL-F-010)', responsible: 'Supervisor', document: 'GIL-F-010', completed: false, aiGenerated: true },
    ],
  },
  {
    // "ESUCON" no es un código de formato oficial confirmado — se mantiene
    // en el título por ser el término que usa el CTMA, pero el documento en
    // sí se modela como "Certificación de cumplimiento" (PENDIENTE_DE_DEFINIR).
    // El Oficio de Pago (GRF-F-089) lo firma el Ordenador del gasto, no el
    // supervisor, así que no aparece como sub-paso de firma aquí.
    id: 5, title: 'CERTIFICACIÓN — Cumplimiento y Trámite de Pago', status: 'pending',
    subSteps: [
      { id: '5.1', label: 'Verificación de vigencia de garantías', responsible: 'Supervisor', document: 'Póliza vigencia', completed: false },
      { id: '5.2', label: 'Revisión de orden de pago y CRP', responsible: 'Supervisor', document: 'CRP', completed: false },
      { id: '5.3', label: 'Firma de la Certificación de cumplimiento', responsible: 'Supervisor', document: 'Certificación de cumplimiento', completed: false, aiGenerated: true },
    ],
  },
  {
    id: 6, title: 'CIERRE — Informe Final y Archivo (GCCON-F-030)', status: 'pending',
    subSteps: [
      { id: '6.1', label: 'Verificación de cumplimiento total del objeto contractual', responsible: 'Supervisor', document: 'Informe final', completed: false },
      { id: '6.2', label: 'Evaluación de modificaciones o adiciones (si aplica)', responsible: 'Supervisor', document: 'Adición / prórroga SECOP II', completed: false },
      { id: '6.3', label: 'Firma del Informe Final de Supervisión (GCCON-F-030)', responsible: 'Supervisor', document: 'GCCON-F-030', completed: false, aiGenerated: true },
      { id: '6.4', label: 'Cierre y archivo del expediente digital en SIGEP', responsible: 'Unidad de Contratación', document: 'Expediente SIGEP', completed: false },
    ],
  },
]

export const TUTORIAL: Record<string, string> = {
  welcome: `Este es su panel de supervisión. Cuando se le asigne un contrato, aquí verá las 6 etapas del proceso GCCON-P-010. Yo lo voy a guiar paso a paso — en cada etapa activa le explico qué hacer, y en los documentos formales los redacto yo; usted solo revisa y firma. Haga clic en "Iniciar Paso" cuando esté listo para empezar.`,

  // Los textos de guía por sub-paso (antes hardcodeados aquí, uno por uno)
  // se eliminaron: SupervisorPanel.tsx ahora le pregunta al Copiloto real
  // (Ollama, vía preguntaGuiaSubPaso + preguntarCopiloto) qué hacer en cada
  // sub-paso, anclado a los datos reales de ESE contrato — no un texto
  // genérico idéntico para cualquier contrato. Solo quedan aquí los mensajes
  // de cierre de cada paso (no son instrucciones de tarea, son un resumen
  // rápido de transición que no necesita ser específico por contrato).
  step1done: 'Ha completado el Paso 1 — Inicio. Ahora empieza su participación activa: en el Paso 2 usted revisa los datos del contratista, confirma el cronograma y firma el Acta de Inicio GCCON-F-018, que yo genero automáticamente con los datos del contrato.',
  step2done: 'Ha completado el Paso 2 — Inicio. El Acta GCCON-F-018 quedó firmada y registrada. Ahora avanzamos al Paso 3: Inspección, donde verificará la entrega física en bodega.',
  step3done: 'Ha completado el Paso 3 — Inspección. El Informe GCCON-F-031 quedó firmado y registrado en el expediente. Ahora avanzamos al Paso 4: Recepción formal con el Acta GIL-F-010.',
  step4done: 'Ha completado el Paso 4 — Recepción. El Acta GIL-F-010 quedó firmada y registrada. Ahora avanzamos al Paso 5: Certificación de cumplimiento y trámite de pago.',
  step5done: 'Ha completado el Paso 5 — Certificación. Su certificación quedó firmada; el trámite de pago ahora sigue con el Ordenador del gasto. Avanzamos al Paso 6: Cierre, el último de su supervisión.',
  step6done: 'Ha completado el Paso 6 — Cierre. Su supervisión del contrato quedó formalmente cerrada: Acta de Inicio, Informe de Supervisión, Acta de Recibo, Certificación de cumplimiento e Informe Final quedaron firmados y registrados en el expediente.',
}

// NOTA: la coincidencia de palabras clave (CHAT_RESPONSES) que vivía aquí se
// eliminó — el chat del Copiloto ahora es una IA real (Ollama, vía
// CopilotoChatService en el backend, POST /api/contratos/{id}/copiloto/chat),
// anclada a los datos reales del contrato y al estado real de sus etapas.
// Ver services/documentoService.ts#preguntarCopiloto.

// Formal documents the Copiloto generates — tracked for the Documentos tab
// (GCCON-P-010 / GCCON-M-002). Claves de generación (`tipo`) coinciden con
// PlantillaDocumentoIA.CATALOGO en el backend.
export const FORMAL_DOCS = [
  { subStepId: '2.7', tipo: 'ACTA_INICIO', name: 'Acta de Inicio', code: 'GCCON-F-018', step: 2, desc: 'Fecha de inicio, duración, alcance y responsabilidades del supervisor.' },
  { subStepId: '3.4', tipo: 'INFORME_SUPERVISION', name: 'Informe de Supervisión', code: 'GCCON-F-031', step: 3, desc: 'Control de ejecución, inspección física, novedades y avances.' },
  { subStepId: '4.3', tipo: 'ACTA_RECIBO', name: 'Acta de Recibo a Satisfacción', code: 'GIL-F-010', step: 4, desc: 'Recepción formal de bienes — cantidad, calidad y especificaciones técnicas.' },
  { subStepId: '5.3', tipo: 'CERTIFICACION_CUMPLIMIENTO', name: 'Certificación de cumplimiento', code: 'PENDIENTE_DE_DEFINIR', step: 5, desc: 'Certificación del supervisor que respalda el trámite de pago ("ESUCON" en el CTMA; sin código de formato oficial confirmado).' },
  { subStepId: '6.3', tipo: 'INFORME_FINAL', name: 'Informe Final de Supervisión', code: 'GCCON-F-030', step: 6, desc: 'Cumplimiento de obligaciones, aspectos financieros y conclusión del contrato (no es el acta de liquidación).' },
]
