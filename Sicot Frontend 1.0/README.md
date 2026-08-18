# SICOT — Frontend

Frontend de SICOT (React 19 + TypeScript + Vite + Tailwind CSS v4), integrado contra el
backend real de Spring Boot — sin datos simulados: lo que se ve viene de la API o de un
estado vacío honesto cuando la API todavía no tiene información.

## Cómo correrlo

Requiere el backend (`sicot-backend`) corriendo en `http://localhost:8080` y PostgreSQL activo.

```bash
npm install
npm run dev
```

Abre la URL que indique la terminal (por defecto http://localhost:8443).

## Cuentas de acceso (desarrollo)

El login es real y valida contraseña contra el backend (BCrypt + JWT). Usuarios de desarrollo
(creados por `DataInitializer` en el backend, ver `sicot-backend/README.md` para las claves):

- `administrador@soy.sena.edu.co` → Panel Administrador
- `gestion@soy.sena.edu.co` → Panel Gestión y Contratación
- `supervisor@soy.sena.edu.co` → Panel Supervisor

## Estructura

```
src/
├── App.tsx                    Orquestador raíz: enruta entre pantallas y monta los providers
├── main.tsx                   Punto de entrada de React (sin cambios)
├── index.css                  Estilos globales, variables CSS, Tailwind v4 (sin cambios)
├── prefs.tsx                  Contexto de preferencias/avatar (sin cambios)
│
├── types/
│   └── domain.ts               Tipos compartidos: Screen, Tab, Step, SubStep, ChatMsg…
│
├── data/
│   └── contractFlow.ts         Config del proceso GCCON-P-010: etapas, tutorial, copiloto,
│                                catálogo de documentos. CONTRACT llega vacío (a poblar desde API).
│
├── screens/                    Una pantalla completa por archivo
│   ├── LoginScreen.tsx
│   ├── SupervisorWelcome.tsx
│   ├── SupervisorPanel.tsx     (incluye helpers privados: StepCircle, ProgressBar,
│   │                            ProminentAlerts, AlertCard — solo se usan aquí)
│   └── GestionPanel.tsx
│
└── components/                 Piezas de UI reutilizables entre pantallas (sin cambios)
    ├── AdminPanel.tsx
    ├── AvatarLayer.tsx
    ├── Registros.tsx
    ├── Settings.tsx
    └── ui.tsx                  Chip, SenaLogo, SicotBadge, StageProgressBar, UserMenu…
```

## Qué NO cambió (a propósito)

- Ningún estilo inline, className ni variable CSS
- Ninguna estructura de JSX
- Ninguna lógica de estado o de negocio
- No se introdujo React Router, TanStack Query, React Hook Form ni Lucide React —
  esa migración al stack oficial queda pendiente como una fase aparte, deliberada
  y verificable, para no arriesgar diferencias visuales al mezclarla con esta limpieza.

## Verificación realizada

- `npx tsc --noEmit` → 0 errores
- `npm run build` → build de producción exitoso
- El CSS generado es **byte-idéntico** al de la versión sin reestructurar (mismo hash de archivo)
