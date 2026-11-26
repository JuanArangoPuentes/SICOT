#!/usr/bin/env bash
#
# Pruebas de scripts/respaldo-sicot.sh, sin base de datos.
#
# Lo que importa de un respaldo que falla no es el mensaje: es lo que deja en la
# carpeta. VigilanciaDelRespaldo y la rotación miran esa carpeta, así que un
# volcado truncado o sin verificar que se queda ahí con nombre de respaldo
# silencia la alarma del RPO (ADR-002) y, noche tras noche, desplaza a los
# respaldos buenos. Estas pruebas fijan que un fallo no deje nada con ese nombre.
#
# `docker` y `pg_restore` se sustituyen por dobles en el PATH: el script se
# ejercita entero —detección del modo, volcado, verificación, rotación— sin
# contenedores ni PostgreSQL.
#
# Uso:  bash scripts/prueba-respaldo-sicot.sh

set -uo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT="$RAIZ/scripts/respaldo-sicot.sh"
TRABAJO="$(mktemp -d)"
trap 'rm -rf "$TRABAJO"' EXIT

fallos=0
ok()    { echo "  ok   $*"; }
falla() { echo "  FALLA $*"; fallos=$((fallos + 1)); }

# $1: cómo termina pg_dump (bien | mal); $2: cómo termina la verificación.
preparar() {
    # ${TRABAJO:?} y no $TRABAJO: si la variable llegara vacía, el borrado
    # recursivo caería sobre /bin y /destino de la máquina (ShellCheck SC2115).
    rm -rf "${TRABAJO:?}/bin" "${TRABAJO:?}/destino"
    mkdir -p "$TRABAJO/bin" "$TRABAJO/destino"
    # Un respaldo bueno de la noche anterior, que nada de lo que pase hoy debe
    # tapar ni borrar.
    printf 'PGDMP volcado completo' > "$TRABAJO/destino/sicot-20260101-020000.dump"

    cat > "$TRABAJO/bin/docker" <<EOF
#!/usr/bin/env bash
case "\$1" in
    ps)   echo sicot-db ;;
    # Escribe parte del volcado antes de terminar, como un pg_dump al que se le
    # llena el disco o se le reinicia la base a mitad.
    exec) printf 'PGDMP volcado a medias'; [ "$1" = bien ] ;;
    *)    exit 1 ;;
esac
EOF
    cat > "$TRABAJO/bin/pg_restore" <<EOF
#!/usr/bin/env bash
[ "$2" = bien ]
EOF
    chmod +x "$TRABAJO/bin/docker" "$TRABAJO/bin/pg_restore"
}

ejecutar() {
    PATH="$TRABAJO/bin:$PATH" bash "$SCRIPT" "$TRABAJO/destino" > "$TRABAJO/salida" 2>&1
}

# Lo que quedó en la carpeta además del respaldo de la noche anterior.
nuevos() {
    find "$TRABAJO/destino" -type f ! -name 'sicot-20260101-020000.dump' -printf '%f\n'
}

comprobar_que_no_dejo_nada() {
    local quedo
    quedo="$(nuevos)"
    if [[ -z "$quedo" ]]; then
        ok "no deja ningún archivo en la carpeta vigilada"
    else
        falla "dejó en la carpeta vigilada: $quedo"
    fi
    if [[ -f "$TRABAJO/destino/sicot-20260101-020000.dump" ]]; then
        ok "conserva el respaldo bueno anterior"
    else
        falla "borró el respaldo bueno anterior"
    fi
}

echo "pg_dump falla a mitad del volcado"
preparar mal bien
if ejecutar; then falla "terminó con éxito"; else ok "termina con error"; fi
if grep -q "pg_dump falló" "$TRABAJO/salida"; then
    ok "el log del cron dice que falló pg_dump"
else
    falla "el log no dice qué falló: $(cat "$TRABAJO/salida")"
fi
comprobar_que_no_dejo_nada

echo "el volcado no supera la verificación"
preparar bien mal
ejecutar
codigo=$?
if [[ $codigo -eq 3 ]]; then ok "termina con el código 3"; else falla "terminó con el código $codigo"; fi
comprobar_que_no_dejo_nada

echo "todo sale bien"
preparar bien bien
if ejecutar; then ok "termina con éxito"; else falla "terminó con error: $(cat "$TRABAJO/salida")"; fi
quedo="$(nuevos)"
if [[ "$quedo" =~ ^sicot-[0-9]{8}-[0-9]{6}\.dump$ ]]; then
    ok "deja un único respaldo nuevo: $quedo"
else
    falla "se esperaba un único sicot-*.dump nuevo y quedó: $quedo"
fi
# Git Bash en Windows no refleja los permisos de Unix: allí no se comprueba.
if [[ "$(uname -s)" != MINGW* && "$(uname -s)" != MSYS* && -n "$quedo" ]]; then
    permisos="$(stat -c %a "$TRABAJO/destino/$quedo")"
    if [[ "$permisos" == 600 ]]; then
        ok "solo lo puede leer quien corre el respaldo (600)"
    else
        falla "el volcado quedó con permisos $permisos: cualquier cuenta del servidor puede leer la base entera"
    fi
fi


# ── Cifrado y copia fuera del servidor (20-11-2026) ────────────────────────
#
# Las dos capacidades nuevas del script. Lo que estas comprobaciones fijan no es
# que gpg funcione —eso es asunto de gpg— sino las tres decisiones del script
# que, mal tomadas, convierten una mejora en una pérdida de datos:
#
#   1. Que el cifrado ocurra DESPUÉS de verificar. La verificación necesita leer
#      el volcado con pg_restore, y un archivo cifrado no se puede leer: cifrar
#      primero dejaría el script «verificando» algo que ningún pg_restore puede
#      abrir, es decir sin verificar nada y sin que se notara.
#   2. Que un fallo del cifrado NO borre el volcado en claro. Cambiar un problema
#      de confidencialidad por uno de disponibilidad es peor que el original.
#   3. Que el nombre acabe en `.dump.gpg`, porque es lo que VigilanciaDelRespaldo
#      cuenta como respaldo. Si no coincidiera, encender el cifrado dejaría al
#      sistema avisando cada día de que no hay respaldos habiendo uno cada noche.
#
# NO se comprueba el caso «se pide cifrar y no hay gpg». Se escribió y se quitó:
# para simularlo hay que ejecutar el script con un PATH sin gpg, y ese mismo PATH
# tiene que seguir teniendo date, du, find y ls. El resultado era una
# comprobación que se omitía sola en cualquier máquina con gpg instalado —es
# decir, en todas— y una prueba que nunca corre es peor que ninguna, porque
# ocupa el sitio de la que falta.

echo "cifrado con frase de paso"
if command -v gpg >/dev/null 2>&1; then
    preparar bien bien
    printf 'frase-de-paso-de-la-prueba' > "$TRABAJO/clave.pass"
    chmod 600 "$TRABAJO/clave.pass"
    if SICOT_RESPALDO_GPG_CLAVE_ARCHIVO="$TRABAJO/clave.pass" ejecutar; then
        ok "termina con éxito"
    else
        falla "terminó con error: $(cat "$TRABAJO/salida")"
    fi
    quedo="$(nuevos)"
    if [[ "$quedo" =~ ^sicot-[0-9]{8}-[0-9]{6}\.dump\.gpg$ ]]; then
        ok "deja solo el cifrado, con el nombre que la vigilancia reconoce: $quedo"
    else
        falla "se esperaba un único sicot-*.dump.gpg y quedó: $quedo"
    fi
    if [[ -n "$quedo" ]] && gpg --batch --quiet --decrypt \
            --passphrase-file "$TRABAJO/clave.pass" "$TRABAJO/destino/$quedo" 2>/dev/null \
            | grep -q 'PGDMP volcado a medias'; then
        # «a medias» es el contenido que escribe el doble de pg_dump; lo que se
        # comprueba es que el descifrado devuelve EXACTAMENTE lo que se volcó.
        ok "lo cifrado se descifra y es el volcado que se verificó"
    else
        falla "el archivo cifrado no devuelve el volcado original al descifrarlo"
    fi
else
    echo "  (omitido: no hay gpg en esta máquina)"
fi

echo "copia fuera del servidor"
if command -v rsync >/dev/null 2>&1; then
    preparar bien bien
    mkdir -p "$TRABAJO/remoto"
    if SICOT_RESPALDO_DESTINO_REMOTO="$TRABAJO/remoto/" ejecutar; then
        ok "termina con éxito"
    else
        falla "terminó con error: $(cat "$TRABAJO/salida")"
    fi
    if [[ -n "$(find "$TRABAJO/remoto" -name 'sicot-*.dump' -print -quit)" ]]; then
        ok "el respaldo llegó al destino remoto"
    else
        falla "el destino remoto quedó vacío"
    fi

    echo "la copia remota falla"
    preparar bien bien
    codigo=0
    SICOT_RESPALDO_DESTINO_REMOTO="$TRABAJO/no-existe-y-no-se-puede-crear/x/y/" ejecutar || codigo=$?
    if [[ $codigo -eq 6 ]]; then ok "termina con el código 6"; else falla "terminó con el código $codigo"; fi
    quedo="$(nuevos)"
    if [[ -n "$quedo" ]]; then
        ok "el respaldo LOCAL se conserva: la copia remota es un extra, no una condición"
    else
        falla "perdió el respaldo local porque falló la copia remota"
    fi
else
    echo "  (omitido: no hay rsync en esta máquina)"
fi

echo "sin configurar nada, lo dice"
preparar bien bien
ejecutar
if grep -q "NO está cifrado" "$TRABAJO/salida" && grep -q "no hay copia fuera del servidor" "$TRABAJO/salida"; then
    ok "avisa de que falta el cifrado y la copia remota"
else
    falla "no avisó de lo que falta: un control ausente en silencio es el que nadie añade"
fi

if [[ $fallos -gt 0 ]]; then
    echo "$fallos comprobación(es) fallida(s)."
    exit 1
fi
echo "Todas las comprobaciones pasaron."
