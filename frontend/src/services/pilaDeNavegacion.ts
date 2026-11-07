// Cuántas entradas de historial ha apilado la sesión abierta.
//
// **Por qué hace falta contarlas.** Al cerrar sesión, `App.tsx` reemplaza la
// entrada actual por `/login`. Eso deja intactas las que se apilaron antes:
// después de pasar por Contrato, Alertas y Documentos, el historial es
// `[bandeja, contrato, alertas, /login]`. El botón «atrás» del teléfono
// entonces no sale de la aplicación —como sí hace en un arranque limpio— sino
// que va a `alertas`, el guardia de sesión ve que no hay sesión y la reemplaza
// otra vez por `/login`: la pantalla no cambia, y parece que el teléfono se
// quedó atascado. Hacen falta tantas pulsaciones como vistas se hubieran
// visitado para que la aplicación se cierre. Observado el 08-10-2026 en el
// emulador, punto 13 de `docs/operacion/PRUEBA_MANUAL_APK.md`.
//
// El navegador no deja borrar entradas del historial, así que la única forma de
// que «atrás» vuelva a salir de la aplicación es RETROCEDER sobre las que se
// apilaron —`history.go(-n)`— antes de aterrizar en `/login`. Para eso hay que
// saber cuántas son, y eso es lo que lleva este módulo.
//
// La cuenta es exacta por construcción: la entrada a la sesión se hace con
// `replace` (no mueve el índice del historial), cada cambio de vista apila una
// entrada (PUSH, +1) y cada «atrás» consume una (POP, −1). Un `replace`
// intermedio —los `<Navigate replace>` de las rutas— no mueve el índice y no
// cuenta.
//
// **Vive en `sessionStorage` y no en estado de React** por dos razones:
//  1. Al recargar la página (o al restaurar el WebView) el historial del
//     navegador se conserva pero el estado de React se pierde. Un contador en
//     memoria volvería a cero mientras el historial sigue teniendo entradas
//     detrás, y el arreglo dejaría de funcionar justo después de una recarga.
//  2. `onUnauthorized` captura `logout` una sola vez, al montar. Leyendo la
//     cuenta de fuera de React, esa copia antigua sigue viendo el valor de
//     ahora en vez del de entonces.
//
// `sessionStorage` es por pestaña y se borra al cerrarla, que es exactamente el
// alcance que esta cuenta necesita.

const CLAVE = 'sicot.navegacion.profundidad'
// La última entrada contada, para no contar dos veces la misma navegación: en
// desarrollo StrictMode ejecuta los efectos dos veces con la misma `key`.
const CLAVE_ULTIMA = 'sicot.navegacion.ultima'

/** Tipos de navegación de react-router, sin importar el enum del router. */
export type TipoDeNavegacion = 'POP' | 'PUSH' | 'REPLACE'

function leer(clave: string): string | null {
  try {
    return sessionStorage.getItem(clave)
  } catch {
    // Modo privado o almacenamiento bloqueado: la cuenta deja de persistir y el
    // cierre de sesión se comporta como antes del arreglo. Es peor, no roto.
    return null
  }
}

function escribir(clave: string, valor: string) {
  try {
    sessionStorage.setItem(clave, valor)
  } catch {
    /* ver leer() */
  }
}

/** Entradas apiladas desde que se entró a la sesión. Nunca negativa. */
export function profundidadDeNavegacion(): number {
  const valor = Number(leer(CLAVE))
  return Number.isInteger(valor) && valor > 0 ? valor : 0
}

/**
 * Apunta una navegación. `clave` es `location.key` de react-router: identifica
 * la entrada del historial, así que repetir la misma no vuelve a contar.
 */
export function registrarNavegacion(tipo: TipoDeNavegacion, clave: string) {
  if (leer(CLAVE_ULTIMA) === clave) return
  escribir(CLAVE_ULTIMA, clave)
  if (tipo === 'PUSH') escribir(CLAVE, String(profundidadDeNavegacion() + 1))
  else if (tipo === 'POP') escribir(CLAVE, String(Math.max(0, profundidadDeNavegacion() - 1)))
  // REPLACE no mueve el índice del historial: no cuenta.
}

/**
 * Vuelve la cuenta a cero. Se llama al entrar a la sesión —cuyo `navigate` es
 * un `replace`, así que esa entrada es la base— y al cerrarla, una vez
 * retrocedido el historial.
 */
export function reiniciarProfundidad() {
  escribir(CLAVE, '0')
  // La clave de la entrada actual NO se limpia: sigue siendo la última contada
  // y limpiarla dejaría que la misma navegación se contara de nuevo.
}
