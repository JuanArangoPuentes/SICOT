#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════
# SICOT — instalar la actualización automática en el servidor del Centro
#
#   sudo ./scripts/instalar-auto-actualizacion.sh
#
# Se corre UNA VEZ, en el servidor. Deja un temporizador de systemd que cada
# diez minutos mira si `master` avanzó y, si avanzó, respalda, reconstruye y
# comprueba que SICOT quedó sano (scripts/auto-actualizar-sicot.sh explica el
# porqué de cada paso y qué deshace si algo falla).
#
# Es idempotente: volver a correrlo reescribe las unidades con las rutas
# actuales y reinicia el temporizador. Sirve para mover el clon de sitio o para
# cambiar la rama que se sigue.
#
# Para desinstalarlo:
#   sudo systemctl disable --now sicot-auto-actualizar.timer
#   sudo rm /etc/systemd/system/sicot-auto-actualizar.{service,timer}
#   sudo systemctl daemon-reload
# ═══════════════════════════════════════════════════════════════════════════
set -euo pipefail

SICOT_DIR="${SICOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
SICOT_RAMA="${SICOT_RAMA:-master}"
SICOT_RESPALDOS="${SICOT_RESPALDOS:-/var/backups/sicot}"
SICOT_LOG="${SICOT_LOG:-/var/log/sicot-auto-actualizar.log}"
UNIDADES=/etc/systemd/system

[ "$(id -u)" = "0" ] || { echo "Córralo con sudo: necesita escribir en $UNIDADES." >&2; exit 1; }

for programa in git docker flock systemctl; do
  command -v "$programa" > /dev/null 2>&1 || { echo "Falta '$programa' en este equipo." >&2; exit 1; }
done

[ -d "$SICOT_DIR/.git" ] || { echo "$SICOT_DIR no es un clon de git de SICOT." >&2; exit 1; }
[ -f "$SICOT_DIR/docker-compose.yml" ] || { echo "$SICOT_DIR no parece el repositorio de SICOT." >&2; exit 1; }
[ -f "$SICOT_DIR/.env" ] || echo "AVISO: no hay $SICOT_DIR/.env. Sin él, docker compose levantará el perfil de desarrollo."

echo "Clon:       $SICOT_DIR"
echo "Rama:       $SICOT_RAMA"
echo "Respaldos:  $SICOT_RESPALDOS"
echo "Registro:   $SICOT_LOG"
echo

# El clon del servidor puede estar en otra rama (hasta hoy se actualizaba a mano
# desde `develop`). Se deja en la rama que se va a seguir, para que lo que diga
# `git status` ahí coincida con lo que de verdad corre.
cd "$SICOT_DIR"
git fetch --quiet --prune origin "$SICOT_RAMA"
if ! git diff --quiet || ! git diff --quiet --cached; then
  git stash push --quiet --message "instalar-auto-actualizacion $(date '+%Y-%m-%d %H:%M')" || true
  echo "Había cambios locales: quedaron guardados ('git stash list')."
fi
git checkout --quiet -B "$SICOT_RAMA" "origin/$SICOT_RAMA"
echo "El clon quedó en $SICOT_RAMA → $(git rev-parse --short HEAD)"

mkdir -p "$SICOT_RESPALDOS"
touch "$SICOT_LOG"
chmod 640 "$SICOT_LOG"
chmod +x "$SICOT_DIR/scripts/auto-actualizar-sicot.sh"

for unidad in sicot-auto-actualizar.service sicot-auto-actualizar.timer; do
  sed -e "s|__SICOT_DIR__|$SICOT_DIR|g" \
      -e "s|__SICOT_RAMA__|$SICOT_RAMA|g" \
      -e "s|__SICOT_RESPALDOS__|$SICOT_RESPALDOS|g" \
      -e "s|__SICOT_LOG__|$SICOT_LOG|g" \
      "$SICOT_DIR/scripts/systemd/$unidad" > "$UNIDADES/$unidad"
  echo "Instalada $UNIDADES/$unidad"
done

systemctl daemon-reload
systemctl enable --now sicot-auto-actualizar.timer
echo
systemctl list-timers sicot-auto-actualizar.timer --no-pager || true
echo
echo "Primera corrida ahora mismo (puede tardar: reconstruye las imágenes)…"
systemctl start sicot-auto-actualizar.service || true
echo
echo "Cómo se mira a partir de ahora:"
echo "  tail -f $SICOT_LOG"
echo "  systemctl status sicot-auto-actualizar.service"
echo "  journalctl -u sicot-auto-actualizar.service -n 50"
