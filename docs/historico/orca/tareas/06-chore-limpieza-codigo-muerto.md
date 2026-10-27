# Tarea 6 — Limpieza de código muerto · rama `chore/limpieza-codigo-muerto`

> Lee primero `.github/copilot-instructions.md` (§2 y §27) y
> `.claude/orca/CONTRATO_DEL_AGENTE.md`.

## Objetivo

Borrar lo que ya no se usa, y **solo** lo que ya no se usa. Esta rama es la más
fácil de hacer mal: un borrado equivocado no falla en compilación, falla en
producción tres semanas después. La regla de esta rama es una sola —
**si no puedes demostrar que está muerto, se queda**.

## Alcance — archivos que **posee** esta rama

- Cualquier archivo de `backend/src/main`, `frontend/src` o `mcp/`, pero
  **únicamente para eliminar**. Nada de renombrar, mover, reordenar ni
  "aprovechar para mejorar".
- Se permite el ajuste mínimo indispensable para que compile tras un borrado
  (quitar un `import` que quedó huérfano, por ejemplo).

## Fuera de alcance

- Refactors, cambios de firma, extracción de métodos, reordenar imports "de
  paso". Todo eso genera conflicto con las otras cinco ramas y hace ilegible el
  diff que Juan tiene que revisar.
- Borrar pruebas. Una prueba que no se ejecuta es un hallazgo que se reporta, no
  un archivo que se elimina.
- Borrar comentarios largos que explican el porqué de una decisión. En este
  repositorio son deliberados y valen más que el código que acompañan.
- `backend/src/main/resources/db/migration/`, `.vscode/settings.json`,
  `backend/direct-dependencies.txt` — dueña asignada (§33).
- Archivos generados: `backend/target/**`, `node_modules/**`, `dist/**`.

## Hallazgos ya verificados (punto de partida, no lista cerrada)

1. **`ia/CopilotoChatService.responder(Long, String)` (línea 94)** —
   sobrecarga de dos argumentos que solo delega en la de tres. El único llamador
   real (`CopilotoController:33`) usa la de tres. Verificado con `grep` sobre
   todo `backend/src`, y **revalidado sobre `develop` con las cinco ramas ya
   integradas**: sigue sin usarla nadie. Es el caso limpio.
2. ~~**`service/ArchivoValidator.contentTypeDe`** — su rama `default` parecía
   inalcanzable.~~ **Pista falsa, descartada al ejecutar la tarea:**
   `TipoDocumento` tiene cinco constantes (`PDF`, `DOCX`, `XLSX`, `IMAGEN`,
   `OTRO`) y el `switch` solo cubre tres, así que el `default` es obligatorio
   para que compile. Quitarlo no sería una eliminación.
3. **`frontend/src/imports/`** — carpeta con nombre de andamiaje. El commit
   `22b1e67` ya retiró "el andamiaje de Figma Make"; comprueba si quedó algo sin
   referenciar.
4. **`frontend/src/data/`** — revisa si queda algún dato de ejemplo. Los commits
   `3452c57` y `d482d06` eliminaron datos fabricados del frontend; lo que quede
   sin usar aquí es tanto limpieza como higiene de la regla de oro (§30).
5. ~~**`mcp/`** — no aparece en la CI ni en el `README` principal.~~ **Medio
   falso:** no entra en la CI, pero sí está en el `README` principal, en el
   diagrama de arquitectura y en la tabla de subproyectos. Está vivo.

## Cómo demostrar que algo está muerto

Para cada candidato, deja escrita la evidencia en el cuerpo del commit:

- `grep -rn "<nombre>" backend/src frontend/src mcp/ docs/ .github/` sin
  resultados fuera de su propia definición.
- No es un punto de entrada de framework: no lo invoca Spring por reflexión
  (`@Component`, `@Bean`, `@Service`, `@RestController`, `@EventListener`,
  `@Scheduled`), no es un DTO deserializado por Jackson, no se referencia desde
  `application.properties`, ni desde una plantilla, ni desde una migración.
- No lo usa el frontend por su nombre de ruta o de campo JSON.
- `cd backend; .\mvnw.cmd -B -ntp verify` sigue en verde después del borrado.

Los cuatro puntos, siempre. Si alguno no se puede comprobar, el candidato pasa a
la lista de "sospechosos no borrados" del resumen final — que es un entregable
igual de válido que el borrado.

## Eres la última, y eso es a tu favor

Las otras cinco tareas ya están integradas en `develop` (cuatro mergeadas, la de
consistencia de API en PR). Arrancas con el sistema completo delante, que es
justo lo que esta tarea necesitaba: ninguna rama va a empezar a usar, después de
ti, algo que tú diste por muerto.

A cambio, la responsabilidad es toda tuya: **haz tus propias comprobaciones sobre
el estado actual**. Los dos candidatos de arriba están revalidados contra
`develop`, pero el resto de la lista es una pista, no un veredicto.

## Criterios de aceptación

- [ ] Cada borrado tiene su evidencia escrita en el commit.
- [ ] El diff es solo eliminaciones (más los `import` huérfanos).
- [ ] Ninguna prueba borrada, ningún comentario explicativo borrado.
- [ ] `cd backend; .\mvnw.cmd -B -ntp verify` en verde, y
      `cd frontend; npm.cmd run build` también si tocaste el frontend.
- [ ] El resumen incluye la lista de "sospechosos no borrados" y por qué.

---

## Resultado — completada

Commits `833eb22`, `76eea16`, `a08c44b`. Diff total: **5 archivos, 20 líneas,
solo eliminaciones**, sin un solo `import` huérfano. `verify` en verde: 125
pruebas, 0 fallos, 0 desactivadas; y `npm run build` del frontend también.

Cinco símbolos muertos eliminados, cada uno con sus cuatro evidencias escritas en
el cuerpo de su commit:

| Símbolo | Por qué estaba muerto |
|---|---|
| `CopilotoChatService.responder(Long, String)` | Sobrecarga que solo delegaba con `null`; el único llamador usa la de tres argumentos |
| `JwtService.extractUsuarioId` | Cero llamadores: el filtro resuelve el usuario por email, nunca por el claim |
| `contratoService.getContrato(id)` | Cero consumidores; la UI usa solo `getContratos` |
| `api/client.getAuthToken()` | Cero consumidores; `apiFetch` lee la variable de módulo |
| `api/types.ChatRequest` | Nunca se importa, y estaba obsoleta: le faltaba `historial` |

**Lo que no borró vale tanto como lo que borró.** Descartó cuatro de las cinco
pistas de este brief —dos de ellas equivocadas de origen, corregidas arriba— y
dejó por escrito dos sospechosos que **no** se tocan: las constantes
`TipoDocumento.IMAGEN` y `OTRO`, que hoy no asigna nadie pero pertenecen a un
enum persistido (`@Enumerated(EnumType.STRING)`) y confirmarlas exige inspección
de base de datos, que es área con dueño asignado (§33); y un par de símbolos
sobre-exportados que no son código muerto, solo `export` de más.
