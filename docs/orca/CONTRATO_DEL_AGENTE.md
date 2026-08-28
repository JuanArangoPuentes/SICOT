# Contrato del agente — flota Orca de SICOT

Este documento lo lee **todo** agente que Orca arranca en un worktree de SICOT,
antes que cualquier otra cosa. Su tarea concreta está en `.claude/orca/TAREA.md`
(dentro del worktree) o en `docs/orca/tareas/` (en el repo principal).

---

## 1. Lo primero: la instrucción del proyecto ya existe

Antes de tocar código, lee **`.github/copilot-instructions.md`**. Es el contrato
funcional de SICOT —versionado y escrito por el equipo— y manda sobre cualquier
cosa que digas o supongas tú. En particular:

- **§2 Estabilidad > velocidad** — inspeccionar, comprender, cambio mínimo.
- **§30 Regla de oro: NO INVENTAR** — nunca mostrar como real algo que el sistema
  no sabe. Un dato falso en un sistema de contratación pública puede llevar a una
  decisión equivocada sobre un contrato del Estado.
- **§31 Prioridades** — seguridad > correctitud de datos > integridad backend >
  integridad frontend > compatibilidad API > tests > rendimiento > refactor.
- **§33 Propiedad de áreas** — hay carpetas con responsable asignado.

Este documento no reemplaza esas instrucciones: las aplica al trabajo en paralelo.

---

## 2. Dónde estás

Corres en un **git worktree aislado** creado por Orca:

```text
C:\Users\juant\orca\workspaces\Proyecto SICOT\<rama>
```

Comparte el historial de git con el repo principal, pero es un checkout propio.
Puede haber **otros agentes trabajando al mismo tiempo**, cada uno en su rama y
todos partiendo del mismo commit base. Por eso el alcance de tu tarea está
delimitado por archivos: respetarlo es lo que permite integrar todas las ramas
después sin que se peleen. Da por hecho que no eres el único aunque no veas a
nadie.

- **No** te salgas a `C:\Users\juant\Downloads\Proyecto SICOT` (el checkout de
  Juan) ni a los worktrees de las otras ramas. Si necesitas algo de ahí, pídelo.
- **No** cambies de rama, ni hagas `rebase`, `merge`, `reset --hard` ni
  `push --force`. La integración la coordina Juan.
- **No** toques archivos que tu tarea marca como fuera de alcance, aunque veas
  algo mejorable. Anótalo en el resumen final; otra rama puede ser su dueña.

---

## 3. Límites duros (no negociables)

| Prohibido | Por qué |
|---|---|
| Migraciones Flyway (`backend/src/main/resources/db/migration/`) y SQL directo | La base es responsabilidad de Juliana (§33). Si tu arreglo necesita esquema, **detente y repórtalo**. |
| `.vscode/settings.json`, `backend/direct-dependencies.txt` | Configuración compartida, dueña asignada (§33). |
| Añadir dependencias al `pom.xml` o al `package.json` | Cambio arquitectónico: requiere aprobación previa (§28). |
| Rediseñar la UI (colores, tipografía, layouts, tarjetas, botones) | El diseño existente es diseño aprobado (§6). |
| Servicios de IA de pago, SaaS con límite de uso o cualquier cosa que cueste | SICOT debe seguir siendo 100 % gratuito en desarrollo y en uso. |
| Inventar endpoints, estados, formatos o reglas del proceso contractual | §18 y §30. Lo no confirmado se marca `PENDIENTE_DE_DEFINIR`. |
| Datos simulados, mocks o "demos" que aparenten funcionar | Un estado vacío honesto siempre gana a un dato inventado (§30.2). |
| `git push`, abrir PRs, borrar ramas | Lo hace Juan cuando revisa. Tú commiteas en local y paras. |

Si tu tarea, para completarse, **exige** cruzar uno de estos límites: haz todo lo
demás, y termina explicando exactamente qué quedó bloqueado y por qué. No lo
resuelvas por la vía rápida.

---

## 4. Cómo se verifica el trabajo

El backend es lo que toca casi toda la flota. Las pruebas corren con H2, sin
Docker y sin PostgreSQL: no necesitas levantar nada.

```powershell
# desde la raíz del worktree, PowerShell
cd backend; .\mvnw.cmd -B -ntp verify
```

```bash
# o desde Git Bash
cd backend && ./mvnw -B -ntp verify
```

Si además tocaste el frontend:

```powershell
cd frontend; npm.cmd run build      # ya incluye tsc --noEmit
```

`verify` es la misma compuerta que corre `.github/workflows/ci.yml` en cada PR.
**Una tarea no está terminada si `verify` no está en verde.** Si estaba en rojo
antes de que llegaras, dilo explícitamente en el resumen en vez de arreglarlo de
paso: eso es alcance de otra rama.

Las pruebas end-to-end de Playwright **no** entran en la verificación: necesitan
backend levantado, PostgreSQL y las cuentas del perfil `dev`.

---

## 5. Commits

Sigue el estilo que ya tiene el historial de SICOT: **español, imperativo, sin
prefijos tipo `feat:`/`fix:`, una línea de asunto que diga qué cambió el sistema**
(no qué archivo tocaste). Ejemplos reales del repo:

```text
Cierra el secreto JWT de desarrollo que llegaba a produccion
Corrige el contrato de errores: 404, 415 y 503 dejan de ser 500
Deja de filtrar la URL interna de Ollama al cliente
```

- Un commit por arreglo coherente, no un commit gigante al final.
- El cuerpo del commit explica **por qué**, y qué se rompía antes.
- Cierra el mensaje con el trailer que ya usa todo el historial:
  `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.
- Si el cambio se verificó con un comando, dilo en el cuerpo
  con una línea del tipo "Verificado con `mvnw verify`.", como hacen los commits
  existentes.
- No hagas `push`.

---

## 6. Qué entregar al terminar

Un resumen final corto, en español, con exactamente esto:

1. **Qué se arregló** — lista de hallazgos reales, cada uno con `archivo:línea`.
2. **Qué se descartó** — cosas que parecían un fallo y no lo eran, y por qué.
   Esto vale tanto como lo arreglado: evita que alguien lo vuelva a auditar.
3. **Qué quedó bloqueado** — lo que necesitaba migración, aprobación u otra rama.
4. **Estado de `verify`** — verde o rojo, con la salida real si está en rojo.
5. **Commits creados** — hashes y asuntos.

No adornes el resultado. Si encontraste dos cosas en vez de diez, el informe dice
dos. Un informe honesto y corto es un buen informe.
