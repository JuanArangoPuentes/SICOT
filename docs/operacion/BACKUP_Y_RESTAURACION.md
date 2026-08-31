# SICOT — Backup y restauración de la base de datos

Procedimiento manual (no hay automatización/cron todavía — queda documentado
para poder correrlo a mano antes de cualquier operación riesgosa: una
migración nueva, una actualización de versión, limpieza de datos, etc.).

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
