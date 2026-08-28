#!/usr/bin/env bash
# Propaga la configuración de la flota Orca a cada worktree.
#
#   bash docs/orca/sync-worktrees.sh [--sincronizar-base]
#
# Escribe en cada worktree, dentro de `.claude/` (que git ignora, así que nada de
# esto ensucia el repositorio):
#
#   .claude/orca/CONTRATO_DEL_AGENTE.md   reglas comunes de la flota
#   .claude/orca/TAREA.md                 el brief de esa rama
#   .claude/settings.local.json           permisos del agente en ese worktree
#
# Es idempotente: se puede volver a correr cada vez que se edite un brief.
# NUNCA toca archivos versionados ni el trabajo del agente.
#
# Con --sincronizar-base, además adelanta cada rama al último commit de la rama base,
# pero solo si el worktree está limpio y no tiene commits propios.
#
# La rama base es `develop`, que es donde integra el equipo. Se puede apuntar a
# otra sin tocar el script:
#
#   RAMA_BASE=otra-rama bash docs/orca/sync-worktrees.sh --sincronizar-base

set -euo pipefail

RAMA_BASE="${RAMA_BASE:-develop}"
SINCRONIZAR_BASE=0
[ "${1:-}" = "--sincronizar-base" ] && SINCRONIZAR_BASE=1

RAIZ="$(git rev-parse --show-toplevel)"
ORIGEN="$RAIZ/docs/orca"

# rama git : archivo de brief
RAMAS="
fix-validaciones-de-entrada:01-fix-validaciones-de-entrada.md
fix-transiciones-de-estado:02-fix-transiciones-de-estado.md
fix-seguridad-modulo-ia:03-fix-seguridad-modulo-ia.md
fix-consistencia-api:04-fix-consistencia-api.md
test-cobertura-idor-y-aislamiento:05-test-cobertura-idor-y-aislamiento.md
chore-limpieza-codigo-muerto:06-chore-limpieza-codigo-muerto.md
"

# Ruta del worktree de una rama, según el propio git (no se adivina).
ruta_de_worktree() {
  git -C "$RAIZ" worktree list --porcelain \
    | awk -v r="refs/heads/$1" '/^worktree /{p=substr($0,10)} /^branch /{if (substr($0,8)==r) print p}'
}

escribir_permisos() {
  cat > "$1/.claude/settings.local.json" <<'JSON'
{
  "permissions": {
    "allow": [
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
  local wt="$1" rama="$2"
  local objetivo propios sucio
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

echo "Sincronizando la flota Orca desde $ORIGEN"
echo

faltantes=0
for entrada in $RAMAS; do
  rama="${entrada%%:*}"
  brief="${entrada##*:}"
  wt="$(ruta_de_worktree "$rama")"

  echo "  $rama"
  if [ -z "$wt" ] || [ ! -d "$wt" ]; then
    echo "    ERROR: no hay worktree para esta rama. Créalo desde Orca y vuelve a correr el script."
    faltantes=$((faltantes + 1))
    continue
  fi
  if [ ! -f "$ORIGEN/tareas/$brief" ]; then
    echo "    ERROR: falta el brief docs/orca/tareas/$brief"
    faltantes=$((faltantes + 1))
    continue
  fi

  mkdir -p "$wt/.claude/orca"
  cp "$ORIGEN/CONTRATO_DEL_AGENTE.md" "$wt/.claude/orca/CONTRATO_DEL_AGENTE.md"
  cp "$ORIGEN/tareas/$brief"          "$wt/.claude/orca/TAREA.md"
  escribir_permisos "$wt"
  echo "    tarea: $brief"
  echo "    ruta:  $wt"
  [ "$SINCRONIZAR_BASE" = "1" ] && sincronizar_base "$wt" "$rama"
done

echo
if [ "$faltantes" != "0" ]; then
  echo "Terminado con $faltantes problema(s). Revisa los ERROR de arriba."
  exit 1
fi
echo "Listo. Ahora pega en cada panel de Orca su prompt de docs/orca/PROMPTS.md."
