// Pasos 2.2 y 2.4 del proceso real: los correos de arranque.
//
// El supervisor escribe dos correos al iniciar el contrato: a los
// profesionales de SST y ambiental, para avisarles que el contrato inició, y
// al contratista, para presentarse y acordar las entregas. SICOT no los envía
// —no hay una cuenta institucional definida para eso, y el correo sale del
// supervisor—: arma el borrador con los datos reales del contrato para que él
// lo revise, lo complete y lo mande desde su correo.
//
// Lo que SICOT no sabe va entre corchetes y se dice arriba, en vez de
// inventarlo: los correos de SST y ambiental están en el estudio previo.
//
// Las cantidades de los bienes ya no son un hueco: desde el 21-10-2026 el
// correo al contratista lleva adjunta la solicitud de pedido en Excel, que es
// lo que pidió el paso 5 de la lista de chequeo de Alex. El borrador la
// anuncia, y el adjunto se arma en SolicitudDePedido.tsx, debajo.
//
// Desde el 22-10-2026 los destinatarios de SST y ambiental tampoco son un
// hueco: el supervisor los registra en el paso 2.2 (ContactosSstAmbiental.tsx).
// Y hay dos correos más, los que siguen al acuerdo de entregas del 2.4
// (AcuerdoDeEntregas.tsx): la confirmación de las fechas al contratista y el
// aviso de esas fechas a SST y ambiental, que el primer correo les prometía.

import { useState } from 'react'
import { formatFecha } from '@/services/format'
import type { ContactoDelContrato, ContratoResponse, EntregaAcordada } from '@/services/api/types'
import { BotonCopiar, BOTON, sePuedenAbrirEnlaces } from './enlacesExternos'

export type TipoDeCorreo = 'CORREO_CONTRATISTA' | 'CORREO_SST' | 'CONFIRMACION_ENTREGAS' | 'ENTREGAS_SST'

/** Lo que el supervisor ya registró al arrancar el contrato y los correos usan. */
export interface DatosDelArranque {
  contactos?: ContactoDelContrato[]
  entregas?: EntregaAcordada[]
}

interface Borrador {
  para: string
  asunto: string
  cuerpo: string
  /** Lo que el supervisor tiene que completar antes de enviar. */
  avisos: string[]
}

function esSuministroOCompra(c: ContratoResponse): boolean {
  const texto = `${c.tipoContrato ?? ''} ${c.objeto}`.toLowerCase()
  return /suministro|compraventa|compra|adquisici/.test(texto)
}

/** Las fechas como las lee el destinatario: una por renglón, con su número. */
function listaDeEntregas(entregas: EntregaAcordada[]): string {
  return entregas.map((e) => `${e.numero}. ${formatFecha(e.fecha)} — ${e.descripcion}`).join('\n')
}

/** Los correos de SST y ambiental registrados, sin repetir el mismo dos veces. */
function correosDeSstYAmbiental(contactos: ContactoDelContrato[] | undefined): string {
  return [...new Set((contactos ?? []).map((k) => k.correo))].join(', ')
}

export function armarBorrador(tipo: TipoDeCorreo, c: ContratoResponse, datos: DatosDelArranque = {}): Borrador {
  const firma = [
    c.supervisorNombre ?? '[Su nombre]',
    'Supervisor(a) del contrato ' + c.numeroContrato,
    'Centro Tecnológico del Mobiliario — SENA',
  ].join('\n')
  const vigencia = `del ${formatFecha(c.fechaInicio)} al ${formatFecha(c.fechaFin)}`

  if (tipo === 'CONFIRMACION_ENTREGAS') {
    const cuerpo = [
      `Señores ${c.contratista ?? '[contratista]'}`,
      c.representanteLegal ? `Atn. ${c.representanteLegal}, representante legal` : null,
      '',
      'Cordial saludo.',
      '',
      `Confirmo las fechas de entrega que acordamos para el contrato ${c.numeroContrato}, dentro de su vigencia ` +
        `${vigencia}:`,
      '',
      listaDeEntregas(datos.entregas ?? []),
      '',
      'Si alguna fecha tiene que cambiar, les pido avisarme con anticipación para acordar la nueva.',
      '',
      firma,
    ]
      .filter((l) => l !== null)
      .join('\n')
    return {
      para: c.correoContratista ?? '',
      asunto: `Contrato ${c.numeroContrato} — confirmación de las fechas de entrega`,
      cuerpo,
      avisos: c.correoContratista
        ? []
        : ['SICOT no tiene el correo del contratista: está en el Acta de Inicio. Agréguelo como destinatario.'],
    }
  }

  if (tipo === 'CORREO_CONTRATISTA') {
    const suministro = esSuministroOCompra(c)
    const avisos: string[] = []
    if (!c.correoContratista) {
      avisos.push('SICOT no tiene el correo del contratista: está en el Acta de Inicio. Agréguelo como destinatario.')
    }
    if (suministro) {
      avisos.push(
        'En un suministro el correo lleva adjunta la solicitud de pedido con las cantidades exactas. Cárguela ' +
          'abajo —se pega de la oferta económica— y descargue el Excel para adjuntarlo.',
      )
    }
    const cuerpo = [
      `Señores ${c.contratista ?? '[contratista]'}`,
      c.representanteLegal ? `Atn. ${c.representanteLegal}, representante legal` : null,
      '',
      'Cordial saludo.',
      '',
      `Me permito presentarme como supervisor(a) designado(a) del contrato ${c.numeroContrato}, cuyo objeto es ` +
        `«${c.objeto}», con vigencia ${vigencia}.`,
      '',
      'Con el fin de dar inicio a la ejecución, les propongo que acordemos las fechas de entrega dentro del plazo del contrato.' +
        (suministro
          ? ' Adjunto la solicitud de pedido con los bienes y las cantidades exactas que se deben entregar.'
          : ' Por favor indíquenme en qué fechas prestarían el servicio.'),
      '',
      'Quedo atento(a) a su respuesta.',
      '',
      firma,
    ]
      .filter((l) => l !== null)
      .join('\n')
    return {
      para: c.correoContratista ?? '',
      asunto: `Inicio del contrato ${c.numeroContrato} — presentación del supervisor y acuerdo de entregas`,
      cuerpo,
      avisos,
    }
  }

  const para = correosDeSstYAmbiental(datos.contactos)
  const avisosSst = para
    ? []
    : [
        'Todavía no registró a los profesionales de SST y ambiental: sus correos están en el estudio previo del ' +
          'contrato, dentro del expediente del proceso en el SECOP II (el enlace, en el paso 2.3). Regístrelos ' +
          'en el paso 2.2 y el correo saldrá dirigido a ellos.',
      ]
  const entregas = datos.entregas ?? []

  if (tipo === 'ENTREGAS_SST') {
    const cuerpo = [
      'Cordial saludo.',
      '',
      `Como les anuncié al inicio del contrato ${c.numeroContrato}, cuyo objeto es «${c.objeto}», estas son las ` +
        `fechas de entrega acordadas con ${c.contratista ?? 'el contratista'}:`,
      '',
      listaDeEntregas(entregas),
      '',
      'Los invito a acompañar las entregas según las obligaciones de SST y ambiental del contrato. Si alguna fecha ' +
        'cambia, se los informaré.',
      '',
      firma,
    ].join('\n')
    return {
      para,
      asunto: `Contrato ${c.numeroContrato} — fechas de entrega acordadas`,
      cuerpo,
      avisos: avisosSst,
    }
  }

  const cuerpo = [
    'Cordial saludo.',
    '',
    `Les informo que inició la ejecución del contrato ${c.numeroContrato}` +
      (c.contratista ? `, suscrito con ${c.contratista},` : '') +
      ` cuyo objeto es «${c.objeto}», con vigencia ${vigencia}.`,
    '',
    'Como profesionales de SST y ambiental, los invito a acompañar las entregas y las actividades del contrato ' +
      'según las obligaciones que les corresponden. ' +
      (entregas.length > 0
        ? 'Las fechas de entrega acordadas con el contratista son:'
        : 'Les compartiré las fechas de entrega en cuanto queden acordadas con el contratista.'),
    ...(entregas.length > 0 ? ['', listaDeEntregas(entregas)] : []),
    '',
    firma,
  ].join('\n')
  return {
    para,
    asunto: `Inicio del contrato ${c.numeroContrato} — acompañamiento de SST y ambiental`,
    cuerpo,
    avisos: avisosSst,
  }
}

export default function BorradorDeCorreo({
  tipo,
  contrato,
  datos,
  etiqueta,
}: {
  tipo: TipoDeCorreo
  contrato: ContratoResponse
  datos?: DatosDelArranque
  /** El texto del botón, cuando en el mismo paso hay más de un correo. */
  etiqueta?: string
}) {
  const [abierto, setAbierto] = useState(false)
  const b = armarBorrador(tipo, contrato, datos)
  // Cada destinatario se codifica por separado y se unen con coma: codificada,
  // la coma («%2C») hace que algunos programas de correo lean un solo
  // destinatario con dos direcciones pegadas.
  const destinatarios = b.para
    .split(',')
    .map((d) => encodeURIComponent(d.trim()))
    .filter(Boolean)
    .join(',')
  const mailto =
    `mailto:${destinatarios}?subject=${encodeURIComponent(b.asunto)}` + `&body=${encodeURIComponent(b.cuerpo)}`

  return (
    <div style={{ paddingLeft: 26, display: 'flex', flexDirection: 'column', gap: 6, marginTop: 6 }}>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
        <button type="button" className="btn-green" style={BOTON} onClick={() => setAbierto((a) => !a)}>
          {abierto ? 'Ocultar el borrador' : (etiqueta ?? 'Ver el borrador del correo')}
        </button>
        {sePuedenAbrirEnlaces() && (
          <a href={mailto} style={{ fontSize: 11.5, color: 'var(--accent)' }}>
            Abrir en mi correo
          </a>
        )}
        <BotonCopiar
          texto={`Para: ${b.para || '[destinatarios]'}\nAsunto: ${b.asunto}\n\n${b.cuerpo}`}
          etiqueta="Copiar el correo"
        />
      </div>
      {b.avisos.map((a) => (
        <span key={a} style={{ fontSize: 11.5, color: 'var(--alert-leve)' }}>
          {a}
        </span>
      ))}
      {abierto && (
        <pre
          style={{
            whiteSpace: 'pre-wrap',
            fontFamily: 'var(--font-ui)',
            fontSize: 11.5,
            color: 'var(--text-primary)',
            background: 'var(--bg-card)',
            border: '1px solid var(--border)',
            borderRadius: 4,
            padding: 10,
            margin: 0,
          }}
        >
          {`Para: ${b.para || '[destinatarios]'}\nAsunto: ${b.asunto}\n\n${b.cuerpo}`}
        </pre>
      )}
    </div>
  )
}
