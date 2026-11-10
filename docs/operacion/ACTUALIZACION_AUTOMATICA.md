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

Deja el clon en `master`, instala las dos unidades de systemd, enciende el
temporizador y hace el primer despliegue. Es idempotente: volver a correrlo
sirve para mover el clon de sitio o cambiar la rama (`SICOT_RAMA=...`).

### Qué hace en cada corrida

1. `git fetch`. Si `master` no avanzó, termina sin escribir nada.
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
2. Mergee a `master`.
3. Empuje la etiqueta:

```bash
git tag v0.10.0 && git push origin v0.10.0
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

La clave **pública** correspondiente está en
`frontend/src-tauri/tauri.conf.json`, y ahí es donde tiene que estar: es lo que
hace que una instalación rechace cualquier paquete que no venga de este
repositorio. Las llaves del APK son otras y están en ADR-013.
