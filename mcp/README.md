# SICOT MCP

Servidor MCP (Model Context Protocol) delgado sobre la API real del backend. No
reimplementa ninguna regla de negocio: cada herramienta es un llamado 1:1 a un endpoint que ya
existe y ya está probado. La fuente de verdad sigue siendo el backend — este paquete es solo
plomería para que un asistente de IA (Claude Desktop, Claude Code, etc.) pueda leer y operar
SICOT con permiso.

## Por qué está curado y no es 1:1 con las ~25 rutas del backend

Exponer cada endpoint mecánicamente como herramienta funciona mal para un LLM: demasiadas
opciones parecidas, sin contexto de cuáles tienen sentido combinar. Este servidor expone 11
herramientas de alto valor (lectura de contratos/etapas/alertas/formatos/usuarios/registros, más
las escrituras que no dependen de reglas de negocio aún sin definir: crear contrato, asignar
supervisor, avanzar una subetapa, marcar alerta leída). Deliberadamente **no** incluye:
administración de cuentas (crear/editar/activar usuarios), cambio de estado de contrato (el
backend todavía no valida transiciones — ver `backend/README.md`), ni carga de formatos
(archivo binario, se puede agregar después con el mismo patrón).

## Configuración

El servidor actúa **como una cuenta real de SICOT** — sus permisos (rol ADMINISTRADOR, GESTION o
SUPERVISOR) determinan qué herramientas funcionan. Variables de entorno:

| Variable | Descripción |
|---|---|
| `SICOT_API_URL` | URL del backend (por defecto `http://localhost:8080`) |
| `SICOT_EMAIL` | Correo de la cuenta SICOT con la que operará el servidor |
| `SICOT_PASSWORD` | Contraseña de esa cuenta |

```bash
npm install
npm run build
```

## Probarlo manualmente

```bash
SICOT_EMAIL=administrador@soy.sena.edu.co SICOT_PASSWORD=... node dist/smoke-test.js
```

Arranca el servidor como subproceso, lista las 11 herramientas y llama tres de ellas (usuarios,
contratos, formatos) contra el backend real para confirmar que la autenticación y el transporte
funcionan de punta a punta.

## Conectarlo a Claude Desktop o Claude Code

Agregar en la configuración de servidores MCP (`claude_desktop_config.json` o equivalente):

```json
{
  "mcpServers": {
    "sicot": {
      "command": "node",
      "args": ["<RUTA-AL-REPO>/mcp/dist/index.js"],
      "env": {
        "SICOT_API_URL": "http://localhost:8080",
        "SICOT_EMAIL": "administrador@soy.sena.edu.co",
        "SICOT_PASSWORD": "reemplazar"
      }
    }
  }
}
```

## Agregar una herramienta nueva

Cada herramienta en `src/index.ts` sigue el mismo patrón: `server.registerTool(nombre, { description, inputSchema }, handler)`,
donde el handler llama `sicotFetch` (en `src/sicotClient.ts`) contra una ruta que **ya existe** en
el backend. No agregar una herramienta para un endpoint que no existe todavía.
