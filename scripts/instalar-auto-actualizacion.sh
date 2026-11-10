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

# Por lo mismo que en auto-actualizar-sicot.sh: este script corre con sudo sobre
# un clon que es de otro usuario, y git se niega a tocarlo sin esto.
export GIT_CONFIG_COUNT=1
export GIT_CONFIG_KEY_0=safe.directory
export GIT_CONFIG_VALUE_0="$SICOT_DIR"

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

# Lo primero, antes de mover el clon: dejar anotado qué está desplegado de
# verdad. Lo que corre ahora se construyó desde el commit en el que está el clon
# en este momento, y en cuanto se haga el `checkout` esa información se pierde.
#
# Sin esta anotación pasan dos cosas, las dos silenciosas: la primera corrida
# concluye «ya está al día» y no despliega nada —el Centro se queda con las
# imágenes viejas—, y si se la fuerza, la vuelta atrás apunta a la misma versión
# que acaba de fallar, con lo que deja de ser una vuelta atrás.
if [ -f "$SICOT_DIR/.ultimo-despliegue" ]; then
  echo "Ya había anotación de lo desplegado: $(cat "$SICOT_DIR/.ultimo-despliegue"). Se respeta."
else
  # SICOT_DESPLEGADO existe para el caso en que el clon YA se movió antes de
  # instalar, que es justo lo que pasa cuando alguien trae `master` para
  # conseguir este script. Entonces HEAD ya no dice qué construyó las imágenes
  # que corren, y volver el clon atrás con `git reset` tampoco sirve: en ese
  # commit anterior estos scripts no existen todavía.
  #
  #   sudo SICOT_DESPLEGADO=<commit> ./scripts/instalar-auto-actualizacion.sh
  #
  # Para averiguar el commit: `docker image inspect` de la imagen que corre, o
  # la fecha de construcción de las imágenes contra el historial de la rama.
  if [ -n "${SICOT_DESPLEGADO:-}" ]; then
    DESPLEGADO="$(git rev-parse --verify "$SICOT_DESPLEGADO^{commit}" 2>/dev/null)" || {
      echo "SICOT_DESPLEGADO=$SICOT_DESPLEGADO no es un commit de este repositorio." >&2
      exit 1
    }
  else
    DESPLEGADO="$(git rev-parse HEAD)"
  fi
  printf '%s %s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$DESPLEGADO" > "$SICOT_DIR/.ultimo-despliegue"
  echo "Lo que corre ahora se anota como desplegado: ${DESPLEGADO:0:8} ($(git log -1 --pretty=%s "$DESPLEGADO"))"
  echo "Si eso NO es lo que construyó las imágenes que están corriendo, vuelva a correr este"
  echo "instalador con SICOT_DESPLEGADO=<commit>, o corrija a mano el archivo:"
  echo "  $SICOT_DIR/.ultimo-despliegue"
fi

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
# Con `sudo`, porque el registro queda en 0640 de root: quien acaba de instalar
# esto es el usuario del Centro, y sin `sudo` lo que recibe es «Permiso
# denegado» justo cuando va a comprobar si su despliegue salió bien. Pasó en el
# servidor del Centro el 10-11-2026, con estas mismas líneas en pantalla.
echo "Cómo se mira a partir de ahora:"
echo "  sudo tail -f $SICOT_LOG"
echo "  systemctl status sicot-auto-actualizar.service"
echo "  sudo journalctl -u sicot-auto-actualizar.service -n 50"
