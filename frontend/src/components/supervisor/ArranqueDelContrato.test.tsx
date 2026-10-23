import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AcuerdoDeEntregas, { problemasDeLasFilas } from './AcuerdoDeEntregas'
import BorradorDeCorreo, { armarBorrador } from './BorradorDeCorreo'
import ContactoDelContratista from './ContactoDelContratista'
import ContactosSstAmbiental from './ContactosSstAmbiental'
import { contrato } from '@/test/dobles'
import { ApiError } from '@/services/api/client'
import type { AcuerdoDeEntregasResponse, ContactoDelContrato, EntregaAcordada } from '@/services/api/types'

/**
 * Lo que la hoja de Alex pide recordar al arrancar el contrato y SICOT no
 * guardaba: las fechas de entrega acordadas (paso 5: «se establecen fechas de
 * entrega … respetando los tiempos de inicio y fin del contrato»), los
 * profesionales de SST y ambiental (paso 3) y el teléfono del contratista
 * (paso 5: «correo de contacto + número de teléfono»).
 *
 * Los datos son los del Lote 4: plazo del 14-10-2026 al 15-12-2026, teléfono
 * 4489948 en el registro de proveedores del SECOP II.
 */
vi.mock('@/services/contratoService', () => ({
  obtenerEntregasAcordadas: vi.fn(),
  guardarEntregasAcordadas: vi.fn(),
  obtenerContactosSstAmbiental: vi.fn(),
  guardarContactosSstAmbiental: vi.fn(),
  leerContactosDelEstudioPrevio: vi.fn(),
  completarContactoDelContratista: vi.fn(),
}))

const servicio = await import('@/services/contratoService')

const LOTE_4 = contrato({
  id: 9,
  numeroContrato: 'CO1.PCCNTR.10057820',
  objeto: '5_9205_306 Contratar el suministro de materiales para la formación',
  valor: 45_900_300,
  fechaInicio: '2026-10-14',
  fechaFin: '2026-12-15',
  tipoContrato: 'Suministro de Bienes',
  contratista: 'SUMINISTROS MAYBE S.A.S',
  contratistaNit: '800154801-6',
  representanteLegal: 'LUZ MARINA MEJIA PEREZ',
  correoContratista: 'maybesuministros@gmail.com',
  supervisorNombre: 'Alex Fernando Zapata Rios',
})

const CONTACTOS: ContactoDelContrato[] = [
  { rol: 'SST', nombre: 'Ana María Gómez', correo: 'amgomez@sena.edu.co' },
  { rol: 'AMBIENTAL', nombre: null, correo: 'cperez@sena.edu.co' },
]

const ENTREGAS: EntregaAcordada[] = [
  { numero: 1, fecha: '2026-10-30', descripcion: 'Ítems 1 a 20' },
  { numero: 2, fecha: '2026-11-20', descripcion: 'Ítems 21 a 35' },
]

const SIN_ACUERDO: AcuerdoDeEntregasResponse = {
  entregas: [],
  inicioDelContrato: '2026-10-14',
  finDelContrato: '2026-12-15',
}

beforeEach(() => {
  vi.mocked(servicio.obtenerEntregasAcordadas).mockReset()
  vi.mocked(servicio.guardarEntregasAcordadas).mockReset()
  vi.mocked(servicio.obtenerContactosSstAmbiental).mockReset()
  vi.mocked(servicio.guardarContactosSstAmbiental).mockReset()
  vi.mocked(servicio.leerContactosDelEstudioPrevio).mockReset()
  vi.mocked(servicio.completarContactoDelContratista).mockReset()
  vi.mocked(servicio.obtenerContactosSstAmbiental).mockResolvedValue({ contactos: [] })
  vi.mocked(servicio.obtenerEntregasAcordadas).mockResolvedValue(SIN_ACUERDO)
})

describe('los correos del arranque usan lo registrado', () => {
  it('el aviso de inicio a SST y ambiental sale dirigido a ellos y, con fechas, las lista', () => {
    const b = armarBorrador('CORREO_SST', LOTE_4, { contactos: CONTACTOS, entregas: ENTREGAS })

    expect(b.para).toBe('amgomez@sena.edu.co, cperez@sena.edu.co')
    expect(b.avisos).toEqual([])
    expect(b.cuerpo).toContain('Las fechas de entrega acordadas con el contratista son:')
    expect(b.cuerpo).toContain('1. 30/10/2026 — Ítems 1 a 20')
    expect(b.cuerpo).toContain('2. 20/11/2026 — Ítems 21 a 35')
    expect(b.cuerpo).not.toContain('Les compartiré')
  })

  it('sin contactos el aviso dice dónde están y dónde registrarlos; sin fechas, que las compartirá', () => {
    const b = armarBorrador('CORREO_SST', LOTE_4)

    expect(b.para).toBe('')
    expect(b.avisos.join(' ')).toContain('estudio previo')
    expect(b.avisos.join(' ')).toContain('paso 2.2')
    expect(b.cuerpo).toContain('Les compartiré las fechas de entrega')
  })

  it('la confirmación al contratista va a su correo con las fechas y la vigencia', () => {
    const b = armarBorrador('CONFIRMACION_ENTREGAS', LOTE_4, { entregas: ENTREGAS })

    expect(b.para).toBe('maybesuministros@gmail.com')
    expect(b.asunto).toBe('Contrato CO1.PCCNTR.10057820 — confirmación de las fechas de entrega')
    expect(b.cuerpo).toContain('Señores SUMINISTROS MAYBE S.A.S')
    expect(b.cuerpo).toContain('del 14/10/2026 al 15/12/2026')
    expect(b.cuerpo).toContain('1. 30/10/2026 — Ítems 1 a 20')
  })

  it('el aviso de las fechas a SST y ambiental cumple lo que prometía el primer correo', () => {
    const b = armarBorrador('ENTREGAS_SST', LOTE_4, { contactos: CONTACTOS, entregas: ENTREGAS })

    expect(b.para).toBe('amgomez@sena.edu.co, cperez@sena.edu.co')
    expect(b.asunto).toContain('fechas de entrega acordadas')
    expect(b.cuerpo).toContain('Como les anuncié al inicio del contrato CO1.PCCNTR.10057820')
    expect(b.cuerpo).toContain('2. 20/11/2026 — Ítems 21 a 35')
  })

  it('con varios destinatarios, el mailto codifica cada uno y los separa con una coma sin codificar', () => {
    render(<BorradorDeCorreo tipo="CORREO_SST" contrato={LOTE_4} datos={{ contactos: CONTACTOS }} />)

    const enlace = screen.getByRole('link', { name: 'Abrir en mi correo' })
    expect(enlace.getAttribute('href')).toMatch(/^mailto:amgomez%40sena\.edu\.co,cperez%40sena\.edu\.co\?subject=/)
  })
})

describe('2.4 — fechas de entrega acordadas', () => {
  it('lo que no se puede guardar se dice por fila, antes de pulsar', () => {
    expect(
      problemasDeLasFilas([{ fecha: '2026-10-30', descripcion: 'Entrega total' }], '2026-10-14', '2026-12-15'),
    ).toEqual([])
    expect(
      problemasDeLasFilas([{ fecha: '2026-12-20', descripcion: 'Entrega total' }], '2026-10-14', '2026-12-15')[0],
    ).toContain('20/12/2026')
    expect(problemasDeLasFilas([{ fecha: '', descripcion: '  ' }], '2026-10-14', '2026-12-15')).toHaveLength(2)
    // El primer y el último día del plazo valen.
    expect(
      problemasDeLasFilas(
        [
          { fecha: '2026-10-14', descripcion: 'a' },
          { fecha: '2026-12-15', descripcion: 'b' },
        ],
        '2026-10-14',
        '2026-12-15',
      ),
    ).toEqual([])
  })

  it('el supervisor registra una fecha y aparecen los dos correos que siguen al acuerdo', async () => {
    vi.mocked(servicio.guardarEntregasAcordadas).mockResolvedValue({
      ...SIN_ACUERDO,
      entregas: [{ numero: 1, fecha: '2026-10-30', descripcion: 'Entrega total' }],
    })
    const onGuardado = vi.fn()
    render(<AcuerdoDeEntregas contrato={LOTE_4} onGuardado={onGuardado} />)

    expect(await screen.findByText(/Todavía no hay fechas registradas/)).toBeTruthy()
    // El plazo se dice, para que no haya que ir a buscarlo.
    expect(screen.getByText(/del 14\/10\/2026 al 15\/12\/2026/)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Registrar las fechas acordadas' }))
    fireEvent.change(screen.getByLabelText('Fecha de la entrega 1'), { target: { value: '2026-10-30' } })
    fireEvent.change(screen.getByLabelText('Qué se entrega en la entrega 1'), { target: { value: ' Entrega total ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar las fechas' }))

    await waitFor(() =>
      expect(servicio.guardarEntregasAcordadas).toHaveBeenCalledWith(9, [
        { fecha: '2026-10-30', descripcion: 'Entrega total' },
      ]),
    )
    expect(await screen.findByText('30/10/2026')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Confirmar las fechas al contratista' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Avisar las fechas a SST y ambiental' })).toBeTruthy()
    expect(onGuardado).toHaveBeenCalled()
  })

  it('una fecha fuera del plazo no deja guardar y dice por qué', async () => {
    render(<AcuerdoDeEntregas contrato={LOTE_4} />)

    fireEvent.click(await screen.findByRole('button', { name: 'Registrar las fechas acordadas' }))
    fireEvent.change(screen.getByLabelText('Fecha de la entrega 1'), { target: { value: '2026-12-20' } })
    fireEvent.change(screen.getByLabelText('Qué se entrega en la entrega 1'), { target: { value: 'Entrega total' } })

    expect(screen.getByText(/cae fuera del plazo del contrato/)).toBeTruthy()
    expect((screen.getByRole('button', { name: 'Guardar las fechas' }) as HTMLButtonElement).disabled).toBe(true)
    expect(servicio.guardarEntregasAcordadas).not.toHaveBeenCalled()
  })

  it('si el servidor rechaza el acuerdo, su motivo se muestra y las filas no se pierden', async () => {
    vi.mocked(servicio.guardarEntregasAcordadas).mockRejectedValue(
      new ApiError(400, 'La entrega del 30/10/2026 cae fuera del plazo del contrato (del 14/10/2026 al 25/10/2026).'),
    )
    render(<AcuerdoDeEntregas contrato={LOTE_4} />)

    fireEvent.click(await screen.findByRole('button', { name: 'Registrar las fechas acordadas' }))
    fireEvent.change(screen.getByLabelText('Fecha de la entrega 1'), { target: { value: '2026-10-30' } })
    fireEvent.change(screen.getByLabelText('Qué se entrega en la entrega 1'), { target: { value: 'Entrega total' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar las fechas' }))

    expect(await screen.findByText(/al 25\/10\/2026/)).toBeTruthy()
    expect((screen.getByLabelText('Fecha de la entrega 1') as HTMLInputElement).value).toBe('2026-10-30')
  })
})

describe('2.2 — profesionales de SST y ambiental', () => {
  it('el supervisor agrega un contacto a mano y el correo sale dirigido a él', async () => {
    vi.mocked(servicio.guardarContactosSstAmbiental).mockResolvedValue({ contactos: [CONTACTOS[0]] })
    render(<ContactosSstAmbiental contrato={LOTE_4} />)

    expect(await screen.findByText('Todavía no hay contactos registrados.')).toBeTruthy()
    fireEvent.change(screen.getByLabelText('Nombre del contacto'), { target: { value: 'Ana María Gómez' } })
    fireEvent.change(screen.getByLabelText('Correo del contacto'), { target: { value: 'amgomez@sena.edu.co' } })
    fireEvent.click(screen.getByRole('button', { name: 'Agregar' }))

    await waitFor(() =>
      expect(servicio.guardarContactosSstAmbiental).toHaveBeenCalledWith(9, [
        { rol: 'SST', nombre: 'Ana María Gómez', correo: 'amgomez@sena.edu.co' },
      ]),
    )
    expect(await screen.findByText(/Ana María Gómez/)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: /Ver el borrador del correo/ }))
    expect(screen.getByText(/Para: amgomez@sena.edu.co/)).toBeTruthy()
  })

  it('del estudio previo propone los correos con el rol sugerido, y nada se guarda hasta elegir', async () => {
    vi.mocked(servicio.leerContactosDelEstudioPrevio).mockResolvedValue({
      aviso: null,
      encontrados: [
        {
          correo: 'cperez@sena.edu.co',
          rolSugerido: 'AMBIENTAL',
          contexto: 'Profesional de Gestión Ambiental: Carlos Pérez Correo: cperez@sena.edu.co',
        },
      ],
    })
    vi.mocked(servicio.guardarContactosSstAmbiental).mockResolvedValue({ contactos: [CONTACTOS[1]] })
    render(<ContactosSstAmbiental contrato={LOTE_4} />)

    await screen.findByText('Todavía no hay contactos registrados.')
    const archivo = new File(['%PDF'], '6. ESTUDIO PREVIO.pdf', { type: 'application/pdf' })
    fireEvent.change(screen.getByLabelText('Estudio previo'), { target: { files: [archivo] } })

    expect(await screen.findByText(/parece de Ambiental/)).toBeTruthy()
    expect(screen.getByText(/Carlos Pérez/)).toBeTruthy()
    expect(servicio.guardarContactosSstAmbiental).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: 'Agregar como Ambiental' }))
    await waitFor(() =>
      expect(servicio.guardarContactosSstAmbiental).toHaveBeenCalledWith(9, [
        { rol: 'AMBIENTAL', nombre: null, correo: 'cperez@sena.edu.co' },
      ]),
    )
  })

  it('un estudio previo escaneado se explica en vez de callar', async () => {
    vi.mocked(servicio.leerContactosDelEstudioPrevio).mockResolvedValue({
      encontrados: [],
      aviso: 'El documento no tiene texto que se pueda leer: parece una imagen escaneada.',
    })
    render(<ContactosSstAmbiental contrato={LOTE_4} />)

    await screen.findByText('Todavía no hay contactos registrados.')
    fireEvent.change(screen.getByLabelText('Estudio previo'), {
      target: { files: [new File(['%PDF'], 'escaneado.pdf', { type: 'application/pdf' })] },
    })
    expect(await screen.findByText(/parece una imagen escaneada/)).toBeTruthy()
  })
})

describe('2.4 — teléfono del contratista', () => {
  it('sin teléfono ofrece traerlo del SECOP II y, traído, lo muestra y avisa al panel', async () => {
    vi.mocked(servicio.completarContactoDelContratista).mockResolvedValue({ ...LOTE_4, telefonoContratista: '4489948' })
    const onActualizado = vi.fn()
    render(<ContactoDelContratista contrato={LOTE_4} onActualizado={onActualizado} />)

    expect(screen.getByText(/El acta de inicio no trae el teléfono/)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Traer el teléfono del SECOP II' }))

    expect(await screen.findByText('4489948')).toBeTruthy()
    expect(onActualizado).toHaveBeenCalledWith(expect.objectContaining({ telefonoContratista: '4489948' }))
    expect(screen.getByRole('button', { name: 'Copiar el teléfono' })).toBeTruthy()
  })

  it('si el registro no lo publica, lo dice y no inventa uno', async () => {
    vi.mocked(servicio.completarContactoDelContratista).mockRejectedValue(
      new ApiError(400, 'El registro de proveedores del SECOP II no publica el teléfono de este contratista.'),
    )
    render(<ContactoDelContratista contrato={LOTE_4} />)

    fireEvent.click(screen.getByRole('button', { name: 'Traer el teléfono del SECOP II' }))
    expect(await screen.findByText(/no publica el teléfono/)).toBeTruthy()
    expect(screen.getByText(/Teléfono:/).textContent).toContain('sin registrar')
  })

  it('con el teléfono ya registrado no ofrece consultar nada', () => {
    render(<ContactoDelContratista contrato={{ ...LOTE_4, telefonoContratista: '4489948' }} />)

    expect(screen.queryByRole('button', { name: 'Traer el teléfono del SECOP II' })).toBeNull()
    expect(screen.getByText('4489948')).toBeTruthy()
  })
})
