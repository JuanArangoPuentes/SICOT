import { describe, expect, it } from 'vitest'
import { guiaDelSubPaso } from './guiaSubPaso'
import type { Step, SubStep } from '@/types/domain'

const paso = (id: number, title: string, subSteps: Partial<SubStep>[] = []): Step =>
  ({ id, title, status: 'active', subSteps }) as unknown as Step
const sub = (over: Partial<SubStep>): SubStep => ({
  id: '1.1',
  label: 'Sub-paso',
  responsible: 'Supervisor',
  document: '',
  completed: false,
  ...over,
})

describe('guiaDelSubPaso', () => {
  it('en un sub-paso con documento dice que SICOT lo arma y el supervisor lo firma', () => {
    const t = guiaDelSubPaso(
      paso(3, 'INSPECCIÓN'),
      sub({ id: '3.4', label: 'Firma del Informe de Supervisión', description: 'SICOT arma el informe.' }),
    )
    expect(t).toContain('Sub-paso 3.4 — Firma del Informe de Supervisión')
    expect(t).toContain('SICOT arma el informe.')
    expect(t).toContain('SICOT arma «Informe de Supervisión» (GCCON-F-031)')
    expect(t).toContain('«dato pendiente»')
  })

  // El proceso real (15-10-2026): el acta la elabora Contratación; SICOT no la
  // arma, la recibe firmada. La guía tiene que decir eso, no lo contrario.
  it('en el paso del acta explica qué cargar y que sin eso no se cierra', () => {
    const t = guiaDelSubPaso(
      paso(2, 'INICIO — Acta de Inicio y arranque'),
      sub({
        id: '2.1',
        label: 'Firma y devolución del Acta de Inicio (GCCON-F-018)',
        documentosRequeridos: 'ACTA_FIRMADA_Y_ENVIO',
      }),
    )
    expect(t).toContain('cargue en este sub-paso el acta firmada (PDF) y la evidencia del envío')
    expect(t).toContain('SICOT no lo deja cerrar sin ellas')
    expect(t).not.toContain('SICOT arma')
  })

  it('en la verificación y los correos del arranque dice qué herramienta usar', () => {
    const inicio = paso(2, 'INICIO')
    expect(guiaDelSubPaso(inicio, sub({ id: '2.3', label: 'Verificación de especificaciones en SECOP II' }))).toContain(
      'lo que el SECOP II publica del contrato',
    )
    expect(guiaDelSubPaso(inicio, sub({ id: '2.2', label: 'Aviso a los profesionales SST y ambiental' }))).toContain(
      'están en el estudio previo',
    )
    expect(
      guiaDelSubPaso(inicio, sub({ id: '2.4', label: 'Contacto con el contratista y acuerdo de entregas' })),
    ).toContain('cargue abajo la solicitud de pedido')
  })

  it('en la evidencia fotográfica manda a tomar la foto', () => {
    expect(guiaDelSubPaso(paso(3, 'INSPECCIÓN'), sub({ id: '3.2', label: 'Evidencia' }))).toContain(
      'Tomar foto de la entrega',
    )
  })

  it('si el responsable es otro, lo dice y no manda al supervisor a hacerlo', () => {
    const t = guiaDelSubPaso(paso(1, 'INICIO'), sub({ id: '1.4', label: 'CDP', responsible: 'Unidad de Contratación' }))
    expect(t).toContain('este sub-paso lo realiza Unidad de Contratación')
  })

  it('en 6.3, que no cierra el paso, no promete pedir observaciones', () => {
    const cierre = paso(6, 'CIERRE', [{ id: '6.3' }, { id: '6.4' }])
    const t = guiaDelSubPaso(cierre, sub({ id: '6.3', label: 'Firma del Informe Final' }))
    expect(t).not.toContain('le pediré que me cuente')
    expect(t).toContain('sin observaciones')
  })

  it('en el último sub-paso del paso sí promete pedir observaciones', () => {
    const t = guiaDelSubPaso(
      paso(3, 'INSPECCIÓN', [{ id: '3.3' }, { id: '3.4' }]),
      sub({ id: '3.4', label: 'Informe' }),
    )
    expect(t).toContain('le pediré que me cuente')
  })

  it('en la evidencia fotográfica dice que hay que cargarla antes de cerrar el sub-paso', () => {
    const t = guiaDelSubPaso(paso(3, 'INSPECCIÓN'), sub({ id: '3.2', label: 'Evidencia' }))
    expect(t).toContain('Elegir una foto')
    expect(t).toContain('«Cargar evidencia»')
  })

  it('la certificación sin código oficial no inventa uno', () => {
    const t = guiaDelSubPaso(paso(5, 'CERTIFICACIÓN'), sub({ id: '5.3', label: 'Certificación' }))
    expect(t).toContain('SICOT arma «Certificación de cumplimiento» con')
    expect(t).not.toContain('PENDIENTE_DE_DEFINIR')
  })
})
