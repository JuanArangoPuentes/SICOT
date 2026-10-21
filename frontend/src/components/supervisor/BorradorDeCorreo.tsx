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

import { useState } from 'react'
import { formatFecha } from '@/services/format'
import type { ContratoResponse } from '@/services/api/types'
import { BotonCopiar, BOTON, sePuedenAbrirEnlaces } from './enlacesExternos'

export type TipoDeCorreo = 'CORREO_CONTRATISTA' | 'CORREO_SST'

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

export function armarBorrador(tipo: TipoDeCorreo, c: ContratoResponse): Borrador {
  const firma = [
    c.supervisorNombre ?? '[Su nombre]',
    'Supervisor(a) del contrato ' + c.numeroContrato,
    'Centro Tecnológico del Mobiliario — SENA',
  ].join('\n')
  const vigencia = `del ${formatFecha(c.fechaInicio)} al ${formatFecha(c.fechaFin)}`

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

  const cuerpo = [
    'Cordial saludo.',
    '',
    `Les informo que inició la ejecución del contrato ${c.numeroContrato}` +
      (c.contratista ? `, suscrito con ${c.contratista},` : '') +
      ` cuyo objeto es «${c.objeto}», con vigencia ${vigencia}.`,
    '',
    'Como profesionales de SST y ambiental, los invito a acompañar las entregas y las actividades del contrato ' +
      'según las obligaciones que les corresponden. Les compartiré las fechas de entrega en cuanto queden ' +
      'acordadas con el contratista.',
    '',
    firma,
  ].join('\n')
  return {
    para: '',
    asunto: `Inicio del contrato ${c.numeroContrato} — acompañamiento de SST y ambiental`,
    cuerpo,
    avisos: [
      'Los correos de los profesionales de SST y ambiental están en el estudio previo del contrato, dentro del ' +
        'expediente del proceso en el SECOP II (el enlace, en el paso 2.3). Agréguelos como destinatarios.',
    ],
  }
}

export default function BorradorDeCorreo({ tipo, contrato }: { tipo: TipoDeCorreo; contrato: ContratoResponse }) {
  const [abierto, setAbierto] = useState(false)
  const b = armarBorrador(tipo, contrato)
  const mailto =
    `mailto:${encodeURIComponent(b.para)}?subject=${encodeURIComponent(b.asunto)}` +
    `&body=${encodeURIComponent(b.cuerpo)}`

  return (
    <div style={{ paddingLeft: 26, display: 'flex', flexDirection: 'column', gap: 6, marginTop: 6 }}>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
        <button type="button" className="btn-green" style={BOTON} onClick={() => setAbierto((a) => !a)}>
          {abierto ? 'Ocultar el borrador' : 'Ver el borrador del correo'}
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
