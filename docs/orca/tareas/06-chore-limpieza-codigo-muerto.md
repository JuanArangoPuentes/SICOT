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

1. **`ia/CopilotoChatService.responder(Long, String)` (línea ~89)** —
   sobrecarga de dos argumentos que solo delega en la de tres. El único llamador
   real (`CopilotoController:33`) usa la de tres. Verificado con `grep` sobre
   todo `backend/src`: nadie más la invoca. Es el caso limpio.
2. **`service/ArchivoValidator.contentTypeDe`** — tiene una rama `default` en el
   `switch` sobre `TipoDocumento`. Comprueba si el enum puede llegar ahí; si no,
   es código inalcanzable (pero mira antes si la tarea 3 va a empezar a usar este
   método, porque entonces la rama puede dejar de ser inalcanzable).
3. **`frontend/src/imports/`** — carpeta con nombre de andamiaje. El commit
   `22b1e67` ya retiró "el andamiaje de Figma Make"; comprueba si quedó algo sin
   referenciar.
4. **`frontend/src/data/`** — revisa si queda algún dato de ejemplo. Los commits
   `3452c57` y `d482d06` eliminaron datos fabricados del frontend; lo que quede
   sin usar aquí es tanto limpieza como higiene de la regla de oro (§30).
5. **`mcp/`** — no aparece en la CI ni en el `README` principal. Averigua si sigue
   vivo **antes** de proponer nada: si no lo tienes claro, no lo borres, pregunta.

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

## Orden de integración

Esta rama se mezcla **de penúltima**, después de las cuatro ramas de arreglo
(1-4) y antes de la de pruebas (5). Motivo: una de esas ramas puede empezar a
usar algo que hoy parece muerto — la sobrecarga de `responder`, por ejemplo, o la
rama `default` de `contentTypeDe`. Cuando te toque integrar, **vuelve a
comprobar** cada borrado contra el estado ya integrado; no des por bueno el
`grep` de hace tres días.

## Criterios de aceptación

- [ ] Cada borrado tiene su evidencia escrita en el commit.
- [ ] El diff es solo eliminaciones (más los `import` huérfanos).
- [ ] Ninguna prueba borrada, ningún comentario explicativo borrado.
- [ ] `cd backend; .\mvnw.cmd -B -ntp verify` en verde, y
      `cd frontend; npm.cmd run build` también si tocaste el frontend.
- [ ] El resumen incluye la lista de "sospechosos no borrados" y por qué.
