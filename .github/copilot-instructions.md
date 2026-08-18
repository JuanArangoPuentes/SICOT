# SICOT — Instrucciones de desarrollo para GitHub Copilot Agent

## 1. Identidad del proyecto

SICOT significa:

**Sistema Inteligente para la Gestión y Acompañamiento de Contratos**

Es una plataforma para gestionar el ciclo de vida de contratos, documentación, etapas, alertas, registros y posteriormente capacidades de inteligencia artificial.

El proyecto está dividido en:

```text
SICOT
├── Sicot Frontend 1.0
└── sicot-backend
```

El workspace contiene ambos proyectos y deben tratarse como un único sistema.

---

# 2. Regla principal

## ESTABILIDAD > VELOCIDAD

El sistema ya tiene una integración funcional entre frontend y backend.

Antes de modificar cualquier cosa:

1. Inspeccionar.
2. Comprender.
3. Identificar dependencias.
4. Determinar impacto.
5. Proponer el cambio.
6. Esperar aprobación cuando el cambio sea arquitectónico, destructivo o afecte varios módulos.
7. Implementar el cambio mínimo necesario.
8. Ejecutar pruebas.
9. Revisar el diff.

Nunca modificar por comodidad algo que ya funciona.

---

# 3. Tecnologías oficiales

## Frontend

* React 19
* ReactDOM 19
* TypeScript 5.7
* Vite 8
* Tailwind CSS v4
* Recharts
* Fetch API
* Node.js 24
* puerto de desarrollo: 8443

## Backend

* Java 21
* Spring Boot 3.5.3
* Spring Web
* Spring Data JPA
* Hibernate
* Spring Security
* JWT
* BCrypt
* Flyway
* Maven
* PostgreSQL 18.4

## Base de datos

Base:

```text
sicot
```

Backend:

```text
http://localhost:8080
```

Frontend:

```text
http://localhost:8443
```

---

# 4. No cambiar tecnologías

No agregar ni reemplazar:

* Axios
* React Query
* Redux
* Zustand
* react-router
* Prisma
* Node backend
* otro ORM
* otro framework frontend

salvo que exista una razón técnica real y se apruebe explícitamente.

La capa HTTP del frontend utiliza `fetch`.

---

# 5. Frontend

El frontend utiliza navegación manual mediante el estado `Screen` de `App.tsx`.

No existe react-router.

Las pantallas principales incluyen:

```text
login
supervisor-welcome
supervisor-panel
gestion-panel
admin-panel
```

No sustituir esta arquitectura por react-router sin aprobación explícita.

---

# 6. Diseño visual

El frontend existente se considera diseño aprobado.

NO:

* rediseñar componentes;
* cambiar colores;
* cambiar tipografía;
* cambiar layouts;
* cambiar tamaños;
* eliminar tarjetas;
* eliminar botones;
* convertir pantallas en interfaces genéricas;
* simplificar la UI;
* reemplazar componentes visuales innecesariamente.

Las transformaciones backend → frontend deben realizarse preferentemente en:

```text
src/services/
```

o funciones de mapeo.

La UI debe recibir modelos adaptados.

---

# 7. Backend como autoridad funcional

El backend define:

* permisos;
* roles;
* estados;
* validaciones;
* contratos API;
* reglas de negocio;
* datos reales.

El frontend NO debe inventar estados ni reglas de negocio.

El frontend puede adaptar la representación visual.

---

# 8. Roles

Los únicos roles oficiales son:

```text
ADMINISTRADOR
GESTION
SUPERVISOR
```

No inventar otros roles.

---

# 9. Usuarios de desarrollo

Usuarios de desarrollo actuales:

```text
administrador@soy.sena.edu.co
Admin123*
ADMINISTRADOR
```

```text
gestion@soy.sena.edu.co
Gestion123*
GESTION
```

```text
supervisor@soy.sena.edu.co
Supervisor123*
SUPERVISOR
```

Son exclusivamente credenciales de desarrollo/demo.

No mostrarlas en la interfaz.

No enviarlas a producción.

No modificarlas sin aprobación.

---

# 10. Autenticación

Login:

```http
POST /api/auth/login
```

Request:

```json
{
  "email": "usuario@soy.sena.edu.co",
  "password": "..."
}
```

Response:

```json
{
  "token": "...",
  "usuarioId": 1,
  "nombre": "...",
  "email": "...",
  "rol": "..."
}
```

Las peticiones protegidas usan:

```http
Authorization: Bearer <token>
```

La sesión está persistida actualmente en:

```text
localStorage
```

La clave utilizada por el frontend es:

```text
sicot.session
```

No almacenar contraseñas.

No imprimir JWT completo en consola.

---

# 11. Endpoints existentes

## Auth

```http
POST /api/auth/login
```

## Usuarios

```http
GET /api/usuarios
GET /api/usuarios/{id}
POST /api/usuarios
PUT /api/usuarios/{id}
PATCH /api/usuarios/{id}/estado
```

Los endpoints administrativos están protegidos por rol.

---

## Contratos

```http
GET /api/contratos
GET /api/contratos/{id}
POST /api/contratos
PUT /api/contratos/{id}
PATCH /api/contratos/{id}/supervisor
PATCH /api/contratos/{id}/estado
```

El listado soporta filtros:

```text
supervisorId
estado
```

---

## Etapas

```http
GET /api/contratos/{contratoId}/etapas
GET /api/contratos/{contratoId}/etapas/{etapaId}
```

---

## Subetapas

```http
GET /api/etapas/{etapaId}/subetapas
PATCH /api/subetapas/{id}/estado
```

El backend recalcula automáticamente el porcentaje/estado de la etapa.

---

## Documentos

```http
GET /api/contratos/{contratoId}/documentos
```

---

## Alertas

```http
GET /api/contratos/{contratoId}/alertas
GET /api/alertas
PATCH /api/alertas/{id}/leida
```

---

## Registros

```http
GET /api/contratos/{contratoId}/registros
GET /api/registros
```

---

# 12. Estados oficiales

## Contrato

Los estados provienen del backend.

No inventar nuevos valores.

## Etapa/Subetapa

Backend:

```text
PENDIENTE
EN_CURSO
COMPLETADA
```

Frontend puede mapearlos visualmente a:

```text
pending
active
completed
```

No cambiar esta representación visual si el mapeo existente funciona.

---

# 13. DTOs

Antes de construir cualquier request o response:

INSPECCIONAR el DTO real del backend.

Nunca inferir campos.

Nunca inventar nombres.

Nunca duplicar DTOs inconsistentes.

---

# 14. Base de datos

PostgreSQL:

```text
database: sicot
```

Las migraciones son administradas por Flyway.

NO:

* ejecutar DROP DATABASE;
* eliminar migraciones;
* modificar tablas manualmente sin necesidad;
* cambiar IDs existentes;
* borrar datos seed arbitrariamente.

Toda modificación estructural debe hacerse mediante migración Flyway.

---

# 15. Datos seed

El seed original representa el flujo GCCON-P-010.

No sustituir el seed por datos arbitrarios.

Actualmente existen contratos, etapas, subetapas, documentos, alertas, registros y usuarios de desarrollo.

---

# 16. Datos de prueba temporales

Durante pruebas se generaron datos adicionales.

No asumir que forman parte del seed.

Antes de eliminarlos:

1. verificar IDs;
2. comprobar relaciones;
3. comprobar foreign keys;
4. confirmar que no pertenecen al seed.

---

# 17. Funcionalidades realmente conectadas

Actualmente están conectadas al backend:

* login JWT;
* sesión;
* logout;
* contratos;
* detalle de contrato;
* etapas;
* subetapas;
* cambio de estado;
* alertas;
* marcar alertas;
* documentos;
* registros;
* Gestión de contratos;
* creación de contratos;
* usuarios;
* activación/desactivación;
* autorización por roles.

---

# 18. Funcionalidades todavía MOCK

Estas funcionalidades NO tienen backend funcional completo y NO deben fingirse como reales:

* copiloto conversacional;
* tutorial;
* generación automática de documentos;
* firma electrónica;
* lectura/análisis real de ficha PDF;
* catálogo documental avanzado del administrador;
* firmas electrónicas administrativas;
* gráfica avanzada de actividad;
* notificación por correo;
* integración SECOP II.

NO inventar endpoints para ellas.

---

# 19. Riesgos conocidos

## Riesgo 1

Supervisor sin contrato ACTIVO o sin etapas podría causar errores si el código asume que existe una etapa determinada.

Debe manejarse con estados vacíos.

Nunca utilizar `!` de forma insegura sobre datos backend.

---

## Riesgo 2

No mostrar alertas mock como si fueran alertas reales.

Si backend devuelve:

```text
[]
```

eso significa:

```text
No hay alertas reales.
```

No reemplazar automáticamente con datos inventados.

---

## Riesgo 3

Producción.

Los secretos de:

```text
DB_PASSWORD
JWT_SECRET
```

deben venir de variables de entorno.

Nunca incluir secretos de producción en Git.

---

## Riesgo 4

Credenciales de desarrollo.

Los usuarios seed son únicamente para DEV/DEMO.

---

# 20. Mapeo de datos

Preferir:

```text
Backend DTO
    ↓
Mapper / Service
    ↓
Frontend Model
    ↓
UI
```

No poner transformaciones complejas dentro de JSX.

---

# 21. Fechas

Backend utiliza ISO.

Frontend puede mostrar:

```text
dd/mm/aaaa
```

Utilizar una función común.

No usar múltiples formatos distintos.

---

# 22. Moneda

Los valores económicos deben mostrarse correctamente en COP.

No cambiar valores numéricos por strings antes de realizar cálculos.

Formatear únicamente en presentación.

---

# 23. Manejo de errores

El backend utiliza respuestas JSON con:

```text
message
fieldErrors
```

El frontend debe interpretar:

```text
400
401
403
404
409
500
```

No mostrar stack traces al usuario.

Los errores de autenticación deben limpiar sesión cuando corresponda.

---

# 24. CORS

Frontend:

```text
http://localhost:8443
```

Backend debe aceptar ese origen.

No solucionar CORS haciendo:

```text
*
```

sin una razón concreta.

---

# 25. Comandos oficiales

Frontend:

```powershell
npm.cmd install
npm.cmd run dev
npm.cmd run build
```

TypeScript:

```powershell
npx tsc --noEmit
```

Backend:

```powershell
.\mvnw.cmd clean test
```

Ejecutar backend:

```powershell
.\mvnw.cmd spring-boot:run
```

---

# 26. Verificación antes de terminar cualquier tarea

Siempre que se modifique frontend:

```text
npm.cmd run build
npx tsc --noEmit
```

Siempre que se modifique backend:

```text
.\mvnw.cmd clean test
.\mvnw.cmd clean package
```

Si se modifican ambos:

ejecutar todas las pruebas.

---

# 27. Regla de cambios mínimos

Antes de modificar un archivo:

* leerlo;
* buscar dependencias;
* entender su responsabilidad;
* modificar solamente lo necesario.

No reemplazar archivos completos cuando un cambio localizado sea suficiente.

---

# 28. Cambios arquitectónicos

Si una tarea requiere:

* nuevo módulo;
* nueva tabla;
* nueva migración;
* nuevo endpoint;
* cambio de DTO;
* cambio de seguridad;
* nueva dependencia;
* cambio de arquitectura;

primero explicar:

1. por qué;
2. impacto;
3. alternativas;
4. riesgo.

Luego esperar aprobación.

---

# 29. IA

La IA no forma parte todavía del núcleo obligatorio.

No integrar Ollama automáticamente.

Cuando llegue esa fase:

```text
React
 ↓
Spring Boot
 ↓
servicio de IA
 ↓
Ollama
```

La IA no debe acceder directamente a PostgreSQL desde React.

El backend será responsable del contexto y permisos.

---

# 30. Regla de oro

NO inventar.

Cuando algo no esté claro:

* inspeccionar código;
* buscar DTO;
* buscar endpoint;
* buscar migración;
* revisar modelo;
* comprobar comportamiento real.

Si todavía no está claro:

detenerse y preguntar.

---

# 31. Prioridades

Orden de prioridad:

1. Seguridad
2. Correctitud de datos
3. Integridad de backend
4. Integridad de frontend
5. Compatibilidad API
6. Tests
7. Rendimiento
8. Refactorización
9. Nuevas funcionalidades

---

# 32. Objetivo del agente

El agente debe comportarse como un ingeniero senior trabajando sobre un sistema existente.

NO como un generador que rehace el proyecto desde cero.

El código existente tiene valor.

Las funcionalidades existentes tienen valor.

La UI existente tiene valor.

La estabilidad tiene prioridad sobre cualquier mejora estética o refactorización.

FIN DE LAS INSTRUCCIONES.
