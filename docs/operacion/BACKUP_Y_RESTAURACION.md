# SICOT — Backup y restauración de la base de datos

## Respaldo automático (recomendado)

`scripts/respaldo-sicot.sh` hace el volcado, **verifica que se pueda leer** y
rota los antiguos. Programarlo a diario:

```bash
0 2 * * * /ruta/al/repo/scripts/respaldo-sicot.sh /var/backups/sicot >> /var/log/sicot-respaldo.log 2>&1
```

La verificación no es un adorno: `pg_dump` puede terminar con código 0 y dejar
un archivo truncado si el disco se llenó a mitad, y eso solo se descubre el día
que hace falta restaurar. El script lee el volcado entero con `pg_restore
--list` y falla si no es válido. Conserva 14 respaldos por defecto
(`SICOT_BACKUPS_A_CONSERVAR`), para que el disco no se llene en silencio.

La **restauración sigue siendo manual a propósito** (ver más abajo): sobrescribe
datos oficiales y no debe poder ocurrir por un cron mal escrito.

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

- Automatizar el backup con un cron/servicio dedicado y rotación de
  respaldos antiguos.
- Subir los backups a un almacenamiento fuera de la misma máquina (si el
  disco del servidor falla, un backup guardado ahí mismo no sirve).
