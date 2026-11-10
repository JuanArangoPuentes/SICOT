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

### 2. El instalador de escritorio se compila al publicar una etiqueta

`.github/workflows/publicar-escritorio.yml` compila el instalador en
`windows-latest` desde la etiqueta, lo **firma** con la llave del actualizador,
crea la versión de GitHub si no existe y adjunta el `.exe`, el `.msi`, la firma
y el `latest.json`. Empujar una etiqueta `v*` es la única orden que hace falta
para publicar.

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
