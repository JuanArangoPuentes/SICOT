package co.sena.ctma.sicot

import android.app.Activity
import android.view.WindowManager
import android.webkit.JavascriptInterface

/**
 * Mantener la pantalla encendida mientras SICOT espera una respuesta larga de
 * la IA, pedido desde la página como `window.SicotPantalla.mantener(true)`.
 *
 * Por qué hace falta: con el apagado automático habitual (30 s o 1 min), la
 * pantalla se apagaba a mitad de una respuesta del Copiloto o de la redacción
 * de un documento; Android cierra las conexiones de la aplicación unos 17 s
 * después y la petición se cortaba. La página ya pedía la pantalla encendida con
 * la Screen Wake Lock API, pero el WebView de este APK no la cumple: el punto 17
 * de la prueba manual (PRUEBA_MANUAL_APK.md) lo dejó escrito, «la pantalla sí se
 * apagó». La ventana nativa sí puede: FLAG_KEEP_SCREEN_ON, que el sistema retira
 * solo al salir de la aplicación.
 *
 * Qué expone: un único método que pone o quita esa bandera. No lee nada, no
 * devuelve nada y no toca datos; el WebView solo carga la propia aplicación.
 */
class PantallaEncendida(private val actividad: Activity) {
  @JavascriptInterface
  fun mantener(encendida: Boolean) {
    actividad.runOnUiThread {
      if (encendida) {
        actividad.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      } else {
        actividad.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }
    }
  }
}
