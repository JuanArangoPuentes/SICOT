#!/usr/bin/env bash
#
# Respaldo automático de la base de datos de SICOT, con rotación y
# verificación.
#
# Hasta ahora el procedimiento de respaldo estaba documentado pero era manual
# (ver docs/operacion/BACKUP_Y_RESTAURACION.md): dependía de que alguien se
# acordara de ejecutarlo antes de cada operación riesgosa. Un respaldo que
# depende de la memoria de una persona no es un respaldo.
#
# Tres cosas que este script hace y que un `pg_dump` suelto no:
#
#   1. VERIFICA el archivo recién creado. Un `pg_dump` puede terminar con
#      código 0 y dejar un archivo truncado si el disco se llenó a mitad.
#      `pg_restore --list` lo lee entero y falla si no es un volcado válido.
#      Un respaldo que nadie ha probado a leer es una suposición, no un
#      respaldo.
#   2. ROTA los antiguos, para que el disco no se llene en silencio — que es
#      la forma habitual en que un sistema de respaldos deja de funcionar.
#   3. FALLA RUIDOSAMENTE. `set -euo pipefail` y códigos de salida distintos
#      de cero para que cron/systemd puedan avisar. Un script de respaldo que
#      falla en silencio es peor que no tenerlo, porque además da confianza.
#
# Uso:
#   ./scripts/respaldo-sicot.sh [directorio-destino]
#
# Programación diaria a las 2:00 (crontab -e):
#   0 2 * * * /ruta/al/repo/scripts/respaldo-sicot.sh /var/backups/sicot >> /var/log/sicot-respaldo.log 2>&1
#
# La restauración sigue siendo deliberadamente manual: sobrescribe datos
# oficiales y no debe poder ocurrir por accidente ni por un cron mal escrito.
# Ver docs/operacion/BACKUP_Y_RESTAURACION.md.

set -euo pipefail

CONTENEDOR="${SICOT_DB_CONTAINER:-sicot-db}"
BASE="${SICOT_DB_NAME:-sicot}"
USUARIO="${SICOT_DB_USER:-sicot}"
DESTINO="${1:-./respaldos}"
# Cuántos respaldos conservar. 14 diarios ≈ dos semanas, suficiente para
# detectar un problema que se descubre tarde sin que el disco crezca sin fin.
CONSERVAR="${SICOT_BACKUPS_A_CONSERVAR:-14}"

marca() { date '+%Y-%m-%d %H:%M:%S'; }
log()   { echo "[$(marca)] $*"; }
error() { echo "[$(marca)] ERROR: $*" >&2; }

# ── Modo de acceso a la base ────────────────────────────────────────────────
#
# Dos modos, detectados en este orden:
#
#   docker  — la base corre en el contenedor $CONTENEDOR (el despliegue normal)
#   nativo  — hay un PostgreSQL instalado en la máquina (desarrollo, o un
#             servidor sin Docker)
#
# El script soportaba SOLO docker, y eso tenía una consecuencia práctica: en
# una máquina sin Docker corriendo no se podía ni siquiera probar. Un
# procedimiento de respaldo que solo se puede ejercitar bajo una condición
# concreta tiende a no ejercitarse nunca — que es exactamente lo que le pasó a
# este archivo hasta hoy.
MODO=""
if command -v docker >/dev/null 2>&1    && docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$CONTENEDOR"; then
    MODO="docker"
elif command -v pg_dump >/dev/null 2>&1      && pg_isready -h "${SICOT_DB_HOST:-localhost}" -p "${SICOT_DB_PORT:-5432}" >/dev/null 2>&1; then
    MODO="nativo"
else
    error "No hay base a la que conectarse: ni el contenedor '$CONTENEDOR' está corriendo,"
    error "ni hay un PostgreSQL accesible en ${SICOT_DB_HOST:-localhost}:${SICOT_DB_PORT:-5432}."
    exit 1
fi

mkdir -p "$DESTINO"

FECHA="$(date +%Y%m%d-%H%M%S)"
ARCHIVO="$DESTINO/sicot-$FECHA.dump"

log "Iniciando respaldo de '$BASE' (modo: $MODO)…"

# -F c = formato "custom": comprimido y permite restaurar tablas sueltas.
if [[ "$MODO" == "docker" ]]; then
    # Se vuelca directo a la salida estándar y se redirige al archivo del host.
    #
    # La versión anterior escribía a un temporal DENTRO del contenedor y después
    # lo copiaba con `docker cp`. Eso fallaba en Git Bash sobre Windows: la capa
    # de compatibilidad traduce `/tmp/...` a una ruta de Windows antes de que el
    # argumento llegue al contenedor, y pg_dump intentaba escribir en una ruta
    # inexistente. Además el temporal duplicaba el tamaño del volcado en disco.
    #
    # SIN `-t`: un pseudo-terminal traduce saltos de línea y corrompería un
    # volcado binario de forma silenciosa — se notaría solo al restaurar.
    docker exec "$CONTENEDOR" pg_dump -U "$USUARIO" -d "$BASE" -F c > "$ARCHIVO"
else
    PGPASSWORD="${SICOT_DB_PASSWORD:-}" pg_dump         -h "${SICOT_DB_HOST:-localhost}" -p "${SICOT_DB_PORT:-5432}"         -U "$USUARIO" -d "$BASE" -F c -f "$ARCHIVO"
fi

if [[ ! -s "$ARCHIVO" ]]; then
    error "El respaldo quedó vacío: $ARCHIVO"
    rm -f "$ARCHIVO"
    exit 2
fi

# Verificación real: leer el volcado entero. Si está truncado o corrupto,
# pg_restore --list falla aquí y no dentro de seis meses, cuando haga falta.
log "Verificando la integridad del volcado…"
# Se prefiere un pg_restore instalado en la máquina, exista Docker o no: la
# verificación solo necesita leer el archivo, y hacerlo por fuera del contenedor
# evita montar volúmenes.
#
# Ese montaje era el que fallaba: `docker run -v "$(pwd):/backup"` bajo Git Bash
# en Windows pasa una ruta tipo `/c/Users/...` que el demonio no resuelve, y la
# verificación daba «volcado no legible» sobre un volcado perfectamente válido.
# Un falso positivo así es peor que no verificar: entrena a ignorar la alarma.
#
# Sin pg_restore local se usa el contenedor, pero por ENTRADA ESTÁNDAR en vez de
# por volumen — `-i` sin `-t`, que no traduce el binario.
if command -v pg_restore >/dev/null 2>&1; then
    verificar() { pg_restore --list "$ARCHIVO"; }
elif [[ "$MODO" == "docker" ]]; then
    verificar() { docker run --rm -i postgres:18-alpine pg_restore --list < "$ARCHIVO"; }
else
    error "No hay pg_restore disponible para verificar el volcado."
    exit 3
fi
if ! verificar > /dev/null 2>&1; then
    error "El volcado no es legible: $ARCHIVO. NO se puede confiar en este respaldo."
    exit 3
fi

TAMANIO="$(du -h "$ARCHIVO" | cut -f1)"
log "Respaldo verificado: $ARCHIVO ($TAMANIO)"

# Rotación: se conservan los N más recientes por fecha de nombre.
sobrantes="$(ls -1t "$DESTINO"/sicot-*.dump 2>/dev/null | tail -n "+$((CONSERVAR + 1))" || true)"
if [[ -n "$sobrantes" ]]; then
    echo "$sobrantes" | while read -r viejo; do
        log "Rotando (eliminando) respaldo antiguo: $viejo"
        rm -f "$viejo"
    done
fi

log "Listo. Respaldos conservados: $(ls -1 "$DESTINO"/sicot-*.dump 2>/dev/null | wc -l)"
