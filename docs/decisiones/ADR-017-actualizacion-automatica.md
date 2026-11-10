# ADR-017 — Que el servidor y la aplicación se actualicen solos

**Estado:** Aceptada, ejecutada el 10 de noviembre de 2026 · **Fecha:** 10 de noviembre de 2026

## Contexto

Hasta esta decisión, poner SICOT al día eran tres trabajos manuales, cada uno
dependiente de una persona concreta:

1. **El servidor del Centro.** Alguien entraba, hacía `git pull` y
   `docker compose up -d --build`. Medido el 10-11-2026 contra el servidor real:
   servía la **0.8.0**, compilada el 5 de noviembre a las 20:40 UTC, mientras la
   0.9.0 llevaba tres días publicada. Nadie lo había notado, porque nada lo
   dice: la pantalla de acceso muestra la versión, pero hay que ir a mirarla.
2. **El instalador de escritorio.** Se compilaba en el portátil de quien
   desarrolla y se subía a mano a la versión de GitHub. Se ve en quién adjuntó
   cada archivo de la 0.9.0: el APK lo subió `github-actions[bot]`; el `.exe` y
   el `.msi`, una persona.
3. **La instalación de cada supervisor.** Había que llevarle el instalador y que
   lo ejecutara encima del anterior.

El desfase del punto 3 no es cosmético: **el servidor y la instalación tienen
que ir juntos.** Desde la 0.6.0 el sub-paso 2.4 no se cierra sin una fecha de
entrega registrada, y esa pantalla solo existe en la 0.6.0; la 0.9.0 trae
«Cambiar contraseña», que es lo primero que necesita un supervisor invitado. Un
supervisor con una versión vieja contra un servidor nuevo se queda sin poder
trabajar **y sin ninguna forma de saber por qué**.

Y hay una restricción que descarta la mitad de las respuestas posibles: el
servidor del Centro **no tiene puertos abiertos a Internet**. Se publica por un
túnel de Cloudflare, que es una conexión saliente (ADR-009). Cualquier
despliegue empujado desde fuera —SSH, un webhook, un runner con credenciales—
exige abrirle una entrada y guardar una llave suya fuera de él, que es
exactamente lo que el túnel evitó.

## Decisión

Se adoptan **tres mecanismos separados**, uno por cada trabajo manual, y los
tres con la misma regla: **nada se actualiza sin poder deshacerse.**

### 1. El servidor jala; nadie empuja hacia él

Un temporizador de systemd (`sicot-auto-actualizar.timer`) corre
`scripts/auto-actualizar-sicot.sh` cada diez minutos. El script mira si `master`
avanzó en GitHub y, si avanzó: respalda, trae el commit, reconstruye y espera a
que los contenedores queden **sanos**. Si no quedan sanos en el plazo, vuelve al
commit anterior y lo reconstruye.

Por qué así:

- **No entra nada al servidor.** Es él quien pregunta, igual que el túnel. No
  hay llave de este servidor guardada en ningún otro sitio.
- **Sigue `master`, no `develop`.** `master` es lo que el CI dejó en verde y lo
  que `develop → master` declara desplegable (CONTRIBUTING.md). Seguir `develop`
  llevaría al Centro cada integración intermedia.
- **No se actualiza sin respaldo.** Si `scripts/respaldo-sicot.sh` falla, el
  despliegue se aborta: Flyway no deshace una migración, así que el respaldo es
  la única vuelta atrás de los datos (ADR-002).
- **La vuelta atrás reconstruye.** No basta `docker compose up` con las imágenes
  nuevas ya construidas: hay que volver al commit anterior y reconstruirlo.
- **Lo que falla, lo cuenta él.** Si el despliegue no se puede completar, el
  script abre una incidencia en GitHub cuando el servidor tiene `gh`
  autenticado. Es el único canal de aviso que no exige abrirle una entrada.
- **Lo desplegado se anota; no se deduce del clon.** Lo que decide si hay algo
  que desplegar es desde qué commit se construyeron las imágenes que corren, y
  eso no es el commit en el que está el clon: son dos cosas que se separan justo
  en el peor momento. La primera versión de este script comparaba `HEAD` contra
  `origin/master`, y como el instalador deja el clon en `master` antes de
  arrancar el servicio, la primera corrida concluía «ya está al día» y el Centro
  se quedaba con las imágenes viejas, sin una línea en el registro. Lo encontró
  el Claude del propio servidor al ir a instalarlo, antes de instalar nada.
- **Un commit que falló no se reintenta solo.** Sin esa compuerta, un `master`
  roto disparaba respaldo, reconstrucción, fallo y vuelta atrás cada diez
  minutos, indefinidamente, sobre el servidor que el Centro está usando. Se
  reintenta cuando llega otro commit —que es lo que de verdad arregla algo— o a
  mano con `SICOT_FORZAR=1`.
- **El servicio corre como root sobre un clon de otro usuario, y git se niega a
  eso** desde la 2.35.2 («detected dubious ownership»). Se declara
  `safe.directory` en las llamadas de estos dos scripts y en ningún sitio más:
  tocar la configuración global del servidor dejaría la instalación dependiendo
  de que alguien se acordara de hacerlo, y el fallo volvería en el próximo
  servidor.

### 2. El instalador de escritorio se compila al publicar una etiqueta

`.github/workflows/publicar-escritorio.yml` compila el instalador en
`windows-latest` desde la etiqueta, lo **firma** con la llave del actualizador,
crea la versión de GitHub si no existe y adjunta el `.exe`, el `.msi`, la firma
y el `latest.json`. Empujar una etiqueta `v*` es la única orden que hace falta
para publicar.

Y esa orden se puede dar **desde Actions**, sin un clon del repositorio: al
dispararlo a mano con una versión, el flujo crea la etiqueta sobre la cabeza de
`master`. Publicar deja de ser un comando y pasa a ser un campo y un botón, que
para un proyecto que mantiene una sola persona con relevos previstos a tres o
cuatro años importa más que la elegancia: «acuérdese de anotar la etiqueta y
empujarla» es la clase de paso que se pierde en un relevo, y cuando se pierde,
el Centro se queda sin la versión que ya estaba lista.

**La compuerta que hace que eso sea seguro** es que la etiqueta no se crea a
ciegas: antes de crearla, el flujo lee la versión que declara `tauri.conf.json`
en la cabeza de `master` y la compara con la que se pidió; si no coinciden, se
detiene sin crear nada. Una etiqueta no se puede mover sin romper lo que ya la
haya descargado, así que una equivocada es un error que se arrastra. La
comprobación posterior, ya con el repositorio descargado y mirando también
`Cargo.toml`, sigue en su sitio: aquella evita publicar un instalador que
anuncia otra versión, esta evita dejar una etiqueta inservible.

### 3. La aplicación de escritorio se actualiza sola; la de Android avisa

El plugin `updater` de Tauri consulta el `latest.json` de la última versión
publicada, **comprueba la firma** contra la clave pública escrita en
`tauri.conf.json` e instala. Sin firma válida no instala nada: ni un instalador
subido por otra persona ni una descarga interceptada se aplican.

En Android **no se instala nada automáticamente**, y no por falta de ganas:
Android solo permite instalar un APK con la autorización explícita de quien
tiene el teléfono, y el actualizador de Tauri no existe para esa plataforma.
Allí la aplicación compara su versión con el `latest.json` y dice que hay una
nueva, con la dirección para bajarla. Prometer una actualización que el sistema
va a bloquear sería peor que no decir nada.

## Consecuencias

- Un merge a `master` llega al Centro en unos diez minutos, sin que nadie entre
  al servidor.
- Publicar una versión es empujar una etiqueta. El APK sigue en su propio flujo
  (ADR-013) porque necesita Linux y el SDK de Android; GitHub no deja que un
  flujo lanzado con el token por omisión dispare otro, así que ese se pide
  desde Actions o se dispara aparte.
- **Hay dos llaves de firma, y no se pueden mezclar.**
  `SICOT_ANDROID_KEYSTORE_*` decide si un APK puede reemplazar a otro (ADR-013);
  `TAURI_SIGNING_PRIVATE_KEY` decide si una actualización de escritorio se
  acepta. Si se pierde la segunda, las instalaciones existentes dejan de poder
  actualizarse solas y hay que instalarlas una vez a mano.
- **Las instalaciones que hoy tienen la 0.9.0 no se actualizan solas**: esa
  versión no lleva el actualizador dentro. Hay que instalar **una última vez a
  mano** la primera versión que lo traiga (la 0.10.0); desde ahí, nunca más.
- **El actualizador va sin la feature `zip`.** Viene por omisión y arrastra el
  crate `zip`, que el 10-11-2026 traía una vulnerabilidad crítica y una alta sin
  arreglo dentro del rango que el plugin acepta; lo paró el escaneo del CI. Se
  puede apagar porque SICOT no la usa: esa feature sirve para abrir un artefacto
  comprimido, y el de escritorio es el instalador NSIS tal cual. Lo que sí hace
  falta queda declarado a mano (`rustls-tls` para la descarga y `system-proxy`
  para respetar el proxy del Centro). Si el artefacto pasara a ser un `.zip`,
  hay que volver a encenderla y comprobar antes que el crate ya esté corregido.
- **Las versiones del paquete npm y del crate de Rust de cada plugin tienen que
  ir en el mismo minor**, y Tauri lo comprueba antes de compilar. Por eso los
  tres paquetes del actualizador van fijados con `~` y no con `^`: un `npm
  install` que subiera solo la mitad de JavaScript deja la compilación de
  Android y la de escritorio sin arrancar, con un error que no menciona
  `package.json`.
- El disco del servidor ya no se llena de imágenes viejas: cada despliegue que
  queda sano hace `docker image prune`. Un disco lleno tumba PostgreSQL, y eso
  sí sería una caída de verdad.

## Lo que se rechazó, y por qué

- **Un runner propio de GitHub Actions en el servidor.** Funciona y no abre
  puertos, pero añade un proceso con credenciales del repositorio corriendo
  permanentemente en el servidor del Centro, para hacer lo que un `git fetch`
  cada diez minutos ya hace.
- **SSH por el túnel con la llave en los secretos de GitHub.** Guarda una llave
  del servidor fuera del servidor. La restricción del contexto es justo esa.
- **Un actualizador de escritorio sin firma.** Tauri no lo permite, y hace bien:
  sería ejecutar en el equipo de un funcionario del SENA lo que cualquiera que
  pudiera interponerse en la descarga le mandara.
- **Desplegar en cada push a `develop`.** Llevaría al Centro código que todavía
  no pasó por la compuerta de `develop → master`.
- **Avisar por correo de cada despliegue.** El correo del servidor sirve para
  las invitaciones a los supervisores (`MAIL_*`); un despliegue correcto no es
  noticia, y uno fallido ya abre una incidencia.

## Cuándo revisar esta decisión

- **Si el Centro pasa a tener más de un servidor.** El temporizador por host
  deja de ser suficiente: habría que coordinar que no se actualicen a la vez.
- **Si la ventana de diez minutos molesta.** Un merge a mediodía reconstruye
  imágenes mientras alguien trabaja. Hoy la reconstrucción no interrumpe el
  servicio (los contenedores viejos siguen en pie hasta que los nuevos están
  listos), pero si llegara a notarse, lo siguiente es una ventana horaria.
- **Si alguna vez hay que desplegar algo que no esté en `master`.** Hoy eso
  exige entrar al servidor, y el script respeta y guarda lo que encuentre
  cambiado (`git stash`), pero el temporizador lo volvería a reemplazar en la
  siguiente corrida.
