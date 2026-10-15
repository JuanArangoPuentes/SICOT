import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ActaFirmadaYEnvio, { NOMBRE_ACTA, NOMBRE_EVIDENCIA, piezasCargadas } from './ActaFirmadaYEnvio'
import BorradorDeCorreo, { armarBorrador } from './BorradorDeCorreo'
import VerificacionSecop from './VerificacionSecop'
import { contrato, documento } from '@/test/dobles'
import type { VerificacionSecopResponse } from '@/services/api/types'

/**
 * Los pasos 2.1 a 2.4 del proceso real que documentó el supervisor el
 * 15-10-2026. Los datos son los del contrato del Lote 4
 * (CO1.PCCNTR.10057820, Suministros Maybe).
 */
vi.mock('@/services/documentoService', () => ({ subirDocumento: vi.fn() }))
vi.mock('@/services/contratoService', () => ({ verificarEnSecop: vi.fn() }))

const { subirDocumento } = await import('@/services/documentoService')
const { verificarEnSecop } = await import('@/services/contratoService')

const LOTE_4 = contrato({
  id: 9,
  numeroContrato: 'CO1.PCCNTR.10057820',
  objeto: '5_9205_306 Contratar el suministro de materiales para la formación',
  valor: 45_900_300,
  fechaInicio: '2026-10-14',
  fechaFin: '2026-12-15',
  tipoContrato: 'Suministro de Bienes',
  contratista: 'SUMINISTROS MAYBE S.A.S',
  representanteLegal: 'LUZ MARINA MEJIA PEREZ',
  correoContratista: 'maybesuministros@gmail.com',
  supervisorNombre: 'Alex Fernando Zapata Rios',
})

describe('2.1 — acta firmada y evidencia del envío', () => {
  beforeEach(() => vi.mocked(subirDocumento).mockReset())

  it('reconoce el acta y la evidencia cargadas en el paso, no las de otros pasos', () => {
    const docs = [
      documento({ id: 1, subetapaId: 21, tipo: 'PDF', nombre: NOMBRE_ACTA }),
      documento({ id: 2, subetapaId: 21, tipo: 'IMAGEN', nombre: NOMBRE_EVIDENCIA }),
      documento({ id: 3, subetapaId: 99, tipo: 'PDF', nombre: 'Otro paso' }),
    ]
    const { acta, evidencia } = piezasCargadas(docs, 21)
    expect(acta?.id).toBe(1)
    expect(evidencia?.id).toBe(2)
    expect(piezasCargadas(docs, 50)).toEqual({ acta: undefined, evidencia: undefined })
  })

  it('sin las dos piezas dice qué falta; con las dos, que ya puede cerrar', () => {
    const { rerender } = render(
      <ActaFirmadaYEnvio contratoId={9} subetapaApiId={21} documentos={[]} onCargado={() => {}} />,
    )
    expect(screen.getByText(/hasta cargar las dos/)).toBeTruthy()

    rerender(
      <ActaFirmadaYEnvio
        contratoId={9}
        subetapaApiId={21}
        documentos={[
          documento({ id: 1, subetapaId: 21, tipo: 'PDF', nombre: NOMBRE_ACTA }),
          documento({ id: 2, subetapaId: 21, tipo: 'IMAGEN', nombre: NOMBRE_EVIDENCIA }),
        ]}
        onCargado={() => {}}
      />,
    )
    expect(screen.getByText(/ya puede marcar el paso como completado/)).toBeTruthy()
  })

  it('carga el acta como PDF asociada al paso y rechaza otra cosa', async () => {
    vi.mocked(subirDocumento).mockResolvedValue(documento({ id: 5 }))
    const onCargado = vi.fn()
    const { container } = render(
      <ActaFirmadaYEnvio contratoId={9} subetapaApiId={21} documentos={[]} onCargado={onCargado} />,
    )
    const [campoActa] = container.querySelectorAll('input[type="file"]')

    fireEvent.change(campoActa, { target: { files: [new File(['x'], 'foto.jpg', { type: 'image/jpeg' })] } })
    expect(await screen.findByText(/tiene que ser el PDF/)).toBeTruthy()
    expect(subirDocumento).not.toHaveBeenCalled()

    const pdf = new File(['%PDF'], 'acta-firmada.pdf', { type: 'application/pdf' })
    fireEvent.change(campoActa, { target: { files: [pdf] } })
    await waitFor(() => expect(onCargado).toHaveBeenCalled())
    expect(subirDocumento).toHaveBeenCalledWith(9, pdf, { nombre: NOMBRE_ACTA, subetapaId: 21 })
  })
})

describe('2.2 y 2.4 — borradores de los correos de arranque', () => {
  it('el correo al contratista va a su correo, con el contrato real y el hueco de las cantidades', () => {
    const b = armarBorrador('CORREO_CONTRATISTA', LOTE_4)
    expect(b.para).toBe('maybesuministros@gmail.com')
    expect(b.asunto).toContain('CO1.PCCNTR.10057820')
    expect(b.cuerpo).toContain('Señores SUMINISTROS MAYBE S.A.S')
    expect(b.cuerpo).toContain('Atn. LUZ MARINA MEJIA PEREZ')
    expect(b.cuerpo).toContain('del 14/10/2026 al 15/12/2026')
    // Es un suministro: lleva las cantidades, que SICOT todavía no tiene y no inventa.
    expect(b.cuerpo).toContain('[Relacione aquí cada bien con su cantidad exacta')
    expect(b.avisos.join(' ')).toContain('cantidades exactas')
    expect(b.cuerpo).toContain('Alex Fernando Zapata Rios')
  })

  it('sin correo del contratista lo dice en vez de dejar el destinatario en blanco sin aviso', () => {
    const b = armarBorrador('CORREO_CONTRATISTA', { ...LOTE_4, correoContratista: null })
    expect(b.para).toBe('')
    expect(b.avisos.join(' ')).toContain('SICOT no tiene el correo del contratista')
  })

  it('el aviso a SST y ambiental dice dónde están sus correos', () => {
    const b = armarBorrador('CORREO_SST', LOTE_4)
    expect(b.cuerpo).toContain('inició la ejecución del contrato CO1.PCCNTR.10057820')
    expect(b.avisos[0]).toContain('estudio previo')
  })

  it('muestra el borrador y lo ofrece para copiar', () => {
    render(<BorradorDeCorreo tipo="CORREO_CONTRATISTA" contrato={LOTE_4} />)
    fireEvent.click(screen.getByRole('button', { name: /Ver el borrador del correo/ }))
    expect(screen.getByText(/Para: maybesuministros@gmail.com/)).toBeTruthy()
    expect(screen.getByRole('button', { name: /Copiar el correo/ })).toBeTruthy()
  })
})

describe('2.3 — verificación contra el SECOP II', () => {
  const respuesta: VerificacionSecopResponse = {
    disponible: true,
    motivo: null,
    consultadoEn: '2026-10-15T21:00:00Z',
    enlaceProceso: 'https://community.secop.gov.co/Public/Tendering/OpportunityDetail/Index?noticeUID=CO1.NTC.10815297',
    ficha: {
      idContrato: 'CO1.PCCNTR.10057820',
      objeto: 'Suministro',
      estado: 'Aprobado',
      proveedor: 'SUMINISTROS MAYBE SAS',
      documentoProveedor: '800154801',
      representanteLegal: 'LUZ MARINA MEJIA PEREZ',
      valor: 45_900_300,
      duracion: '67 Dia(s)',
      fechaFirma: '2026-10-13',
      fechaFin: '2026-12-15',
      inicioLiquidacion: '2026-12-16',
      finLiquidacion: '2027-06-30',
      supervisor: 'Alex Fernando Zapata Rios',
      ordenadorDelGasto: null,
      modalidad: null,
      tipoContrato: 'Suministros',
      obligacionAmbiental: true,
      valorFacturado: 0,
      valorPagado: 0,
      valorPendienteDePago: 45_900_300,
      enlaceProceso: null,
    },
    diferencias: [{ campo: 'Valor', enSicot: '$45.000.000', enSecop: '$45.900.300' }],
    documentos: [
      {
        nombre: '8. ECONOMICA LOTE 4 AJUSTADA.pdf',
        tamanoBytes: 568_915,
        fechaCarga: '2026-10-08',
        url: 'https://community.secop.gov.co/x',
      },
    ],
    garantias: [],
  }

  afterEach(() => {
    delete (window as unknown as Record<string, unknown>).__TAURI_INTERNALS__
  })

  it('muestra lo que no coincide, la obligación ambiental y los documentos del expediente', async () => {
    vi.mocked(verificarEnSecop).mockResolvedValue(respuesta)
    render(<VerificacionSecop contratoId={9} />)

    expect(await screen.findByText('Un dato no coincide con lo publicado:')).toBeTruthy()
    expect(screen.getByText('$45.900.300')).toBeTruthy()
    expect(screen.getByText(/tenga en cuenta al profesional ambiental/)).toBeTruthy()
    expect(screen.getByRole('link', { name: '8. ECONOMICA LOTE 4 AJUSTADA.pdf' })).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Abrir el proceso en el SECOP II' })).toBeTruthy()
  })

  it('sin conexión dice el motivo y conserva el enlace para hacerlo a mano', async () => {
    vi.mocked(verificarEnSecop).mockResolvedValue({
      ...respuesta,
      disponible: false,
      motivo: 'No hay conexión con datos.gov.co en este momento.',
      ficha: null,
      diferencias: [],
      documentos: [],
    })
    render(<VerificacionSecop contratoId={9} />)

    expect(await screen.findByText(/No hay conexión con datos.gov.co/)).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Abrir el proceso en el SECOP II' })).toBeTruthy()
  })

  it('en la aplicación empaquetada no ofrece un enlace que no abre nada, solo copiarlo', async () => {
    ;(window as unknown as Record<string, unknown>).__TAURI_INTERNALS__ = {}
    vi.mocked(verificarEnSecop).mockResolvedValue(respuesta)
    render(<VerificacionSecop contratoId={9} />)

    expect(await screen.findByText('Abrir el proceso en el SECOP II')).toBeTruthy()
    expect(screen.queryByRole('link', { name: 'Abrir el proceso en el SECOP II' })).toBeNull()
    expect(screen.getAllByRole('button', { name: 'Copiar enlace' }).length).toBeGreaterThan(0)
  })
})
