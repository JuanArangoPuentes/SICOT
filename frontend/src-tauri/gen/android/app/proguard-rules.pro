# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# El puente que la página usa para mantener la pantalla encendida mientras
# espera a la IA (PantallaEncendida.kt). Sin esta regla, R8 renombra o quita el
# método en la compilación de publicación y la llamada desde JavaScript falla
# en silencio: justo la compilación que instala el supervisor.
-keepclassmembers class co.sena.ctma.sicot.PantallaEncendida {
    @android.webkit.JavascriptInterface public *;
}
