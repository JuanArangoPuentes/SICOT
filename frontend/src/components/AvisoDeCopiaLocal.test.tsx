import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { AvisoDeCopiaLocal, cuandoSeGuardo } from './AvisoDeCopiaLocal'
import { conCopiaLocal, copiaLocalDe, reiniciarCopiaLocalParaPruebas } from '@/services/copiaLocal'

describe('cuandoSeGuardo', () => {
  const ahora = new Date('2026-10-26T15:00:00').getTime()

  it('dice la hora cuando es de hoy', () => {
    expect(cuandoSeGuardo(new Date('2026-10-26T09:05:00').getTime(), ahora)).toMatch(/^hoy a las /)
  })

  it('dice «ayer» cuando es del día anterior, aunque hayan pasado menos de 24 horas', () => {
    // 20 horas antes, pero de otro día: «hace 20 horas» haría pensar que es de
    // la misma jornada de trabajo.
    expect(cuandoSeGuardo(new Date('2026-10-25T19:00:00').getTime(), ahora)).toMatch(/^ayer a las /)
  })

  it('da la fecha entera cuando es de más atrás', () => {
    expect(cuandoSeGuardo(new Date('2026-10-20T16:40:00').getTime(), ahora)).toMatch(
      /^el \d{1,2}\/\d{1,2}\/\d{4} a las /,
    )
  })
})

describe('AvisoDeCopiaLocal', () => {
  beforeEach(async () => {
    reiniciarCopiaLocalParaPruebas()
    await copiaLocalDe('42')
  })

  it('no se ve mientras el servidor contesta', async () => {
    await conCopiaLocal('/api/contratos', () => Promise.resolve(['x']))
    render(<AvisoDeCopiaLocal />)

    expect(screen.queryByRole('status')).toBeNull()
  })

  it('al servir de la copia avisa, dice de cuándo es y qué se puede hacer sin red', async () => {
    await conCopiaLocal('/api/contratos', () => Promise.resolve(['x']))
    await conCopiaLocal('/api/contratos', () => Promise.reject(new TypeError('Failed to fetch')))

    render(<AvisoDeCopiaLocal />)

    const aviso = screen.getByRole('status')
    expect(aviso).toHaveTextContent('Sin conexión con el servidor')
    expect(aviso).toHaveTextContent(/copia guardada en este equipo hoy a las \d/)
    // Desde la cola de cambios casi todo se puede hacer sin red; lo que no,
    // se dice igual de claro que antes.
    expect(aviso).toHaveTextContent('se guarda aquí y se envía cuando vuelva la red')
    expect(aviso).toHaveTextContent('cargar, generar y firmar documentos necesitan conexión')
  })

  it('si la sesión se abrió sin red, lo dice', async () => {
    localStorage.setItem(
      'sicot.session',
      JSON.stringify({ token: '', usuarioId: 42, nombre: 'S', email: 's@x', rol: 'SUPERVISOR', sinConexion: true }),
    )
    await conCopiaLocal('/api/contratos', () => Promise.resolve(['x']))
    await conCopiaLocal('/api/contratos', () => Promise.reject(new TypeError('Failed to fetch')))

    render(<AvisoDeCopiaLocal />)

    expect(screen.getByRole('status')).toHaveTextContent('Entró sin conexión con la contraseña recordada')
    localStorage.removeItem('sicot.session')
  })
})
