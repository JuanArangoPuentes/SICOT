# Base de datos local

> **Entorno estándar del equipo (26 ago 2026):** la base de desarrollo es la del
> contenedor `sicot-db`, **puerto 5433**, levantada con `docker compose`. Un
> PostgreSQL instalado a mano en la máquina no es el entorno de referencia.

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
docker compose up --build -d
docker compose ps
```

`down -v` elimina unicamente el volumen local de este proyecto y borra sus
datos. No debe ejecutarse en una base con informacion que se quiera conservar.

### Cuando hace falta hacerlo

Si el backend no arranca y en sus logs aparece:

```
FlywayValidateException: Migration checksum mismatch for migration version 1
Detected applied migration not resolved locally: 2. (…3, 4, 5, 6, 7, 8)
```

significa que el volumen recuerda las 9 migraciones anteriores a la
consolidacion, mientras que el codigo ya solo trae dos. Flyway se niega a
continuar con esa discrepancia. La secuencia de arriba resuelve el caso: borra
ese historial y deja que las dos migraciones actuales se apliquen desde cero.

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

Todas viven en `backend/src/main/resources/db/migration/`:

```text
V1__create_sicot_schema.sql                                  linea base: tablas, constraints, indices
V9__add_indices_fecha_alertas_registros.sql                  indices por fecha (alertas, registros)
V10__reconcilia_esquema_con_la_linea_base.sql                restaura restricciones ausentes en bases antiguas
V11__indices_compuestos_tablas_de_crecimiento_libre.sql      (contrato_id, fecha DESC) en alertas y registros
V12__bloqueo_optimista.sql                                   columna lock_version en siete tablas
```

El salto de `V1` a `V9` es intencional: las migraciones `V1`–`V8` originales se
consolidaron en la nueva `V1`, y `V9` se conservó porque ya estaba aplicada en
bases existentes. No falta ninguna migración.

`V10` merece una nota: la consolidación de `V1` agregó siete restricciones que
las migraciones originales no tenían, pero en las bases que ya existían solo se
reparó el historial de Flyway — el SQL nuevo nunca corrió. `V10` las restaura y
es un no-op en las bases creadas desde cero. Es la migración que vuelve a dejar
todas las bases del proyecto con el mismo esquema. El detalle completo está en
[MODELO_DE_DATOS.md](MODELO_DE_DATOS.md).

Ninguna contiene datos demo ni operaciones `DELETE`. Los usuarios demo solo se
crean con el perfil `dev` mediante `DataInitializer`.

## Verificar que dos bases tienen el mismo esquema

Si sospecha que la base del `5432` y la del `5433` divergieron, hay una consulta
lista para compararlas con `diff` en
[MODELO_DE_DATOS.md](MODELO_DE_DATOS.md#comparar-dos-bases).
