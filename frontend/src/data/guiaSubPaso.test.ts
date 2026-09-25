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
      paso(2, 'INICIO — Acta de Inicio'),
      sub({ id: '2.7', label: 'Firma del Acta de Inicio', description: 'Suscripción del acta con el contratista.' }),
    )
    expect(t).toContain('Sub-paso 2.7 — Firma del Acta de Inicio')
    expect(t).toContain('Suscripción del acta con el contratista.')
    expect(t).toContain('SICOT arma «Acta de Inicio» (GCCON-F-018)')
    expect(t).toContain('«dato pendiente»')
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
    const t = guiaDelSubPaso(paso(2, 'INICIO', [{ id: '2.6' }, { id: '2.7' }]), sub({ id: '2.7', label: 'Acta' }))
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
