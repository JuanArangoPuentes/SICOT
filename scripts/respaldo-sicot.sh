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

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTENEDOR"; then
    error "El contenedor '$CONTENEDOR' no está corriendo. No hay nada que respaldar."
    exit 1
fi

mkdir -p "$DESTINO"

FECHA="$(date +%Y%m%d-%H%M%S)"
ARCHIVO="$DESTINO/sicot-$FECHA.dump"
TEMPORAL="/tmp/sicot-$FECHA.dump"

log "Iniciando respaldo de '$BASE' desde el contenedor '$CONTENEDOR'…"

# -F c = formato "custom": comprimido y permite restaurar tablas sueltas.
docker exec "$CONTENEDOR" pg_dump -U "$USUARIO" -d "$BASE" -F c -f "$TEMPORAL"
docker cp "$CONTENEDOR:$TEMPORAL" "$ARCHIVO"
docker exec "$CONTENEDOR" rm -f "$TEMPORAL"

if [[ ! -s "$ARCHIVO" ]]; then
    error "El respaldo quedó vacío: $ARCHIVO"
    rm -f "$ARCHIVO"
    exit 2
fi

# Verificación real: leer el volcado entero. Si está truncado o corrupto,
# pg_restore --list falla aquí y no dentro de seis meses, cuando haga falta.
log "Verificando la integridad del volcado…"
if ! docker run --rm -v "$(cd "$(dirname "$ARCHIVO")" && pwd):/backup:ro" \
        postgres:18-alpine \
        pg_restore --list "/backup/$(basename "$ARCHIVO")" > /dev/null 2>&1; then
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
