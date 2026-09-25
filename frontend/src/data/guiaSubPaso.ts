// Guía de cada sub-paso del tutorial, armada con los datos del procedimiento.
//
// Hasta el 24-09-2026 el tutorial le preguntaba al modelo de IA qué hacer en
// cada sub-paso: 27 llamadas por contrato, de varios minutos cada una en un
// portátil sin GPU. En la prueba integral de ese día, dentro del APK, la
// primera pregunta se cortó a los 240 s y el supervisor se quedó sin guía. Lo
// que el tutorial necesita decir —qué es el sub-paso, quién lo hace y qué hacer
// en SICOT— ya está escrito en la plantilla GCCON-P-010 del backend
// (descripción y responsable de cada subetapa), así que se arma aquí al
// instante. El copiloto sigue disponible para preguntas de más detalle: es el
// motor el que resuelve lo que es fijo, para que baste una IA pequeña.

import { AI_GENERATED_DOCS, FORMAL_DOCS, SUBETAPAS_CON_EVIDENCIA_FOTOGRAFICA } from '@/data/contractFlow'
import type { Step, SubStep } from '@/types/domain'

export function guiaDelSubPaso(step: Step, sub: SubStep): string {
  const partes = [`Sub-paso ${sub.id} — ${sub.label} (Paso ${step.id}: ${step.title}).`]
  if (sub.description) partes.push(sub.description)
  const responsable = sub.responsible?.trim()
  const esDelSupervisor = !responsable || /supervisor/i.test(responsable)
  if (responsable) partes.push(`Responsable: ${responsable}.`)

  const doc = AI_GENERATED_DOCS.has(sub.id) ? FORMAL_DOCS.find((d) => d.subStepId === sub.id) : undefined
  // Solo al cerrar el último sub-paso de un paso se le piden al supervisor las
  // observaciones (SupervisorPanel.handleActionSubStep). 2.7, 3.4, 4.3 y 5.3 lo
  // son; 6.3 no (el último del Paso 6 es 6.4), y su documento se genera y firma
  // al pulsar el botón. Prometer las observaciones en 6.3 dejaba al supervisor
  // esperando una pregunta que no llegaba, con el Informe Final ya firmado.
  const pideObservaciones = step.subSteps.length === 0 || step.subSteps[step.subSteps.length - 1]?.id === sub.id
  if (doc) {
    partes.push(
      `Qué hacer en SICOT: cuando tenga lo necesario, pulse «Firmar documento». SICOT arma «${doc.name}»` +
        `${doc.code === 'PENDIENTE_DE_DEFINIR' ? '' : ` (${doc.code})`} con los datos exactos del contrato, ` +
        'y usted lo revisa y lo firma con su firma electrónica. ' +
        (pideObservaciones
          ? 'Antes le pediré que me cuente qué hizo en el paso: eso va como observaciones del documento. '
          : 'Se genera y se firma en el momento, sin observaciones: revise antes que todo lo del paso esté hecho. ') +
        'Lo que SICOT no sabe (facturas, pólizas, pagos) queda marcado como «dato pendiente».',
    )
  } else if (SUBETAPAS_CON_EVIDENCIA_FOTOGRAFICA.has(sub.id)) {
    // La vista previa no es la carga: hasta pulsar «Cargar evidencia» la foto
    // no llega al expediente, y marcar el sub-paso antes lo cerraba sin ella.
    partes.push(
      'Qué hacer en SICOT: tome la foto con «Tomar foto de la entrega» o elija una con «Elegir una foto», ' +
        'pulse «Cargar evidencia» y, cuando aparezca como cargada, marque el sub-paso como completado. ' +
        'Con la cámara, SICOT guarda la fecha y el lugar de la toma; una foto de la galería puede no traer el lugar.',
    )
  } else if (!esDelSupervisor) {
    partes.push(
      `Qué hacer en SICOT: este sub-paso lo realiza ${responsable}; márquelo como completado cuando le confirmen que está hecho.`,
    )
  } else {
    partes.push('Qué hacer en SICOT: cuando lo haya hecho, márquelo como completado.')
  }
  partes.push('Si necesita más detalle, pregúnteme abajo.')
  return partes.join('\n\n')
}
