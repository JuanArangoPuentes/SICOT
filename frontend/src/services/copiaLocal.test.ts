import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './api/client'
import {
  alCambiarLaCopia,
  conCopiaLocal,
  copiaLocalDe,
  estadoDeLaCopia,
  olvidarLaCopiaLocal,
  reiniciarCopiaLocalParaPruebas,
  simularReinicioParaPruebas,
} from './copiaLocal'

/** Lo que lanza `fetch` cuando la petición no sale: sin red. */
const sinRed = () => Promise.reject(new TypeError('Failed to fetch'))

function error(status: number) {
  return () => Promise.reject(new ApiError(status, `Error ${status}`, undefined))
}

describe('copiaLocal', () => {
  beforeEach(async () => {
    reiniciarCopiaLocalParaPruebas()
    await copiaLocalDe('42')
  })

  it('guarda lo que contesta el servidor y lo devuelve cuando no hay red', async () => {
    const servidor = vi.fn().mockResolvedValue([{ id: 1, numeroContrato: 'CO1.PCCNTR.1' }])

    await expect(conCopiaLocal('/api/contratos', servidor)).resolves.toHaveLength(1)
    expect(estadoDeLaCopia().sirviendoCopia).toBe(false)

    const guardado = await conCopiaLocal('/api/contratos', sinRed)
    expect(guardado).toEqual([{ id: 1, numeroContrato: 'CO1.PCCNTR.1' }])
    expect(estadoDeLaCopia().sirviendoCopia).toBe(true)
    expect(estadoDeLaCopia().cuando).toBeTypeOf('number')
  })

  it('vuelve a decir que hay conexión en cuanto el servidor contesta otra vez', async () => {
    await conCopiaLocal('/api/contratos', () => Promise.resolve(['viejo']))
    await conCopiaLocal('/api/contratos', sinRed)
    expect(estadoDeLaCopia().sirviendoCopia).toBe(true)

    await expect(conCopiaLocal('/api/contratos', () => Promise.resolve(['nuevo']))).resolves.toEqual(['nuevo'])
    expect(estadoDeLaCopia()).toEqual({ sirviendoCopia: false, cuando: null })
  })

  /**
   * La diferencia entre sobrevivir a un apagón y saltarse un permiso. Si el
   * servidor contesta que ya no puede ver algo, enseñarle la copia de cuando sí
   * podía sería desautorizar al servidor desde el cliente.
   */
  it.each([403, 404, 500])('ante un %i del servidor no sirve la copia: propaga el error', async (status) => {
    await conCopiaLocal('/api/contratos/9/etapas', () => Promise.resolve(['etapa']))

    await expect(conCopiaLocal('/api/contratos/9/etapas', error(status))).rejects.toBeInstanceOf(ApiError)
    expect(estadoDeLaCopia().sirviendoCopia).toBe(false)
  })

  it('sin copia guardada relanza el fallo de red tal cual', async () => {
    await expect(conCopiaLocal('/api/contratos', sinRed)).rejects.toBeInstanceOf(TypeError)
    expect(estadoDeLaCopia().sirviendoCopia).toBe(false)
  })

  /**
   * Lo que da sentido a todo: la copia tiene que seguir ahí al volver a abrir
   * la aplicación. La primera versión vaciaba el almacén en cada arranque
   * —`usuarioActual` empieza en `null`, así que entrar siempre parecía un
   * cambio de cuenta— y la copia no sobrevivía ni a una recarga. Lo encontró
   * la comprobación en el navegador, no estas pruebas.
   */
  it('la copia sobrevive a cerrar y volver a abrir la aplicación', async () => {
    await conCopiaLocal('/api/contratos', () => Promise.resolve(['mis contratos']))

    simularReinicioParaPruebas()
    await copiaLocalDe('42')

    await expect(conCopiaLocal('/api/contratos', sinRed)).resolves.toEqual(['mis contratos'])
  })

  /** Dos supervisores en el mismo portátil del Centro. */
  it('no le enseña a una cuenta la copia guardada por otra', async () => {
    await conCopiaLocal('/api/contratos', () => Promise.resolve(['del supervisor 42']))

    await copiaLocalDe('77')

    await expect(conCopiaLocal('/api/contratos', sinRed)).rejects.toBeInstanceOf(TypeError)
  })

  it('al cerrar sesión olvida lo guardado', async () => {
    await conCopiaLocal('/api/contratos', () => Promise.resolve(['algo']))
    await olvidarLaCopiaLocal()

    await expect(conCopiaLocal('/api/contratos', sinRed)).rejects.toBeInstanceOf(TypeError)
    expect(estadoDeLaCopia()).toEqual({ sirviendoCopia: false, cuando: null })
  })

  it('avisa a quien escucha cuando se empieza a servir de la copia', async () => {
    const oyente = vi.fn()
    const dejarDeEscuchar = alCambiarLaCopia(oyente)

    await conCopiaLocal('/api/contratos', () => Promise.resolve(['x']))
    await conCopiaLocal('/api/contratos', sinRed)

    expect(oyente).toHaveBeenCalledWith(expect.objectContaining({ sirviendoCopia: true }))
    dejarDeEscuchar()
  })

  /** Cada lectura tiene su clave: la de las etapas no responde por la de las alertas. */
  it('no mezcla lecturas distintas', async () => {
    await conCopiaLocal('/api/contratos/1/etapas', () => Promise.resolve(['etapa']))

    await expect(conCopiaLocal('/api/contratos/1/alertas', sinRed)).rejects.toBeInstanceOf(TypeError)
    await expect(conCopiaLocal('/api/contratos/1/etapas', sinRed)).resolves.toEqual(['etapa'])
  })

  /**
   * Que no haya dónde guardar no puede estropear una lectura que sí funcionó.
   *
   * <p>Es el caso de la ventana privada o del equipo con el almacenamiento
   * bloqueado por políticas: ahí `indexedDB.open` revienta. jsdom no trae
   * IndexedDB, así que aquí se pone uno que falla a propósito; el camino de
   * IndexedDB de verdad se comprueba en el navegador, no en estas pruebas.
   */
  it('si el almacén falla al abrirse, la lectura en línea sigue valiendo', async () => {
    Object.defineProperty(globalThis, 'indexedDB', {
      configurable: true,
      value: {
        open: () => {
          throw new Error('almacenamiento bloqueado')
        },
      },
    })
    try {
      reiniciarCopiaLocalParaPruebas()
      await copiaLocalDe('42')

      await expect(conCopiaLocal('/api/contratos', () => Promise.resolve(['vale']))).resolves.toEqual(['vale'])
      expect(estadoDeLaCopia().sirviendoCopia).toBe(false)
    } finally {
      Reflect.deleteProperty(globalThis, 'indexedDB')
    }
  })
})
