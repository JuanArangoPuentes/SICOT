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
#      `pg_restore -f -` lo lee entero y falla si no es un volcado válido.
#      Un respaldo que nadie ha probado a leer es una suposición, no un
#      respaldo.
#   2. ROTA los antiguos, para que el disco no se llene en silencio — que es
#      la forma habitual en que un sistema de respaldos deja de funcionar.
#   3. FALLA RUIDOSAMENTE. `set -euo pipefail` y códigos de salida distintos
#      de cero para que cron/systemd puedan avisar. Un script de respaldo que
#      falla en silencio es peor que no tenerlo, porque además da confianza.
#   4. CIFRA el volcado, si se configura una clave. El `.dump` es la base
#      ENTERA: nombres, correos, teléfonos, NIT, representantes legales y las
#      coordenadas GPS de las fotos de evidencia (V17). Son datos personales
#      bajo la Ley 1581 de 2012 y hasta el 20-11-2026 quedaban en claro en el
#      disco del servidor. Los SECRETOS del despliegue ya tenían procedimiento
#      —`gpg --symmetric`, copia fuera del servidor, borrar el original: ver
#      docs/operacion/BACKUP_Y_RESTAURACION.md— y la base, que es el dato
#      sensible de verdad, no lo tenía.
#   5. COPIA FUERA DEL SERVIDOR, si se configura un destino. Un respaldo en el
#      mismo disco que la base cubre el borrado accidental y la migración mal
#      hecha, que son los casos frecuentes, y NO cubre el único irreversible:
#      que el disco no vuelva. ADR-002 acepta explícitamente perder el trabajo
#      del día; no decía qué pasa si no hay disco al que volver.
#
# Las dos son OPCIONALES y están apagadas si no se configuran, a propósito: el
# script tiene que seguir corriendo tal cual en el servidor del Centro mientras
# alguien decide dónde guardar la clave y a dónde copiar. Lo que no hace es
# callarse: sin cifrado y sin copia remota lo DICE en cada ejecución, porque un
# control que falta en silencio es el que nadie añade nunca.
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

# Los volcados llevan la base entera —contratos, documentos, hashes de
# contraseña—. Con el umask habitual (022) quedaban legibles por cualquier
# cuenta del servidor; con 077 solo los lee quien corre el cron. La carpeta, si
# ya existe, conserva sus permisos: el backend solo necesita listarla para ver
# la fecha del último respaldo, no leer su contenido.
umask 077

CONTENEDOR="${SICOT_DB_CONTAINER:-sicot-db}"
BASE="${SICOT_DB_NAME:-sicot}"
USUARIO="${SICOT_DB_USER:-sicot}"
DESTINO="${1:-./respaldos}"
# Cuántos respaldos conservar. 14 diarios ≈ dos semanas, suficiente para
# detectar un problema que se descubre tarde sin que el disco crezca sin fin.
CONSERVAR="${SICOT_BACKUPS_A_CONSERVAR:-14}"

# ── Cifrado (opcional) ─────────────────────────────────────────────────────
# Dos formas, y se prefiere la primera:
#
#   SICOT_RESPALDO_GPG_DESTINATARIO  — clave PÚBLICA (id o correo de una clave
#       ya importada). Es la mejor: en el servidor solo queda la pública, así
#       que quien se lleve el disco NO puede descifrar nada. La privada vive
#       donde viva el técnico.
#   SICOT_RESPALDO_GPG_CLAVE_ARCHIVO — archivo con una frase de paso, para
#       cifrado simétrico. Más fácil de montar y peor: la frase está en el
#       mismo equipo que el respaldo. Sirve cuando el destino remoto es lo que
#       se quiere proteger y el servidor ya es de confianza.
#
# Sin ninguna de las dos no se cifra y se avisa.
GPG_DESTINATARIO="${SICOT_RESPALDO_GPG_DESTINATARIO:-}"
GPG_CLAVE_ARCHIVO="${SICOT_RESPALDO_GPG_CLAVE_ARCHIVO:-}"

# ── Copia fuera del servidor (opcional) ────────────────────────────────────
# Destino de `rsync`: una ruta local en OTRO disco montado
# (/mnt/respaldos/sicot/) o un destino remoto por SSH
# (respaldos@otro-equipo:/srv/sicot/). Con SSH hace falta una clave sin frase
# de paso para el usuario que corre el cron; el procedimiento está en
# docs/operacion/BACKUP_Y_RESTAURACION.md.
DESTINO_REMOTO="${SICOT_RESPALDO_DESTINO_REMOTO:-}"

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
# El volcado se escribe con otro nombre y solo pasa a llamarse `.dump` cuando
# ya se verificó. Antes se escribía directamente con el nombre final, y un
# pg_dump cortado o un volcado ilegible se quedaban en la carpeta: la
# vigilancia del backend (VigilanciaDelRespaldo) lo tomaba por el respaldo de
# anoche y no avisaba, y la rotación, noche tras noche, borraba los buenos para
# conservar esos. El `trap` lo borra en cualquier salida que no llegue al `mv`.
PARCIAL="$ARCHIVO.parcial"
trap 'rm -f "$PARCIAL"' EXIT

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
    volcar() { docker exec "$CONTENEDOR" pg_dump -U "$USUARIO" -d "$BASE" -F c > "$PARCIAL"; }
else
    volcar() {
        PGPASSWORD="${SICOT_DB_PASSWORD:-}" pg_dump             -h "${SICOT_DB_HOST:-localhost}" -p "${SICOT_DB_PORT:-5432}"             -U "$USUARIO" -d "$BASE" -F c -f "$PARCIAL"
    }
fi
# Con `set -e` el script ya saldría, pero sin decir por qué: esta línea es la
# que queda en el log del cron.
if ! volcar; then
    error "pg_dump falló; no se guardó ningún respaldo. Revise el espacio en disco y el estado de la base."
    exit 4
fi

if [[ ! -s "$PARCIAL" ]]; then
    error "El respaldo quedó vacío; no se guardó."
    exit 2
fi

# Verificación real: leer el volcado entero. Si está truncado o corrupto,
# falla aquí y no dentro de seis meses, cuando haga falta.
#
# `pg_restore -f -` y no `pg_restore --list`: --list solo lee el índice del
# volcado, no los datos. Se comprobó el 28-09-2026 con un volcado de la base de
# desarrollo cortado al 60 %: --list lo daba por bueno (salida 0) y el volcado
# habría devuelto los archivos de `documentos` incompletos al restaurarlo.
# -f - recorre todos los datos y escribe el SQL a la salida, que se descarta.
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
    verificar() { pg_restore -f - "$PARCIAL"; }
elif [[ "$MODO" == "docker" ]]; then
    # Con el pg_restore del MISMO contenedor de la base, no con una imagen
    # aparte. Antes era `docker run postgres:18-alpine`: una etiqueta flotante
    # (el resto de la pila fija la versión exacta) y, en un servidor sin
    # Internet —SICOT es 100 % local—, una descarga imposible: la verificación
    # fallaba y el respaldo no se guardaba, con la imagen buena ya en el equipo.
    verificar() { docker exec -i "$CONTENEDOR" pg_restore -f - < "$PARCIAL"; }
else
    error "No hay pg_restore disponible para verificar el volcado."
    exit 3
fi
if ! verificar > /dev/null 2>&1; then
    error "El volcado no es legible: NO se puede confiar en él y no se guardó."
    exit 3
fi
mv "$PARCIAL" "$ARCHIVO"

TAMANIO="$(du -h "$ARCHIVO" | cut -f1)"
log "Respaldo verificado: $ARCHIVO ($TAMANIO)"

# ── Cifrado ────────────────────────────────────────────────────────────────
#
# AQUÍ Y NO ANTES, y el orden no es un detalle: la verificación necesita LEER
# el volcado con `pg_restore`, y un archivo cifrado no se puede leer. Cifrar
# primero habría dejado el script "verificando" un archivo que ningún
# pg_restore puede abrir — es decir, sin verificar nada y sin que se notara.
#
# El archivo en claro se borra en cuanto el cifrado termina bien. Si algo falla,
# NO se borra: perder el respaldo por un fallo del cifrado sería cambiar un
# problema de confidencialidad por uno de disponibilidad, que es peor.
if [[ -n "$GPG_DESTINATARIO" || -n "$GPG_CLAVE_ARCHIVO" ]]; then
    if ! command -v gpg >/dev/null 2>&1; then
        error "Se pidió cifrar el respaldo pero no hay gpg instalado (apt-get install -y gnupg)."
        error "El respaldo quedó SIN CIFRAR en $ARCHIVO."
        exit 5
    fi
    CIFRADO="$ARCHIVO.gpg"
    log "Cifrando el respaldo…"
    if [[ -n "$GPG_DESTINATARIO" ]]; then
        # --trust-model always: la clave la importó a mano quien montó el
        # servidor, así que su validez ya está decidida fuera de aquí; sin esto
        # gpg pide confirmación por consola y en un cron se queda colgado.
        cifrar() {
            gpg --batch --yes --quiet --trust-model always \
                --recipient "$GPG_DESTINATARIO" --encrypt --output "$CIFRADO" "$ARCHIVO"
        }
    else
        if [[ ! -r "$GPG_CLAVE_ARCHIVO" ]]; then
            error "No se puede leer la frase de paso en $GPG_CLAVE_ARCHIVO."
            error "El respaldo quedó SIN CIFRAR en $ARCHIVO."
            exit 5
        fi
        cifrar() {
            gpg --batch --yes --quiet --symmetric --cipher-algo AES256 \
                --passphrase-file "$GPG_CLAVE_ARCHIVO" --output "$CIFRADO" "$ARCHIVO"
        }
    fi
    if ! cifrar; then
        error "El cifrado falló. El respaldo queda SIN CIFRAR en $ARCHIVO (no se borra: vale más que su secreto)."
        rm -f "$CIFRADO"
        exit 5
    fi
    if [[ ! -s "$CIFRADO" ]]; then
        error "El cifrado produjo un archivo vacío. Se conserva el original SIN CIFRAR en $ARCHIVO."
        rm -f "$CIFRADO"
        exit 5
    fi
    rm -f "$ARCHIVO"
    ARCHIVO="$CIFRADO"
    log "Respaldo cifrado: $ARCHIVO"
else
    log "AVISO: el respaldo NO está cifrado. Contiene la base entera, con datos personales."
    log "       Defina SICOT_RESPALDO_GPG_DESTINATARIO (clave pública, recomendado) o"
    log "       SICOT_RESPALDO_GPG_CLAVE_ARCHIVO. Ver docs/operacion/BACKUP_Y_RESTAURACION.md."
fi

# ── Copia fuera del servidor ───────────────────────────────────────────────
#
# Se copia el archivo final —cifrado si se cifró— y se hace ANTES de rotar: así
# el destino remoto recibe el respaldo del día aunque la rotación falle después.
#
# Un fallo aquí sale con código distinto de cero y por tanto systemd marca la
# unidad como fallida, que es lo que hace que alguien se entere. El respaldo
# local SÍ se conservó, y el mensaje lo dice para que nadie piense que hay que
# volver a lanzarlo a mano.
if [[ -n "$DESTINO_REMOTO" ]]; then
    if ! command -v rsync >/dev/null 2>&1; then
        error "Se pidió copiar el respaldo fuera del servidor pero no hay rsync (apt-get install -y rsync)."
        error "El respaldo local está bien, en $ARCHIVO; lo que falta es la copia remota."
        exit 6
    fi
    log "Copiando a $DESTINO_REMOTO…"
    # --partial para que una copia cortada se reanude en vez de empezar de cero;
    # --chmod para que el archivo llegue con los mismos permisos restrictivos
    # que el umask 077 le dio aquí, sea cual sea el umask del destino.
    if ! rsync --archive --partial --chmod=F600 "$ARCHIVO" "$DESTINO_REMOTO"; then
        error "La copia a $DESTINO_REMOTO falló. El respaldo local está bien, en $ARCHIVO."
        error "Mientras esto no funcione, un disco perdido se lleva todo: revise la ruta, la red y la clave SSH."
        exit 6
    fi
    log "Copia fuera del servidor: hecha."
else
    log "AVISO: no hay copia fuera del servidor. Un respaldo en el mismo disco que la base no"
    log "       cubre el único fallo irreversible: que ese disco no vuelva. Defina"
    log "       SICOT_RESPALDO_DESTINO_REMOTO (otro disco montado o un destino SSH)."
fi

# Rotación: se conservan los N más recientes por fecha de nombre.
# Se listan los dos patrones: los `.dump` de antes del cifrado y los `.dump.gpg`
# de después. Sin `.dump.gpg` en la rotación, los cifrados se acumularían para
# siempre y el disco se llenaría en silencio — justo lo que la rotación evita.
sobrantes="$(ls -1t "$DESTINO"/sicot-*.dump "$DESTINO"/sicot-*.dump.gpg 2>/dev/null \
    | tail -n "+$((CONSERVAR + 1))" || true)"
if [[ -n "$sobrantes" ]]; then
    echo "$sobrantes" | while read -r viejo; do
        log "Rotando (eliminando) respaldo antiguo: $viejo"
        rm -f "$viejo"
    done
fi

log "Listo. Respaldos conservados: $(ls -1 "$DESTINO"/sicot-*.dump "$DESTINO"/sicot-*.dump.gpg 2>/dev/null | wc -l)"
