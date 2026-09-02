# SICOT — Gestión de secretos

Dónde viven las credenciales, cómo se rotan y qué se rompe al rotarlas.

La decisión de fondo —por qué `.env` y no un gestor dedicado— está en
[ADR-005](../decisiones/ADR-005-gestion-de-secretos.md). Este documento es el
procedimiento.

## Qué secretos existen

| Variable | Qué protege | Dónde se usa |
| --- | --- | --- |
| `DB_PASSWORD` | Acceso a PostgreSQL | Backend y contenedor de la base |
| `JWT_SECRET` | Firma de los tokens de sesión | Solo el backend |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | Cuenta de correo saliente | Solo el backend |
| `SICOT_ADMIN_EMAIL` / `SICOT_ADMIN_PASSWORD` | Administrador del primer arranque | Solo el backend, y solo con la tabla de usuarios vacía |

Todos viven en un único archivo `.env` en la raíz del proyecto, **en el host de
despliegue**. Ese archivo nunca se sube al repositorio: está en `.gitignore` y se
verificó que no aparece en ningún punto del historial.

## Permisos del archivo

`.env` contiene todas las llaves del sistema en texto plano. En el host:

```bash
chmod 600 .env
```

Solo su dueño puede leerlo. Sin esto, cualquier cuenta del servidor puede leer la
contraseña de la base de datos.

## Generar un secreto nuevo

Nunca a mano ni reutilizando uno anterior:

```bash
openssl rand -base64 48
```

Para `JWT_SECRET` el resultado sirve tal cual (la clave HMAC-SHA256 necesita al
menos 32 bytes; 48 va sobrado). Para contraseñas, `openssl rand -base64 24`.

## Rotar cada secreto

Rotar **no es solo cambiar la línea del `.env`**. Cada credencial tiene un efecto
distinto y algunas exigen un paso adicional.

### `JWT_SECRET`

```bash
# 1. Generar y reemplazar la línea en .env
openssl rand -base64 48
# 2. Reiniciar solo el backend
docker compose up -d --force-recreate backend
```

**Efecto:** todas las sesiones abiertas se invalidan al instante. Todo el mundo
tiene que volver a iniciar sesión. No se pierde ningún dato — los tokens
anteriores simplemente dejan de verificar.

**Cuándo hacerlo:** ante cualquier sospecha de filtración, y obligatoriamente al
salir alguien del equipo.

### `DB_PASSWORD`

Este tiene **dos lados**: el `.env` y la propia base. Cambiar solo el `.env` deja
al backend sin poder conectarse.

```bash
# 1. Cambiarla dentro de PostgreSQL primero
docker exec -it sicot-db psql -U sicot -d sicot \
  -c "ALTER USER sicot WITH PASSWORD 'la-nueva';"
# 2. Actualizar DB_PASSWORD en .env con ese mismo valor
# 3. Reiniciar el backend
docker compose up -d --force-recreate backend
```

**Efecto:** corte de servicio de unos segundos mientras el backend reinicia.

> **Cuidado:** `POSTGRES_PASSWORD` en el contenedor de la base solo se aplica
> cuando el volumen se crea por primera vez. En una base que ya existe, cambiar
> esa variable **no cambia nada**: hay que ejecutar el `ALTER USER` de arriba.
> Es la confusión más común de este procedimiento.

### `MAIL_PASSWORD`

Si es una cuenta de Gmail, se revoca la contraseña de aplicación anterior desde
la cuenta de Google y se genera una nueva.

**Efecto:** solo deja de funcionar el envío de credenciales por correo. El resto
del sistema sigue igual — el correo es una integración opcional y su fallo no
tumba el arranque ni el healthcheck.

### `SICOT_ADMIN_PASSWORD`

Caso especial: **solo se usa cuando la tabla de usuarios está vacía**. Una vez
creado el administrador, esa variable no vuelve a leerse nunca.

Para cambiar la contraseña de esa cuenta ya creada, se hace **desde la propia
aplicación** (panel de Administración), no tocando el `.env`. Lo correcto tras el
primer arranque es **retirar ambas variables** del archivo: no sirven para nada y
son una credencial de más esperando a filtrarse.

## Cuándo hay que rotar, sin excepción

1. **Cuando alguien deja el equipo** y tenía acceso al host. No importa en qué
   términos se fue: el procedimiento no es sobre confianza, es sobre reducir el
   número de personas que conocen una llave activa.
2. **Ante cualquier filtración, real o sospechada.** Rotar de más cuesta un
   reinicio; rotar de menos cuesta el sistema.
3. **Si un secreto llegó alguna vez a un canal de chat, un correo o una captura
   de pantalla.** Ese secreto ya está comprometido aunque el mensaje se borre.

## Qué hacer ante una filtración

En este orden:

1. Rotar **`JWT_SECRET` primero** — es lo que corta el acceso de cualquiera que
   tenga un token robado, y es el único cambio de efecto inmediato.
2. Rotar `DB_PASSWORD` con el procedimiento completo de arriba.
3. Rotar las credenciales de correo.
4. Revisar `/api/registros` (bitácora de auditoría) buscando actividad que no
   cuadre.
5. Anotar qué pasó y qué se rotó. Sin ese registro, dentro de seis meses nadie
   recuerda si esa credencial se cambió o no.

## Lo que este esquema no da

- No hay historial de quién leyó un secreto ni cuándo.
- No hay rotación automática ni caducidad.
- No escala a dos entornos: en el momento en que existan producción y
  preproducción, dos copias manuales del mismo archivo divergen y nadie sabe cuál
  es la buena. **Ese es el disparador que obliga a revisar
  [ADR-005](../decisiones/ADR-005-gestion-de-secretos.md).**
