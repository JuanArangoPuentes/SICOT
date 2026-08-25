# SICOT — Frontend

Frontend de SICOT (React 19 + TypeScript 5.7 + Vite 8 + Tailwind CSS v4), integrado contra el
backend real de Spring Boot — **sin datos simulados**: lo que se ve viene de la API o de un
estado vacío honesto cuando la API todavía no tiene información. Si una consulta falla, se
muestra el error; nunca un estado tranquilizador (ver §30 de
[`.github/copilot-instructions.md`](../.github/copilot-instructions.md)).

## Cómo correrlo

Requiere el backend corriendo en `http://localhost:8080` y PostgreSQL activo.

```bash
npm install
npm run dev
```

Abre http://localhost:8443.

## Comandos

| Comando | Qué hace |
|---|---|
| `npm run dev` | Servidor de desarrollo con recarga en caliente |
| `npm run typecheck` | `tsc --noEmit` — solo chequeo de tipos |
| `npm run build` | Chequeo de tipos **y** build de producción |
| `npm run preview` | Sirve el build de producción localmente |
| `npm run format` | Formatea con oxfmt |
| `npm run test:e2e` | Pruebas end-to-end con Playwright |

`build` incluye el chequeo de tipos a propósito: un error de tipos debe romper el build, no
colarse hasta producción.

### Pruebas end-to-end

`npm run test:e2e` levanta Vite automáticamente, pero **necesita además el backend, PostgreSQL
y las cuentas del perfil `dev` ya sembradas**. Por eso no corre en CI. Los specs están en
`e2e/specs/`; hoy cubren solo autenticación.

## Cuentas de acceso (desarrollo)

El login es real y valida contraseña contra el backend (BCrypt + JWT). Usuarios creados por
`DataInitializer` **solo bajo el perfil `dev`** (claves en [`backend/README.md`](../backend/README.md)):

- `administrador@soy.sena.edu.co` → Panel Administrador
- `gestion@soy.sena.edu.co` → Panel Gestión y Contratación
- `supervisor@soy.sena.edu.co` → Panel Supervisor

## Estructura

```
src/
├── App.tsx                    Orquestador raíz: enruta entre pantallas y monta los providers
├── main.tsx                   Punto de entrada de React
├── index.css                  Estilos globales, variables CSS, Tailwind v4
├── prefs.tsx                  Contexto de preferencias/tema (persistidas en localStorage)
│
├── types/
│   └── domain.ts              Tipos compartidos: Screen, Tab, Step, SubStep, ChatMsg…
│
├── data/
│   └── contractFlow.ts        Proceso GCCON-P-010: etapas, tutorial, catálogo de documentos
│
├── screens/                   Una pantalla completa por archivo
│   ├── LoginScreen.tsx
│   ├── SupervisorPanel.tsx    (incluye helpers privados: StepCircle, ProgressBar,
│   │                           ProminentAlerts, AlertCard, y los estados vacío/carga/error)
│   └── GestionPanel.tsx
│
├── components/                Piezas de UI reutilizables entre pantallas
│   ├── AdminPanel.tsx
│   ├── AvatarLayer.tsx
│   ├── Registros.tsx
│   ├── Settings.tsx
│   ├── icons.tsx              Iconos SVG en línea (reemplazaron a los emoji)
│   └── ui.tsx                 Chip, Modal, TopBar, SenaLogo, StageProgressBar…
│
└── services/                  Capa de acceso al backend — TODA transformación va aquí
    ├── api/
    │   ├── client.ts          fetch envuelto: token JWT, manejo de 401, ApiError
    │   └── types.ts           DTOs del backend, espejo 1:1 de las respuestas reales
    ├── authService.ts         login
    ├── contratoService.ts     contratos
    ├── etapaService.ts        etapas y sub-etapas
    ├── documentoService.ts    documentos, extracción IA, generación, firma, chat del copiloto
    ├── alertaService.ts       alertas
    ├── registroService.ts     bitácora de auditoría
    ├── usuarioService.ts      usuarios
    ├── firmaService.ts        firmas electrónicas
    ├── formatoService.ts      catálogo de formatos
    ├── mappers.ts             DTO del backend → modelo de la UI
    ├── format.ts              formato de moneda (COP) y fechas
    └── session.ts             sesión persistida en localStorage
```

`services/` es la frontera: la UI nunca habla directamente con `fetch` ni transforma datos
crudos del backend dentro del JSX.

## Convenciones

- **Navegación manual**, sin react-router: el estado `Screen` en `App.tsx` decide qué se
  renderiza.
- **Capa HTTP con `fetch`**, sin Axios.
- **El backend es la autoridad**: el frontend no inventa estados, reglas de negocio ni datos.
- **Iconos SVG en línea** (`components/icons.tsx`), no emoji.
- El diseño visual existente se considera aprobado — ver §6 de `copilot-instructions.md`.
