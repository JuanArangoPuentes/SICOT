import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import GestionPanel from './GestionPanel'
import { PrefsProvider } from '@/prefs'
import { contrato, sesionGestion } from '@/test/dobles'

/**
 * Pruebas del panel de Gestión Contractual.
 *
 * Esta pantalla es la que carga la ficha de un contrato con ayuda del Copiloto,
 * y por eso concentra el riesgo de "dato inventado": lo que la IA extrae de un
 * PDF es una PROPUESTA que una persona confirma, nunca un hecho. Lo que se fija
 * aquí es que el panel siga presentando su información real y no se caiga
 * cuando el backend o la IA fallan — los dos casos donde sería más tentador
 * mostrar algo aproximado.
 */

vi.mock('@/services/contratoService', () => ({
  getContratos: vi.fn(),
  crearContrato: vi.fn(),
  actualizarContrato: vi.fn(),
  cambiarEstadoContrato: vi.fn(),
}))
vi.mock('@/services/usuarioService', () => ({
  getUsuarios: vi.fn(),
  crearUsuario: vi.fn(),
  actualizarUsuario: vi.fn(),
  cambiarEstadoUsuario: vi.fn(),
  enviarCredenciales: vi.fn(),
}))
vi.mock('@/services/documentoService', () => ({
  extraerDatosContrato: vi.fn(),
  subirDocumento: vi.fn(),
}))

function montar() {
  return render(
    <PrefsProvider>
      <GestionPanel usuario={sesionGestion()} onLogout={vi.fn()} onOpenSettings={vi.fn()} onStartTour={vi.fn()} />
    </PrefsProvider>,
  )
}

describe('GestionPanel', () => {
  // Ver la nota de SupervisorPanel.test.tsx sobre el `restoreAllMocks` global.
  beforeEach(async () => {
    localStorage.clear()
    vi.mocked((await import('@/services/contratoService')).getContratos).mockResolvedValue([])
    vi.mocked((await import('@/services/usuarioService')).getUsuarios).mockResolvedValue([])
  })

  it('consulta los contratos reales al montar', async () => {
    const { getContratos } = await import('@/services/contratoService')

    montar()

    await waitFor(() => expect(getContratos).toHaveBeenCalled())
  })

  it('muestra el número de un contrato existente', async () => {
    const { getContratos } = await import('@/services/contratoService')
    vi.mocked(getContratos).mockResolvedValue([contrato()])

    montar()

    expect(await screen.findAllByText(/CTMA-2026-0184/)).not.toHaveLength(0)
  })

  /**
   * Si el backend no responde, la pantalla no puede quedar en blanco ni
   * romperse: quien gestiona contratos debe poder seguir viendo la interfaz y
   * reintentar.
   */
  it('sobrevive a un fallo del backend sin romper el render', async () => {
    const { getContratos } = await import('@/services/contratoService')
    vi.mocked(getContratos).mockRejectedValue(new Error('backend caído'))

    montar()

    await waitFor(() => expect(screen.getAllByText(/contrato/i).length).toBeGreaterThan(0))
  })

  /**
   * Sin contratos cargados, la tabla no debe inventar filas de ejemplo. Es la
   * regla de "no simular" del proyecto aplicada a esta pantalla.
   */
  it('sin contratos no muestra ningún número de contrato inventado', async () => {
    montar()

    await waitFor(() => expect(screen.getAllByText(/contrato/i).length).toBeGreaterThan(0))
    expect(screen.queryByText(/CTMA-2026-0184/)).not.toBeInTheDocument()
  })

  /**
   * El tipo que el backend lee del documento tiene que llegar al formulario.
   * Hasta el 24-09-2026 se comparaba con Object.keys de la lista —los índices—
   * y el formulario se quedaba siempre en «Suministro de Bienes».
   */
  it('aplica al formulario el tipo de contrato leído del documento', async () => {
    const { extraerDatosContrato } = await import('@/services/documentoService')
    vi.mocked(extraerDatosContrato).mockResolvedValue({
      idContrato: 'CO1.PCCNTR.9100001',
      objeto: 'Adquisición de herramienta',
      proveedor: 'FERRETERÍA INDUSTRIAL LOS ANDES S.A.S.',
      nit: '901.234.567-8',
      representanteLegal: 'MARTA LUCÍA OSPINA GÓMEZ',
      valor: '120450000',
      vigenciaInicio: '2026-09-02',
      vigenciaFin: '2026-12-31',
      lugarEjecucion: 'Itagüí',
      registroPresupuestal: '71204',
      tipoContrato: 'Compraventa',
    })

    const { container } = montar()
    fireEvent.click((await screen.findAllByText(/Cargar nueva ficha/))[0])
    const input = container.ownerDocument.querySelector('input[type="file"]') as HTMLInputElement
    expect(input.accept).toContain('.docx')
    fireEvent.change(input, { target: { files: [new File(['%PDF'], 'acta.pdf', { type: 'application/pdf' })] } })
    fireEvent.click(await screen.findByText(/Continuar a confirmación/))

    await waitFor(() => expect(screen.getByDisplayValue('Compraventa')).toBeInTheDocument())
  })

  /**
   * La subida y la extracción van en la misma petición y nada mide cuánto lleva
   * subido. Hubo un «Subiendo… 100 %» que React nunca pintaba y que, de
   * reactivarse, habría mostrado un porcentaje inventado.
   */
  it('mientras lee la ficha muestra un solo estado de espera, sin porcentaje', async () => {
    const { extraerDatosContrato } = await import('@/services/documentoService')
    vi.mocked(extraerDatosContrato).mockReturnValue(new Promise(() => {}))

    const { container } = montar()
    fireEvent.click((await screen.findAllByText(/Cargar nueva ficha/))[0])
    const input = container.ownerDocument.querySelector('input[type="file"]') as HTMLInputElement
    fireEvent.change(input, { target: { files: [new File(['%PDF'], 'acta.pdf', { type: 'application/pdf' })] } })

    expect(await screen.findByText(/Analizando 1 documento/)).toBeInTheDocument()
    expect(screen.queryByText(/Subiendo/)).not.toBeInTheDocument()
    expect(screen.queryByText(/\d+\s*%/)).not.toBeInTheDocument()
  })

  /**
   * Activar y corregir el contrato.
   *
   * Hasta el 06-10-2026 esta pantalla solo hacía GET y POST: todo contrato
   * creado se quedaba en BORRADOR para siempre, y como las cuatro reglas de
   * calendario del motor parten de los contratos ACTIVO, ninguna evaluaba nada.
   * Un error de tecleo en el valor o en la vigencia tampoco se podía corregir
   * desde ninguna pantalla, aunque el `PUT` existiera.
   */
  describe('activar y corregir el contrato', () => {
    it('ofrece activar solo los contratos en borrador', async () => {
      const { getContratos } = await import('@/services/contratoService')
      vi.mocked(getContratos).mockResolvedValue([
        contrato({ id: 1, numeroContrato: 'CTMA-2026-0001', estado: 'BORRADOR' }),
        contrato({ id: 2, numeroContrato: 'CTMA-2026-0002', estado: 'ACTIVO' }),
      ])

      montar()

      // Un solo botón «Activar» para dos contratos: el que está en borrador.
      await waitFor(() => expect(screen.getAllByTitle(/^Activar el contrato/)).toHaveLength(1))
      expect(screen.getByTitle('Activar el contrato CTMA-2026-0001')).toBeInTheDocument()
      expect(screen.getAllByTitle(/^Corregir los datos generales/)).toHaveLength(2)
    })

    it('al confirmar la activación lleva el contrato a ACTIVO y recarga la tabla', async () => {
      const { getContratos, cambiarEstadoContrato } = await import('@/services/contratoService')
      vi.mocked(getContratos).mockResolvedValue([contrato({ estado: 'BORRADOR' })])
      vi.mocked(cambiarEstadoContrato).mockResolvedValue(contrato({ estado: 'ACTIVO' }))

      montar()

      fireEvent.click(await screen.findByTitle('Activar el contrato CTMA-2026-0184'))
      // El diálogo no activa nada al abrirse: la decisión es del botón.
      expect(cambiarEstadoContrato).not.toHaveBeenCalled()
      const consultasAntes = vi.mocked(getContratos).mock.calls.length
      fireEvent.click(screen.getByRole('button', { name: 'Activar el contrato' }))

      await waitFor(() => expect(cambiarEstadoContrato).toHaveBeenCalledWith(1, 'ACTIVO'))
      // Una consulta más: la tabla se recarga para que el estado que se ve sea
      // el que quedó guardado, no el que se supone.
      await waitFor(() => expect(vi.mocked(getContratos).mock.calls.length).toBe(consultasAntes + 1))
    })

    /**
     * El `PUT` reemplaza el contrato completo: si el formulario manda solo lo
     * que cambió, el backend guarda null en todo lo demás y corregir la fecha
     * de fin borraría el NIT, el representante legal y el registro
     * presupuestal — los datos con los que se redactan los documentos.
     */
    it('al corregir manda también los campos que no se tocaron', async () => {
      const { getContratos, actualizarContrato } = await import('@/services/contratoService')
      vi.mocked(getContratos).mockResolvedValue([contrato()])
      vi.mocked(actualizarContrato).mockResolvedValue(contrato())

      montar()

      fireEvent.click(await screen.findByTitle('Corregir los datos generales de CTMA-2026-0184'))
      fireEvent.change(screen.getByDisplayValue('2026-09-30'), { target: { value: '2026-10-31' } })
      fireEvent.click(screen.getByText('Guardar los cambios'))

      await waitFor(() =>
        expect(actualizarContrato).toHaveBeenCalledWith(1, {
          numeroContrato: 'CTMA-2026-0184',
          objeto: 'Suministro e instalación de mobiliario para las aulas',
          valor: 184_500_000,
          fechaInicio: '2026-03-02',
          fechaFin: '2026-10-31',
          tipoContrato: 'Suministro de Bienes',
          contratista: 'Maderas del Norte S.A.S.',
          contratistaNit: '900123456-7',
          representanteLegal: 'María Restrepo',
          lugarEjecucion: 'Centro Tecnológico del Mobiliario',
          numeroRegistroPresupuestal: 'RP-2026-118',
          fechaRegistroPresupuestal: '2026-02-20',
          centroCosto: 'CTMA-01',
        }),
      )
    })

    /**
     * El centro de costo guardado puede no estar en la lista de opciones (otra
     * versión de la lista, o lo que leyó la IA). Un <select> que no contiene su
     * propio valor lo pierde al guardar sin que nadie lo note.
     */
    it('conserva un centro de costo que no está en la lista de opciones', async () => {
      const { getContratos } = await import('@/services/contratoService')
      vi.mocked(getContratos).mockResolvedValue([contrato({ centroCosto: '920599 — CTMA Metalmecánica' })])

      montar()

      fireEvent.click(await screen.findByTitle('Corregir los datos generales de CTMA-2026-0184'))

      expect(screen.getByDisplayValue('920599 — CTMA Metalmecánica')).toBeInTheDocument()
    })

    it('si el backend rechaza la corrección lo dice y no cierra el formulario', async () => {
      const { getContratos, actualizarContrato } = await import('@/services/contratoService')
      const { ApiError } = await import('@/services/api/client')
      vi.mocked(getContratos).mockResolvedValue([contrato()])
      vi.mocked(actualizarContrato).mockRejectedValue(
        new ApiError(400, 'La fecha de fin no puede ser anterior a la fecha de inicio.'),
      )

      montar()

      fireEvent.click(await screen.findByTitle('Corregir los datos generales de CTMA-2026-0184'))
      fireEvent.change(screen.getByDisplayValue('2026-09-30'), { target: { value: '2026-01-01' } })
      fireEvent.click(screen.getByText('Guardar los cambios'))

      expect(await screen.findByText(/La fecha de fin no puede ser anterior/)).toBeInTheDocument()
      expect(screen.getByText('Guardar los cambios')).toBeInTheDocument()
    })
  })
})
