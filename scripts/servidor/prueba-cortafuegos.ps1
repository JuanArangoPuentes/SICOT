# ══════════════════════════════════════════════════════════════════════════
# PRUEBA DE LA LECTURA DEL CORTAFUEGOS
#
# Comprueba la decisión de cortafuegos.ps1 con reglas inventadas, sin leer
# ni tocar el cortafuegos de este equipo. Existe porque esa decisión no se
# puede probar a mano: depende de cómo tenga Windows marcada la red, y eso
# no se cambia para hacer una prueba.
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\servidor\prueba-cortafuegos.ps1
#
# Termina con código 0 si todos los casos dan lo esperado.
# ══════════════════════════════════════════════════════════════════════════

. (Join-Path $PSScriptRoot "cortafuegos.ps1")

# Perfiles de red, como los numera Windows.
$PRIVADA = 2
$PUBLICA = 4
$TODOS = 2147483647

$docker = "C:\users\alguien\appdata\local\programs\dockerdesktop\resources\com.docker.backend.exe"

function Regla($nombre, $puertos, $perfiles, $cambios) {
  $r = @{
    Name = $nombre; Enabled = $true; Direction = 1; Action = 1; Protocol = 6
    LocalPorts = $puertos; Profiles = $perfiles; ApplicationName = ""
  }
  if ($cambios) { foreach ($k in $cambios.Keys) { $r[$k] = $cambios[$k] } }
  return New-Object PSObject -Property $r
}

$fallos = 0
function Caso($texto, $obtenido, $esperado) {
  if ("$obtenido" -eq "$esperado") {
    Write-Host "  OK  $texto" -ForegroundColor Green
  } else {
    Write-Host "  X   $texto" -ForegroundColor Red
    Write-Host "        esperado: $esperado   obtenido: $obtenido" -ForegroundColor Red
    $script:fallos++
  }
}

Write-Host ""
Write-Host "  Lectura del cortafuegos: la decisión, con reglas inventadas" -ForegroundColor Cyan
Write-Host ""

$propia      = Regla "SICOT web (8443)" "8443" $TODOS
$soloPrivada = Regla "SICOT web (8443)" "8443" $PRIVADA
$deDocker    = Regla "com.docker.backend.exe" "*" $PUBLICA @{ ApplicationName = $docker }
$deLaTienda  = Regla "Microsoft Store" "*" $TODOS @{ Protocol = 256 }
$bloqueo     = Regla "Bloquear 8443" "8443" $TODOS @{ Action = 0 }

Caso "Sin reglas, el puerto está cerrado" `
  (ReglaQueAbre @() $PRIVADA 8443) $false
Caso "Una regla propia del puerto lo abre" `
  (ReglaQueAbre @($propia) $PRIVADA 8443) "SICOT web (8443)"
Caso "La regla de otro puerto no abre este" `
  (ReglaQueAbre @((Regla "SICOT pruebas (8080)" "8080" $TODOS)) $PRIVADA 8443) $false

Caso "Una regla solo para redes privadas no abre en una red pública" `
  (ReglaQueAbre @($soloPrivada) $PUBLICA 8443) $false
Caso "Esa misma regla sí abre en una red privada" `
  (ReglaQueAbre @($soloPrivada) $PRIVADA 8443) "SICOT web (8443)"

Caso "La regla de Docker abre el puerto donde vale (red pública)" `
  (ReglaQueAbre @($deDocker) $PUBLICA 8443) "com.docker.backend.exe"
Caso "La regla de Docker no abre nada donde no vale (red privada)" `
  (ReglaQueAbre @($deDocker) $PRIVADA 8443) $false

Caso "«Todos los puertos» de una aplicación de la Tienda no cuenta" `
  (ReglaQueAbre @($deLaTienda) $PRIVADA 8443) $false
Caso "Una regla del puerto atada a otro programa no cuenta" `
  (ReglaQueAbre @((Regla "Otro programa" "8443" $TODOS @{ ApplicationName = "C:\otro\programa.exe" })) $PRIVADA 8443) $false

Caso "Un bloqueo le gana a la regla que permite" `
  (ReglaQueAbre @($propia, $bloqueo) $PRIVADA 8443) $false
Caso "…también si el bloqueo va primero" `
  (ReglaQueAbre @($bloqueo, $propia) $PRIVADA 8443) $false

Caso "Un rango que contiene el puerto lo abre" `
  (ReglaQueAbre @((Regla "Rango" "8000-9000" $TODOS)) $PRIVADA 8443) "Rango"
Caso "Una lista sin el puerto no lo abre" `
  (ReglaQueAbre @((Regla "Web" "80,443" $TODOS)) $PRIVADA 8443) $false
Caso "Una lista con el puerto lo abre" `
  (ReglaQueAbre @((Regla "Varios" "80, 8443" $TODOS)) $PRIVADA 8443) "Varios"

Caso "Una regla desactivada no cuenta" `
  (ReglaQueAbre @((Regla "Apagada" "8443" $TODOS @{ Enabled = $false })) $PRIVADA 8443) $false
Caso "Una regla de salida no cuenta" `
  (ReglaQueAbre @((Regla "Salida" "8443" $TODOS @{ Direction = 2 })) $PRIVADA 8443) $false
Caso "Una regla de UDP no cuenta" `
  (ReglaQueAbre @((Regla "UDP" "8443" $TODOS @{ Protocol = 17 })) $PRIVADA 8443) $false

Write-Host ""
if ($fallos -eq 0) {
  Write-Host "  Todos los casos dieron lo esperado." -ForegroundColor Green
  Write-Host ""
  exit 0
}
Write-Host "  $fallos caso(s) no dieron lo esperado." -ForegroundColor Red
Write-Host ""
exit 1
