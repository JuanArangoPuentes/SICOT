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
    rm -rf "$TRABAJO/bin" "$TRABAJO/destino"
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

if [[ $fallos -gt 0 ]]; then
    echo "$fallos comprobación(es) fallida(s)."
    exit 1
fi
echo "Todas las comprobaciones pasaron."
