#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════
# SICOT — actualización automática del servidor del Centro
#
#   Lo corre el temporizador `sicot-auto-actualizar.timer` cada pocos minutos.
#   Mira si `master` en GitHub avanzó; si avanzó, respalda, reconstruye y
#   comprueba que SICOT quedó sano. Si NO quedó sano, vuelve a la versión
#   anterior por su cuenta.
#
# POR QUÉ EL SERVIDOR JALA, Y NADIE EMPUJA HACIA ÉL
# -------------------------------------------------
# El servidor del Centro no tiene puertos abiertos a Internet: se publica por un
# túnel de Cloudflare, que es una conexión SALIENTE (ADR-009 y la sección del
# túnel en INSTALACION.md). Un despliegue empujado desde GitHub exigiría abrirle
# una entrada —SSH, un webhook, un runner con credenciales— y eso es
# exactamente lo que el túnel evitó. Aquí no entra nada: el servidor pregunta.
#
# Consecuencia práctica: no hay ninguna llave de este servidor guardada fuera de
# él. Un repositorio público y un clon de solo lectura es todo lo que hace falta.
#
# LA REGLA QUE HACE QUE ESTO SE PUEDA DEJAR SOLO
# ----------------------------------------------
# Un despliegue automático sin vuelta atrás no es automatización, es una ruleta:
# el día que `master` traiga algo roto, el Centro se queda sin SICOT y nadie
# sabrá por qué. Por eso aquí **todo paso tiene su deshacer**:
#
#   1. No se actualiza sin respaldo verificado (RPO de 24 h, ADR-002).
#   2. Se guarda el commit que estaba corriendo ANTES de tocar nada.
#   3. Si el arranque nuevo no queda sano en SICOT_ESPERA_SALUD segundos, se
#      vuelve a ese commit y se reconstruye. El Centro pierde unos minutos, no
#      el día.
#   4. Todo queda escrito en el registro, con hora y commit.
#
# QUÉ NO HACE
# -----------
# No toca el `.env` (está en .gitignore: los secretos del servidor no viven en
# el repositorio, ADR-005), no aplica migraciones a mano —las aplica Flyway al
# arrancar el backend— y no publica nada. Solo pone este servidor en el commit
# que `master` ya tiene probado por el CI.
#
# USO
#   ./auto-actualizar-sicot.sh            # lo normal: solo actúa si hay cambios
#   SICOT_FORZAR=1 ./auto-actualizar-sicot.sh   # reconstruye aunque no haya cambios
#
# VARIABLES (todas con valor por omisión razonable)
#   SICOT_DIR            el clon del repositorio (por omisión, el que contiene este script)
#   SICOT_RAMA           la rama que se sigue (master)
#   SICOT_RESPALDOS      dónde deja los respaldos (/var/backups/sicot)
#   SICOT_LOG            el registro (/var/log/sicot-auto-actualizar.log)
#   SICOT_ESPERA_SALUD   segundos que espera a que SICOT quede sano (420)
#   SICOT_AVISAR_GH      si vale 1 y hay `gh` autenticado, abre una incidencia
#                        en GitHub cuando un despliegue falla y hay que mirarlo
# ═══════════════════════════════════════════════════════════════════════════
set -euo pipefail

SICOT_DIR="${SICOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
SICOT_RAMA="${SICOT_RAMA:-master}"
SICOT_RESPALDOS="${SICOT_RESPALDOS:-/var/backups/sicot}"
SICOT_LOG="${SICOT_LOG:-/var/log/sicot-auto-actualizar.log}"
SICOT_ESPERA_SALUD="${SICOT_ESPERA_SALUD:-420}"
SICOT_AVISAR_GH="${SICOT_AVISAR_GH:-1}"
SICOT_FORZAR="${SICOT_FORZAR:-0}"

# Dónde se anota qué commit está DESPLEGADO de verdad, que no es lo mismo que
# el commit en el que está el clon. Ver «QUÉ SE COMPARA» más abajo.
SICOT_ESTADO="$SICOT_DIR/.ultimo-despliegue"
SICOT_FALLIDO="$SICOT_DIR/.ultimo-fallo"

# git se niega a trabajar sobre un repositorio que es de otro usuario: desde la
# 2.35.2 responde «detected dubious ownership» y no hace nada. Y este script
# corre como root (necesita hablar con Docker y escribir el registro) sobre un
# clon que es del usuario del Centro, así que ese es el caso normal, no el raro.
#
# Se declara aquí, para las llamadas de este script y nada más. La alternativa
# —`git config --system --add safe.directory`— tocaría la configuración global
# del servidor para siempre, y dejaría la instalación dependiendo de que alguien
# se acordara de hacerlo: el fallo volvería en el próximo servidor.
export GIT_CONFIG_COUNT=1
export GIT_CONFIG_KEY_0=safe.directory
export GIT_CONFIG_VALUE_0="$SICOT_DIR"

# Los contenedores que tienen que quedar sanos para dar el despliegue por bueno.
# `sicot-backend` y `sicot-frontend` declaran healthcheck en docker-compose.yml;
# el proxy lo declara en docker-compose.prod.yml. Un contenedor que no exista
# —el proxy no está en el perfil de desarrollo— no cuenta como fallo.
CONTENEDORES_VITALES=(sicot-backend sicot-frontend)

# GIT CORRE COMO EL DUEÑO DEL CLON, NO COMO ROOT
# ----------------------------------------------
# Este script corre como root —lo necesita para Docker y para el registro— pero
# el clon es del usuario del Centro. Si git escribe ahí como root, deja objetos
# de root en `.git/objects`, y a partir de ese momento el dueño NO PUEDE usar su
# propio repositorio: cualquier `git fetch` suyo falla con «permisos
# insuficientes para agregar un objeto a la base de datos». Pasó en el servidor
# del Centro el 10-11-2026, y el daño sobrevive al script: hay que arreglarlo a
# mano con un `chown -R`.
#
# Así que las órdenes de git se ejecutan como el dueño. Tiene dos ventajas
# además de no romper nada: lo que git escriba queda con el dueño correcto, y
# desaparece el motivo para declarar `safe.directory` en esas llamadas.
DUENO_DEL_CLON="$(stat -c %U "$SICOT_DIR" 2>/dev/null || echo root)"

git_del_clon() {
  if [ "$(id -u)" = "0" ] && [ "$DUENO_DEL_CLON" != "root" ]; then
    sudo -u "$DUENO_DEL_CLON" git -C "$SICOT_DIR" "$@"
  else
    git -C "$SICOT_DIR" "$@"
  fi
}

# Lo que este script escribe en el clon (el estado del despliegue) también es
# del dueño: si no, él no puede corregirlo ni borrarlo.
suyo() {
  [ "$(id -u)" = "0" ] && [ "$DUENO_DEL_CLON" != "root" ] && chown "$DUENO_DEL_CLON" "$@" 2>/dev/null || true
}

registrar() {
  printf '%s  %s\n' "$(date '+%Y-%m-%d %H:%M:%S%z')" "$*" >> "$SICOT_LOG"
}

# Abrir una incidencia en GitHub cuando algo queda mal y hay que mirarlo.
#
# Es el único canal de aviso que existe sin abrirle una entrada al servidor:
# nadie puede preguntarle cómo le fue, así que es él quien lo cuenta. Solo si el
# equipo tiene `gh` autenticado; si no lo tiene, el registro es el único rastro
# y no se falla por eso.
avisar_fallo() {
  local detalle="$1"
  [ "$SICOT_AVISAR_GH" = "1" ] || return 0
  command -v gh > /dev/null 2>&1 || return 0
  gh auth status > /dev/null 2>&1 || return 0
  gh issue create \
    --title "El despliegue automático del servidor falló ($(date '+%Y-%m-%d %H:%M'))" \
    --body "$(printf '%s\n\n%s\n\n%s\n' \
      "$detalle" \
      "Servidor: $(hostname). Clon: $SICOT_DIR. Rama: $SICOT_RAMA." \
      "Últimas líneas del registro ($SICOT_LOG):

\`\`\`
$(tail -n 40 "$SICOT_LOG" 2>/dev/null)
\`\`\`")" \
    > /dev/null 2>&1 || true
}

# Volver al commit que estaba corriendo y reconstruirlo.
#
# Se llama cuando la versión nueva no arranca o no queda sana. Reconstruir el
# commit anterior es lento, pero es lo único que devuelve el servidor al estado
# en que estaba: `docker compose up` con las imágenes ya construidas de la
# versión nueva seguiría levantando la versión nueva.
volver_atras() {
  local motivo="$1"
  git_del_clon reset --quiet --hard "$ANTES" || true
  printf '%s\n' "$NUEVO" > "$SICOT_FALLIDO"
  suyo "$SICOT_FALLIDO"
  if docker compose up -d --build --remove-orphans >> "$SICOT_LOG" 2>&1; then
    printf '%s %s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$ANTES" > "$SICOT_ESTADO"
    suyo "$SICOT_ESTADO"
    registrar "VUELTA ATRÁS  ${ANTES:0:8} restaurado ($motivo). No se reintentará ${NUEVO:0:8} solo."
    avisar_fallo "$(printf 'El servidor intentó actualizarse a %s y %s. Volvió solo a %s, así que SICOT sigue en pie, pero en la versión anterior: lo que traía ese commit NO está desplegado.' "${NUEVO:0:8}" "$motivo" "${ANTES:0:8}")"
  else
    registrar "CRÍTICO  no se pudo restaurar ${ANTES:0:8}. El servidor puede estar caído."
    avisar_fallo "$(printf 'El servidor intentó actualizarse a %s, %s, y la vuelta atrás a %s TAMBIÉN falló. Puede estar caído: hay que entrar a mirarlo.' "${NUEVO:0:8}" "$motivo" "${ANTES:0:8}")"
  fi
}

# Un solo despliegue a la vez. El temporizador corre cada pocos minutos y una
# reconstrucción de las imágenes puede tardar más que eso: sin el cerrojo, dos
# `docker compose up --build` simultáneos se pelean por el mismo daemon y dejan
# los contenedores a medio arrancar.
exec 9>"${TMPDIR:-/tmp}/sicot-auto-actualizar.lock"
if ! flock -n 9; then
  exit 0
fi

cd "$SICOT_DIR"

if [ ! -d .git ]; then
  registrar "ERROR  $SICOT_DIR no es un clon de git. Ajuste SICOT_DIR."
  exit 1
fi

# `git fetch` falla por red más a menudo que por cualquier otra causa, y un
# fallo de red no es un fallo de despliegue: se reintenta y, si no, se calla
# hasta la siguiente corrida del temporizador.
#
# Lo que NO se hace es tragarse el motivo. Antes este script mandaba el error de
# git a /dev/null y registraba siempre «no se pudo consultar GitHub (red)». Con
# eso, un fallo permanente —el de propiedad del repositorio, por ejemplo— se
# veía exactamente igual que un wifi intermitente: el temporizador habría
# fallado cada diez minutos, para siempre, echándole la culpa a la red. Un
# diagnóstico equivocado en el registro es peor que ninguno.
descargar() {
  local intento
  for intento in 1 2 3; do
    if MOTIVO=$(git_del_clon fetch --quiet --prune origin "$SICOT_RAMA" 2>&1); then
      return 0
    fi
    sleep $((intento * 5))
  done
  return 1
}

if ! descargar; then
  registrar "AVISO  no se pudo consultar GitHub. Se reintenta en la próxima corrida. git dijo: ${MOTIVO:-(sin mensaje)}"
  exit 0
fi

# QUÉ SE COMPARA, Y POR QUÉ NO ES `git rev-parse HEAD`
# ----------------------------------------------------
# Lo que decide si hay algo que desplegar es qué commit construyeron las
# imágenes que están corriendo, y eso NO es el commit en el que está el clon:
# son dos cosas que se pueden separar, y de hecho se separan en el momento más
# inoportuno. Al instalar, el instalador deja el clon en `master`; si aquí se
# comparara HEAD contra origin/master, la primera corrida concluiría «ya está al
# día» y el Centro se quedaría con las imágenes viejas, sin una línea en el
# registro que lo dijera. Pasó en el servidor del Centro el 10-11-2026, con el
# servidor sirviendo la 0.8.0 y el clon ya en la 0.10.0.
#
# Por eso lo desplegado se anota en un archivo y se lee de ahí. Si el archivo no
# existe —una instalación anterior a este cambio— se cae a HEAD y se dice, que
# es la única suposición razonable que queda.
if [ -f "$SICOT_ESTADO" ]; then
  ANTES="$(awk 'END {print $2}' "$SICOT_ESTADO")"
else
  ANTES="$(git_del_clon rev-parse HEAD)"
  registrar "AVISO  no hay $SICOT_ESTADO: se toma el commit del clon (${ANTES:0:8}) como lo desplegado."
fi
NUEVO="$(git_del_clon rev-parse "origin/$SICOT_RAMA")"

if [ "$ANTES" = "$NUEVO" ] && [ "$SICOT_FORZAR" != "1" ]; then
  exit 0
fi

# Un commit que ya falló no se vuelve a intentar solo. Sin esto, un `master`
# roto dispararía respaldo, reconstrucción, fallo y vuelta atrás cada diez
# minutos, indefinidamente: dos reconstrucciones de imágenes por vuelta, a
# perpetuidad, sobre el servidor que el Centro está usando. Se reintenta cuando
# llegue otro commit a `master` (que es lo que de verdad arregla algo) o a mano
# con SICOT_FORZAR=1.
if [ "$SICOT_FORZAR" != "1" ] && [ "$NUEVO" = "$(cat "$SICOT_FALLIDO" 2>/dev/null)" ]; then
  exit 0
fi

registrar "INICIO  $SICOT_RAMA: ${ANTES:0:8} → ${NUEVO:0:8} · $(git_del_clon log -1 --pretty=%s "$NUEVO")"

# ── 1. Respaldo antes de tocar nada ─────────────────────────────────────────
# Sin esto, el deshacer del punto 3 recupera el código pero no los datos que el
# arranque nuevo haya podido migrar. Flyway no deshace una migración.
if [ -x scripts/respaldo-sicot.sh ]; then
  mkdir -p "$SICOT_RESPALDOS"
  if scripts/respaldo-sicot.sh "$SICOT_RESPALDOS" >> "$SICOT_LOG" 2>&1; then
    registrar "RESPALDO  hecho en $SICOT_RESPALDOS"
  else
    registrar "ABORTADO  el respaldo falló. No se actualiza sin respaldo (ADR-002)."
    avisar_fallo "El respaldo previo falló, así que el servidor NO se actualizó. Sigue corriendo ${ANTES:0:8}."
    exit 1
  fi
else
  registrar "AVISO  no hay scripts/respaldo-sicot.sh ejecutable; se actualiza sin respaldo previo."
fi

# ── 2. Traer el código ──────────────────────────────────────────────────────
# Si alguien editó archivos del repositorio en el servidor —un parche de
# urgencia, una prueba— `git reset --hard` se los llevaría sin dejar rastro. Se
# guardan en un stash con fecha: el despliegue sigue y el trabajo no se pierde.
if ! git_del_clon diff --quiet || ! git_del_clon diff --quiet --cached; then
  git_del_clon stash push --quiet --message "auto-actualizar $(date '+%Y-%m-%d %H:%M')" || true
  registrar "AVISO  había cambios locales; quedaron guardados en 'git stash list'."
fi

git_del_clon reset --quiet --hard "$NUEVO"

# ── 3. Reconstruir y arrancar ───────────────────────────────────────────────
# `COMPOSE_FILE` viene del .env del servidor (prod, y el túnel si se publica
# así), igual que cuando se levanta a mano. --remove-orphans limpia el
# contenedor que deje de existir en una versión nueva.
if ! docker compose up -d --build --remove-orphans >> "$SICOT_LOG" 2>&1; then
  registrar "FALLO  docker compose no pudo arrancar la versión nueva. Se vuelve atrás."
  volver_atras "la construcción falló"
  exit 1
fi

# ── 4. ¿Quedó sano? ─────────────────────────────────────────────────────────
esta_sano() {
  local nombre estado
  for nombre in "${CONTENEDORES_VITALES[@]}"; do
    estado="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$nombre" 2>/dev/null || echo ausente)"
    case "$estado" in
      healthy|running) ;;
      ausente) registrar "AVISO  el contenedor $nombre no existe." ; return 1 ;;
      *) return 1 ;;
    esac
  done
  return 0
}

ESPERADO=$((SECONDS + SICOT_ESPERA_SALUD))
while [ "$SECONDS" -lt "$ESPERADO" ]; do
  if esta_sano; then
    registrar "LISTO  ${NUEVO:0:8} corriendo y sano."
    # Las imágenes viejas se acumulan: una reconstrucción diaria llena el disco
    # del servidor en semanas, y un disco lleno tumba PostgreSQL.
    docker image prune --force >> "$SICOT_LOG" 2>&1 || true
    printf '%s %s\n' "$(date '+%Y-%m-%dT%H:%M:%S%z')" "$NUEVO" > "$SICOT_ESTADO"
    suyo "$SICOT_ESTADO"
    rm -f "$SICOT_FALLIDO"
    exit 0
  fi
  sleep 10
done

registrar "FALLO  ${NUEVO:0:8} no quedó sano en ${SICOT_ESPERA_SALUD}s. Se vuelve a ${ANTES:0:8}."
volver_atras "la versión nueva no quedó sana en ${SICOT_ESPERA_SALUD}s"
exit 1
