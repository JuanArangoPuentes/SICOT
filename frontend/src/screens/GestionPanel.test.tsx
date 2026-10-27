import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import GestionPanel from './GestionPanel'
import { PrefsProvider } from '@/prefs'
import { contrato, sesionGestion } from '@/test/dobles'
import type { InvitacionDeSupervisorResponse } from '@/services/api/types'

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
  asignarSupervisor: vi.fn(),
  invitarSupervisor: vi.fn(),
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
          // Los de la designación también viajan: el PUT reemplaza el contrato
          // entero, y hasta el 18-10-2026 la corrección los borraba.
          enlaceSecop: null,
          apoyoSupervision: null,
          fechaDesignacion: null,
          // Correo y teléfono no viajan si Gestión no los tocó: los llena también
          // el supervisor, y el backend conserva el que había.
          correoContratista: undefined,
          telefonoContratista: undefined,
        }),
      )
    })

    it('al corregir conserva el enlace del SECOP, el apoyo y la fecha de designación, y no reenvía correo ni teléfono sin tocar', async () => {
      const { getContratos, actualizarContrato } = await import('@/services/contratoService')
      vi.mocked(getContratos).mockResolvedValue([
        contrato({
          enlaceSecop: 'https://community.secop.gov.co/Public/Tendering/OpportunityDetail/Index?noticeUID=CO1.NTC.1',
          apoyoSupervision: 'Yeison Zapata',
          fechaDesignacion: '2026-10-10',
          correoContratista: 'ventas@maderasdelnorte.com',
          telefonoContratista: '604 448 9948',
        }),
      ])
      vi.mocked(actualizarContrato).mockResolvedValue(contrato())

      montar()

      fireEvent.click(await screen.findByTitle('Corregir los datos generales de CTMA-2026-0184'))
      fireEvent.click(screen.getByText('Guardar los cambios'))

      await waitFor(() =>
        expect(actualizarContrato).toHaveBeenCalledWith(
          1,
          expect.objectContaining({
            enlaceSecop: 'https://community.secop.gov.co/Public/Tendering/OpportunityDetail/Index?noticeUID=CO1.NTC.1',
            apoyoSupervision: 'Yeison Zapata',
            fechaDesignacion: '2026-10-10',
          }),
        ),
      )
      // El supervisor pudo traerlos del SECOP después de que se abrió esta
      // lista: reenviar los de la lista los borraría o los pisaría.
      const cuerpo = vi.mocked(actualizarContrato).mock.calls[0][1]
      expect(cuerpo.correoContratista).toBeUndefined()
      expect(cuerpo.telefonoContratista).toBeUndefined()
    })

    it('si Gestión cambia o borra el teléfono, sí viaja: el nuevo, o vacío para borrarlo', async () => {
      const { getContratos, actualizarContrato } = await import('@/services/contratoService')
      vi.mocked(getContratos).mockResolvedValue([
        contrato({ correoContratista: 'ventas@maderasdelnorte.com', telefonoContratista: '604 448 9948' }),
      ])
      vi.mocked(actualizarContrato).mockResolvedValue(contrato())

      montar()

      fireEvent.click(await screen.findByTitle('Corregir los datos generales de CTMA-2026-0184'))
      fireEvent.change(screen.getByDisplayValue('604 448 9948'), { target: { value: '3001234567' } })
      fireEvent.change(screen.getByDisplayValue('ventas@maderasdelnorte.com'), { target: { value: '' } })
      fireEvent.click(screen.getByText('Guardar los cambios'))

      await waitFor(() =>
        expect(actualizarContrato).toHaveBeenCalledWith(
          1,
          expect.objectContaining({ telefonoContratista: '3001234567', correoContratista: '' }),
        ),
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

  /**
   * Poner supervisor en un contrato por su correo.
   *
   * POR QUÉ IMPORTA. Hasta el 26-10-2026 Gestión solo podía elegir de una
   * lista de supervisores que ya existían: crear la cuenta y mandarle la clave
   * eran dos acciones más, de otra persona (el Administrador). Un supervisor
   * nuevo no recibía nada y el contrato se quedaba sin nadie a cargo. Lo que
   * se fija aquí es que la pantalla NO dé por hecho que el correo salió: si no
   * salió, la clave recién creada solo existe en esa respuesta y tiene que
   * verse, o la cuenta queda creada y nadie puede entrar con ella.
   */
  describe('invitar a un supervisor por su correo', () => {
    const invitacion = (parcial: Partial<InvitacionDeSupervisorResponse> = {}): InvitacionDeSupervisorResponse => ({
      contrato: contrato({
        supervisorId: 9,
        supervisorNombre: 'Supervisora Nueva',
        // El backend guarda el correo en minúsculas: por eso es el servidor
        // quien dice con qué usuario se entra, no lo que se tecleó.
        supervisorEmail: 'nueva@sena.edu.co',
      }),
      cuentaCreada: true,
      credencialesEnviadas: true,
      claveParaEntregar: null,
      detalle: 'Cuenta creada. Sus datos de acceso salieron a nueva@sena.edu.co.',
      ...parcial,
    })

    // `restoreAllMocks` deshace los espías, pero no borra las llamadas
    // anotadas por los vi.fn() de la factoría de `vi.mock`: sin esto, la
    // prueba que comprueba que NO se llamó al servidor ve la llamada de la
    // prueba anterior.
    beforeEach(async () => {
      vi.mocked((await import('@/services/contratoService')).invitarSupervisor).mockClear()
    })

    async function abrirElDialogo() {
      const { getContratos } = await import('@/services/contratoService')
      vi.mocked(getContratos).mockResolvedValue([contrato({ supervisorId: null, supervisorNombre: null })])
      montar()
      fireEvent.click(await screen.findByTitle('Asignar o cambiar el supervisor de CTMA-2026-0184'))
    }

    function escribirAlInvitado() {
      fireEvent.change(screen.getByPlaceholderText('Nombre y apellidos'), {
        target: { value: 'Supervisora Nueva' },
      })
      // A propósito con mayúsculas: el backend crea la cuenta en minúsculas, y
      // el usuario que se le entrega tiene que ser el que de verdad quedó
      // guardado, no el que alguien escribió.
      fireEvent.change(screen.getByPlaceholderText('nombre@sena.edu.co'), {
        target: { value: 'Nueva@sena.edu.co' },
      })
      fireEvent.change(screen.getByPlaceholderText('3001234567'), { target: { value: '3009998877' } })
    }

    /**
     * Sin supervisores en SICOT, la condición anterior escondía el botón de
     * asignar: los contratos de un SICOT recién instalado no se podían asignar
     * a nadie, que es justo cuando invitar hace falta.
     */
    it('sin ningún supervisor en SICOT el botón sigue estando y abre en «invitar por correo»', async () => {
      await abrirElDialogo()

      expect(screen.getByLabelText('Invitar a uno nuevo por su correo')).toBeChecked()
      expect(screen.getByPlaceholderText('nombre@sena.edu.co')).toBeInTheDocument()
    })

    it('con supervisores en SICOT abre en la lista, y se puede cambiar al correo', async () => {
      const { getUsuarios } = await import('@/services/usuarioService')
      vi.mocked(getUsuarios).mockResolvedValue([
        {
          id: 3,
          nombre: 'Alex Fernando Zapata',
          email: 'supervisor@soy.sena.edu.co',
          telefono: null,
          rol: 'SUPERVISOR',
          activo: true,
          fechaCreacion: '2026-01-01T00:00:00Z',
        },
      ])

      await abrirElDialogo()

      await waitFor(() => expect(screen.getByLabelText('Uno que ya trabaja en SICOT')).toBeChecked())
      expect(screen.queryByPlaceholderText('nombre@sena.edu.co')).not.toBeInTheDocument()

      fireEvent.click(screen.getByLabelText('Invitar a uno nuevo por su correo'))

      expect(screen.getByPlaceholderText('nombre@sena.edu.co')).toBeInTheDocument()
    })

    it('manda al servidor el nombre, el correo y el teléfono escritos', async () => {
      const { invitarSupervisor } = await import('@/services/contratoService')
      vi.mocked(invitarSupervisor).mockResolvedValue(invitacion())

      await abrirElDialogo()
      escribirAlInvitado()
      fireEvent.click(screen.getByText('Invitar y asignar'))

      await waitFor(() =>
        expect(invitarSupervisor).toHaveBeenCalledWith(1, {
          nombre: 'Supervisora Nueva',
          email: 'Nueva@sena.edu.co',
          telefono: '3009998877',
        }),
      )
    })

    it('con un campo vacío no llama al servidor y dice qué falta', async () => {
      const { invitarSupervisor } = await import('@/services/contratoService')

      await abrirElDialogo()
      fireEvent.change(screen.getByPlaceholderText('nombre@sena.edu.co'), {
        target: { value: 'nueva@sena.edu.co' },
      })
      fireEvent.click(screen.getByText('Invitar y asignar'))

      expect(await screen.findByRole('alert')).toHaveTextContent(/nombre, el correo y el teléfono/)
      expect(invitarSupervisor).not.toHaveBeenCalled()
    })

    /** Lo que esta pantalla existe para no perder. */
    it('si el correo no salió enseña la clave, avisa de que no se repite y no cierra', async () => {
      const { invitarSupervisor } = await import('@/services/contratoService')
      vi.mocked(invitarSupervisor).mockResolvedValue(
        invitacion({
          credencialesEnviadas: false,
          claveParaEntregar: 'Hk7mRt2pQw9xZv4n',
          detalle: 'La cuenta quedó creada, pero el correo no salió. Entréguele usted la clave.',
        }),
      )

      await abrirElDialogo()
      escribirAlInvitado()
      fireEvent.click(screen.getByText('Invitar y asignar'))

      expect(await screen.findByText('Hk7mRt2pQw9xZv4n')).toBeInTheDocument()
      // El usuario que se entrega es el que guardó el servidor —en minúsculas—,
      // no el «Nueva@sena.edu.co» que se tecleó: con el otro, el primer intento
      // de entrar podría fallar.
      expect(screen.getByText('nueva@sena.edu.co')).toBeInTheDocument()
      expect(screen.queryByText('Nueva@sena.edu.co')).not.toBeInTheDocument()
      expect(screen.getByText(/no se vuelve a mostrar/)).toBeInTheDocument()
      expect(screen.getByText('Ya la copié, cerrar')).toBeInTheDocument()
    })

    it('si el correo salió no enseña ninguna clave', async () => {
      const { invitarSupervisor } = await import('@/services/contratoService')
      vi.mocked(invitarSupervisor).mockResolvedValue(invitacion())

      await abrirElDialogo()
      escribirAlInvitado()
      fireEvent.click(screen.getByText('Invitar y asignar'))

      expect(await screen.findByText(/salieron a nueva@sena.edu.co/)).toBeInTheDocument()
      expect(screen.queryByText(/no se vuelve a mostrar/)).not.toBeInTheDocument()
      expect(screen.getByText('Listo')).toBeInTheDocument()
    })

    /**
     * A un supervisor que ya trabaja en SICOT no se le manda clave nueva: la
     * suya sigue sirviendo. Enseñar una aquí sería mentira, y además dejaría
     * que cualquiera con acceso a Gestión le restableciera la clave.
     */
    it('a uno que ya tenía cuenta solo le asigna el contrato, sin clave', async () => {
      const { invitarSupervisor } = await import('@/services/contratoService')
      vi.mocked(invitarSupervisor).mockResolvedValue(
        invitacion({
          cuentaCreada: false,
          detalle: 'Ese supervisor ya tenía cuenta en SICOT: conserva su clave y se le asignó el contrato.',
        }),
      )

      await abrirElDialogo()
      escribirAlInvitado()
      fireEvent.click(screen.getByText('Invitar y asignar'))

      expect(await screen.findByText(/ya tenía cuenta en SICOT/)).toBeInTheDocument()
      expect(screen.queryByText(/no se vuelve a mostrar/)).not.toBeInTheDocument()
    })

    it('si el servidor rechaza la invitación lo dice y deja corregir', async () => {
      const { invitarSupervisor } = await import('@/services/contratoService')
      const { ApiError } = await import('@/services/api/client')
      vi.mocked(invitarSupervisor).mockRejectedValue(
        new ApiError(400, 'Ese correo es de un administrador, no de un supervisor.'),
      )

      await abrirElDialogo()
      escribirAlInvitado()
      fireEvent.click(screen.getByText('Invitar y asignar'))

      expect(await screen.findByRole('alert')).toHaveTextContent(/no de un supervisor/)
      // El formulario sigue ahí con lo escrito: se corrige el correo y se reintenta.
      expect(screen.getByDisplayValue('Nueva@sena.edu.co')).toBeInTheDocument()
    })
  })
})
