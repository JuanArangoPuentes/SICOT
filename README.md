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
| [`backend/`](./backend) | API en Spring Boot 3 + Java 25 + PostgreSQL + JWT | [Backend](./backend/README.md) |
| [`mcp/`](./mcp) | Servidor MCP delgado sobre la API real, para asistentes de IA | [MCP](./mcp/README.md) |
| [`docs/fases/`](./docs/fases) | Reportes de inspección/checkpoint por fase del proyecto | — |
| [`docs/auditorias/`](./docs/auditorias) | Auditorías de datos/BD (consultas y resultados) | — |

## Correr todo con Docker (recomendado)

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

## Correr sin Docker (desarrollo día a día)

1. PostgreSQL nativo en `localhost:5432`, base `sicot` (detalle en
   [`backend/README.md`](./backend/README.md)).
2. Backend: `cd backend && mvn spring-boot:run` → http://localhost:8080
3. Frontend: `cd frontend && npm install && npm run dev` → http://localhost:8443

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
persona o agente que contribuya.
