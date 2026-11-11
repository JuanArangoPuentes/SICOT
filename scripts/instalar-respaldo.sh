#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════
# SICOT — dejar el respaldo diario corriendo solo, y que se recupere si el
# equipo estaba apagado a la hora
#
#   sudo ./scripts/instalar-respaldo.sh [directorio-de-respaldos]
#
# Se corre UNA VEZ. Deja un temporizador de systemd que ejecuta
# scripts/respaldo-sicot.sh todos los días y, lo importante, **recupera la
# corrida perdida** cuando el equipo estuvo apagado a la hora de la cita.
#
# POR QUÉ NO LA LÍNEA DEL CRON QUE DICE ADR-002
# ---------------------------------------------
# Porque en el servidor del Centro no funcionó, y el fallo fue silencioso. El
# 11-11-2026 llevaba seis días sin un solo respaldo: el cron estaba activo y la
# línea `0 2 * * *` bien escrita, pero el equipo está apagado a las 02:00 y
# cron no recupera las citas perdidas. Un temporizador con `Persistent=true` sí.
# El porqué completo está en scripts/systemd/sicot-respaldo.timer.
#
# Para quitarlo:
#   sudo systemctl disable --now sicot-respaldo.timer
#   sudo rm /etc/systemd/system/sicot-respaldo.{service,timer}
#   sudo systemctl daemon-reload
# ═══════════════════════════════════════════════════════════════════════════
set -euo pipefail

SICOT_DIR="${SICOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
UNIDADES=/etc/systemd/system
SICOT_CONSERVAR="${SICOT_BACKUPS_A_CONSERVAR:-14}"
SICOT_LOG="${SICOT_RESPALDO_LOG:-/var/log/sicot-respaldo.log}"

[ "$(id -u)" = "0" ] || { echo "Córralo con sudo: necesita escribir en $UNIDADES." >&2; exit 1; }
[ -d "$SICOT_DIR/.git" ] || { echo "$SICOT_DIR no es un clon de git de SICOT." >&2; exit 1; }

for programa in docker systemctl stat; do
  command -v "$programa" >/dev/null || { echo "Falta '$programa'." >&2; exit 1; }
done

# El dueño del clon, no quien corre el sudo: es quien puede usar Docker sin
# sudo y quien ya es dueño de los volcados que se hicieron a mano.
SICOT_USUARIO="${SICOT_USUARIO:-$(stat -c %U "$SICOT_DIR")}"
HOGAR="$(getent passwd "$SICOT_USUARIO" | cut -d: -f6)"
[ -n "$HOGAR" ] || { echo "No se pudo averiguar el HOME de '$SICOT_USUARIO'." >&2; exit 1; }
SICOT_RESPALDOS="${1:-${SICOT_RESPALDOS:-$HOGAR/sicot-respaldos}}"

echo "Clon:        $SICOT_DIR"
echo "Usuario:     $SICOT_USUARIO"
echo "Respaldos:   $SICOT_RESPALDOS"
echo "Registro:    $SICOT_LOG"
echo "Se conservan: $SICOT_CONSERVAR volcados"
echo

# ── Comprobación previa: ¿ese usuario puede hablar con Docker? ──────────────
# Si no puede, el temporizador quedaría instalado y fallando cada día, que es
# otra forma de no tener respaldo. Mejor saberlo ahora.
if ! runuser -u "$SICOT_USUARIO" -- docker ps >/dev/null 2>&1; then
  echo "AVISO: '$SICOT_USUARIO' no puede usar Docker sin sudo." >&2
  echo "       El respaldo caerá al modo nativo (pg_dump) si lo hay, y si no," >&2
  echo "       fallará. Para arreglarlo:  sudo usermod -aG docker $SICOT_USUARIO" >&2
  echo "       (hay que volver a iniciar sesión para que surta efecto)." >&2
  echo >&2
fi

# ── El cron viejo, si está ──────────────────────────────────────────────────
# No se borra solo: el crontab es de la persona y puede tener más cosas. Pero
# dejar los dos puestos haría el respaldo dos veces al día sin motivo.
if crontab -u "$SICOT_USUARIO" -l 2>/dev/null | grep -q 'respaldo-sicot'; then
  echo "AVISO: el crontab de '$SICOT_USUARIO' todavía tiene una línea de respaldo-sicot."
  echo "       Con el temporizador puesto, sobra. Para quitarla:"
  echo "         sudo crontab -u $SICOT_USUARIO -e"
  echo "       y borrar la línea que contiene respaldo-sicot."
  echo
fi

mkdir -p "$SICOT_RESPALDOS"
chown "$SICOT_USUARIO" "$SICOT_RESPALDOS"

# El registro lo crea root, pero lo escribe el servicio como el dueño del clon:
# sin el chown, systemd no podría abrirlo para añadir y la unidad fallaría al
# arrancar, antes incluso de tocar la base.
touch "$SICOT_LOG"
chown "$SICOT_USUARIO" "$SICOT_LOG"
chmod 640 "$SICOT_LOG"
chmod +x "$SICOT_DIR/scripts/respaldo-sicot.sh"

for unidad in sicot-respaldo.service sicot-respaldo.timer; do
  sed -e "s|__SICOT_DIR__|$SICOT_DIR|g" \
      -e "s|__SICOT_USUARIO__|$SICOT_USUARIO|g" \
      -e "s|__SICOT_RESPALDOS__|$SICOT_RESPALDOS|g" \
      -e "s|__SICOT_CONSERVAR__|$SICOT_CONSERVAR|g" \
      -e "s|__SICOT_LOG__|$SICOT_LOG|g" \
      "$SICOT_DIR/scripts/systemd/$unidad" > "$UNIDADES/$unidad"
  echo "Instalada $UNIDADES/$unidad"
done

systemctl daemon-reload
systemctl enable --now sicot-respaldo.timer
echo
systemctl list-timers sicot-respaldo.timer --no-pager || true

# ── Y uno ahora mismo ───────────────────────────────────────────────────────
# Instalar el temporizador no respalda nada hoy: la primera cita es la que
# toque. Si se está instalando esto es porque faltaban respaldos, así que se
# hace uno ya y se comprueba que de verdad quedó el archivo.
echo
echo "Haciendo un respaldo ahora para no esperar a la primera cita…"
if systemctl start sicot-respaldo.service; then
  echo "Listo. Lo más reciente en $SICOT_RESPALDOS:"
  ls -lht "$SICOT_RESPALDOS" | head -3
else
  echo "El respaldo falló. Qué dijo:" >&2
  tail -20 "$SICOT_LOG" >&2
  exit 1
fi

echo
echo "Comprobaciones:"
echo "  systemctl list-timers sicot-respaldo.timer"
echo "  systemctl is-failed sicot-respaldo.service"
echo "  tail -20 $SICOT_LOG"
echo
echo "Y la que de verdad importa, que no la hace esto: probar una restauración."
echo "  $SICOT_DIR/scripts/prueba-respaldo-sicot.sh"
echo "Un respaldo que nunca se ha restaurado no es un respaldo. Ver"
echo "docs/operacion/BACKUP_Y_RESTAURACION.md."
