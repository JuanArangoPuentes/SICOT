# Flota Orca de SICOT — configuración y plan de trabajo

Orca ejecuta varios agentes de Claude Code en paralelo, cada uno en su propio
**git worktree**, sobre el mismo repositorio. Este directorio es la configuración
versionada de esa flota: qué hace cada agente, qué archivos posee, cómo se
verifica su trabajo y en qué orden se integra.

Es documentación del proyecto, no configuración privada de una herramienta: por
eso vive en `docs/` y no en `.claude/` (que está ignorado por git a propósito).

---

## 1. Dónde vive cada cosa

| Ruta | Qué es | ¿En git? |
|---|---|---|
| `docs/orca/CONTRATO_DEL_AGENTE.md` | Reglas comunes a los seis agentes | Sí |
| `docs/orca/tareas/*.md` | Un brief por rama — la fuente de verdad | Sí |
| `docs/orca/PROMPTS.md` | Lo que se pega en cada panel de Orca | Sí |
| `docs/orca/sync-worktrees.sh` | Copia el contrato y el brief a cada worktree | Sí |
| `C:\Users\juant\orca\workspaces\Proyecto SICOT\<rama>\` | El worktree de cada agente | — |
| `<worktree>\.claude\orca\CONTRATO_DEL_AGENTE.md` | Copia local que lee el agente | No (`.claude/*` ignorado) |
| `<worktree>\.claude\orca\TAREA.md` | Su brief concreto | No |
| `<worktree>\.claude\settings.local.json` | Permisos del agente en ese worktree | No |

El agente lee su tarea desde **`.claude/orca/`** dentro de su propio worktree.
Los briefs se editan en `docs/orca/tareas/` y se propagan con el script; nunca al
revés.

---

## 2. Las seis ramas

Todas parten del mismo commit: la punta de `chore/saneamiento-integral`, que a su
vez está en PR hacia `develop`. Cuando ese PR aterrice, las seis abren PR
**directamente hacia `develop`** — y `develop` va a `master` cuando esté validada.
El modelo de ramas del equipo no cambia por usar Orca.

Basar las seis en la punta de `chore/saneamiento-integral` es compatible con ese
PR: una vez mergeado, ese commit es antepasado de `develop`, así que las seis
ramas siguen mezclando limpio sin rebasar nada.

| # | Tarea (Orca) | Rama git | Área que posee |
|---|---|---|---|
| 1 | `fix/validaciones-de-entrada` | `fix-validaciones-de-entrada` | `dto/**` + anotaciones de validación en controladores |
| 2 | `fix/transiciones-de-estado` | `fix-transiciones-de-estado` | `EtapaService`, estado de `ContratoService`, enums de estado |
| 3 | `fix/seguridad-modulo-ia` | `fix-seguridad-modulo-ia` | `ia/**`, `dto/ia/**`, `IAController`, `CopilotoController` |
| 4 | `fix/consistencia-api` | `fix-consistencia-api` | `controller/**` (firmas, códigos, OpenAPI), `exception/**` |
| 5 | `test/cobertura-idor-y-aislamiento` | `test-cobertura-idor-y-aislamiento` | `backend/src/test/**` únicamente |
| 6 | `chore/limpieza-codigo-muerto` | `chore-limpieza-codigo-muerto` | Solo eliminaciones, en cualquier paquete |

Orca muestra el nombre de la tarea con barra (`fix/validaciones-de-entrada`) y el
de la rama con guion (`fix-validaciones-de-entrada`). Es la misma cosa.

### Por qué el reparto es por archivos

Seis agentes sobre el mismo commit base se pisan si no se les asigna territorio.
El reparto está hecho por **capa**, no por síntoma: los DTOs son de una rama, los
servicios de otra, los controladores de otra, las pruebas de otra. Donde dos
ramas tocan el mismo archivo (tareas 1 y 4 en `controller/**`), cada una se
limita a sus líneas: la 1 añade `@Valid`, la 4 cambia códigos y OpenAPI.

---

## 3. Orden de integración

```text
1. fix/validaciones-de-entrada        (DTOs: la base sobre la que todo lo demás valida)
2. fix/transiciones-de-estado         (servicios)
3. fix/seguridad-modulo-ia            (paquete ia, aislado)
4. fix/consistencia-api               (controladores; puede tocar al frontend — revisar con calma)
5. chore/limpieza-codigo-muerto       (borra al final, con lo demás ya dentro)
6. test/cobertura-idor-y-aislamiento  (las pruebas corren contra el resultado real)
```

La limpieza va de penúltima porque una rama anterior puede empezar a usar algo
que hoy parece muerto. Las pruebas van al final para que midan el sistema
integrado, no seis fotos parciales.

---

## 4. Reglas comunes (resumen; el detalle está en el contrato del agente)

- **`.github/copilot-instructions.md` manda.** Es el contrato funcional de SICOT
  y ninguna tarea de la flota lo contradice.
- **Nadie toca migraciones Flyway.** Si un arreglo necesita esquema, se reporta y
  se espera a Juliana (§33).
- **Nadie hace `push`, `rebase`, `merge` ni abre PRs.** Los agentes commitean en
  local; la integración la hace Juan.
- **Nadie añade dependencias** ni cambia de tecnología (§28, §4).
- **Nadie rediseña la UI** (§6).
- **Todo se verifica con `mvnw verify`**, la misma compuerta de la CI.
- **Nada simulado.** Si algo no se puede arreglar, se dice; no se aparenta.

---

## 5. Poner la flota en marcha

```bash
# 1. Propagar contrato + briefs + permisos a los seis worktrees
bash docs/orca/sync-worktrees.sh

# 2. En Orca: abrir el panel de cada rama y pegar su prompt (docs/orca/PROMPTS.md)
```

El script es idempotente: se puede volver a correr cada vez que se edite un brief.
No toca el trabajo del agente — solo escribe dentro de `.claude/`, que git ignora.
Los tres archivos que genera (`orca/CONTRATO_DEL_AGENTE.md`, `orca/TAREA.md` y
`settings.local.json`) **se sobrescriben** en cada corrida: no los edites dentro
del worktree, edítalos aquí en `docs/orca/`.

### Estado de la puesta en marcha

Las dos cosas que bloqueaban el arranque están resueltas:

1. **La funcionalidad de listas de chequeo ya está commiteada** en
   `chore/saneamiento-integral`, así que los agentes la ven: entra en el alcance
   de las validaciones, del inventario de API y de las pruebas de IDOR.
2. **Los seis worktrees están sincronizados** con la punta de esa rama
   (`sync-worktrees.sh --sincronizar-base`).

Cuando el PR `chore/saneamiento-integral → develop` esté mergeado, cambia la
variable `RAMA_BASE` del script a `develop` y vuelve a correrlo con
`--sincronizar-base`:

```bash
RAMA_BASE=develop bash docs/orca/sync-worktrees.sh --sincronizar-base
```

### Olas de lanzamiento

No se lanzan las seis a la vez. Tres de las seis tareas son trabajo de criterio
—codifican reglas de negocio o cambian contratos que el frontend consume— y seis
diffs simultáneos convierten la revisión en un arbitraje. El orden es:

| Ola | Ramas | Por qué juntas |
|---|---|---|
| 1 | `test/cobertura-idor-y-aislamiento` + `fix/seguridad-modulo-ia` | La primera no toca `src/main` (no puede chocar con nadie) y su mapa de brechas dice cuáles de las otras tareas apuntan a un problema real; la segunda está aislada en su paquete y su hallazgo es inequívoco. |
| 2 | `fix/validaciones-de-entrada` + `fix/transiciones-de-estado` | DTOs y servicios: capas distintas, y ya con el mapa de la ola 1 sobre la mesa. |
| 3 | `fix/consistencia-api`, luego `chore/limpieza-codigo-muerto` | La primera puede romper el frontend y se revisa acompañada; la limpieza va de última, cuando ya se sabe qué está vivo. |
