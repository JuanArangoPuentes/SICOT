#!/usr/bin/env bash
#
# Propaga la configuración de la flota Orca a cada worktree.
#
#   bash docs/orca/sync-worktrees.sh [--sincronizar-base] [--help]
#
# Escribe en cada worktree, dentro de `.claude/` (ignorado por git, así que nada
# de esto ensucia el repositorio):
#
#   .claude/orca/CONTRATO_DEL_AGENTE.md   reglas comunes de la flota
#   .claude/orca/TAREA.md                 el brief de esa rama
#   .claude/orca/PROMPT.txt               lo que se pega en el panel de Orca
#   .claude/settings.local.json           permisos del agente en ese worktree
#
# Las tareas NO se listan aquí: se descubren desde `docs/orca/tareas/`, donde
# cada archivo se llama `NN-<rama-git>.md`. Añadir una tarea es añadir un brief
# y crear su worktree en Orca — este script no se toca.
#
# Es idempotente y sobrescribe siempre: los archivos generados no se editan
# dentro del worktree, se editan en `docs/orca/`.
#
# Con --sincronizar-base adelanta además cada rama al último commit de la rama
# base, pero solo si el worktree está limpio y no tiene commits propios. La rama
# base es `develop`; para apuntar a otra: RAMA_BASE=otra-rama bash ...

set -euo pipefail

RAMA_BASE="${RAMA_BASE:-develop}"
SINCRONIZAR_BASE=0

for arg in "$@"; do
  case "$arg" in
    --sincronizar-base) SINCRONIZAR_BASE=1 ;;
    --help|-h) sed -n '2,28p' "$0" | sed 's/^#\s\?//'; exit 0 ;;
    *) echo "Opción desconocida: $arg (usa --help)" >&2; exit 2 ;;
  esac
done

RAIZ="$(git rev-parse --show-toplevel)"
ORIGEN="$RAIZ/docs/orca"

# Ruta del worktree de una rama, según el propio git (no se adivina).
ruta_de_worktree() {
  git -C "$RAIZ" worktree list --porcelain \
    | awk -v r="refs/heads/$1" '/^worktree /{p=substr($0,10)} /^branch /{if (substr($0,8)==r) print p}'
}

escribir_prompt() {
  cat > "$1/.claude/orca/PROMPT.txt" <<PROMPT
Trabajas en el worktree de la rama $2 del proyecto SICOT.

Lee completos y en este orden, antes de tocar nada:

  1. .github/copilot-instructions.md      (contrato funcional de SICOT; manda sobre todo)
  2. .claude/orca/CONTRATO_DEL_AGENTE.md  (reglas de la flota: limites, verificacion, entrega)
  3. .claude/orca/TAREA.md                (tu tarea concreta)

Ejecuta la tarea completa respetando el alcance por archivos que fija TAREA.md.
Puede haber otros agentes trabajando en paralelo sobre la misma base: ese reparto
es lo que permite integrar despues sin conflictos, asi que respetalo aunque no
veas a nadie.

No toques migraciones Flyway, no anadas dependencias, no hagas push ni rebase.

Verifica antes de dar nada por terminado, con el comando de tu shell:

  PowerShell:  cd backend; .\mvnw.cmd -B -ntp verify
  Git Bash:    cd backend && ./mvnw -B -ntp verify

Cierra con el resumen de 5 puntos que pide el contrato.
PROMPT
}

escribir_permisos() {
  cat > "$1/.claude/settings.local.json" <<'JSON'
{
  "permissions": {
    "allow": [
      "Bash(cd:*)",
      "Bash(./mvnw:*)",
      "Bash(mvnw.cmd:*)",
      "Bash(git status:*)",
      "Bash(git diff:*)",
      "Bash(git log:*)",
      "Bash(git show:*)",
      "Bash(git add:*)",
      "Bash(git commit:*)",
      "Bash(grep:*)",
      "Bash(rg:*)",
      "Bash(find:*)"
    ],
    "deny": [
      "Read(.env)",
      "Read(.env.*)",
      "Read(.secrets)",
      "Bash(git push:*)",
      "Bash(git rebase:*)",
      "Bash(git merge:*)",
      "Bash(git reset:*)",
      "Bash(git checkout:*)",
      "Bash(git switch:*)",
      "Bash(gh pr:*)"
    ]
  }
}
JSON
}

sincronizar_base() {
  local wt="$1" rama="$2" objetivo propios sucio
  objetivo="$(git -C "$RAIZ" rev-parse "$RAMA_BASE")"
  propios="$(git -C "$wt" rev-list --count "$RAMA_BASE..$rama" 2>/dev/null || echo 0)"
  sucio="$(git -C "$wt" status --porcelain | wc -l | tr -d ' ')"
  if [ "$propios" != "0" ]; then
    echo "    base:  se omite — la rama ya tiene $propios commit(s) propio(s)"
  elif [ "$sucio" != "0" ]; then
    echo "    base:  se omite — hay $sucio cambio(s) sin commitear en el worktree"
  elif [ "$(git -C "$wt" rev-parse HEAD)" = "$objetivo" ]; then
    echo "    base:  ya está al día"
  else
    git -C "$wt" merge --ff-only "$RAMA_BASE" >/dev/null
    echo "    base:  adelantada a $(git -C "$wt" rev-parse --short HEAD) ($RAMA_BASE)"
  fi
}

echo "Flota Orca — sincronizando desde docs/orca/ (base: $RAMA_BASE)"
echo

problemas=0
for brief in "$ORIGEN"/tareas/*.md; do
  [ -e "$brief" ] || { echo "No hay ningún brief en docs/orca/tareas/." >&2; exit 1; }
  archivo="$(basename "$brief")"
  rama="${archivo%.md}"        # 04-fix-consistencia-api.md -> 04-fix-consistencia-api
  rama="${rama#*-}"            #                            -> fix-consistencia-api
  wt="$(ruta_de_worktree "$rama")"

  echo "  $rama"
  if [ -z "$wt" ] || [ ! -d "$wt" ]; then
    echo "    ERROR: no hay worktree para esta rama. Créalo desde Orca y vuelve a correr."
    problemas=$((problemas + 1))
    continue
  fi

  mkdir -p "$wt/.claude/orca"
  cp "$ORIGEN/CONTRATO_DEL_AGENTE.md" "$wt/.claude/orca/CONTRATO_DEL_AGENTE.md"
  cp "$brief"                         "$wt/.claude/orca/TAREA.md"
  escribir_prompt "$wt" "$rama"
  escribir_permisos "$wt"
  echo "    tarea: $archivo"
  [ "$SINCRONIZAR_BASE" = "1" ] && sincronizar_base "$wt" "$rama"
done

echo
if [ "$problemas" != "0" ]; then
  echo "Terminado con $problemas problema(s). Revisa los ERROR de arriba."
  exit 1
fi
echo "Listo. El prompt de cada panel está en <worktree>/.claude/orca/PROMPT.txt."
