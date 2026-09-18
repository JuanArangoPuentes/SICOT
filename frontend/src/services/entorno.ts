// Dónde se está ejecutando SICOT, y qué consecuencias tiene.
//
// La aplicación es la misma en tres sitios —navegador, instalador de escritorio
// y APK de Android (ADR-012)— y casi nunca necesita saber en cuál está. Este
// módulo existe por una excepción concreta y con consecuencias: Android bloquea
// el tráfico sin cifrar, y lo hace **en silencio**.

/**
 * ¿Se está ejecutando dentro de la aplicación empaquetada con Tauri?
 *
 * Tauri 2 inyecta `__TAURI_INTERNALS__` en la ventana del WebView. Se comprueba
 * esa marca y no el origen —`http://tauri.localhost` en Android— porque el
 * origen es un detalle de implementación que Tauri ya ha cambiado entre
 * versiones mayores, y la marca es parte de su interfaz pública.
 */
export function enAplicacionEmpaquetada(): boolean {
  return typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window
}

/** ¿El sistema operativo es Android? */
export function enAndroid(): boolean {
  return typeof navigator !== 'undefined' && /android/i.test(navigator.userAgent)
}

/**
 * Aviso cuando la dirección configurada va a quedar bloqueada por Android.
 *
 * <h2>Qué problema resuelve</h2>
 * Android bloquea por omisión el tráfico sin cifrar en las compilaciones de
 * **publicación**. Una instalación publicada apuntando a un servidor servido
 * por `http://` no habla con él y **no muestra ningún error**: las peticiones no
 * salen. Quien lo sufra concluirá que el servidor está caído o que la
 * aplicación no sirve, y ninguna de las dos cosas será cierta.
 *
 * <p>Es un caso que va a ocurrir, no una hipótesis: la dirección se escribe a
 * mano en cada instalación —ver {@link apiBase}— y el servidor de un Centro
 * dentro de su propia red puede perfectamente no tener TLS todavía (ADR-009).
 *
 * <h2>Qué NO hace esto</h2>
 * No desbloquea nada. Escribir una excepción de seguridad de red para el
 * servidor del Centro exige el nombre de ese servidor, y **nadie lo ha dado
 * todavía**; inventarlo sería peor que dejarlo sin escribir, y así está dicho en
 * ADR-012. Lo único que se hace aquí es que el fallo deje de ser mudo.
 *
 * <h2>Por qué avisa también en una compilación de depuración</h2>
 * Desde JavaScript no hay forma de saber si el APK instalado es de depuración
 * —donde `http://` sí funciona— o de publicación. Se avisa en las dos: quien
 * configura el servidor no sabe qué compilación le instalaron, y un aviso de más
 * cuesta una frase, mientras que un aviso de menos cuesta el diagnóstico entero.
 *
 * @returns el aviso, o `null` si no aplica.
 */
export function avisoTraficoSinCifrar(direccion: string): string | null {
  const limpia = direccion.trim()
  if (!limpia.toLowerCase().startsWith('http://')) return null
  if (!enAplicacionEmpaquetada() || !enAndroid()) return null
  return (
    'Android bloquea las conexiones sin cifrar. Con esta dirección la aplicación ' +
    'no podrá hablar con el servidor y no mostrará ningún error al intentarlo. ' +
    'Pida al área de sistemas la dirección https:// del servidor del Centro.'
  )
}

/**
 * Mensaje para cuando no se puede alcanzar el servidor al iniciar sesión.
 *
 * Antes era siempre «No se pudo conectar con el servidor.», sin más. Es verdad,
 * pero no dice qué revisar, y en el APK de Android hay dos causas que solo
 * existen ahí y que el usuario no puede adivinar:
 *
 * - `http://` en una compilación de publicación: Android no deja salir la
 *   petición (ya lo avisa {@link avisoTraficoSinCifrar} antes de intentarlo).
 * - `https://` con el certificado local de Caddy (ADR-009): la aplicación solo
 *   confía en él si está instalado en el teléfono. Comprobado en el emulador el
 *   18 de septiembre de 2026: sin instalar la raíz, el acceso falla con el
 *   mismo mensaje genérico que un servidor caído.
 *
 * Desde el navegador del teléfono lo segundo no pasa por aquí —el navegador
 * muestra su propia página de advertencia antes—, así que la pista del
 * certificado se da solo dentro del APK.
 */
export function mensajeSinConexion(direccion: string): string {
  const base = 'No se pudo conectar con el servidor.'
  if (!enAplicacionEmpaquetada() || !enAndroid()) return base
  if (direccion.trim().toLowerCase().startsWith('https://')) {
    return (
      `${base} Revise que la dirección sea la correcta. Si el servidor del Centro usa un certificado propio, ` +
      'tiene que estar instalado en este teléfono: Ajustes → Seguridad → Cifrado y credenciales → Instalar un ' +
      'certificado → Certificado de CA. El área de sistemas del Centro le dará el archivo.'
    )
  }
  return `${base} Revise que la dirección del servidor sea la correcta.`
}
