# Actualización automática

Cómo se pone SICOT al día sin que nadie entre al servidor ni le lleve el
instalador a cada supervisor. El *por qué* de cada pieza está en
[ADR-017](../decisiones/ADR-017-actualizacion-automatica.md); aquí está el *cómo*.

| Qué | Quién lo hace | Cada cuánto |
| --- | --- | --- |
| El servidor del Centro | `sicot-auto-actualizar.timer` en el propio servidor | 10 min, siguiendo `master` |
| El instalador y el APK de una versión | el flujo «Publicar escritorio» al empujar una etiqueta `v*` | al publicar |
| La aplicación de escritorio del supervisor | ella misma, al abrirla | al abrir |
| La aplicación de Android | avisa; instala el supervisor | al abrir |

## El servidor

### Instalarlo (una sola vez, en el servidor)

```bash
cd /ruta/al/clon/de/SICOT
sudo ./scripts/instalar-auto-actualizacion.sh
```

**Si para conseguir este script hubo que traer `master` al clon**, entonces el
commit del clon ya no dice qué construyó las imágenes que están corriendo, y hay
que decírselo. Volver el clon atrás con `git reset` no sirve: en ese commit
anterior el script todavía no existe.

```bash
sudo SICOT_DESPLEGADO=<commit que construyó las imágenes> ./scripts/instalar-auto-actualizacion.sh
```

Para averiguar ese commit, la fecha de construcción de las imágenes
(`docker image inspect`) contra el historial de la rama.

Deja el clon en `master`, instala las dos unidades de systemd, enciende el
temporizador y hace el primer despliegue. Es idempotente: volver a correrlo
sirve para mover el clon de sitio o cambiar la rama (`SICOT_RAMA=...`).

### Qué hace en cada corrida

1. `git fetch`. Compara `master` con el commit anotado en `.ultimo-despliegue`
   —no con el commit del clon, que puede no ser lo que construyó las imágenes
   que corren— y, si no hay nada nuevo, termina sin escribir nada. Tampoco
   reintenta un commit anotado en `.ultimo-fallo`.
2. Respalda con `scripts/respaldo-sicot.sh`. **Si el respaldo falla, no
   actualiza**: Flyway no deshace una migración.
3. Trae el commit (lo que esté cambiado en el servidor se guarda en `git stash`,
   no se pierde) y `docker compose up -d --build --remove-orphans`.
4. Espera a que `sicot-backend` y `sicot-frontend` queden sanos.
5. Si quedan sanos: limpia las imágenes viejas y anota el commit en
   `.ultimo-despliegue`. **Si no**: vuelve al commit anterior, lo reconstruye y
   abre una incidencia en GitHub (si el servidor tiene `gh` autenticado).

### Mirarlo

```bash
tail -f /var/log/sicot-auto-actualizar.log
```

Lo escribe root, pero queda con el grupo del clon para que lo lea su dueño sin
`sudo` — y con él, una sesión de Claude que corra en el servidor como ese
usuario, que es quien diagnostica cuando no hay nadie en el Centro. Sigue en
`0640`: lleva la salida de `docker compose build` y del respaldo, y no hay
motivo para abrirlo al resto del servidor. Si da «Permiso denegado», el
instalador se corrió antes de este cambio: vuelva a correrlo, es idempotente.

```bash
systemctl list-timers sicot-auto-actualizar.timer
```

Una corrida sin novedad no escribe en el registro, así que un registro quieto
significa «no hubo versiones nuevas», no «está roto». Para comprobar que el
mecanismo vive, mire el temporizador.

### Pararlo, forzarlo, desinstalarlo

```bash
sudo systemctl stop sicot-auto-actualizar.timer
```

```bash
sudo SICOT_FORZAR=1 ./scripts/auto-actualizar-sicot.sh
```

`SICOT_FORZAR=1` es también la forma de reintentar un commit que ya falló: el
temporizador no lo reintenta solo, a propósito.

Dos cosas que conviene saber al leer el registro después de una vuelta atrás: la
vuelta atrás revierte **también estos scripts**, así que la corrida siguiente es
la del script de la versión anterior; y el commit que falló queda anotado en
`.ultimo-fallo`, que es lo que hace que no se reintente.

```bash
sudo systemctl disable --now sicot-auto-actualizar.timer
sudo rm /etc/systemd/system/sicot-auto-actualizar.{service,timer}
sudo systemctl daemon-reload
```

### Comprobar desde fuera qué versión está sirviendo

Sin entrar al servidor, desde cualquier sitio:

```bash
curl -s https://sicot.medialab-sena.com/actuator/health
```

La versión que sirve se ve en la pantalla de acceso («SICOT v0.10.0»), y
también en el propio paquete que descarga el navegador.

## Publicar una versión

1. Suba la versión en `frontend/src-tauri/tauri.conf.json` y en
   `frontend/src-tauri/Cargo.toml` (las dos: el flujo se detiene si no coinciden
   con la etiqueta).
2. Escriba las notas en `docs/versiones/vX.Y.Z.md`.
3. Mergee a `master`.
4. Dispare la publicación, de una de estas dos maneras:

   - **Desde Actions**, sin necesidad de un clon: *Publicar escritorio → Run
     workflow*, y escriba la versión (`v0.10.0`). Si la etiqueta no existe, el
     flujo la crea sobre la cabeza de `master` — pero solo si la versión que
     `master` declara coincide con la que se pidió; si no, se detiene sin crear
     nada y dice qué falta subir.
   - **Empujando la etiqueta**, que es lo mismo por el otro extremo:

```bash
git tag -a v0.10.0 -m "v0.10.0" && git push origin v0.10.0
```

El flujo «Publicar escritorio» compila el instalador en Windows, lo firma, crea
la versión de GitHub si no existe y adjunta el `.exe`, el `.msi`, la firma y el
`latest.json`. El APK firmado lo adjunta «Publicar APK»; si no arrancó solo,
se pide en *Actions → Publicar APK → Run workflow* con la etiqueta.

Desde que el `latest.json` está publicado, cada instalación de escritorio se
actualiza al abrirse.

## La aplicación del supervisor

**Escritorio.** Al abrir SICOT, consulta la última versión publicada, comprueba
su firma e instala. Una franja verde dice qué está pasando: en Windows el
instalador cierra SICOT un momento para reemplazar los archivos, y eso se avisa
antes de que ocurra.

**Android.** Avisa de que hay versión nueva y da la dirección para bajarla.
Instalar un APK exige la autorización del dueño del teléfono; ninguna
aplicación puede hacerlo por él.

**Una última instalación a mano.** Las instalaciones que hoy tienen la 0.9.0 no
traen el actualizador dentro: hay que instalarles la 0.10.0 como se hizo hasta
ahora. Desde ahí, se actualizan solas.

## Los dos secretos del repositorio

| Secreto | Para qué | Si se pierde |
| --- | --- | --- |
| `TAURI_SIGNING_PRIVATE_KEY` | firma el instalador de escritorio; la aplicación solo acepta actualizaciones firmadas con esta llave | las instalaciones existentes dejan de actualizarse solas: hay que instalarlas una vez a mano con una versión que lleve la llave nueva |
| `TAURI_SIGNING_PRIVATE_KEY_PASSWORD` | la contraseña de esa llave (vacía si se generó sin contraseña) | lo mismo |

### Cómo se guardan

En *Settings → Secrets and variables → Actions → New repository secret*:

- **`TAURI_SIGNING_PRIVATE_KEY`**: el contenido del archivo de la llave privada
  (`tauri signer generate` lo escribe como **una sola línea** de base64 que
  empieza por `dW50cnVzdGVkIGNvbW1lbnQ6`). Pegar la línea entera, **sin salto de
  línea ni espacios al final**: comprobado el 10-11-2026, con un salto de más
  `tauri signer sign` responde «failed to decode base64 secret key: Invalid
  symbol». Por eso el flujo le quita los espacios antes de usarla, pero cuanto
  más limpio entre, menos hay que diagnosticar.
- **`TAURI_SIGNING_PRIVATE_KEY_PASSWORD`**: **vacío**, si la llave se generó sin
  contraseña. Si GitHub no admite guardar un secreto vacío, se puede omitir: el
  flujo declara la variable de todos modos y entonces vale cadena vacía, que es
  lo que la llave espera. Lo que **no** se puede es inventarle una contraseña:
  con una que no sea la de la llave, la firma falla con «incorrect updater
  private key password».

Para comprobar que la llave guardada sirve, sin publicar nada: *Actions →
Publicar escritorio → Run workflow* con una etiqueta ya existente. El primer
paso dice si la llave está y cuántos caracteres tiene tras limpiarla.

La clave **pública** correspondiente está en
`frontend/src-tauri/tauri.conf.json`, y ahí es donde tiene que estar: es lo que
hace que una instalación rechace cualquier paquete que no venga de este
repositorio. Las llaves del APK son otras y están en ADR-013.

## Pedirle cosas al servidor sin estar en el Centro

Esto **no** hace falta para que SICOT funcione ni para que se actualice: el
temporizador de arriba no habla con nadie. Es para lo otro — que quien mantiene
el proyecto pueda pedirle al servidor que mire un registro, compruebe un
contenedor o diagnostique un despliegue, estando fuera del Centro.

```bash
sudo ./scripts/instalar-remote-control.sh
```

Si responde que no encontró el ejecutable `claude`, pregúnteselo al propio
usuario del servidor (`command -v claude` en su terminal) y dígaselo:

```bash
sudo SICOT_CLAUDE=/la/ruta/que/salio ./scripts/instalar-remote-control.sh
```

Pasa cuando la ruta de `claude` la añade `~/.bashrc` o un gestor de versiones de
Node, que no se cargan en el shell no interactivo con el que el instalador
pregunta.

Deja `claude remote-control` corriendo como servicio, en el clon, arrancando con
la máquina y volviendo solo si se cae la red. Antes de esto hacía falta una
terminal abierta a mano: al cerrarla, o al reiniciar el servidor, el canal
desaparecía sin avisar.

**Qué puede hacer quien se conecte por ahí, dicho sin adornos:** ejecutar
comandos en el servidor como el usuario dueño del clon, dentro de esa carpeta.
**No** puede usar `sudo` sin la contraseña, que no está guardada en ningún sitio;
las tareas de root —parar el temporizador, leer `/var/log`— siguen necesitando a
una persona. Y solo se conectan sesiones de Claude de la misma cuenta, después
de que su dueño lo haya autorizado una vez, en persona, en un dispositivo de
confianza.

El interruptor, cuando no se quiera:

```bash
sudo systemctl disable --now claude-remote-control
```

Mirarlo:

```bash
systemctl status claude-remote-control
```

```bash
sudo journalctl -u claude-remote-control -f
```
