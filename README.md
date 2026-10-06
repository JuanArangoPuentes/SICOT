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

Dentro del backend corre además el **motor de automatizaciones** (ADR-008): un
módulo que genera las alertas del sistema —vencimientos, atrasos de cronograma,
asignación de supervisor, integridad de documentos— sin ninguna herramienta
externa. Reglas en Java, cola persistente en la misma base y plantillas para los
textos: **el motor no llama al modelo de IA**, así que las alertas funcionan en
un servidor sin Ollama instalado. Detalle en
[`backend/README.md §9`](./backend/README.md).

| Carpeta | Qué es | README |
|---|---|---|
| [`frontend/`](./frontend) | UI en React 19 + TypeScript + Vite + Tailwind v4 | [Frontend](./frontend/README.md) |
| [`backend/`](./backend) | API en Spring Boot 3 + Java 25 + PostgreSQL + JWT | [Backend](./backend/README.md) |
| [`mcp/`](./mcp) | Servidor MCP delgado sobre la API real, para asistentes de IA | [MCP](./mcp/README.md) |
| [`docs/producto/`](./docs/producto) | Qué hace SICOT: especificación funcional del sistema | — |
| [`docs/api/`](./docs/api) | Inventario de endpoints: rol, control de acceso y forma de respuesta | — |
| [`docs/operacion/`](./docs/operacion) | Operación día a día: [modelo de datos](./docs/operacion/MODELO_DE_DATOS.md), base de datos local, backup y restauración | — |
| [`docs/decisiones/`](./docs/decisiones) | Decisiones de arquitectura (ADR): despliegue, respaldo, IA, automatizaciones | — |
| [`docs/formatos/`](./docs/formatos) | Qué se decidió para cada formato institucional al armarlo desde SICOT | — |
| [`docs/historico/`](./docs/historico) | Fotos de una fecha: auditorías, revisiones y fases cerradas. No describen el SICOT de hoy | [Histórico](./docs/historico/README.md) |

## Correr todo con Docker — entorno de trabajo estándar

> **Decidido el 26 de agosto de 2026:** el entorno de trabajo es este.
> La base de datos de desarrollo es la del contenedor `sicot-db` (**puerto 5433**),
> no un PostgreSQL instalado a mano. Así el esquema con el que se trabaja sale
> siempre de las migraciones del repositorio, con un solo comando y sin instalar
> ni versionar nada por separado.

La base de datos se crea y versiona con Flyway desde el backend. Una instalacion
nueva aplica el esquema de `backend/src/main/resources/db/migration` y no carga
datos transaccionales demo. Los usuarios de desarrollo solo aparecen con el
perfil `dev`.

Requiere [Docker Desktop](https://www.docker.com/products/docker-desktop/) abierto y corriendo.
Desde la raíz del repo:

```bash
docker compose up -d --build
```

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

La `-v` del último comando borra el volumen de la base. Aquí no importa, porque
los datos son de desarrollo; en el servidor del Centro ese comando no se usa
nunca (ver [`INSTALACION.md`](./INSTALACION.md) § «Si algo no arranca»).

## Despliegue en producción (multi-máquina)

El comando de arriba (`docker compose up`) está pensado para desarrollo en una
sola máquina: publica el puerto de Postgres y levanta Adminer sin
autenticación, cosas razonables en un laptop de desarrollo pero no en un
servidor real. El procedimiento completo para un servidor está en
[`INSTALACION.md`](./INSTALACION.md) (opción B); en resumen, se despliega con
[`docker-compose.prod.yml`](./docker-compose.prod.yml) encima del archivo base:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

Esto añade:
- Un proxy Caddy que termina TLS y es el único servicio con puertos
  publicados (80 y 443, [ADR-009](./docs/decisiones/ADR-009-terminacion-tls.md)).
  Postgres, el backend y el frontend solo se alcanzan por la red interna de
  Docker.
- Adminer no arranca por defecto (agregar `--profile tools` al comando de
  arriba para usarlo puntualmente).
- `DB_PASSWORD`, `JWT_SECRET`, `SICOT_DOMINIO` y `RESPALDO_DIRECTORIO`
  obligatorias, y el perfil `prod` fijado de forma literal.

En el `.env` del servidor conviene descomentar `COMPOSE_FILE` (ver
[`.env.example`](./.env.example)): así cualquier `docker compose …` de esa
carpeta carga el archivo de producción aunque se olviden los `-f`, y un comando
escrito deprisa no recrea el backend con la configuración de desarrollo.

Backup/restauración de la base de datos: ver
[`docs/operacion/BACKUP_Y_RESTAURACION.md`](./docs/operacion/BACKUP_Y_RESTAURACION.md).

## Correr sin Docker (desarrollo día a día)

1. PostgreSQL nativo en `localhost:5432`, base `sicot` (detalle en
   [`backend/README.md`](./backend/README.md)).
2. Backend: `cd backend && mvn spring-boot:run` → http://localhost:8080
3. Frontend: `cd frontend && npm install && npm run dev` → http://localhost:8443

> ⚠️ **Este modo NO es el entorno estándar** — ver la sección de Docker arriba.
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

## Primer arranque en producción

Estas cuentas **no existen** fuera de los perfiles `dev` y `test`: las siembra
`DataInitializer`, que está restringido a ellos precisamente porque sus contraseñas
están publicadas en este repositorio.

En un despliegue real la tabla de usuarios arranca vacía, y como
`POST /api/usuarios` exige rol ADMINISTRADOR y el login es el único endpoint
público, hace falta crear la primera cuenta desde el entorno. Se declara en el
`.env` del servidor antes del primer `docker compose up`:

```bash
SICOT_ADMIN_EMAIL=nombre.apellido@sena.edu.co
SICOT_ADMIN_PASSWORD=<al menos 12 caracteres — genérela con: openssl rand -base64 24>
```

Con la base vacía y sin estas variables, **el backend se niega a arrancar** y
dice por qué. Es deliberado: un sistema en pie al que nadie puede entrar es peor
que un despliegue que falla con un mensaje claro.

Después del primer arranque ambas variables pueden retirarse — solo se usan
cuando no hay ningún usuario. Cambie esa contraseña desde el panel de
administración en cuanto entre.

## Operación

| Documento | Para qué |
| --- | --- |
| [`BACKUP_Y_RESTAURACION.md`](./docs/operacion/BACKUP_Y_RESTAURACION.md) | Respaldo automático verificado y restauración manual |
| [`GESTION_DE_SECRETOS.md`](./docs/operacion/GESTION_DE_SECRETOS.md) | Dónde viven las credenciales, cómo rotarlas y qué hacer ante una filtración |
| [`MODELO_DE_DATOS.md`](./docs/operacion/MODELO_DE_DATOS.md) | Las tablas, sus reglas y el inventario de migraciones |
| [`LOCAL_DATABASE.md`](./docs/operacion/LOCAL_DATABASE.md) | Base de datos local para desarrollo |

## Decisiones de arquitectura

Las decisiones que **no son evidentes leyendo el código** —y que alguien tendría
que volver a tomar, probablemente mal, si no estuvieran escritas— viven en
[`docs/decisiones/`](./docs/decisiones/README.md):

| ADR | Decide |
| --- | --- |
| [001](./docs/decisiones/ADR-001-bifurcamiento-de-despliegue.md) | Qué significa "instalación local" para el Supervisor |
| [002](./docs/decisiones/ADR-002-continuidad-y-perdida-aceptable.md) | Cuánta información es aceptable perder y en cuánto tiempo se vuelve a estar en pie |
| [003](./docs/decisiones/ADR-003-umbral-de-migracion-de-documentos.md) | Cuándo dejar de guardar los archivos dentro de PostgreSQL |
| [004](./docs/decisiones/ADR-004-un-solo-sistema-de-estilos.md) | Un solo sistema de estilos en el frontend |
| [005](./docs/decisiones/ADR-005-gestion-de-secretos.md) | Dónde viven las credenciales y cómo se rotan |
| [006](./docs/decisiones/ADR-006-modelo-de-ia.md) | Qué modelo de IA local usa el Copiloto |
| [007](./docs/decisiones/ADR-007-enrutado-y-enlaces-profundos.md) | Enrutado por URL y enlaces compartibles |
| [008](./docs/decisiones/ADR-008-motor-de-automatizaciones.md) | Dónde viven las automatizaciones: dentro del backend, ni n8n ni un proceso aparte |
| [009](./docs/decisiones/ADR-009-terminacion-tls.md) | Dónde termina TLS: un proxy inverso delante del stack |
| [010](./docs/decisiones/ADR-010-versionado-de-la-api.md) | Versionado de la API: política de compatibilidad en vez de prefijo |

Un ADR **no se edita para cambiar la decisión**: se escribe uno nuevo que lo
reemplaza. El historial de por qué el sistema tuvo una forma anterior es parte
del valor.

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

> **Decidido el 26 de agosto de 2026:** los instaladores se publican como **GitHub Releases**
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

### El instalador del Supervisor

La duda que bloqueaba este trabajo —si el supervisor necesita trabajar sin conexión— **está
resuelta** desde el 1 de septiembre por
[ADR-001](./docs/decisiones/ADR-001-bifurcamiento-de-despliegue.md), que adoptó la interpretación
B: la instalación local es la misma aplicación web en una ventana propia, y exige conexión. El
escenario de «varios GB con el asistente de IA dentro» que se temía **no aplica**: bajo esa
interpretación el modelo vive en el host del despliegue —`docker-compose.yml` apunta a
`host.docker.internal:11434`— y la máquina del supervisor solo corre la ventana.

El empaquetado vive en [`frontend/src-tauri/`](./frontend/src-tauri), sobre el mismo frontend y sin
una segunda base de código:

```bash
cd frontend
npm run tauri:build     # genera el instalador de Windows (NSIS y MSI)
npm run tauri:dev       # abre la ventana de escritorio contra el frontend en desarrollo
```

Requiere la cadena de herramientas de Rust (`rustup`) y las *Build Tools* de Visual Studio con el
componente C++. WebView2 ya viene con Windows 11.

**A qué servidor se conecta.** El instalador **no lleva la dirección del servidor dentro**. Si la
llevara, el ejecutable que descarga un supervisor apuntaría para siempre a un servidor concreto y
mover el servidor obligaría a recompilar y republicar el instalador para todo el mundo. En su
lugar, la dirección se guarda en cada máquina y se cambia desde **Configuración → Servidor**; el
valor de compilación queda solo como defecto. Así el instalador es un único artefacto válido para
cualquier despliegue.

**Lo que todavía no tiene.** El instalador no está firmado con un certificado de código, así que
Windows muestra una advertencia de editor desconocido al instalarlo. Firmarlo exige un certificado
de pago, lo que choca con la regla de que SICOT se mantenga en herramientas gratuitas; es una
decisión que corresponde al SENA y hay que plantearla en la reunión institucional.

### SICOT en el teléfono

La tercera forma de SICOT, sobre el mismo frontend y otra vez sin una segunda base de código
([`ADR-012`](./docs/decisiones/ADR-012-aplicacion-movil.md)).

**Qué funciona hoy:** la interfaz está adaptada a pantalla estrecha en los tres roles — la
navegación pasa a la fila inferior, al alcance del pulgar, y las tablas se convierten en fichas
con sus campos etiquetados en vez de recortar columnas. Eso sirve igual abriendo SICOT en el
navegador del teléfono que dentro de la aplicación empaquetada. El proyecto de Android existe y
vive en [`frontend/src-tauri/gen/android`](./frontend/src-tauri).

**Qué no hay:** un APK firmado y publicado —es una decisión de cuenta institucional—, ni versión
para iPhone —exige macOS con Xcode y un programa de pago—, ni funcionamiento sin conexión, que
sigue fuera de alcance por [`ADR-001`](./docs/decisiones/ADR-001-bifurcamiento-de-despliegue.md).

Cómo compilarlo y qué cadena de herramientas exige está en
[`frontend/README.md`](./frontend/README.md). El estado medido de la interfaz en un teléfono, con
el antes y el después, en [la auditoría del 16 de septiembre](./docs/historico/AUDITORIA_MOVIL_2026-09-16.md).

## Herramientas de desarrollo asistido (opcional)

El repo no versiona la maquinaria de asistentes de IA — es regenerable, y el
asistente que se use es una elección de cada máquina. Si quiere los flujos de trabajo de GSD sobre este proyecto:

```bash
npx @opengsd/gsd-core@latest --local --claude
```

Instala bajo `.claude/`, que está ignorado por git salvo `launch.json`.
