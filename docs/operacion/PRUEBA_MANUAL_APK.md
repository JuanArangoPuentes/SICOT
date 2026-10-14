# Prueba manual del APK antes de publicar una versión

**Por qué existe este documento, y por qué es manual.** Las pruebas de extremo a
extremo del CI corren en un navegador con tamaño de teléfono. Comprueban que la
interfaz cabe y que las tareas funcionan en el navegador. Lo que **no** pueden
ver es lo que hace distinto el envoltorio de Android, y el 18 de septiembre de
2026 cinco de doce tareas del supervisor estaban rotas justo ahí, cuatro de ellas
sin ningún mensaje ([auditoría, apéndice del 18](../historico/AUDITORIA_MOVIL_2026-09-16.md)).

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
  documento con observaciones pendiente de firmar. El único candidato es el
  Informe de Supervisión (3.4) —son las notas que el Copiloto redacta—, y para
  llegar a él hay que **cerrar antes el sub-paso 3.3** del contrato con el que se
  prueba. Decirlo importa: es lo que ha dejado estos dos puntos sin recorrer en
  los tres recorridos, porque cerrar un sub-paso es un cambio permanente sobre
  los datos de ese contrato y no se hace para pasar una prueba.

## La lista

**La columna «Resultado» se rellena al recorrerla, con la fecha.** La fecha no es
decoración: un resultado vale para el código que había ese día, así que cuando
una parte de la aplicación cambia después, su punto vuelve a quedar en el aire y
se recorre otra vez. Un punto sin fecha es un punto que nadie ha comprobado,
aunque el código parezca correcto.

Los resultados de abajo son los de los recorridos del **7, 8 y 14 de octubre de
2026**, hechos sobre un emulador Android 14 con el APK de publicación `x86_64`
firmado con el almacén de depuración, detrás de Caddy, tal como describe la
sección siguiente. Están informados día por día en MDL-247. **A 14-10-2026: 14
pasan y 3 siguen sin recorrer** — el 1, porque exige la llave real de
publicación, y el 11 y el 12, porque exigen cerrar antes el sub-paso 3.3. Ningún
punto queda a medias.

> **Corrección del 14-10-2026 a la cuenta anterior.** Aquí decía «12 pasan, 1
> pasa a medias y 4 siguen sin recorrer», y el punto 17 figuraba «Sin recorrer».
> Las dos cosas eran el mismo error arrastrado: el informe del 8 de octubre sumó
> mal sus propios pasos —dijo 12 cuando eran 13— y la revisión del 12 resolvió el
> descuadre quitándole el 17, que ese mismo informe reporta recorrido y con su
> medida (83,9 s). Lo que faltaba era un punto en la suma, no un punto por
> recorrer.

| # | Qué hacer | Qué tiene que pasar | Resultado |
| --- | --- | --- | --- |
| 1 | Instalar el APK encima de la versión anterior | Se instala sin desinstalar y conserva la dirección del servidor | Sin recorrer — exige la llave real de publicación. El 14-10 se instaló encima con el almacén de **depuración** y la dirección del servidor se conservó: es indicio, no el punto |
| 2 | Escribir una dirección `http://` en Servidor | Aviso de que Android bloquea las conexiones sin cifrar, **antes** de intentar entrar. Con el campo vacío, el pie dice qué dirección se usaría y que en el teléfono es el propio teléfono | Pasa · 07-10-2026 |
| 3 | Entrar con la dirección `https://` del Centro | Abre la bandeja del supervisor | Pasa · 14-10-2026 |
| 4 | Documentos → «Descargar» sobre un acta firmada | Se abre «Guardar como» del sistema; el PDF queda en Descargas y se abre en el visor; SICOT dice que quedó guardado | Pasa · 07-10-2026 |
| 5 | Registros → «Descargar registros (CSV)» | Igual que el 4, con un CSV que abre en una hoja de cálculo | Pasa · 07-10-2026 |
| 6 | Escribir en el campo del copiloto | El teclado **no** tapa el campo, el botón de enviar ni las sugerencias | Pasa · 14-10-2026 — recorrido otra vez sobre el APK reconstruido de ese día, que es lo que la marca «a recomprobar» pedía |
| 7 | Preguntar al copiloto, salir a otra aplicación un par de minutos y volver | Si la respuesta no llegó, SICOT dice que se cortó por salir de la aplicación y vuelve a preguntar solo; la respuesta aparece | Pasa · 08-10-2026 |
| 8 | En el sub-paso 3.1 o 3.2, «Tomar foto de la entrega» | Se abre directamente la cámara, **no** la galería. Al cargar la foto, la evidencia dice cuándo se tomó y, si la cámara guarda la ubicación, dónde | Pasa · 08-10-2026 — la cámara se abrió de verdad, confirmado con el `mCurrentFocus` del sistema |
| 9 | Repetir el 8 sin permiso de cámara: negarlo cuando Android lo pida, o quitarlo en Ajustes › Aplicaciones › SICOT › Permisos | SICOT dice que no se tomó ninguna foto, cómo activar el permiso y que puede usar «Elegir una foto» | Pasa · 08-10-2026 |
| 10 | En el mismo sub-paso, «Elegir una foto» | Se abre el selector del sistema y la foto queda cargada; si llegó sin ubicación, SICOT lo dice en vez de omitirlo | Pasa · 08-10-2026 |
| 11 | «Firmar documento» en el documento con observaciones y, mientras el Copiloto redacta, salir a otra aplicación o apagar la pantalla un par de minutos | Al volver, SICOT dice que la conexión se cortó por salir de la aplicación —no culpa al Copiloto ni al servidor— y no firma nada por su cuenta: el sub-paso sigue abierto | Sin recorrer — exige el Informe de Supervisión (3.4) pendiente de firmar |
| 12 | Repetir el 11 sin salir de la aplicación | Antes de firmar aparece «Revisar antes de firmar» con la redacción y lo que cambió frente a las notas; solo firma al pulsar «Firmar», y «Cancelar» deja el borrador sin firmar | Sin recorrer — mismo requisito que el 11 |
| 13 | Contrato → botón «atrás» del sistema | Vuelve a la vista anterior; en la pantalla de acceso, sale de la aplicación | Pasa · 14-10-2026 — las dos mitades: vuelve a la vista anterior, y en la pantalla de acceso sale de la aplicación **también después de cerrar sesión** |
| 14 | Como Gestión: «Cargar nueva ficha» y elegir un PDF | Se abre el selector del sistema y el archivo aparece en SICOT | Pasa · 08-10-2026 — el 14-10 se recomprobó solo que el selector del sistema se abre |
| 15 | Como Administración: eliminar un formato | Diálogo de confirmación del sistema; al aceptar, el formato desaparece | Pasa · 14-10-2026 — con el GCCON-F-031 real (881 kB) cargado para poder borrarlo; el catálogo queda como estaba |
| 16 | Cerrar sesión | Vuelve a la pantalla de acceso | Pasa · 14-10-2026 |
| 17 | Preguntar al copiloto y dejar el teléfono quieto, sin tocarlo, más que el apagado automático de pantalla | La pantalla sigue encendida hasta que llega la respuesta y después se apaga como siempre. Si se apagó, anotarlo (ese WebView no permite mantenerla encendida): al desbloquear, SICOT dice que se cortó y vuelve a preguntar solo; la respuesta aparece | Pasa · 08-10-2026 — la pantalla **sí** se apagó, que es el caso que el punto anticipa; la consulta terminó en el servidor (83,9 s) y al desbloquear la respuesta estaba ahí, referida al sub-paso 3.4 |

El 8 vigila algo que se rompe sin ruido: desde Android 11, la aplicación solo
ve la cámara si el manifiesto declara la consulta `IMAGE_CAPTURE` en
`<queries>`, además del permiso `CAMERA`. Sin ella, «Tomar foto» abre la
galería sin avisar y la foto llega sin ubicación (lo que pasó el 23-09-2026).
Una regeneración con `tauri android init` o un cambio de wry pueden quitar esa
declaración, y ninguna prueba automática lo vería.

Si algo de la tabla no pasa, la versión no se publica hasta saber por qué. Un
punto «sin recorrer» tampoco es un punto que pase: la compuerta está cumplida
cuando los 17 tienen resultado y fecha.

## Fallos conocidos, abiertos

Lo que el recorrido encontró y todavía no se puede dar por cerrado. Queda escrito
aquí porque hasta ahora solo existía en el informe del día, y un fallo que solo
vive en un informe se pierde.

- **El cargador de fichas de Gestión y el de formatos abren el diálogo del
  sistema, y sus botones salen en el idioma del teléfono.** El texto de
  «Cancelar / Aceptar» de un `confirm()` y los rótulos del selector de archivos
  los escribe Android, no SICOT: en el emulador, configurado en inglés, salen
  `CANCEL` y `OK` mientras el resto de la pantalla está en español. En el
  teléfono de un supervisor, configurado en español, saldrán en español. No es
  un fallo de la aplicación y no se puede arreglar desde ella; se deja escrito
  para que quien recorra la lista en un equipo en inglés no lo reporte como uno.
  Observado el 14-10-2026.

### Cerrados, con la fecha en la que se comprobaron sobre el APK

- **El botón «atrás» después de cerrar sesión** — dejaba el punto 13 a medias.
  Cada pulsación se consumía navegando a una ruta del panel que el guardia de
  sesión devolvía a `/login`, sin que cambiara nada en pantalla. La causa es que
  cerrar sesión solo reemplazaba la entrada de arriba del historial y dejaba
  debajo una por cada vista visitada. **Arreglado y comprobado en el APK el
  14-10-2026**: con tres vistas apiladas, una sola pulsación cierra la
  aplicación. Observado el 08-10-2026.
- **El cargador de formatos de Administración usaba el selector nativo**, con su
  `Choose File / No file chosen` en inglés y sin el estilo del resto de la
  aplicación. El campo quedó oculto y lo dispara un botón del proyecto («Elegir
  archivo» / «Cambiar archivo») con el nombre del archivo al lado, el mismo
  patrón del cargador de fichas de Gestión. **Arreglado y comprobado en el APK el
  14-10-2026**, cargando el GCCON-F-031 real. Observado el 08-10-2026 al preparar
  el punto 15.

### Corrección del 14-10-2026 sobre los tres arreglos del 08-10

Aquí decía que `3ae6cb7`, `c2ed895` y `ec55172` «siguen sin recomprobar sobre el
APK» porque «el último recorrido con el APK reconstruido es el del 7 de
octubre». **No es cierto, y se puede comprobar por las fechas**: los tres
commits son de las 14:21, 14:45 y 14:46 del 08-10-2026, y el APK de ese día
—`app-x86_64-release.apk`— se construyó a las 14:49, después de los tres. El
informe del 8 de octubre decía lo correcto; esta nota se escribió el 13 a partir
de una revisión que no miró esa fecha.

De todas formas, dos de los tres volvieron a verse en el APK del 14-10-2026:
`3ae6cb7` (la lista del Copiloto abre por el último mensaje, no por el primero) y
`c2ed895` (el aviso «El nombre del formato es obligatorio» desaparece en cuanto
se escribe el nombre). `ec55172` («Ir a firmar» mueve la pantalla hasta el
sub-paso) **no** se recorrió el 14 y se sostiene en el recorrido del 8.

### Lo que el recorrido del 14-10-2026 no cubrió

- **El punto 7** se sostiene en el recorrido del 08-10. Ese día se midió con una
  pregunta que fue al modelo y tardó 80 s; el 14 las preguntas de prueba las
  resolvió la vía determinista al instante, así que el caso que el punto vigila
  —que la respuesta NO haya llegado al volver— no llegó a darse. Lo que sí se vio
  el 14 es que al volver a la aplicación la conversación seguía completa. El
  cambio de este día toca el historial de navegación y la sesión, no la espera
  del Copiloto.
- **El punto 17** se sostiene en el recorrido del 08-10, por lo mismo.

## Cómo montar el emulador, si no hay teléfono a mano

La preparación de arriba pide un servidor con Caddy y su raíz instalada en el
teléfono. Esto es lo mismo sobre un emulador, comprobado el 07-10-2026; no
reemplaza la prueba en un teléfono real (lo que un emulador no puede imitar
está en la nota del punto 8), pero deja correr la mayor parte de la tabla sin
depender de tener uno.

El emulador alcanza el equipo anfitrión en `10.0.2.2`. Caddy necesita un
NOMBRE y no esa IP: para un sitio declarado por IP tiene que reconocerla en la
dirección local de la conexión, y detrás del NAT de Docker eso no ocurre —el
saludo TLS se cae sin certificado—. Con un nombre, el emulador lo resuelve por
su fichero `hosts`.

```bash
# 1. El proxy, contra la pila de desarrollo ya levantada.
docker run -d --name sicot-apk-proxy --network sicot_default -p 443:443 -p 80:80   -e SICOT_DOMINIO=sicot.centro.test -e SICOT_TLS=internal   -v "$PWD/Caddyfile:/etc/caddy/Caddyfile:ro" caddy:2.11.4-alpine

# 2. El emulador, con la partición de sistema escribible para poder
#    tocar `hosts`. `-gpu host` es lo que pide la preparación.
emulator -avd <nombre> -gpu host -writable-system -no-boot-anim

# 3. El nombre del servidor, dentro del emulador.
adb root && adb remount && adb reboot && adb root && adb remount
adb shell 'echo "10.0.2.2 sicot.centro.test" >> /system/etc/hosts'

# 4. La raíz de Caddy, como autoridad instalada por el usuario — que es como
#    la tendría el teléfono de un supervisor, y lo que el APK de publicación
#    acepta (ver app/src/main/res/xml/network_security_config.xml).
docker cp sicot-apk-proxy:/data/caddy/pki/authorities/local/root.crt ./caddy-root.crt
adb shell locksettings set-pin 1234     # Android exige bloqueo de pantalla
H=$(openssl x509 -inform PEM -subject_hash_old -in caddy-root.crt -noout)
adb push caddy-root.crt /data/local/tmp/
adb shell "mkdir -p /data/misc/user/0/cacerts-added   && cp /data/local/tmp/caddy-root.crt /data/misc/user/0/cacerts-added/$H.0   && chmod 644 /data/misc/user/0/cacerts-added/$H.0   && chown system:system /data/misc/user/0/cacerts-added/$H.0"
```

Después, en Configuración › Servidor del APK: `https://sicot.centro.test`.

Dos avisos sobre el APK que se instala:

- Los emuladores corrientes son `x86_64` y el APK que publica el CI es
  `aarch64`: hay que compilar `--target x86_64`. En Windows, si el paso de
  enlaces simbólicos falla, la vuelta está en `frontend/README.md`.
- Sin las variables de firma, la compilación de publicación sale **sin firmar**
  y no se instala (ADR-013). Para la prueba sirve firmarla con el almacén de
  depuración del SDK; lo que esa firma no permite comprobar es el punto 1,
  instalar encima de la versión anterior publicada, que exige la llave real.
