package co.sena.ctma.sicot

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : TauriActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    reservarEspacioParaElTeclado()
  }

  /**
   * Que el teclado del sistema no tape el campo en el que se está escribiendo.
   *
   * Con `enableEdgeToEdge()` y `targetSdk` 36, Android ya no encoge la ventana
   * cuando aparece el teclado: `windowSoftInputMode="adjustResize"` deja de
   * tener efecto, y es la aplicación la que tiene que apartarse. Sin esto, el
   * WebView ni se enteraba —`innerHeight` y `visualViewport` seguían en 915 px
   * CSS con el teclado abierto— y el teclado se dibujaba encima de la página.
   *
   * Medido en el APK el 18 de septiembre de 2026: el teclado empieza en y = 578
   * y el campo del copiloto está en 776–820. El supervisor escribía su pregunta
   * sin ver lo que escribía, ni el botón de enviar, ni las sugerencias. La
   * pantalla de acceso no lo acusaba solo porque su campo de contraseña queda
   * por encima de 578; cualquier campo en la mitad baja de la pantalla sí.
   *
   * Qué se hace: el contenedor raíz recibe como relleno inferior la altura del
   * teclado MENOS la de la barra de navegación. Se resta porque esa franja ya la
   * cubre la propia página con `env(safe-area-inset-bottom)` (ver
   * `viewport-fit=cover` en index.html); sumar las dos dejaría un hueco vacío
   * entre la página y el teclado. Con el teclado cerrado el relleno vuelve a 0 y
   * la aplicación sigue dibujándose de borde a borde como hasta ahora.
   *
   * Vive aquí y no en `generated/` a propósito: `MainActivity.kt` es el sitio
   * que Tauri reserva para ajustes propios y no lo sobrescribe al regenerar.
   */
  private fun reservarEspacioParaElTeclado() {
    val raiz = findViewById<View>(android.R.id.content)
    ViewCompat.setOnApplyWindowInsetsListener(raiz) { vista, insets ->
      val teclado = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
      val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
      vista.setPadding(0, 0, 0, maxOf(0, teclado - barras))
      insets
    }
  }
}
