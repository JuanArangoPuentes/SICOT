# ADR-013 — Cómo se firma y se reparte la aplicación de Android

**Estado:** Aceptada · **Fecha:** 18 de septiembre de 2026

## Contexto

ADR-012 decidió que la aplicación móvil de SICOT es el mismo frontend empaquetado
para Android. Hasta aquí, sin embargo, la única forma de tener SICOT en un
teléfono era clonar el repositorio e instalar la cadena de herramientas de
Android: la compilación de publicación existía —12 MB, medida el 17 de
septiembre— pero salía **sin firmar**, y Android no instala un APK sin firma.

`INSTALACION.md` explicaba el hueco diciendo que «la firma de distribución es una
decisión de cuenta institucional, no de código». Esa frase mezclaba dos cosas que
no se parecen:

| | Qué exige | Coste | De quién es la decisión |
| --- | --- | --- | --- |
| **Publicar en Google Play** | Una cuenta de desarrollador a nombre de alguien | De pago | Institucional. ADR-012 la prohíbe sin esa decisión previa |
| **Firmar un APK para instalarlo directamente** | Una llave que se genera con las herramientas del JDK | **Gratuito** | Del proyecto, sobre quién la custodia |

La segunda es exactamente el modelo con el que ya se distribuye el instalador de
escritorio: la versión 0.3.0 está publicada en GitHub Releases **sin firmar**, e
`INSTALACION.md` explica cómo pasar el aviso de «editor desconocido». El
equivalente en Android es permitir la instalación desde fuentes desconocidas.

## Decisión

1. El APK se firma con **una llave propia del proyecto**, generada el 18 de
   septiembre de 2026: RSA 4096, alias `sicot`, validez de unos 27 años, formato
   PKCS12. Huella SHA-256:
   `7F:AD:CA:B2:47:26:A0:03:52:69:F6:13:A0:93:A5:0D:41:6B:DE:88:F6:07:CC:9A:79:CC:71:E8:6A:7C:FA:FC`.
2. **Custodia en dos sitios, y en ninguno más:**
   - Como secretos del repositorio en GitHub Actions
     (`SICOT_ANDROID_KEYSTORE_BASE64`, `SICOT_ANDROID_KEYSTORE_PASSWORD`,
     `SICOT_ANDROID_KEY_ALIAS`), que es de donde firma el flujo de publicación.
   - Una copia fuera de línea bajo custodia de Juan Arango.
3. La llave **nunca** entra en el repositorio. Gradle la lee de variables de
   entorno (`gen/android/app/build.gradle.kts`); si faltan, la compilación de
   publicación sale sin firmar en vez de fallar, para que el job que solo
   comprueba que el proyecto compila no necesite ningún secreto.
4. El APK firmado se publica como un asset más de la versión de GitHub, junto al
   instalador de escritorio. Google Play sigue fuera, como decía ADR-012.

## Por qué la custodia importa tanto como para escribirla aquí

Android exige que **cada versión** de una aplicación esté firmada con la misma
llave que la anterior. Las dos formas de fallar son asimétricas y ninguna tiene
arreglo después:

- **Si la llave se pierde** —las dos copias—, ningún teléfono que ya tenga SICOT
  podrá actualizarse. Habría que desinstalar y reinstalar en cada uno, y con la
  desinstalación se va la dirección del servidor que cada supervisor configuró.
- **Si la llave se filtra**, cualquiera puede publicar una «actualización» de
  SICOT que Android aceptaría como legítima.

Por eso dos copias y no una, y por eso ninguna tercera.

## Versión y actualizaciones

Android rechaza una actualización cuyo `versionCode` no supere al instalado. No
hay que llevarlo a mano: Tauri lo calcula a partir de `version` en
`src-tauri/tauri.conf.json` como `mayor × 1 000 000 + menor × 1 000 + parche`
(0.3.0 → 3000, 0.4.0 → 4000). La única disciplina que exige es la que ya tiene
el proyecto: **subir la versión en cada publicación**.

## Consecuencias

**Lo que se gana.** Un supervisor instala SICOT descargando un archivo, sin
compilar nada, igual que el instalador de escritorio. Y el mismo APK sirve para
cualquier Centro, porque la dirección del servidor se elige en la instalación.

**Lo que se asume.** La instalación exige permitir fuentes desconocidas, y Play
Protect puede avisar de una aplicación que no conoce. Se explica en
`INSTALACION.md`; es el mismo tipo de aviso que el de «editor desconocido» del
escritorio.

**Lo que queda prohibido.** Publicar un APK firmado con otra llave: los
teléfonos que tuvieran el anterior no podrían actualizarlo. Y versionar la
llave, su contraseña o cualquier fichero `key.properties` o `keystore.properties`
(`gen/android/.gitignore` ya los ignora por ese motivo).

## Cuándo revisar

- Si el SENA decide publicar en Google Play: Play gestiona su propia llave de
  distribución y esta pasaría a ser la «llave de subida». Se puede migrar sin
  romper las instalaciones existentes, pero hay que hacerlo con Play App Signing
  desde el principio.
- Si cambia quién custodia la copia fuera de línea. Este ADR debe decir siempre
  quién la tiene.
