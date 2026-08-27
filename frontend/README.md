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

## Interfaz

La aplicación usa un **armazón común** (`components/AppShell.tsx`) para los tres roles: barra
lateral de navegación a la izquierda (contraíble, con contadores de pendientes), cabecera con el
título de la vista y el módulo del usuario, y el contenido a ancho completo.

El **tema por defecto es oscuro institucional** — azul pizarra profundo (`#111C28` de lienzo,
`#0B131D` en barra lateral y cabecera; nunca negro puro) con el verde SENA como acento. Todos los
colores salen de variables CSS que `prefs.tsx` escribe sobre `:root`, así que los presets de
Configuración (Azul Institucional Oscuro, Grafito Oscuro, Claro Institucional, Alto Contraste)
cambian la aplicación entera, incluida la pantalla de login.

`prefs.tsx` versiona el tema guardado (`THEME_VERSION`): al cambiar la paleta base, las
preferencias de color que quedaron en `localStorage` se descartan y se conservan solo las que no
son de apariencia (copiloto, notificaciones). Sin eso, un equipo que ya había usado SICOT
seguiría viendo la paleta anterior restaurada desde el navegador.

El panel del Supervisor abre en la **Bandeja de entrada**, que es deliberadamente sobria: un
saludo, en qué paso va el contrato (indicador compacto de etapas) y la lista de alertas y
pendientes — alertas no leídas del backend, cronograma calculado con las fechas reales, sub-pasos
abiertos de la etapa en curso, documentos sin firmar y la firma electrónica faltante. Nada de
tableros: la bandeja responde "¿qué tengo que hacer?" en dos segundos.

Todo lo cuantitativo vive en la vista **Contrato**: barra de recorrido de extremo a extremo con el
paso actual marcado, indicadores (avance global, etapas cerradas, sub-pasos por cerrar, vigencia),
ficha completa del contrato, acordeón de etapas y tablero de gráficas. El **Copiloto** ocupa una
columna propia de altura completa a la derecha y se puede plegar desde la cabecera cuando se
necesita el ancho para leer la ficha o las gráficas.

Ningún control de la interfaz es decorativo: todo botón visible ejecuta su acción. Lo que no
existe todavía se dice con texto, no con un botón deshabilitado.

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
│   ├── SupervisorPanel.tsx    Orquesta las cinco vistas del supervisor (bandeja, contrato,
│   │                           alertas, documentos, registros) e incluye helpers privados:
│   │                           StepCircle, ProgressBar, AlertCard y los estados
│   │                           vacío/carga/error de "sin contrato asignado"
│   └── GestionPanel.tsx
│
├── components/                Piezas de UI reutilizables entre pantallas
│   ├── AppShell.tsx           Armazón común: barra lateral izquierda + cabecera + contenido
│   ├── AdminPanel.tsx
│   ├── AvatarLayer.tsx
│   ├── Registros.tsx
│   ├── Settings.tsx
│   ├── icons.tsx              Iconos SVG en línea (reemplazaron a los emoji)
│   ├── ui.tsx                 Chip, Modal, SenaLogo, StageJourney, StatCard, UserMenu…
│   └── supervisor/            Vistas del panel del supervisor
│       ├── Bandeja.tsx        Bandeja de entrada: pendientes reales + situación actual
│       ├── ContratoInfo.tsx   Ficha completa del contrato asignado
│       └── ContratoGraficas.tsx  Tablero de indicadores (recharts) del contrato
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
