import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import SeguimientoSupervisores, { ResumenSeguimiento, estadoDocumentosFormales } from './SeguimientoSupervisores'
import type { ContratoSeguimiento, EtapaResponse, SeguimientoResponse } from '@/services/api/types'

/**
 * El seguimiento es lo que el Administrador mira para saber cómo va cada
 * supervisor. Estas pruebas fijan lo que no puede dejar de decir: dónde va el
 * contrato, cómo van sus documentos y qué necesita atención.
 */

function etapas(completadasHasta: string, enCurso: string): EtapaResponse[] {
  const codigos: Record<number, string[]> = {
    1: ['1.1', '1.2', '1.3', '1.4', '1.5', '1.6'],
    2: ['2.1', '2.2', '2.3', '2.4', '2.5', '2.6', '2.7'],
    3: ['3.1', '3.2', '3.3', '3.4'],
    4: ['4.1', '4.2', '4.3'],
    5: ['5.1', '5.2', '5.3'],
    6: ['6.1', '6.2', '6.3', '6.4'],
  }
  const orden = Object.values(codigos).flat()
  const limite = orden.indexOf(completadasHasta)
  return Object.entries(codigos).map(([n, subs]) => {
    const subEtapas = subs.map((c, i) => ({
      id: Number(n) * 100 + i,
      codigo: c,
      nombre: `Subetapa ${c}`,
      descripcion: '',
      estado: (orden.indexOf(c) <= limite ? 'COMPLETADA' : c === enCurso ? 'EN_CURSO' : 'PENDIENTE') as
        | 'COMPLETADA'
        | 'EN_CURSO'
        | 'PENDIENTE',
      responsable: 'Supervisor',
    }))
    const hechas = subEtapas.filter((s) => s.estado === 'COMPLETADA').length
    return {
      id: Number(n),
      numero: Number(n),
      nombre: `Paso ${n}`,
      estado:
        hechas === subs.length
          ? 'COMPLETADA'
          : subEtapas.some((s) => s.estado !== 'PENDIENTE')
            ? 'EN_CURSO'
            : 'PENDIENTE',
      porcentaje: Math.round((hechas / subs.length) * 100),
      subEtapas,
    } as EtapaResponse
  })
}

function contrato(over: Partial<ContratoSeguimiento> = {}): ContratoSeguimiento {
  return {
    id: 10,
    numeroContrato: 'CO1.PCCNTR.9100001',
    objeto: 'Adquisición de herramienta',
    contratista: 'Ferretería Los Andes S.A.S.',
    estado: 'ACTIVO',
    valor: 120450000,
    fechaInicio: '2026-09-02',
    fechaFin: '2026-12-31',
    cronograma: {
      semaforo: 'ROJO',
      fraccionDePlazo: 0.2,
      fraccionDeAvance: 0.1,
      brecha: 0.1,
      etapaActual: 3,
      cierreEsperado: null,
      diasDeAtraso: 4,
      mensaje: 'Atrasado respecto al plazo',
    },
    subetapasCompletadas: 16,
    subetapasTotales: 27,
    etapaActual: 3,
    etapaActualNombre: 'INSPECCIÓN',
    subetapaEnCurso: {
      id: 1,
      codigo: '3.4',
      nombre: 'Firma del Informe de Supervisión',
      descripcion: '',
      estado: 'EN_CURSO',
      responsable: 'Supervisor',
    },
    etapas: etapas('3.3', '3.4'),
    documentos: [],
    alertasSinLeer: 2,
    ultimaActividad: {
      contratoId: 10,
      fecha: '2026-09-24T19:47:55Z',
      accion: 'SUBETAPA',
      descripcion: 'Subetapa 3.3 completada',
    },
    ...over,
  }
}

function datos(c: ContratoSeguimiento, firma = true): SeguimientoResponse {
  return {
    supervisores: [
      {
        id: 7,
        nombre: 'Paola Mejía',
        email: 'paola@soy.sena.edu.co',
        activo: true,
        firmaVigente: firma,
        contratosFinalizados: 1,
        contratos: [c],
      },
    ],
    contratosSinSupervisor: [],
    generadoEn: '2026-09-24T20:00:00Z',
  }
}

describe('estadoDocumentosFormales', () => {
  it('distingue un paso cerrado sin su documento de uno al que aún no se llega', () => {
    const estados = estadoDocumentosFormales(contrato())
    const por = Object.fromEntries(estados.map((e) => [e.subStepId, e.estado.label]))
    // 2.7 quedó completada sin Acta de Inicio en el expediente.
    expect(por['2.7']).toBe('Paso cerrado sin documento')
    expect(por['3.4']).toBe('Por generar')
    expect(por['6.3']).toBe('Aún no corresponde')
  })

  it('reconoce un documento firmado y uno pendiente de firma por su subetapa', () => {
    const c = contrato({
      documentos: [
        {
          id: 1,
          contratoId: 10,
          subetapaCodigo: '2.7',
          nombre: 'Acta',
          estado: 'APROBADO',
          generadoPorIa: true,
          firmado: true,
          fechaSubida: '2026-09-24T19:00:00Z',
          fechaFirma: '2026-09-24T19:01:00Z',
        },
        {
          id: 2,
          contratoId: 10,
          subetapaCodigo: '3.4',
          nombre: 'Informe',
          estado: 'PENDIENTE',
          generadoPorIa: true,
          firmado: false,
          fechaSubida: '2026-09-24T19:30:00Z',
          fechaFirma: null,
        },
      ],
    })
    const por = Object.fromEntries(estadoDocumentosFormales(c).map((e) => [e.subStepId, e.estado.label]))
    expect(por['2.7']).toBe('Firmado')
    expect(por['3.4']).toBe('Pendiente de firma')
  })
})

describe('SeguimientoSupervisores', () => {
  it('muestra dónde va el contrato, su semáforo y su última actividad', () => {
    render(<SeguimientoSupervisores datos={datos(contrato())} error="" cargando={false} onActualizar={vi.fn()} />)

    expect(screen.getByText('Paola Mejía')).toBeInTheDocument()
    expect(screen.getByText('CO1.PCCNTR.9100001')).toBeInTheDocument()
    expect(screen.getByText('Atrasado')).toBeInTheDocument()
    expect(screen.getByText(/Paso 3 de 6 — INSPECCIÓN/)).toBeInTheDocument()
    expect(screen.getByText(/3.4 Firma del Informe de Supervisión/)).toBeInTheDocument()
    expect(screen.getByText(/16 de 27 subetapas completadas/)).toBeInTheDocument()
    expect(screen.getByText(/Subetapa 3.3 completada/)).toBeInTheDocument()
    expect(screen.getByText(/2 alertas sin leer/)).toBeInTheDocument()
  })

  it('avisa cuando el supervisor no tiene firma y no podrá firmar', () => {
    render(
      <SeguimientoSupervisores datos={datos(contrato(), false)} error="" cargando={false} onActualizar={vi.fn()} />,
    )

    expect(screen.getByText('Sin firma electrónica')).toBeInTheDocument()
    expect(screen.getByText(/No podrá firmar los documentos/)).toBeInTheDocument()
  })

  it('abre el detalle con todas las subetapas del contrato', () => {
    render(<SeguimientoSupervisores datos={datos(contrato())} error="" cargando={false} onActualizar={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: 'Ver detalle del contrato' }))

    expect(screen.getByText('Subetapa 6.4')).toBeInTheDocument()
    expect(screen.getByText('Todavía no hay documentos en el expediente.')).toBeInTheDocument()
  })

  it('filtra por número de contrato', () => {
    render(<SeguimientoSupervisores datos={datos(contrato())} error="" cargando={false} onActualizar={vi.fn()} />)

    fireEvent.change(screen.getByLabelText('Buscar supervisor, correo o contrato'), { target: { value: 'no-existe' } })

    expect(screen.queryByText('Paola Mejía')).not.toBeInTheDocument()
    expect(screen.getByText(/Ningún supervisor coincide/)).toBeInTheDocument()
  })

  it('dice que falló la consulta en vez de mostrar una lista vacía', () => {
    render(
      <SeguimientoSupervisores datos={null} error="No se pudo consultar" cargando={false} onActualizar={vi.fn()} />,
    )

    expect(screen.getByRole('alert')).toHaveTextContent('No se pudo consultar')
  })
})

describe('ResumenSeguimiento', () => {
  it('cuenta los contratos por semáforo y señala los pasos cerrados sin documento', () => {
    render(<ResumenSeguimiento datos={datos(contrato(), false)} />)

    expect(screen.getByText('Atrasados').previousSibling).toHaveTextContent('1')
    expect(screen.getByText(/1 supervisor con contratos y sin firma electrónica/)).toBeInTheDocument()
    expect(screen.getByText(/1 paso cerrado sin su documento/)).toBeInTheDocument()
  })
})
