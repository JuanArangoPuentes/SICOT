# SICOT — Sistema Inteligente para la Gestión y Acompañamiento de Contratos

Plataforma para el **SENA — Centro Tecnológico del Mobiliario** para gestionar el ciclo de vida
de contratos: etapas del flujo GCCON-P-010, documentos, alertas, registros de auditoría y roles
(`ADMINISTRADOR`, `GESTION`, `SUPERVISOR`). Backend como autoridad funcional: el frontend no
inventa datos ni reglas de negocio — lo que se ve viene de la API real o de un estado vacío
honesto.

## Arquitectura

```
┌───────────────────────┐        ┌───────────────────────┐        ┌────────────────┐
│       frontend         │ ─────▶ │        backend         │ ─────▶ │   PostgreSQL   │
│  React 19 + Vite + TS  │  HTTP  │  Spring Boot 3 + JWT   │  JDBC  │   BD: sicot    │
│  :8443                 │ ◀───── │  :8080                 │ ◀───── │                │
└───────────────────────┘  JSON  └───────────────────────┘        └────────────────┘
                                            ▲
                                            │ mismos endpoints REST,
                                            │ como una cuenta real
                                  ┌───────────────────────┐
                                  │          mcp           │
                                  │  servidor MCP para IA  │
                                  │  (Claude Desktop/Code) │
                                  └───────────────────────┘
```

| Carpeta | Qué es | README |
|---|---|---|
| [`frontend/`](./frontend) | UI en React 19 + TypeScript + Vite + Tailwind v4 | [Frontend](./frontend/README.md) |
| [`backend/`](./backend) | API en Spring Boot 3.5.12 + Java 25 + PostgreSQL + JWT | [Backend](./backend/README.md) |
| [`mcp/`](./mcp) | Servidor MCP delgado sobre la API real, para asistentes de IA | [MCP](./mcp/README.md) |
| [`docs/producto/`](./docs/producto) | Qué hace SICOT: especificación funcional del sistema | — |
| [`docs/operations/`](./docs/operations) | Operación día a día (base de datos local) | — |
| [`docs/despliegue/`](./docs/despliegue) | Backup y restauración | — |
| [`docs/fases/`](./docs/fases) | Reportes de inspección/checkpoint por fase del proyecto | — |
| [`docs/auditorias/`](./docs/auditorias) | Auditorías de datos/BD (consultas y resultados) | — |

## Correr todo con Docker — entorno estándar del equipo

> **Decisión del equipo (26 ago 2026):** el entorno de trabajo es este.
> La base de datos de desarrollo es la del contenedor `sicot-db` (**puerto 5433**),
> no un PostgreSQL instalado a mano. Así todos trabajamos contra el mismo esquema
> con un solo comando, y no hay que instalar ni versionar nada por separado.

La base de datos se crea y versiona con Flyway desde el backend. Una instalacion
nueva aplica el esquema de `backend/src/main/resources/db/migration` y no carga
datos transaccionales demo. Los usuarios de desarrollo solo aparecen con el
perfil `dev`.

Requiere [Docker Desktop](https://www.docker.com/products/docker-desktop/) abierto y corriendo.
Desde la raíz del repo:

```bash
docker compose up -d --build
```

### ⚠️ Primera vez después del `git pull` que consolidó las migraciones

Si ya habías levantado el proyecto antes de esa consolidación, tu volumen de
PostgreSQL recuerda las 9 migraciones antiguas y **el backend no arrancará**:

```
FlywayValidateException: Migration checksum mismatch for migration version 1
Detected applied migration not resolved locally: 2. (…3, 4, 5, 6, 7, 8)
```

Hay que borrar ese volumen para que Flyway parta de cero con las dos migraciones
actuales:

```bash
docker compose down -v
docker compose up --build -d
docker compose ps
```

`down -v` borra **solo** el volumen local de este proyecto. No se ejecuta nunca
sobre una base con información que se quiera conservar.

Esto levanta 4 contenedores (agrupados en Docker Desktop bajo el proyecto **sicot**):

| Contenedor | URL | Qué es |
|---|---|---|
| `sicot-frontend` | http://localhost:8443 | UI |
| `sicot-backend` | http://localhost:8080/swagger-ui.html | API + Swagger/OpenAPI |
| `sicot-db` | `localhost:5433` | PostgreSQL 18, base `sicot` |
| `sicot-adminer` | http://localhost:8081 | Panel visual de la base de datos |

**Panel de PostgreSQL (Adminer)** en http://localhost:8081 — el campo Servidor ya viene
precargado (`db`); solo falta Usuario `sicot`, Contraseña (`sicot_dev_password` en desarrollo,
o el valor de `DB_PASSWORD` si se sobrescribió) y Base de datos `sicot`.

Configuración opcional: copie [`.env.example`](./.env.example) a `.env` en esta carpeta antes de
levantar el stack para sobrescribir contraseñas/secretos de desarrollo.

```bash
docker compose ps                 # estado y salud de cada contenedor
docker compose logs -f backend    # seguir logs de un servicio
docker compose down               # apagar
docker compose down -v            # apagar y borrar también los datos de Postgres
```

## Despliegue en producción (multi-máquina)

El comando de arriba (`docker compose up`) está pensado para desarrollo en una
sola máquina: publica el puerto de Postgres y levanta Adminer sin
autenticación, cosas razonables en un laptop de desarrollo pero no en un
servidor real. Para un despliegue de verdad (accesible desde otras máquinas
de la red o de Internet):

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

Esto añade (ver [`docker-compose.prod.yml`](./docker-compose.prod.yml)):
- Postgres deja de publicar su puerto al host — solo el backend le habla,
  por la red interna de Docker.
- Adminer no arranca por defecto (agregar `--profile tools` al comando de
  arriba para usarlo puntualmente).

Antes de levantar así, en el `.env` de esta carpeta:
1. Fijar `SPRING_PROFILES_ACTIVE=prod` (evita que se creen las cuentas de
   prueba y restringe Swagger — ver `backend/src/main/resources/application-prod.properties`).
2. Fijar `VITE_API_URL` y `CORS_ALLOWED_ORIGINS` a la IP/dominio **real** del
   servidor, no `localhost` — de lo contrario el frontend, ya compilado con
   `localhost` incrustado, no podrá hablarle al backend desde ninguna otra
   máquina. Ver los comentarios en [`.env.example`](./.env.example).
3. Si alguien no puede conectarse desde otra máquina de la red (síntoma
   típico: "no me conecta a la base de datos" o el navegador se cuelga
   cargando), revisar primero el Firewall de Windows/Linux de la máquina que
   corre Docker Desktop — debe permitir conexiones entrantes en los puertos
   publicados (8443, 8080). El puerto de Postgres ya no es alcanzable desde
   fuera con el override de producción, así que no debería intentarse
   conectar ahí directamente.

Backup/restauración de la base de datos: ver
[`docs/despliegue/BACKUP_Y_RESTAURACION.md`](./docs/despliegue/BACKUP_Y_RESTAURACION.md).

## Correr sin Docker (desarrollo día a día)

1. PostgreSQL nativo en `localhost:5432`, base `sicot` (detalle en
   [`backend/README.md`](./backend/README.md)).
2. Backend: `cd backend && mvn spring-boot:run` → http://localhost:8080
3. Frontend: `cd frontend && npm install && npm run dev` → http://localhost:8443

> ⚠️ **Este modo NO es el entorno estándar del equipo** — ver la sección de Docker arriba.
> Úselo solo para depurar el backend desde el IDE.
>
> **Son dos bases de datos distintas, no la misma vista desde dos puertos.** El Postgres
> **nativo** (`localhost:5432`) que usa este modo y el Postgres **del contenedor**
> (`localhost:5433`) son instancias independientes con datos independientes: un contrato creado
> en una **no** aparece en la otra. Si alguien reporta que "los datos desaparecieron", lo primero
> a verificar es contra cuál de las dos está corriendo el backend (variable `DB_URL`).

## Cuentas de desarrollo

| Email | Rol |
|---|---|
| `administrador@soy.sena.edu.co` | ADMINISTRADOR |
| `gestion@soy.sena.edu.co` | GESTION |
| `supervisor@soy.sena.edu.co` | SUPERVISOR |

Contraseñas en [`backend/README.md`](./backend/README.md). Son exclusivamente de
desarrollo/demo — nunca deben usarse en producción.

## Reglas del proyecto

Las reglas de estabilidad, alcance y "no inventar" que gobiernan este repo están en
[`.github/copilot-instructions.md`](./.github/copilot-instructions.md) y aplican a cualquier
persona o agente que contribuya. Su §33 define además qué áreas tienen responsable asignado.

## Integración continua

[`.github/workflows/ci.yml`](./.github/workflows/ci.yml) corre en cada PR hacia `develop` o
`master`: pruebas del backend (`mvnw verify`) y chequeo de tipos + build del frontend. Un PR
con la CI en rojo no se mergea. La suite end-to-end de Playwright queda fuera de esa compuerta
porque necesita backend, PostgreSQL y las cuentas del perfil `dev`; se corre a mano con
`npm run test:e2e`.

## Distribución a los usuarios finales

> **Decisión del equipo (26 ago 2026):** los instaladores se publican como **GitHub Releases**
> de este repositorio. No se usa GitHub Pages ni una página de descarga aparte.

El motivo es la trazabilidad: en un sistema institucional hay que poder responder *"¿qué versión
tiene instalada este supervisor?"* y poder revertir una entrega defectuosa. Releases lo resuelve
de forma nativa —cada versión queda con su etiqueta, su fecha y sus notas de cambios— mientras
que una página de descarga obligaría a construir ese control a mano. Ambas opciones son
gratuitas, así que la diferencia está en el versionado, no en el costo.

Cada publicación llevará:

- El instalador de Windows como *asset* descargable.
- Etiqueta de versión semántica (`v1.0.0`) y fecha.
- Notas de cambios redactadas para el usuario final, no en lenguaje técnico.

**Todavía no hay ninguna versión publicada**, y es deliberado: el contenido del instalador
depende de una decisión pendiente (ver [MDL-66](https://linear.app/medialab-sena/issue/MDL-66)).
Si el supervisor trabaja conectado al servidor del Centro, el instalador es una ventana ligera de
unos pocos MB; si trabaja sin conexión, debe incluir el asistente de IA completo y pasa a varios
GB, con requisitos de hardware distintos. Construirlo antes de resolver eso implicaría rehacerlo.

## Herramientas de desarrollo asistido (opcional)

El repo no versiona la maquinaria de asistentes de IA — es regenerable y no todo el equipo usa
el mismo. Si quiere los flujos de trabajo de GSD sobre este proyecto:

```bash
npx @opengsd/gsd-core@latest --local --claude
```

Instala bajo `.claude/`, que está ignorado por git salvo `launch.json`.
