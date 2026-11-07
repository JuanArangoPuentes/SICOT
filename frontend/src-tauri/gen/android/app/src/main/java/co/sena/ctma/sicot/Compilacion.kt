package co.sena.ctma.sicot

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.webkit.JavascriptInterface

/**
 * Qué compilación del APK está instalada, pedido desde la página como
 * `window.SicotCompilacion.aceptaHttp()`.
 *
 * Por qué: la página avisa «Android bloquea las conexiones sin cifrar» al
 * escribir una dirección `http://`, porque el APK oficial (publicación) las
 * bloquea en silencio. Pero el APK de pruebas (compilación de depuración, el
 * que se publica para probar contra un portátil de la red local) sí las acepta
 * —su configuración de red está en `src/debug/res/xml/`—, y el aviso le decía
 * lo contrario a quien lo estaba usando bien. Desde JavaScript no hay forma de
 * distinguirlas; desde aquí sí: la de depuración es la única «depurable».
 */
class Compilacion(private val actividad: Activity) {
  @JavascriptInterface
  fun aceptaHttp(): Boolean = (actividad.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
