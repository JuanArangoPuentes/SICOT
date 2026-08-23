# Base de datos local

## PostgreSQL con Docker

El servicio `db` de `docker-compose.yml` ejecuta PostgreSQL 18 y crea la base
`sicot`. Flyway aplica el esquema desde el backend cuando inicia por primera
vez. El puerto publicado para herramientas del host es `5433`.

Para una base local completamente nueva:

```powershell
docker compose down -v
docker compose up -d --build
```

`down -v` elimina unicamente el volumen local de este proyecto y borra sus
datos. No debe ejecutarse en una base con informacion que se quiera conservar.

## Conexion desde pgAdmin

En pgAdmin, crea un nuevo servidor con estos valores:

| Campo | Valor |
| --- | --- |
| Name | SICOT local |
| Host name/address | `localhost` |
| Port | `5433` |
| Maintenance database | `sicot` |
| Username | `sicot` |
| Password | El valor de `DB_PASSWORD` en `.env` |

Activa `Save password` solo si el equipo es confiable. La base debe estar
levantada antes de conectar:

```powershell
docker compose ps db
```

En el arbol de pgAdmin, la estructura queda en `SICOT local > Databases >
sicot > Schemas > public > Tables`. Las tablas se crean por Flyway; no se deben
crear manualmente desde pgAdmin.

## Migraciones

La base nueva usa una linea base de estructura en:

```text
backend/src/main/resources/db/migration/V1__create_sicot_schema.sql
```

No contiene datos demo ni operaciones `DELETE`. Los usuarios demo solo se
crean con el perfil `dev` mediante `DataInitializer`.
