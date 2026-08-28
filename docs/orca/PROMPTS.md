# Prompts de la flota — para pegar en cada panel de Orca

Un prompt por panel. Antes de pegarlos, corre `bash docs/orca/sync-worktrees.sh`
para que los archivos que citan existan dentro de cada worktree.

Todos siguen la misma forma: **leer el contrato + leer la tarea + ejecutar +
informar**. El detalle vive en los archivos, no en el prompt, para que se pueda
corregir una tarea sin volver a pegar nada.

---

## 1 · `fix/validaciones-de-entrada`

```text
Trabajas en el worktree de la rama fix-validaciones-de-entrada del proyecto SICOT.

Lee, en este orden y completos, antes de tocar nada:
1. .github/copilot-instructions.md   (contrato funcional del proyecto; manda sobre todo)
2. .claude/orca/CONTRATO_DEL_AGENTE.md  (reglas de la flota: alcance, límites, verificación)
3. .claude/orca/TAREA.md             (tu tarea concreta)

Tu objetivo: que ningún dato llegue a la capa de servicio o a la base sin una
restricción de validación declarada. El alcance por archivos está en TAREA.md y es
estricto — hay otros cinco agentes trabajando en paralelo sobre este mismo commit.

No toques migraciones Flyway, no añadas dependencias, no hagas push ni rebase.
Verifica con `cd backend; .\mvnw.cmd -B -ntp verify` antes de dar nada por terminado.

Ejecuta la tarea completa y termina con el resumen de 5 puntos que pide el contrato.
```

---

## 2 · `fix/transiciones-de-estado`

```text
Trabajas en el worktree de la rama fix-transiciones-de-estado del proyecto SICOT.

Lee, en este orden y completos, antes de tocar nada:
1. .github/copilot-instructions.md
2. .claude/orca/CONTRATO_DEL_AGENTE.md
3. .claude/orca/TAREA.md

Tu objetivo: que los estados de subetapa, etapa y contrato solo cambien por caminos
válidos. Hoy el backend acepta cualquier estado destino que le manden.

Regla crítica: NO inventes el proceso institucional del SENA. Lo que no esté
confirmado en la documentación real se marca PENDIENTE_DE_DEFINIR y se deja pasar;
solo cierras lo aritméticamente imposible y lo que contradice al propio código.
Escribe el mapa de transiciones actual y el propuesto antes de implementar nada.

No toques migraciones, no hagas push. Verifica con
`cd backend; .\mvnw.cmd -B -ntp verify` y comprueba que el flujo completo del
supervisor (6 etapas, 27 subetapas) sigue funcionando de principio a fin.

Ejecuta la tarea completa y termina con el resumen de 5 puntos que pide el contrato.
```

---

## 3 · `fix/seguridad-modulo-ia`

```text
Trabajas en el worktree de la rama fix-seguridad-modulo-ia del proyecto SICOT.

Lee, en este orden y completos, antes de tocar nada:
1. .github/copilot-instructions.md   (presta atención especial a §29 IA y §30 no inventar)
2. .claude/orca/CONTRATO_DEL_AGENTE.md
3. .claude/orca/TAREA.md

Tu objetivo: cerrar la superficie de ataque del módulo de IA. Es el único punto de
SICOT donde entra contenido que nadie del equipo escribió — un PDF subido, una
pregunta del supervisor — y sale hacia un modelo que responde sobre un contrato del
Estado. Trata todo eso como dato no confiable.

El hallazgo de partida más grave está en TAREA.md: POST /api/ia/extraer-contrato no
pasa los archivos por ArchivoValidator. Úsalo, no lo reescribas (es de otra rama).

SICOT corre sobre Ollama local y debe seguir siendo gratuito: nada de APIs de pago.
Si Ollama no está, el sistema falla honestamente con 503 — no añadas fallbacks que
fabriquen una respuesta.

Verifica con `cd backend; .\mvnw.cmd -B -ntp verify`.
Ejecuta la tarea completa y termina con el resumen de 5 puntos que pide el contrato.
```

---

## 4 · `fix/consistencia-api`

```text
Trabajas en el worktree de la rama fix-consistencia-api del proyecto SICOT.

Lee, en este orden y completos, antes de tocar nada:
1. .github/copilot-instructions.md   (§11 endpoints, §13 DTOs, §23 errores)
2. .claude/orca/CONTRATO_DEL_AGENTE.md
3. .claude/orca/TAREA.md

Tu objetivo: que la API se comporte igual en todas partes — mismos códigos HTTP para
las mismas situaciones, mismas formas de respuesta, y documentación que describa lo
que el código hace de verdad.

Empieza por el inventario completo de los 13 controladores y sus ~39 endpoints
(método, ruta, rol exigido, dónde se aplica realmente el control de acceso, código de
éxito, forma de respuesta). Ese inventario es medio entregable: la rama de pruebas
IDOR lo va a necesitar.

El frontend consume esta API con fetch a mano. Léelo (frontend/src/services/) para
saber qué rompes, pero no lo edites sin decirlo. La compatibilidad de API pesa más
que la elegancia: lo que sea feo pero funcione y esté en uso, se documenta, no se
"arregla".

Verifica con `cd backend; .\mvnw.cmd -B -ntp verify`.
Ejecuta la tarea completa y termina con el resumen de 5 puntos que pide el contrato.
```

---

## 5 · `test/cobertura-idor-y-aislamiento`

```text
Trabajas en el worktree de la rama test-cobertura-idor-y-aislamiento del proyecto SICOT.

Lee, en este orden y completos, antes de tocar nada:
1. .github/copilot-instructions.md
2. .claude/orca/CONTRATO_DEL_AGENTE.md
3. .claude/orca/TAREA.md

Tu objetivo: demostrar con pruebas que un supervisor solo alcanza su propio contrato,
por cada puerta de entrada de la API. Esa regla vive hoy en un solo método
(SecurityUtils.verificarAccesoAlContrato) aplicado en 15 sitios de 7 servicios, y
nadie ha comprobado que no falte ninguno.

Esta rama NO modifica ni una línea de backend/src/main. Solo escribe pruebas.
Si una prueba revela una brecha real: la escribes, la marcas @Disabled con el motivo,
la reportas — y no la arreglas. El arreglo es de la rama de su área.

Sigue el patrón de las pruebas que ya existen (ContratoIntegrationTest,
ContratoDeErroresIntegrationTest). Corren con H2: no necesitas Docker ni PostgreSQL.

Verifica con `cd backend; .\mvnw.cmd -B -ntp verify`.
Ejecuta la tarea completa y termina con el resumen de 5 puntos que pide el contrato,
incluyendo la lista de brechas encontradas y a qué rama corresponde cada una.
```

---

## 6 · `chore/limpieza-codigo-muerto`

```text
Trabajas en el worktree de la rama chore-limpieza-codigo-muerto del proyecto SICOT.

Lee, en este orden y completos, antes de tocar nada:
1. .github/copilot-instructions.md   (§2 estabilidad > velocidad, §27 cambios mínimos)
2. .claude/orca/CONTRATO_DEL_AGENTE.md
3. .claude/orca/TAREA.md

Tu objetivo: borrar lo que ya no se usa, y solo eso. La regla de esta rama es una
sola: si no puedes demostrar que está muerto, se queda.

Cada borrado necesita las cuatro evidencias que lista TAREA.md, escritas en el cuerpo
del commit. El diff debe ser solo eliminaciones — nada de renombrar, mover, reordenar
ni "aprovechar para mejorar": eso genera conflictos con las otras cinco ramas.

No borres pruebas. No borres los comentarios largos que explican el porqué de una
decisión: en este repositorio son deliberados y valen más que el código que
acompañan. Ante la duda, el candidato va a la lista de "sospechosos no borrados" del
resumen, que es un entregable igual de válido.

Verifica con `cd backend; .\mvnw.cmd -B -ntp verify`.
Ejecuta la tarea completa y termina con el resumen de 5 puntos que pide el contrato.
```
