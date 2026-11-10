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

# CÓMO SE BUSCA `claude`, Y POR QUÉ NO BASTA UN `command -v`
# ----------------------------------------------------------
# Se busca COMO EL USUARIO dueño del clon, nunca en el PATH de root, porque
# Claude Code se instala en su HOME. Pero preguntarle a un shell de INICIO no
# alcanza, y eso se comprobó en el servidor del Centro el 10-11-2026: allí
# `claude` funciona al escribirlo en una terminal y sin embargo
# `sudo -u … -i command -v claude` no lo encuentra. El motivo habitual es que la
# ruta la añade `~/.bashrc` —o un gestor de versiones de Node—, y `.bashrc` no
# se carga en un shell no interactivo.
#
# Así que se prueban cuatro cosas, de la más fiable a la más tosca, y se acepta
# que quien sabe la respuesta la dé directamente con SICOT_CLAUDE=/ruta.
encontrar_claude() {
  # 0. Dicho a mano. Gana siempre.
  if [ -n "${SICOT_CLAUDE:-}" ]; then
    printf '%s' "$SICOT_CLAUDE"
    return
  fi
  # 1. Shell de inicio, que es lo correcto cuando la instalación es ordenada.
  local via
  via="$(sudo -u "$USUARIO" -i command -v claude 2>/dev/null || true)"
  [ -n "$via" ] && { printf '%s' "$via"; return; }
  # 2. Shell interactivo, que es lo que sí carga ~/.bashrc y los gestores de
  #    versiones de Node. `-i` sin terminal se queja, pero responde.
  via="$(sudo -u "$USUARIO" bash -ic 'command -v claude' 2>/dev/null | tr -d '\r' | tail -1 || true)"
  [ -x "$via" ] && { printf '%s' "$via"; return; }
  # 3. Los sitios donde Claude Code se instala, por orden de probabilidad.
  #    `.npm-global/bin` es el prefijo que usa quien configuró npm para instalar
  #    sin sudo; es donde estaba en el servidor del Centro.
  local candidato
  for candidato in "$HOGAR/.local/bin/claude" "$HOGAR/.claude/local/claude" \
                   "$HOGAR/.npm-global/bin/claude" "$HOGAR/.npm/bin/claude" \
                   "$HOGAR/bin/claude" /usr/local/bin/claude /usr/bin/claude; do
    [ -x "$candidato" ] && { printf '%s' "$candidato"; return; }
  done
  # 4. Rastrear su HOME. Acotado en profundidad para no recorrer el disco.
  #    Se admiten enlaces simbólicos (`-type l`) porque una instalación global de
  #    npm deja justamente eso en su `bin`: un enlace al `cli.js` del paquete.
  #    Con `-type f` a secas, el caso más común se escapaba.
  local hallado
  while IFS= read -r hallado; do
    [ -x "$hallado" ] && { printf '%s' "$hallado"; return; }
  done < <(find "$HOGAR" -maxdepth 5 -name claude \( -type f -o -type l \) 2>/dev/null)
}

CLAUDE="$(encontrar_claude)"
if [ -z "$CLAUDE" ] || [ ! -x "$CLAUDE" ]; then
  echo "No encontré el ejecutable 'claude' para el usuario '$USUARIO'." >&2
  echo >&2
  echo "Pregúnteselo a él mismo, en una terminal de ese usuario:" >&2
  echo "  command -v claude" >&2
  echo >&2
  echo "y vuelva a correr este instalador diciéndole la ruta:" >&2
  echo "  sudo SICOT_CLAUDE=/la/ruta/que/salio ./scripts/instalar-remote-control.sh" >&2
  exit 1
fi
# `node` TAMBIÉN tiene que estar en el PATH de la unidad. El ejecutable de
# Claude Code instalado por npm es un enlace a un script que empieza por
# `#!/usr/bin/env node`: sin node a la vista, systemd no lo arranca y el error
# —«env: 'node': No such file or directory»— no menciona a Claude por ningún
# lado. Si node vino de apt está en /usr/bin y ya estaría cubierto; si vino de
# un gestor de versiones, vive en el HOME del usuario y hay que buscarlo igual
# que el propio claude.
NODE="$(sudo -u "$USUARIO" bash -ic 'command -v node' 2>/dev/null | tr -d '\r' | tail -1 || true)"
[ -x "$NODE" ] || NODE="$(sudo -u "$USUARIO" -i command -v node 2>/dev/null || true)"
RUTA="$(dirname "$CLAUDE")"
[ -x "$NODE" ] && RUTA="$RUTA:$(dirname "$NODE")"
RUTA="$RUTA:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

echo "Clon:     $SICOT_DIR"
echo "Usuario:  $USUARIO (HOME $HOGAR)"
echo "Claude:   $CLAUDE"
echo "Node:     ${NODE:-(no encontrado; se confía en /usr/bin/node)}"
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
