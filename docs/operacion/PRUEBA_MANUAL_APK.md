# Prueba manual del APK antes de publicar una versión

**Por qué existe este documento, y por qué es manual.** Las pruebas de extremo a
extremo del CI corren en un navegador con tamaño de teléfono. Comprueban que la
interfaz cabe y que las tareas funcionan en el navegador. Lo que **no** pueden
ver es lo que hace distinto el envoltorio de Android, y el 18 de septiembre de
2026 cinco de doce tareas del supervisor estaban rotas justo ahí, cuatro de ellas
sin ningún mensaje ([auditoría, apéndice del 18](../AUDITORIA_MOVIL_2026-09-16.md)).

Esta lista es la forma de no volver a descubrirlo en el teléfono de un
supervisor. Está escrita como lo que es —una comprobación a mano— y no disfrazada
de prueba automática.

Se hace con **el APK firmado de la versión**, descargado de GitHub, no con uno
compilado en la máquina: es lo que va a instalar el supervisor.

## Preparación

- Un teléfono Android con bloqueo de pantalla. Un emulador sirve, pero con la GPU
  del anfitrión (`-gpu host`): con renderizado por software aparecen ANR que no
  son de la aplicación.
- Un servidor de SICOT montado con Caddy (ADR-009) y su raíz instalada en el
  teléfono, como describe `INSTALACION.md`. Con el `.env` **de producción**, el
  que tiene `CORS_ALLOWED_ORIGINS` con la dirección real y no el de desarrollo:
  los valores por defecto de desarrollo aceptan orígenes que un servidor no
  tiene, y una prueba contra ellos no vería un rechazo CORS al APK mientras el
  navegador sigue funcionando.
- El supervisor con firma electrónica asignada y un contrato con documentos.
- Para el 8 al 10, el contrato en el Paso 3 (los sub-pasos 3.1 y 3.2 son los que
  piden fotos de la entrega) y, si se quiere ver la ubicación en la evidencia, la
  ubicación activada en la cámara del teléfono.
- Para el 11 y el 12, el Copiloto (Ollama) funcionando en el servidor y un
  documento con observaciones pendiente de firmar, por ejemplo el Informe de
  Supervisión (3.4): son las notas que el Copiloto redacta.

## La lista

| # | Qué hacer | Qué tiene que pasar |
| --- | --- | --- |
| 1 | Instalar el APK encima de la versión anterior | Se instala sin desinstalar y conserva la dirección del servidor |
| 2 | Escribir una dirección `http://` en Servidor | Aviso de que Android bloquea las conexiones sin cifrar, **antes** de intentar entrar. Con el campo vacío, el pie dice qué dirección se usaría y que en el teléfono es el propio teléfono |
| 3 | Entrar con la dirección `https://` del Centro | Abre la bandeja del supervisor |
| 4 | Documentos → «Descargar» sobre un acta firmada | Se abre «Guardar como» del sistema; el PDF queda en Descargas y se abre en el visor; SICOT dice que quedó guardado |
| 5 | Registros → «Descargar registros (CSV)» | Igual que el 4, con un CSV que abre en una hoja de cálculo |
| 6 | Escribir en el campo del copiloto | El teclado **no** tapa el campo, el botón de enviar ni las sugerencias |
| 7 | Preguntar al copiloto, salir a otra aplicación un par de minutos y volver | Si la respuesta no llegó, SICOT dice que se cortó por salir de la aplicación y vuelve a preguntar solo; la respuesta aparece |
| 8 | En el sub-paso 3.1 o 3.2, «Tomar foto de la entrega» | Se abre directamente la cámara, **no** la galería. Al cargar la foto, la evidencia dice cuándo se tomó y, si la cámara guarda la ubicación, dónde |
| 9 | Repetir el 8 sin permiso de cámara: negarlo cuando Android lo pida, o quitarlo en Ajustes › Aplicaciones › SICOT › Permisos | SICOT dice que no se tomó ninguna foto, cómo activar el permiso y que puede usar «Elegir una foto» |
| 10 | En el mismo sub-paso, «Elegir una foto» | Se abre el selector del sistema y la foto queda cargada; si llegó sin ubicación, SICOT lo dice en vez de omitirlo |
| 11 | «Firmar documento» en el documento con observaciones y, mientras el Copiloto redacta, salir a otra aplicación o apagar la pantalla un par de minutos | Al volver, SICOT dice que la conexión se cortó por salir de la aplicación —no culpa al Copiloto ni al servidor— y no firma nada por su cuenta: el sub-paso sigue abierto |
| 12 | Repetir el 11 sin salir de la aplicación | Antes de firmar aparece «Revisar antes de firmar» con la redacción y lo que cambió frente a las notas; solo firma al pulsar «Firmar», y «Cancelar» deja el borrador sin firmar |
| 13 | Contrato → botón «atrás» del sistema | Vuelve a la vista anterior; en la pantalla de acceso, sale de la aplicación |
| 14 | Como Gestión: «Cargar nueva ficha» y elegir un PDF | Se abre el selector del sistema y el archivo aparece en SICOT |
| 15 | Como Administración: eliminar un formato | Diálogo de confirmación del sistema; al aceptar, el formato desaparece |
| 16 | Cerrar sesión | Vuelve a la pantalla de acceso |
| 17 | Preguntar al copiloto y dejar el teléfono quieto, sin tocarlo, más que el apagado automático de pantalla | La pantalla sigue encendida hasta que llega la respuesta y después se apaga como siempre. Si se apagó, anotarlo (ese WebView no permite mantenerla encendida): al desbloquear, SICOT dice que se cortó y vuelve a preguntar solo; la respuesta aparece |

El 8 vigila algo que se rompe sin ruido: desde Android 11, la aplicación solo
ve la cámara si el manifiesto declara la consulta `IMAGE_CAPTURE` en
`<queries>`, además del permiso `CAMERA`. Sin ella, «Tomar foto» abre la
galería sin avisar y la foto llega sin ubicación (lo que pasó el 23-09-2026).
Una regeneración con `tauri android init` o un cambio de wry pueden quitar esa
declaración, y ninguna prueba automática lo vería.

Si algo de la tabla no pasa, la versión no se publica hasta saber por qué.
