# Flota Orca de SICOT

Orca ejecuta varios agentes en paralelo, cada uno en su propio **git worktree**
sobre este repositorio. Este directorio es la configuración versionada de esa
flota: qué hace cada agente, qué archivos posee, cómo se verifica su trabajo y
qué salió de cada tarea.

Es documentación del proyecto, no configuración privada de una herramienta: por
eso vive en `docs/` y no en `.claude/`, que está ignorado por git a propósito.

---

## 1. Cómo está montado

```text
docs/orca/
├── README.md               este archivo: cómo se opera la flota y en qué estado está
├── CONTRATO_DEL_AGENTE.md  reglas comunes a todos los agentes
├── sync-worktrees.sh       propaga la configuración a cada worktree
└── tareas/
    └── NN-<rama-git>.md    un brief por rama — la fuente de verdad de cada tarea
```

El script descubre las tareas leyendo `tareas/`: el nombre del archivo **es** el
nombre de la rama. Añadir una tarea es añadir un brief y crear su worktree en
Orca; el script no se toca.

En cada worktree deja cuatro archivos generados, dentro de `.claude/` para que
git los ignore:

| Archivo | Qué es |
|---|---|
| `.claude/orca/CONTRATO_DEL_AGENTE.md` | Copia de las reglas comunes |
| `.claude/orca/TAREA.md` | Copia del brief de esa rama |
| `.claude/orca/PROMPT.txt` | Lo que se pega en el panel de Orca |
| `.claude/settings.local.json` | Permisos del agente en ese worktree |

**Se sobrescriben en cada corrida.** No se editan dentro del worktree: se editan
aquí, en `docs/orca/`, y se propagan.

Sobre `settings.local.json`: **es una comodidad, no un aislamiento**. Recorta los
permisos que el agente tendría que confirmar a mano en el uso normal —compilar,
leer, commitear— y bloquea lo que no debe hacer nunca —`push`, `rebase`, `merge`,
abrir PRs, leer `.env`—. Pero las reglas casan por prefijo del comando, así que
una orden compuesta o escrita de otra forma puede quedarse fuera del patrón: no
es una caja de arena y no hay que confiarle nada crítico. **La restricción real
es el contrato del agente**, que dice por escrito qué no se toca y por qué; el
archivo de permisos solo evita que el operador esté aprobando `mvnw verify` cada
cinco minutos.

---

## 2. Operación

```bash
# Propagar contrato, briefs, prompts y permisos a todos los worktrees
bash docs/orca/sync-worktrees.sh

# Además, adelantar a develop las ramas que no tengan trabajo propio
bash docs/orca/sync-worktrees.sh --sincronizar-base
```

Después, en Orca: abrir el panel de la rama y pegar el contenido de su
`.claude/orca/PROMPT.txt`. El prompt es el mismo para todos salvo el nombre de la
rama — lo que cambia entre tareas vive en el brief, no en el prompt, para que no
haya dos sitios que puedan contradecirse.

`--sincronizar-base` **nunca pisa trabajo**: omite cualquier worktree con
commits propios o con cambios sin commitear, y lo dice. Una rama con trabajo que
se haya quedado atrás necesita un `git merge develop` hecho a mano, dentro de su
worktree y con el agente parado.

La rama base es `develop`. Para apuntar a otra: `RAMA_BASE=otra-rama bash …`.

### Reglas comunes (el detalle está en el contrato del agente)

- **`.github/copilot-instructions.md` manda.** Ninguna tarea lo contradice.
- **Nadie toca migraciones Flyway** — si un arreglo necesita esquema, se reporta.
- **Nadie hace `push`, `rebase`, `merge` ni abre PRs.** Los agentes commitean en
  local; la integración es de una persona.
- **Nadie añade dependencias** ni rediseña la UI.
- **Todo se verifica con `mvnw verify`**, la misma compuerta que la CI.
- **Nada simulado.** Lo que no se puede arreglar se dice; no se aparenta.

### Por qué el reparto es por capas

Varios agentes sobre el mismo commit base se pisan si no se les asigna
territorio. El reparto va por **capa** y no por síntoma: los DTOs son de una
rama, los servicios de otra, los controladores de otra, las pruebas de otra.
Donde dos ramas tocan el mismo archivo, cada una se limita a sus líneas. En la
primera ola esto funcionó: las cuatro ramas simultáneas se integraron **sin un
solo conflicto**.

---

## 3. Estado

| # | Rama | Estado | PR |
|---|---|---|---|
| 1 | `fix-validaciones-de-entrada` | ✅ mergeada | #5 |
| 2 | `fix-transiciones-de-estado` | ✅ mergeada | #6 |
| 3 | `fix-seguridad-modulo-ia` | ✅ mergeada | #7 |
| 4 | `fix-consistencia-api` | 🔵 PR abierto | #9 |
| 5 | `test-cobertura-idor-y-aislamiento` | ✅ mergeada | #8 |
| 6 | `chore-limpieza-codigo-muerto` | ⚪ sin lanzar | — |

Cada brief cierra con una sección **Resultado** que cuenta qué salió de esa
tarea. La 6 va deliberadamente al final: necesita el sistema ya integrado para
saber qué código está muerto de verdad.

### Qué produjo la flota

Partiendo de 54 pruebas, `develop` está hoy en **125 pruebas, 0 fallos y 2
desactivadas** — las dos que documentan las brechas de acceso. El PR #9 las
habilita: cuando entre, quedan **125 con 0 desactivadas**.

- Dos brechas reales de control de acceso encontradas y cerradas: el oráculo de
  enumeración entre contrato ajeno y contrato inexistente, y el orden de
  comprobaciones en `DocumentoService.firmar`.
- Transiciones de estado validadas en el backend, entrada acotada en los DTOs,
  contenido no confiable delimitado antes de llegar al modelo de IA, y el
  contrato de la API documentado endpoint por endpoint.

### Cómo se lanzó, y cómo conviene lanzar la próxima

No se lanzan todas a la vez. Las tareas de criterio —las que codifican reglas de
negocio o cambian contratos que el frontend consume— producen diffs que hay que
arbitrar, y seis a la vez son inrevisables.

El orden que funcionó: primero la de pruebas (no toca `src/main`, no puede
chocar con nadie, y su mapa de brechas dice cuáles de las demás apuntan a un
problema real) junto a la de seguridad de IA (aislada en su paquete). Después
las de DTOs y servicios. La de controladores, que es la que puede romper al
frontend, acompañada. La limpieza, al final.

Lección de la primera ola: **una rama que se queda atrás mientras las demás se
integran acaba trabajando a ciegas.** `fix-consistencia-api` tuvo que arreglar
dos brechas cuyas pruebas estaban en `develop` pero no en su worktree. Si una
tarea va a arrancar tarde, primero se le trae `develop`.
