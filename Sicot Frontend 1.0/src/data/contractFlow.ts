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

// Sub-steps where the Copiloto generates the document; supervisor only signs
export const AI_GENERATED_DOCS = new Set(['2.7', '3.4', '4.3', '4.4', '6.2'])

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
    id: 5, title: 'CERTIFICACIÓN — ESUCON y Trámite de Pago', status: 'pending',
    subSteps: [
      { id: '5.1', label: 'Verificación de vigencia de garantías', responsible: 'Supervisor', document: 'Póliza vigencia', completed: false },
      { id: '5.2', label: 'Revisión de orden de pago y CRP', responsible: 'Supervisor', document: 'CRP', completed: false },
      { id: '5.3', label: 'Firma del Certificado ESUCON y Oficio SCM', responsible: 'Supervisor', document: 'ESUCON + SCM', completed: false, aiGenerated: true },
      { id: '5.4', label: 'Registro de cumplimiento en GRF-F-089', responsible: 'Supervisor', document: 'GRF-F-089', completed: false, aiGenerated: true },
    ],
  },
  {
    id: 6, title: 'CIERRE — Liquidación y Archivo (GCCON-F-030)', status: 'pending',
    subSteps: [
      { id: '6.1', label: 'Verificación de cumplimiento total del objeto contractual', responsible: 'Supervisor', document: 'Informe final', completed: false },
      { id: '6.2', label: 'Evaluación de modificaciones o adiciones (si aplica)', responsible: 'Supervisor', document: 'Adición / prórroga SECOP II', completed: false },
      { id: '6.3', label: 'Firma del Acta de Liquidación (GCCON-F-030)', responsible: 'Supervisor', document: 'GCCON-F-030', completed: false, aiGenerated: true },
      { id: '6.4', label: 'Cierre y archivo del expediente digital en SIGEP', responsible: 'Unidad de Contratación', document: 'Expediente SIGEP', completed: false },
    ],
  },
]

export const TUTORIAL: Record<string, string> = {
  welcome: `Este es tu panel de supervisión. Cuando se te asigne un contrato, aquí verás las etapas del proceso GCCON-P-010 y el paso activo que te corresponde. Haz clic en cada etapa para ver el detalle y comenzar.`,
  '3.1': 'GCCON-P-010 · Etapa Inspección: Ve a bodega y verifica físicamente que todos los materiales del contrato hayan llegado. Revisa el estado de embalaje y registra novedades. Cuando termines, haz clic en "Marcar completado".',
  '3.2': 'Carga las fotos de bodega con georreferenciación activa. Documenta cada caja o pallet. Las imágenes quedarán como evidencia formal en el expediente SIGEP. Cuando termines, haz clic en "Marcar completado".',
  '3.3': 'Compara la cantidad y calidad recibida contra la ficha técnica del contrato. Anota cualquier discrepancia — afectará el Informe de Supervisión GCCON-F-031. Cuando termines, haz clic en "Marcar completado".',
  '3.4': 'Ya generé el Informe de Supervisión GCCON-F-031 con los datos verificados en los pasos anteriores — cantidades, estado, fechas y número de contrato incluidos. Solo necesito tu firma electrónica para registrarlo oficialmente. Haz clic en "Firmar documento".',
  step3done: '¡Excelente! Completaste el Paso 3 — Inspección. El Informe GCCON-F-031 quedó firmado y registrado en el expediente. Ahora avanzamos al Paso 4: Recepción formal con el Acta GIL-F-010.',
}

export const CHAT_RESPONSES: Array<[string, string]> = [
  ['cómo crear un contrato', 'El rol de Gestión carga la ficha del contrato desde su panel. El Copiloto extrae los datos automáticamente y los asigna al Supervisor designado.'],
  ['qué documentos necesito', 'Para el Paso 3 activo (Inspección) necesitas verificar la entrega física, cargar evidencia fotográfica y comparar con la ficha técnica. Yo genero el Informe GCCON-F-031 automáticamente; tú solo lo firmas.'],
  ['iniciar paso 1', 'Los Pasos 1 y 2 ya están completados — incluyen estudios previos, suscripción SECOP II, designación de supervisor y el Acta de Inicio GCCON-F-018 que ya firmaste.'],
  ['cómo conectar secop', 'La publicación en SECOP II corresponde a la Unidad de Contratación. El botón "Conectar SECOP II" en la barra superior está disponible para consultar el estado del contrato.'],
  ['gccon-m-002', 'El GCCON-M-002 es el Manual de Supervisión e Interventoría del SENA. Define las responsabilidades del supervisor en cada etapa del contrato: Inicio, Inspección, Recepción, Certificación y Cierre.'],
  ['gccon-f-018', 'El GCCON-F-018 es el Acta de Inicio del Contrato. Ya está firmada (Paso 2, sub-paso 2.7) — el Copiloto la generó con los datos del contratista, fechas y alcance.'],
  ['gil-f-010', 'El GIL-F-010 es el Acta de Recibo a Satisfacción de Bienes. La genero automáticamente con los datos del Paso 4 — tú solo la firmas en el subpaso 4.3.'],
  ['gccon-f-031', 'El GCCON-F-031 es el Informe de Supervisión Unificado. Lo genero automáticamente con el resultado de la inspección (Paso 3) — tú solo firmas en el subpaso 3.4.'],
  ['esucon', 'El ESUCON es el Certificado del Supervisor de Contratación. Lo genero junto con el Oficio SCM en el Paso 5 (Certificación) — tú solo firmas el subpaso 5.3.'],
  ['gccon-f-030', 'El GCCON-F-030 es el Acta de Liquidación del Contrato. La genero en el Paso 6 (Cierre) con los valores finales, deducciones y cumplimiento — tú firmas en el subpaso 6.3.'],
]

// Formal documents the Copiloto generates — tracked for the Documentos tab (GCCON-P-010)
export const FORMAL_DOCS = [
  { subStepId: '2.7', name: 'Acta de Inicio', code: 'GCCON-F-018', step: 2, desc: 'Fecha de inicio, duración, alcance y responsabilidades del supervisor.' },
  { subStepId: '3.4', name: 'Informe de Supervisión', code: 'GCCON-F-031', step: 3, desc: 'Control de ejecución, inspección física, novedades y avances.' },
  { subStepId: '4.3', name: 'Acta de Recibo a Satisfacción', code: 'GIL-F-010', step: 4, desc: 'Recepción formal de bienes — cantidad, calidad y especificaciones técnicas.' },
  { subStepId: '5.3', name: 'Certificado ESUCON + Oficio SCM', code: 'ESUCON + SCM', step: 5, desc: 'Respaldo formal de supervisión y certificación de cumplimiento de pago.' },
  { subStepId: '5.4', name: 'Certificación de Cumplimiento', code: 'GRF-F-089', step: 5, desc: 'Certifica cumplimiento de especificaciones para trámite de pago.' },
  { subStepId: '6.3', name: 'Acta de Liquidación', code: 'GCCON-F-030', step: 6, desc: 'Liquidación final del contrato con valores, deducciones y archivo.' },
]
