// Peticiones largas que se cortan al salir de la aplicación.
//
// <h2>Qué pasa, medido</h2>
// En el APK de Android, cuando el supervisor cambia de aplicación, el sistema
// destruye las conexiones abiertas de SICOT a los pocos segundos. Se vio en el
// emulador (Android 15) el 18 de septiembre de 2026: 17 s después de pasar a
// segundo plano, `system_server` registró «Destroyed live tcp sockets for
// uids={…, 10209, …}», y 10209 es el uid de SICOT.
//
// Casi ninguna petición de SICOT dura tanto. Las de la IA sí: la primera
// pregunta al copiloto sobre CPU tarda entre uno y dos minutos y medio, y
// generar un documento, más de dos. Es justo el rato en que un supervisor con
// el teléfono en la mano se va a otra aplicación. El servidor terminaba su
// trabajo —la respuesta se generó en 119 s— y la aplicación la perdía,
// mostrando además un mensaje falso: «verifique que Ollama esté disponible».
//
// <h2>Qué hace este módulo</h2>
// Solo lo mínimo para no mentir y no perder trabajo: saber si la página pasó a
// segundo plano mientras una petición estaba en curso, y esperar a que vuelva.
// Qué hacer con eso lo decide quien llama, porque no es lo mismo en todas: una
// pregunta al copiloto se puede repetir sin consecuencias, pero generar un
// documento oficial dos veces crearía dos actas.
//
// El arreglo de fondo sería que el servidor guarde la respuesta y la aplicación
// la recoja al volver. Eso toca el backend y queda como siguiente paso escrito;
// esto es lo que se puede hacer sin él.

/** Vigila si la página se oculta mientras dura una operación. */
export function vigilarSegundoPlano() {
  let seOculto = document.visibilityState === 'hidden'
  const alCambiar = () => {
    if (document.visibilityState === 'hidden') seOculto = true
  }
  document.addEventListener('visibilitychange', alCambiar)
  return {
    /** ¿Pasó la página a segundo plano en algún momento desde que se empezó a vigilar? */
    seOculto: () => seOculto,
    terminar: () => document.removeEventListener('visibilitychange', alCambiar),
  }
}

/** Se resuelve cuando la página vuelve a estar en pantalla (o enseguida, si ya lo está). */
export function esperarPrimerPlano(): Promise<void> {
  if (document.visibilityState === 'visible') return Promise.resolve()
  return new Promise((resolver) => {
    const alCambiar = () => {
      if (document.visibilityState !== 'visible') return
      document.removeEventListener('visibilitychange', alCambiar)
      resolver()
    }
    document.addEventListener('visibilitychange', alCambiar)
  })
}
