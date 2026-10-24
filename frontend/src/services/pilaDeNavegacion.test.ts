import { beforeEach, describe, expect, it } from 'vitest'
import { profundidadDeNavegacion, registrarNavegacion, reiniciarProfundidad } from './pilaDeNavegacion'

/**
 * La cuenta de entradas de historial que apila la sesión.
 *
 * Lo que de verdad protege esta prueba es el punto 13 de la lista del APK: si
 * la cuenta se queda corta, al cerrar sesión sobra una entrada y el botón
 * «atrás» del teléfono se come una pulsación sin que cambie nada en pantalla.
 */
describe('pila de navegación', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('arranca en cero', () => {
    expect(profundidadDeNavegacion()).toBe(0)
  })

  it('cuenta una entrada por cada vista nueva', () => {
    registrarNavegacion('PUSH', 'k1')
    registrarNavegacion('PUSH', 'k2')
    registrarNavegacion('PUSH', 'k3')
    expect(profundidadDeNavegacion()).toBe(3)
  })

  it('descuenta la entrada que consume el botón atrás', () => {
    registrarNavegacion('PUSH', 'k1')
    registrarNavegacion('PUSH', 'k2')
    registrarNavegacion('POP', 'k1')
    expect(profundidadDeNavegacion()).toBe(1)
  })

  // Un `replace` reemplaza la entrada actual en vez de apilar otra, así que el
  // índice del historial no se mueve. Los `<Navigate … replace>` de las rutas
  // (/supervisor -> /supervisor/bandeja, la ruta comodín, el guardia de sesión)
  // pasan por aquí constantemente: si contaran, `logout` retrocedería más
  // entradas de las que existen.
  it('no cuenta un reemplazo', () => {
    registrarNavegacion('PUSH', 'k1')
    registrarNavegacion('REPLACE', 'k2')
    registrarNavegacion('REPLACE', 'k3')
    expect(profundidadDeNavegacion()).toBe(1)
  })

  // En desarrollo StrictMode ejecuta los efectos dos veces con la misma
  // entrada. Sin la clave, una sola navegación contaría doble.
  it('no cuenta dos veces la misma entrada', () => {
    registrarNavegacion('PUSH', 'k1')
    registrarNavegacion('PUSH', 'k1')
    registrarNavegacion('PUSH', 'k1')
    expect(profundidadDeNavegacion()).toBe(1)
  })

  // La primera carga de la aplicación llega como POP con la clave `default`.
  it('nunca baja de cero', () => {
    registrarNavegacion('POP', 'default')
    registrarNavegacion('POP', 'otra')
    expect(profundidadDeNavegacion()).toBe(0)
  })

  it('vuelve a cero al reiniciar, que es lo que hace entrar y salir de la sesión', () => {
    registrarNavegacion('PUSH', 'k1')
    registrarNavegacion('PUSH', 'k2')
    reiniciarProfundidad()
    expect(profundidadDeNavegacion()).toBe(0)
  })

  // Reiniciar no olvida cuál era la última entrada contada: si la olvidara, el
  // efecto que vuelve a correr tras el reinicio contaría otra vez la navegación
  // que acababa de contarse.
  it('al reiniciar no vuelve a contar la entrada en la que está', () => {
    registrarNavegacion('PUSH', 'k1')
    reiniciarProfundidad()
    registrarNavegacion('PUSH', 'k1')
    expect(profundidadDeNavegacion()).toBe(0)
  })

  // La cuenta vive en sessionStorage justamente para esto: al recargar la
  // página el historial del navegador se conserva y el estado de React no.
  it('sobrevive a una recarga de la página', () => {
    registrarNavegacion('PUSH', 'k1')
    registrarNavegacion('PUSH', 'k2')
    // Una recarga no borra sessionStorage, y la entrada en la que se recarga
    // vuelve a llegar al efecto con su misma clave y como POP.
    registrarNavegacion('POP', 'k2')
    expect(profundidadDeNavegacion()).toBe(2)
  })

  it('aguanta una cuenta corrupta en el almacenamiento', () => {
    sessionStorage.setItem('sicot.navegacion.profundidad', 'ni un número')
    expect(profundidadDeNavegacion()).toBe(0)
  })
})
