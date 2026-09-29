import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import DatosDelDocumento, { esRespuestaNegativa } from './DatosDelDocumento'
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
    fireEvent.click(screen.getByText('Generar y firmar'))

    expect(onConfirmar).toHaveBeenCalledWith({ numeroFactura: 'FE 547', cedulaSupervisor: '43.512.887' })
  })

  it('cancelar no genera nada y devuelve lo escrito para no perderlo', () => {
    const onConfirmar = vi.fn()
    const onCancelar = vi.fn()
    render(
      <DatosDelDocumento plantilla={certificado} iniciales={{}} onConfirmar={onConfirmar} onCancelar={onCancelar} />,
    )

    fireEvent.change(screen.getByLabelText('Banco'), { target: { value: 'BANCOLOMBIA' } })
    fireEvent.click(screen.getByText('Cancelar'))

    expect(onCancelar).toHaveBeenCalledWith({ banco: 'BANCOLOMBIA' })
    expect(onConfirmar).not.toHaveBeenCalled()
  })

  it('un toque fuera del formulario no lo cierra; Escape sí, sin perder lo escrito', () => {
    const onCancelar = vi.fn()
    render(<DatosDelDocumento plantilla={certificado} iniciales={{}} onConfirmar={() => {}} onCancelar={onCancelar} />)
    fireEvent.change(screen.getByLabelText('Banco'), { target: { value: 'BANCOLOMBIA' } })

    fireEvent.click(screen.getByRole('presentation'))
    expect(onCancelar).not.toHaveBeenCalled()

    fireEvent.keyDown(document, { key: 'Escape' })
    expect(onCancelar).toHaveBeenCalledWith({ banco: 'BANCOLOMBIA' })
  })
})

describe('esRespuestaNegativa', () => {
  it('lee el «no» igual que el backend, con o sin tilde', () => {
    for (const v of ['No', 'no.', 'N/A', 'No aplica', 'Ningún', 'ninguna', 'No hubo', 'No se presentaron'])
      expect(esRespuestaNegativa(v)).toBe(true)
    for (const v of ['Otrosí No. 1', '$ 5.000.000', '', undefined]) expect(esRespuestaNegativa(v)).toBe(false)
  })
})
