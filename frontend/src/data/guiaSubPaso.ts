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

import {
  ACTA_FIRMADA_Y_ENVIO,
  AI_GENERATED_DOCS,
  FORMAL_DOCS,
  HERRAMIENTA_POR_PASO,
  SUBETAPAS_CON_EVIDENCIA_FOTOGRAFICA,
} from '@/data/contractFlow'
import type { Step, SubStep } from '@/types/domain'

export function guiaDelSubPaso(step: Step, sub: SubStep): string {
  const partes = [`Sub-paso ${sub.id} — ${sub.label} (Paso ${step.id}: ${step.title}).`]
  if (sub.description) partes.push(sub.description)
  const responsable = sub.responsible?.trim()
  const esDelSupervisor = !responsable || /supervisor/i.test(responsable)
  if (responsable) partes.push(`Responsable: ${responsable}.`)

  const doc = AI_GENERATED_DOCS.has(sub.id) ? FORMAL_DOCS.find((d) => d.subStepId === sub.id) : undefined
  // Solo al cerrar el último sub-paso de un paso se le piden al supervisor las
  // observaciones (SupervisorPanel.handleActionSubStep). 3.4, 4.3 y 5.3 lo
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
          ? doc.llevaObservaciones
            ? 'Antes le pediré que me cuente qué hizo en el paso: eso va como observaciones del documento. ' +
              // Desde el 29-09-2026 la redacción del Copiloto se muestra antes
              // de firmar: decir que va «tal cual» o firmarla sin enseñarla
              // eran las dos cosas que no podían pasar.
              'Si el Copiloto la redacta en lenguaje formal, se la mostraré junto a lo que usted escribió para que ' +
              'decida antes de firmar. '
            : // La Certificación no tiene apartado de observaciones:
              // prometer que la descripción iría al documento era falso desde
              // que sigue el formato oficial (28-09-2026).
              'Antes le pediré que me cuente qué hizo en el paso, para revisarlo con usted. '
          : 'Se genera y se firma en el momento, sin observaciones: revise antes que todo lo del paso esté hecho. ') +
        'Justo antes de generarlo le pediré los datos que el formato necesita y el contrato no tiene (facturas, ' +
        'pólizas, cédulas…); lo que deje vacío queda marcado como «dato pendiente».',
    )
  } else if (sub.documentosRequeridos === ACTA_FIRMADA_Y_ENVIO) {
    // El proceso real: el acta la elabora Contratación y llega por correo; el
    // paso queda esperando hasta tener el acta firmada y la evidencia del envío.
    partes.push(
      'Qué hacer en SICOT: cuando haya firmado el acta y la haya devuelto respondiendo el correo de Contratación, ' +
        'cargue en este sub-paso el acta firmada (PDF) y la evidencia del envío (el pantallazo o el PDF del correo ' +
        'que respondió). Cuando las dos aparezcan como cargadas, márquelo como completado: SICOT no lo deja cerrar ' +
        'sin ellas.',
    )
  } else if (HERRAMIENTA_POR_PASO[sub.label] === 'VERIFICACION_SECOP') {
    partes.push(
      'Qué hacer en SICOT: debajo de este sub-paso verá lo que el SECOP II publica del contrato y lo que no coincide ' +
        'con lo registrado en SICOT. Las cantidades y las especificaciones no vienen en esos datos: abra el proceso ' +
        'en el SECOP II con el enlace del sub-paso y revíselas en la oferta económica y el estudio previo del ' +
        'expediente; cuando esté todo revisado, márquelo como completado.',
    )
  } else if (HERRAMIENTA_POR_PASO[sub.label] === 'CORREO_SST') {
    partes.push(
      'Qué hacer en SICOT: registre debajo a los profesionales de SST y ambiental —sus correos están en el ' +
        'estudio previo, que se abre entrando al proceso en el SECOP II con el enlace del sub-paso de ' +
        'verificación; si lo carga, SICOT le propone los correos que encuentre en él—. Después abra el borrador ' +
        'del correo, que ya sale dirigido a ellos, y envíelo desde su correo. Cuando lo haya enviado, márquelo ' +
        'como completado.',
    )
  } else if (HERRAMIENTA_POR_PASO[sub.label] === 'CORREO_CONTRATISTA') {
    partes.push(
      'Qué hacer en SICOT: si es un suministro, cargue abajo la solicitud de pedido —se pega de la oferta ' +
        'económica— y descargue el Excel; después abra el borrador del correo al contratista, adjúntelo y envíelo ' +
        'desde su correo. Cuando acuerden las fechas de entrega, regístrelas en «Fechas de entrega acordadas»: ' +
        'deben caer dentro del plazo del contrato, SICOT le recuerda cada una antes de que llegue y le arma los ' +
        'correos para confirmarlas al contratista y compartirlas con SST y ambiental. El paso no se cierra sin al ' +
        'menos una fecha registrada.',
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
