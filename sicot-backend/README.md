# SICOT Backend

Backend del **Sistema Inteligente para la Gestión y Acompañamiento de Contratos** (SENA — Centro Tecnológico del Mobiliario).

Spring Boot 3.x · Java 21 · PostgreSQL · JWT · Flyway · Swagger/OpenAPI.

> ⚠️ **Fase actual:** monolito modular con autenticación JWT, CRUD de usuarios/contratos, flujo de etapas GCCON-P-010, alertas, documentos (solo consulta) y auditoría. Las integraciones externas (SECOP II, firma electrónica, OCR, IA/Ollama, correo) se implementarán en fases posteriores.

---

## 1. Requisitos

| Herramienta | Versión |
|---|---|
| JDK | 21+ (el proyecto compila con `--release 21`) |
| Maven | 3.9+ |
| PostgreSQL | 14+ (probado con 18) |

## 2. Base de datos

```sql
-- (con el usuario superusuario de PostgreSQL)
CREATE USER sicot WITH PASSWORD 'sicot_dev_password';
CREATE DATABASE sicot OWNER sicot;
```

El esquema se crea automáticamente con **Flyway** al primer arranque:
- `V1__create_initial_schema.sql` — esquema (usuarios, contratos, etapas, subetapas, documentos, alertas, registros)
- `V2__seed_dev_data.sql` — datos de desarrollo (3 contratos de ejemplo con el flujo GCCON-P-010)
- `V3__clear_demo_transactional_data.sql` — limpia los datos transaccionales de ejemplo (deja el esquema y los usuarios)
- `V4__create_formatos_documentales.sql` — catálogo de formatos documentales oficiales (carga real de archivos)

Los **usuarios** se crean al arrancar (solo si la tabla está vacía) por `DataInitializer` con contraseñas codificadas en BCrypt:

| Email | Rol | Contraseña (solo desarrollo) |
|---|---|---|
| `administrador@soy.sena.edu.co` | ADMINISTRADOR | `Admin123*` |
| `gestion@soy.sena.edu.co` | GESTION | `Gestion123*` |
| `supervisor@soy.sena.edu.co` | SUPERVISOR | `Supervisor123*` |

## 3. Configuración (variables de entorno)

Copie `.env.example` a un `.env` (o exporte las variables) — los valores por defecto en `application.properties` solo funcionan en desarrollo:

| Variable | Descripción |
|---|---|
| `DB_URL` | URL JDBC (por defecto `jdbc:postgresql://localhost:5432/sicot`) |
| `DB_USERNAME` / `DB_PASSWORD` | Credenciales de la base |
| `JWT_SECRET` | Clave HMAC-SHA256 ≥ 32 bytes **en Base64** (`openssl rand -base64 48`) |
| `JWT_EXPIRATION_MS` | Vigencia del token en ms (por defecto 8 h) |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos separados por coma |
| `PORT` | Puerto HTTP (por defecto 8080) |

## 4. Ejecutar

```bash
mvn spring-boot:run
# o
mvn clean package && java -jar target/sicot-backend-0.1.0.jar
```

- API: <http://localhost:8080/api>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Actuator: <http://localhost:8080/actuator/health>

## 5. Autenticación

1. `POST /api/auth/login` con `{"email": "...", "password": "..."}` → devuelve el `token`.
2. Enviar el token en cada petición: `Authorization: Bearer <token>`.

## 6. Endpoints principales

| Método | Ruta | Rol |
|---|---|---|
| POST | `/api/auth/login` | público |
| GET/POST/PUT | `/api/usuarios`, `/api/usuarios/{id}`, `PATCH /api/usuarios/{id}/estado` | ADMINISTRADOR |
| GET | `/api/contratos` (`?supervisorId=&estado=`) | autenticados |
| GET | `/api/contratos/{id}` | autenticados |
| POST/PUT | `/api/contratos`, `/api/contratos/{id}` | GESTION, ADMINISTRADOR |
| PATCH | `/api/contratos/{id}/supervisor` · `/api/contratos/{id}/estado` | GESTION, ADMINISTRADOR |
| GET | `/api/contratos/{id}/etapas`, `/api/etapas/{id}/subetapas` | autenticados |
| PATCH | `/api/subetapas/{id}/estado` | SUPERVISOR, GESTION, ADMINISTRADOR |
| GET | `/api/contratos/{id}/documentos` | autenticados (carga real de archivos por contrato en fase posterior) |
| GET/PATCH | `/api/contratos/{id}/alertas`, `/api/alertas/{id}/leida` | autenticados |
| GET | `/api/contratos/{id}/registros`, `/api/registros` | autenticados (todos: ADMINISTRADOR) |
| GET | `/api/formatos` | autenticados |
| POST | `/api/formatos` (multipart: `codigo`, `nombre`, `archivo`) | ADMINISTRADOR |
| GET | `/api/formatos/{id}/archivo` (descarga real del archivo) | autenticados |
| DELETE | `/api/formatos/{id}` | ADMINISTRADOR |

**Reglas de negocio destacadas:**
- Al cambiar el estado de una subetapa se recalcula automáticamente el porcentaje y estado de su etapa (todas COMPLETADA → etapa COMPLETADA 100%).
- Solo usuarios con rol SUPERVISOR pueden ser asignados como supervisores.
- Número de contrato y email de usuario son únicos.
- Toda operación de trámite queda registrada en la tabla `registros` (auditoría).

## 7. Estructura del proyecto

```
co.sena.sicot
├── config/      → OpenAPI, DataInitializer (usuarios de desarrollo)
├── controller/  → REST + Swagger
├── dto/         → request/response (validación con Bean Validation)
├── entity/      → JPA + enums (Rol, EstadoContrato, EstadoEtapa, …)
├── exception/   → errores y manejador global (@RestControllerAdvice)
├── mapper/      → entidad ↔ DTO
├── repository/  → Spring Data JPA
├── security/    → JWT, filtro, CORS, BCrypt, autorización por rol
└── service/     → lógica de negocio
```

## 8. Pruebas

```bash
mvn test
```

Las pruebas de integración usan **H2 en memoria** (perfil `test`); las migraciones Flyway se validan contra PostgreSQL real al ejecutar la aplicación.

## 9. Pendiente (fases siguientes)

- Carga y descarga real de documentos **por contrato** (evidencias) — el catálogo general de
  formatos documentales (`/api/formatos`) ya tiene carga/descarga real
- Integración SECOP II (consulta de procesos)
- Firma electrónica de documentos (GCCON-F-018, GCCON-F-031, GIL-F-010, …)
- Asistente IA local (Llama/Qwen) + RAG (Qdrant/pgvector)
- OCR de documentos (PaddleOCR)
- Notificaciones por correo (Outlook)
- Despliegue (docker-compose, variables de entorno reales, HTTPS)
