#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════
# SICOT — dejar Remote Control de Claude Code arrancando solo en el servidor
#
#   sudo ./scripts/instalar-remote-control.sh
#
# Se corre UNA VEZ. Deja una unidad de systemd que mantiene viva la sesión de
# Remote Control en este clon, para que quien mantiene el proyecto pueda pedirle
# cosas al servidor sin estar en el Centro. El porqué, y qué puede hacer quien
# se conecte, está en scripts/systemd/claude-remote-control.service.
#
# SICOT no necesita esto para funcionar ni para actualizarse.
#
# Para quitarlo:
#   sudo systemctl disable --now claude-remote-control
#   sudo rm /etc/systemd/system/claude-remote-control.service
#   sudo systemctl daemon-reload
# ═══════════════════════════════════════════════════════════════════════════
set -euo pipefail

SICOT_DIR="${SICOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
UNIDADES=/etc/systemd/system
UNIDAD=claude-remote-control.service

[ "$(id -u)" = "0" ] || { echo "Córralo con sudo: necesita escribir en $UNIDADES." >&2; exit 1; }
[ -d "$SICOT_DIR/.git" ] || { echo "$SICOT_DIR no es un clon de git de SICOT." >&2; exit 1; }

# El dueño del clon, no quien corre el sudo: la configuración y las credenciales
# de Claude Code viven en el HOME de ese usuario, y como root no las vería.
USUARIO="${SICOT_USUARIO:-$(stat -c %U "$SICOT_DIR")}"
HOGAR="$(getent passwd "$USUARIO" | cut -d: -f6)"
[ -n "$HOGAR" ] || { echo "No se pudo averiguar el HOME de '$USUARIO'." >&2; exit 1; }

# `claude` se busca COMO ESE USUARIO y con su sesión de inicio, porque suele
# estar en su ~/.local/bin o detrás de un gestor de versiones de Node, no en el
# PATH de root. Si se escribiera a mano una ruta aquí, la unidad se rompería en
# la próxima actualización de Claude Code.
CLAUDE="$(sudo -u "$USUARIO" -i command -v claude 2>/dev/null || true)"
if [ -z "$CLAUDE" ]; then
  echo "No encontré el ejecutable 'claude' para el usuario '$USUARIO'." >&2
  echo "Compruebe que Claude Code está instalado para ese usuario:" >&2
  echo "  sudo -u $USUARIO -i command -v claude" >&2
  exit 1
fi
RUTA="$(dirname "$CLAUDE"):/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

echo "Clon:     $SICOT_DIR"
echo "Usuario:  $USUARIO (HOME $HOGAR)"
echo "Claude:   $CLAUDE"
echo

sed -e "s|__USUARIO__|$USUARIO|g" \
    -e "s|__HOGAR__|$HOGAR|g" \
    -e "s|__RUTA__|$RUTA|g" \
    -e "s|__SICOT_DIR__|$SICOT_DIR|g" \
    -e "s|__CLAUDE__|$CLAUDE|g" \
    "$SICOT_DIR/scripts/systemd/$UNIDAD" > "$UNIDADES/$UNIDAD"
echo "Instalada $UNIDADES/$UNIDAD"

systemctl daemon-reload
systemctl enable --now "$UNIDAD"
echo

# Esperar a que diga algo antes de dar el visto bueno. La primera vez que esto
# corre sin una terminal detrás es donde puede fallar —Claude Code se configuró
# respondiendo preguntas en pantalla—, y es mejor verlo ahora que descubrirlo el
# día que haga falta el canal.
sleep 8
systemctl is-active --quiet "$UNIDAD" && echo "La unidad está activa." || echo "AVISO: la unidad NO quedó activa. Mire el registro de abajo."
echo
echo "Últimas líneas de su registro:"
journalctl -u "$UNIDAD" -n 25 --no-pager || true
echo
echo "Cómo se mira a partir de ahora:"
echo "  systemctl status $UNIDAD"
echo "  sudo journalctl -u $UNIDAD -f"
