# SICOT — Backup y restauración de la base de datos

## Respaldo automático (recomendado)

`scripts/respaldo-sicot.sh` hace el volcado, **verifica que se pueda leer** y
rota los antiguos. Programarlo a diario:

```bash
0 2 * * * /ruta/al/repo/scripts/respaldo-sicot.sh /var/backups/sicot >> /var/log/sicot-respaldo.log 2>&1
```

La verificación no es un adorno: `pg_dump` puede terminar con código 0 y dejar
un archivo truncado si el disco se llenó a mitad, y eso solo se descubre el día
que hace falta restaurar. El script lee el volcado entero con `pg_restore -f -`
y falla si no es válido. Conserva 14 respaldos por defecto
(`SICOT_BACKUPS_A_CONSERVAR`), para que el disco no se llene en silencio.

El volcado se escribe como `sicot-<fecha>.dump.parcial` y solo pasa a llamarse
`.dump` después de verificarlo; si algo falla, se borra. Así, en la carpeta solo
hay respaldos que se pudieron leer enteros, y eso es lo que cuentan tanto la
rotación como la vigilancia del backend, que solo mira los `.dump` con
contenido. Antes un volcado cortado se quedaba con nombre de respaldo: el
backend lo tomaba por el de anoche y no avisaba, y la rotación iba borrando los
buenos para conservarlo.

La **restauración sigue siendo manual a propósito** (ver más abajo): sobrescribe
datos oficiales y no debe poder ocurrir por un cron mal escrito.

Los volcados se crean con permisos `600` (el script fija `umask 077`): llevan la
base entera, contraseñas cifradas incluidas, y con el umask habitual quedaban
legibles por cualquier cuenta del servidor.

## Lo que el respaldo de la base no cubre

Dos cosas viven fuera de PostgreSQL y no se pueden volver a generar iguales. Se
guardan **una vez al instalar, y otra vez cada vez que cambien**, cifradas y
fuera del servidor (si el disco falla, una copia guardada en él no sirve):

| Qué | Por qué importa |
| --- | --- |
| El `.env` | No guarda datos, pero sin él hay que volver a generar todos los secretos y rehacer la configuración antes de que el servidor arranque |
| La autoridad de certificados de Caddy (`/data/caddy/pki/authorities/local` en el volumen `sicot_caddy_data`) | Es la raíz que se instaló a mano en cada teléfono y equipo (INSTALACION.md). Si se pierde, Caddy crea otra al arrancar y ninguno vuelve a conectar hasta reinstalarla en todos |

Desde la carpeta del proyecto en el servidor (con `COMPOSE_FILE` en el `.env`,
ver INSTALACION.md, paso 1):

```bash
docker compose cp proxy:/data/caddy/pki/authorities/local ./ca-sicot
tar czf - .env ca-sicot | gpg --symmetric --cipher-algo AES256   -o sicot-secretos-$(date +%Y%m%d).tar.gz.gpg
rm -rf ca-sicot
```

`gpg` pide una frase de paso: guárdela por separado del archivo. Copie el
`.gpg` fuera del servidor y bórrelo de él.

Para devolver la autoridad a un servidor reinstalado, antes de que los teléfonos
intenten conectar:

```bash
gpg -d sicot-secretos-AAAAMMDD.tar.gz.gpg | tar xzf -
docker compose cp ./ca-sicot/. proxy:/data/caddy/pki/authorities/local/
docker compose exec proxy rm -rf /data/caddy/certificates/local
docker compose restart proxy
rm -rf ca-sicot
```

El `rm` de en medio borra el certificado del sitio que Caddy ya hubiera emitido
con la autoridad nueva; sin él lo seguiría sirviendo hasta renovarlo, y los
teléfonos lo rechazarían mientras tanto. Al reiniciar, Caddy emite otro con la
autoridad restaurada.

## Procedimiento manual

Sigue siendo válido y es el que conviene correr a mano antes de cualquier
operación riesgosa: una migración nueva, una actualización de versión, limpieza
de datos, etc.

Asume que la base corre en el contenedor `sicot-db` (ver `docker-compose.yml`).
Si corre sin Docker, cambiar `docker exec sicot-db` por el `psql`/`pg_dump`
nativo apuntando a `localhost:5432`.

## Backup

```bash
docker exec sicot-db pg_dump -U sicot -d sicot -F c -f /tmp/sicot.dump
docker cp sicot-db:/tmp/sicot.dump ./sicot-backup-$(date +%Y%m%d-%H%M).dump
```

`-F c` usa el formato "custom" de Postgres (comprimido, permite restaurar
tablas individuales) en vez de SQL plano.

## Restauración

⚠️ Esto sobrescribe los datos actuales de la base `sicot`. Confirmar que es
lo que se quiere antes de correrlo — no hay deshacer.

```bash
docker cp ./sicot-backup-XXXXXXXX-XXXX.dump sicot-db:/tmp/restore.dump
docker exec sicot-db pg_restore -U sicot -d sicot --clean --if-exists /tmp/restore.dump
```

`--clean --if-exists` hace que `pg_restore` borre los objetos existentes
antes de recrearlos, para que la restauración funcione aunque la base ya
tenga el esquema de Flyway aplicado.

## Verificación después de restaurar

```bash
docker exec sicot-db psql -U sicot -d sicot -c "SELECT count(*) FROM contratos;"
docker compose logs backend | grep -i flyway
```

El backend valida el esquema contra las migraciones de Flyway al arrancar
(`spring.jpa.hibernate.ddl-auto=validate`) — si la base restaurada no
coincide con el historial de migraciones esperado, el backend se niega a
arrancar en vez de correr con un esquema inconsistente. Revisar los logs si
eso pasa.

## Qué falta (fuera de alcance de este documento)

- Subir los backups a un almacenamiento fuera de la misma máquina (si el
  disco del servidor falla, un backup guardado ahí mismo no sirve).
