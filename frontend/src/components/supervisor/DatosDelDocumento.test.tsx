import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import DatosDelDocumento from './DatosDelDocumento'
import type { PlantillaDocumento } from '@/services/api/types'

const certificado: PlantillaDocumento = {
  tipo: 'CERTIFICACION_CUMPLIMIENTO',
  codigo: 'PENDIENTE_DE_DEFINIR',
  nombre: 'Certificación de cumplimiento',
  llevaObservaciones: false,
  campos: [
    { clave: 'numeroFactura', etiqueta: 'Número de la factura', ejemplo: 'FE 547', opcional: false },
    { clave: 'banco', etiqueta: 'Banco', ejemplo: 'BANCOLOMBIA', opcional: false },
    { clave: 'cedulaSupervisor', etiqueta: 'Cédula del supervisor', ejemplo: '98.587.121', opcional: false },
    { clave: 'plazo', etiqueta: 'Plazo pactado del contrato', ejemplo: 'Dos (02) meses', opcional: true },
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

  it('cancelar no genera nada', () => {
    const onConfirmar = vi.fn()
    const onCancelar = vi.fn()
    render(
      <DatosDelDocumento plantilla={certificado} iniciales={{}} onConfirmar={onConfirmar} onCancelar={onCancelar} />,
    )

    fireEvent.click(screen.getByText('Cancelar'))

    expect(onCancelar).toHaveBeenCalled()
    expect(onConfirmar).not.toHaveBeenCalled()
  })
})
