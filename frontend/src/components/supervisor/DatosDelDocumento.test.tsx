import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import DatosDelDocumento, {
  MAX_FILAS,
  esRespuestaNegativa,
  guardarTablasDelContrato,
  leerTablasDelContrato,
  soloLoDelDocumento,
  tablasDelContrato,
} from './DatosDelDocumento'
import type { PlantillaDocumento } from '@/services/api/types'

const campo = (
  clave: string,
  etiqueta: string,
  extra: Partial<PlantillaDocumento['campos'][number]> = {},
): PlantillaDocumento['campos'][number] => ({
  clave,
  etiqueta,
  ejemplo: 'x',
  opcional: false,
  dependeDe: null,
  porDocumento: false,
  ...extra,
})

const certificado: PlantillaDocumento = {
  tipo: 'CERTIFICACION_CUMPLIMIENTO',
  codigo: 'PENDIENTE_DE_DEFINIR',
  nombre: 'Certificación de cumplimiento',
  llevaObservaciones: false,
  campos: [
    campo('numeroFactura', 'Número de la factura', { porDocumento: true }),
    campo('banco', 'Banco'),
    campo('cedulaSupervisor', 'Cédula del supervisor'),
    campo('plazo', 'Plazo pactado del contrato', { opcional: true }),
  ],
}

const informe: PlantillaDocumento = {
  tipo: 'INFORME_SUPERVISION',
  codigo: 'GCCON-F-031',
  nombre: 'Informe de supervisión',
  llevaObservaciones: true,
  campos: [
    campo('adicion', 'Adición'),
    campo('valorActual', 'Valor actual del contrato', { opcional: true, dependeDe: 'adicion' }),
  ],
}

// El certificado para el pago se firmaba sin factura ni cuenta porque nadie las
// preguntaba antes de firmar (28-09-2026): este formulario es esa pregunta.
describe('DatosDelDocumento', () => {
  it('pide cada dato que el formato necesita y cuenta los que quedarán pendientes', () => {
    render(<DatosDelDocumento plantilla={certificado} iniciales={{}} onConfirmar={() => {}} onCancelar={() => {}} />)

    expect(screen.getByLabelText('Número de la factura')).toBeTruthy()
    expect(screen.getByLabelText('Banco')).toBeTruthy()
    // El plazo se deduce de las fechas: que falte no deja nada pendiente.
    expect(screen.getByLabelText('Plazo pactado del contrato (opcional)')).toBeTruthy()
    expect(screen.getByText('3 de 3 datos obligatorios quedarán marcados como pendientes.')).toBeTruthy()
  })

  it('no muestra la marca interna de un formato sin código oficial', () => {
    render(<DatosDelDocumento plantilla={certificado} iniciales={{}} onConfirmar={() => {}} onCancelar={() => {}} />)

    expect(screen.getByRole('heading', { name: 'Datos para Certificación de cumplimiento' })).toBeTruthy()
    expect(screen.queryByText(/PENDIENTE_DE_DEFINIR/)).toBeNull()
  })

  it('con una adición, el valor actualizado pasa a ser obligatorio; con un «no», no', () => {
    render(<DatosDelDocumento plantilla={informe} iniciales={{}} onConfirmar={() => {}} onCancelar={() => {}} />)
    expect(screen.getByLabelText('Valor actual del contrato (opcional)')).toBeTruthy()
    expect(screen.getByText('1 de 1 datos obligatorios quedarán marcados como pendientes.')).toBeTruthy()

    fireEvent.change(screen.getByLabelText('Adición'), { target: { value: 'Otrosí 1 por $ 5.000.000' } })
    expect(screen.getByLabelText('Valor actual del contrato')).toBeTruthy()
    expect(screen.getByText('1 de 2 datos obligatorios quedarán marcados como pendientes.')).toBeTruthy()

    fireEvent.change(screen.getByLabelText('Adición'), { target: { value: 'Ningún' } })
    expect(screen.getByLabelText('Valor actual del contrato (opcional)')).toBeTruthy()
    expect(screen.getByText('Todos los datos del formato están completos.')).toBeTruthy()
  })

  it('trae lo ya escrito para otro documento del contrato y entrega solo lo diligenciado, sin espacios', () => {
    const onConfirmar = vi.fn()
    render(
      <DatosDelDocumento
        plantilla={certificado}
        iniciales={{ cedulaSupervisor: '43.512.887', otroCampo: 'no es de este formato' }}
        onConfirmar={onConfirmar}
        onCancelar={() => {}}
      />,
    )

    expect((screen.getByLabelText('Cédula del supervisor') as HTMLInputElement).value).toBe('43.512.887')
    fireEvent.change(screen.getByLabelText('Número de la factura'), { target: { value: '  FE 547  ' } })
    fireEvent.change(screen.getByLabelText('Banco'), { target: { value: '   ' } })
    fireEvent.click(screen.getByText('Generar y revisar'))

    expect(onConfirmar).toHaveBeenCalledWith({ numeroFactura: 'FE 547', cedulaSupervisor: '43.512.887' }, {})
  })

  it('cancelar no genera nada y devuelve lo escrito para no perderlo', () => {
    const onConfirmar = vi.fn()
    const onCancelar = vi.fn()
    render(
      <DatosDelDocumento plantilla={certificado} iniciales={{}} onConfirmar={onConfirmar} onCancelar={onCancelar} />,
    )

    fireEvent.change(screen.getByLabelText('Banco'), { target: { value: 'BANCOLOMBIA' } })
    fireEvent.click(screen.getByText('Cancelar'))

    expect(onCancelar).toHaveBeenCalledWith({ banco: 'BANCOLOMBIA' }, {})
    expect(onConfirmar).not.toHaveBeenCalled()
  })

  it('un toque fuera del formulario no lo cierra; Escape sí, sin perder lo escrito', () => {
    const onCancelar = vi.fn()
    render(<DatosDelDocumento plantilla={certificado} iniciales={{}} onConfirmar={() => {}} onCancelar={onCancelar} />)
    fireEvent.change(screen.getByLabelText('Banco'), { target: { value: 'BANCOLOMBIA' } })

    fireEvent.click(screen.getByRole('presentation'))
    expect(onCancelar).not.toHaveBeenCalled()

    fireEvent.keyDown(document, { key: 'Escape' })
    expect(onCancelar).toHaveBeenCalledWith({ banco: 'BANCOLOMBIA' }, {})
  })
})

// El Informe Final real (CO1.PCCNTR.8426076) relaciona treinta obligaciones,
// cada una con su cumplimiento y su evidencia; hasta el 30-09-2026 el
// formulario no tenía dónde escribirlas y salían como una fila pendiente.
const informeFinal: PlantillaDocumento = {
  tipo: 'INFORME_FINAL',
  codigo: 'GCCON-F-030',
  nombre: 'Informe Final de Supervisión',
  llevaObservaciones: true,
  campos: [campo('siga', 'Cumplimiento del SIGA', { largo: true, porDocumento: true })],
  tablas: [
    {
      clave: 'obligacionesGenerales',
      etiqueta: 'Obligaciones del contrato',
      ayuda: 'Una fila por obligación.',
      columnas: [
        { etiqueta: 'Obligación', ejemplo: 'Ejecutar el objeto del contrato', delContrato: true },
        { etiqueta: '¿Cumplió?', ejemplo: 'SI CUMPLIO', delContrato: false },
        { etiqueta: 'Producto o evidencia', ejemplo: 'Acta en SECOP II', delContrato: false },
      ],
    },
    {
      clave: 'ordenesDePago',
      etiqueta: 'Órdenes de pago',
      ayuda: 'Una fila por orden.',
      columnas: [
        { etiqueta: 'Número de orden de pago', ejemplo: '70614726', delContrato: false },
        { etiqueta: 'Fecha de pago', ejemplo: '11/03/2026', delContrato: false },
        { etiqueta: 'Valor de pago', ejemplo: '$16.798.000,00', delContrato: false },
      ],
    },
  ],
}

describe('DatosDelDocumento con tablas', () => {
  it('cada tabla abre con una fila, se agregan y se quitan filas, y solo se envían las escritas', () => {
    const onConfirmar = vi.fn()
    render(
      <DatosDelDocumento plantilla={informeFinal} iniciales={{}} onConfirmar={onConfirmar} onCancelar={() => {}} />,
    )

    expect(screen.getByRole('group', { name: 'Obligaciones del contrato, fila 1' })).toBeTruthy()
    // El párrafo del SIGA es un área de texto, no un dato de una línea.
    expect(screen.getByLabelText('Cumplimiento del SIGA').tagName).toBe('TEXTAREA')
    // Dos tablas vacías y el SIGA: tres pendientes.
    expect(screen.getByText('3 de 3 datos obligatorios quedarán marcados como pendientes.')).toBeTruthy()

    fireEvent.change(screen.getByLabelText('Obligación (fila 1) de Obligaciones del contrato'), {
      target: { value: ' Ejecutar el objeto ' },
    })
    fireEvent.change(screen.getByLabelText('¿Cumplió? (fila 1) de Obligaciones del contrato'), {
      target: { value: 'SI CUMPLIO' },
    })
    fireEvent.click(screen.getByText('+ Agregar fila a «Obligaciones del contrato»'))
    fireEvent.click(screen.getByText('+ Agregar fila a «Obligaciones del contrato»'))
    fireEvent.change(screen.getByLabelText('Obligación (fila 3) de Obligaciones del contrato'), {
      target: { value: 'Mantener los precios' },
    })
    fireEvent.click(screen.getByLabelText('Quitar la fila 2 de Obligaciones del contrato'))
    expect(screen.queryByRole('group', { name: 'Obligaciones del contrato, fila 3' })).toBeNull()
    // El SIGA y las órdenes de pago siguen vacíos; y en las filas escritas
    // quedan tres celdas sin llenar.
    expect(
      screen.getByText(
        /^2 de 3 datos obligatorios quedarán marcados como pendientes\. 3 celdas de las tablas están vacías/,
      ),
    ).toBeTruthy()

    fireEvent.click(screen.getByText('Generar y revisar'))

    expect(onConfirmar).toHaveBeenCalledWith(
      {},
      {
        obligacionesGenerales: [
          ['Ejecutar el objeto', 'SI CUMPLIO', ''],
          ['Mantener los precios', '', ''],
        ],
        ordenesDePago: [],
      },
    )
  })

  it('trae las filas ya escritas y las devuelve al cancelar para no perderlas', () => {
    const onCancelar = vi.fn()
    render(
      <DatosDelDocumento
        plantilla={informeFinal}
        iniciales={{}}
        tablasIniciales={{ obligacionesGenerales: [['Ejecutar el objeto', '', '']], otraTabla: [['no es de este']] }}
        onConfirmar={() => {}}
        onCancelar={onCancelar}
      />,
    )

    expect(
      (screen.getByLabelText('Obligación (fila 1) de Obligaciones del contrato') as HTMLTextAreaElement).value,
    ).toBe('Ejecutar el objeto')
    fireEvent.change(screen.getByLabelText('Número de orden de pago (fila 1) de Órdenes de pago'), {
      target: { value: '70614726' },
    })
    fireEvent.click(screen.getByText('Cancelar'))

    expect(onCancelar).toHaveBeenCalledWith(
      {},
      { obligacionesGenerales: [['Ejecutar el objeto', '', '']], ordenesDePago: [['70614726', '', '']] },
    )
  })
})

describe('DatosDelDocumento con tablas: lo que encontró la revisión del 01-10-2026', () => {
  it('avisa de las celdas vacías aunque cada tabla tenga una fila', () => {
    render(
      <DatosDelDocumento
        plantilla={{ ...informeFinal, campos: [] }}
        iniciales={{}}
        tablasIniciales={{
          obligacionesGenerales: [['Ejecutar el objeto', 'SI CUMPLIO', '']],
          ordenesDePago: [['70614726', '11/03/2026', '$16.798.000,00']],
        }}
        onConfirmar={() => {}}
        onCancelar={() => {}}
      />,
    )

    expect(screen.getByText(/Una celda de las tablas está vacía y saldrá como «dato pendiente»/)).toBeTruthy()
    expect(screen.queryByText('Todos los datos del formato están completos.')).toBeNull()
  })

  it('el segundo clic de un doble clic en «Quitar» no borra la fila siguiente', () => {
    render(
      <DatosDelDocumento
        plantilla={informeFinal}
        iniciales={{}}
        tablasIniciales={{
          obligacionesGenerales: [
            ['Primera', '', ''],
            ['Segunda', '', ''],
          ],
        }}
        onConfirmar={() => {}}
        onCancelar={() => {}}
      />,
    )

    const quitar = screen.getByLabelText('Quitar la fila 1 de Obligaciones del contrato')
    fireEvent.click(quitar, { detail: 1 })
    fireEvent.click(screen.getByLabelText('Quitar la fila 1 de Obligaciones del contrato'), { detail: 2 })

    expect(
      (screen.getByLabelText('Obligación (fila 1) de Obligaciones del contrato') as HTMLTextAreaElement).value,
    ).toBe('Segunda')
  })

  it('con las filas que admite el formato ya no ofrece agregar otra', () => {
    const llenas = Array.from({ length: MAX_FILAS }, (_, i) => [`Obligación ${i + 1}`, '', ''])
    render(
      <DatosDelDocumento
        plantilla={informeFinal}
        iniciales={{}}
        tablasIniciales={{ obligacionesGenerales: llenas }}
        onConfirmar={() => {}}
        onCancelar={() => {}}
      />,
    )

    expect(screen.queryByText('+ Agregar fila a «Obligaciones del contrato»')).toBeNull()
    expect(screen.getByText(`El formato admite hasta ${MAX_FILAS} filas en esta tabla.`)).toBeTruthy()
  })
})

describe('tablas del contrato guardadas en el equipo', () => {
  it('se leen tal como se guardaron, por contrato', () => {
    guardarTablasDelContrato(41, { obligacionesGenerales: [['Ejecutar el objeto', '', '']] })

    expect(leerTablasDelContrato(41)).toEqual({ obligacionesGenerales: [['Ejecutar el objeto', '', '']] })
    expect(leerTablasDelContrato(42)).toEqual({})
  })

  it('lo que no tiene la forma esperada se ignora', () => {
    localStorage.setItem('sicot.tablasDelContrato.43', '{"buena":[["a","b"]],"mala":[["a",3]],"otra":"x"}')
    localStorage.setItem('sicot.tablasDelContrato.44', 'no es json')

    expect(leerTablasDelContrato(43)).toEqual({ buena: [['a', 'b']] })
    expect(leerTablasDelContrato(44)).toEqual({})
  })
})

describe('tablasDelContrato', () => {
  // Arrastrar la evidencia del informe anterior al siguiente es invitar a
  // firmarla como si fuera de este periodo: solo se recuerda el texto de la
  // obligación, que es del contrato.
  it('recuerda solo las columnas del contrato y nunca las órdenes de pago', () => {
    expect(
      tablasDelContrato(informeFinal, {
        obligacionesGenerales: [
          ['Ejecutar el objeto', 'SI CUMPLIO', 'Acta del 15/09'],
          ['', 'NO', 'sin obligación escrita'],
        ],
        ordenesDePago: [['70614726', '11/03/2026', '$16.798.000,00']],
      }),
    ).toEqual({ obligacionesGenerales: [['Ejecutar el objeto', '', '']] })
  })

  it('si el supervisor vació la tabla, la olvida', () => {
    expect(tablasDelContrato(informeFinal, { obligacionesGenerales: [] })).toEqual({ obligacionesGenerales: [] })
  })
})

describe('soloLoDelDocumento', () => {
  // El borrador de un sub-paso guardaba también la fecha de suscripción; si
  // el supervisor la corregía después en otro documento, al volver le salía
  // la fecha vieja (revisión del 29-09-2026).
  it('del borrador solo guarda lo que es de ese documento, nunca lo del contrato', () => {
    expect(
      soloLoDelDocumento(certificado, {
        numeroFactura: ' FE 900 ',
        banco: 'BANCOLOMBIA',
        cedulaSupervisor: '43.512.887',
      }),
    ).toEqual({ numeroFactura: 'FE 900' })
  })
})

describe('esRespuestaNegativa', () => {
  it('lee el «no» igual que el backend, con o sin tilde', () => {
    for (const v of ['No', 'no.', 'N/A', 'No aplica', 'Ningún', 'ninguna', 'No hubo', 'No se presentaron'])
      expect(esRespuestaNegativa(v)).toBe(true)
    for (const v of ['Otrosí No. 1', '$ 5.000.000', '', undefined]) expect(esRespuestaNegativa(v)).toBe(false)
  })
})
