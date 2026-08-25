# Base de datos local

## Importante: pueden coexistir dos bases distintas

En una máquina de desarrollo es normal terminar con **dos PostgreSQL independientes**:

| Instancia | Puerto en el host | Cuándo se usa |
| --- | --- | --- |
| Contenedor `sicot-db` (`docker compose`) | `5433` | Al levantar el stack con Docker |
| PostgreSQL nativo instalado en Windows | `5432` | Al correr el backend sin Docker (`mvn spring-boot:run` con la `DB_URL` por defecto) |

**No son la misma base.** Tienen datos distintos: un contrato creado en una no existe en la
otra. Antes de concluir que "se borraron los datos", verifique contra cuál está apuntando el
backend (`DB_URL`) y en cuál está consultando con pgAdmin/Adminer.

Un tercer caso reportado por el equipo: conectarse al `5432` creyendo que es el de SICOT cuando
en realidad es un PostgreSQL nativo de otro proyecto instalado en esa máquina.

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

La base nueva aplica dos migraciones, ambas en
`backend/src/main/resources/db/migration/`:

```text
V1__create_sicot_schema.sql                    linea base: tablas, constraints, indices
V9__add_indices_fecha_alertas_registros.sql    indices por fecha (alertas, registros)
```

El salto de `V1` a `V9` es intencional: las migraciones `V1`–`V8` originales se
consolidaron en la nueva `V1`, y `V9` se conservó porque ya estaba aplicada en
bases existentes. No falta ninguna migración.

Ninguna contiene datos demo ni operaciones `DELETE`. Los usuarios demo solo se
crean con el perfil `dev` mediante `DataInitializer`.
