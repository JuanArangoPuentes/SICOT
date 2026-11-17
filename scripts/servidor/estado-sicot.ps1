# ══════════════════════════════════════════════════════════════════════════
# ESTADO DEL SERVIDOR DE SICOT
#
# Dice si SICOT está prendido en este equipo, pieza por pieza, y vuelve a
# mostrar con qué dirección se entra. Solo mira: no prende ni apaga nada.
#
# Se usa con doble clic en «Estado de SICOT.bat».
#
# ¿Por qué hace falta, si ya existe «Prender SICOT.bat»? Porque prender no
# es una forma de preguntar. Volver a prender para saber si quedó prendido
# tarda uno o dos minutos, ofrece cerrar programas y carga el modelo de IA
# (unos 5 GB) aunque nadie lo vaya a usar. Y las tres dudas que aparecen
# en medio de una prueba son de mirar, no de arrancar:
#
#   1. «¿Quedó prendido?», al volver al equipo después de un rato.
#   2. «¿Cuál era la dirección?», cuando el portátil cambió de red y la que
#      se había leído dejó de servir.
#   3. «¿Por qué la IA va lenta?». Casi siempre es que el modelo salió de
#      memoria, y eso no se ve en ninguna pantalla de SICOT.
#
# Termina con código 0 solo si SICOT se puede usar: Docker, los tres
# contenedores, la API y la aplicación web. La IA no cuenta para eso,
# porque SICOT funciona sin ella, y así se dice en pantalla.
#
# Los puertos son parámetros para poder probar este script contra puertos
# donde no hay nada, sin apagar el SICOT que alguien esté usando.
# ══════════════════════════════════════════════════════════════════════════

param(
  [int]$PuertoWeb = 8443,
  [int]$PuertoApi = 8080,
  [int]$PuertoIa = 11434,
  # Sin limpiar la pantalla ni esperar una tecla: para llamarlo desde otro
  # script o desde una prueba.
  [switch]$SinPausa
)

$ErrorActionPreference = "Continue"

# ── Ajustes ───────────────────────────────────────────────────────────────
$modeloIa = "qwen2.5:7b"
$contenedores = @(
  @{ Nombre = "sicot-db";       Que = "La base de datos" },
  @{ Nombre = "sicot-backend";  Que = "La API" },
  @{ Nombre = "sicot-frontend"; Que = "La aplicación web" }
)

# ── Ayudas para que se lea bien en pantalla ───────────────────────────────
function Titulo($texto) {
  Write-Host ""
  Write-Host "  $texto" -ForegroundColor Cyan
  Write-Host "  $('─' * 66)" -ForegroundColor DarkGray
}
function Paso($texto)  { Write-Host "  · $texto" -ForegroundColor Gray }
function Bien($texto)  { Write-Host "  OK  $texto" -ForegroundColor Green }
function Aviso($texto) { Write-Host "  !   $texto" -ForegroundColor Yellow }
function Mal($texto)   { Write-Host "  X   $texto" -ForegroundColor Red }

function Recuadro($texto, $color) {
  $ancho = 64
  $izquierda = [math]::Floor(($ancho - $texto.Length) / 2)
  $derecha = $ancho - $texto.Length - $izquierda
  Write-Host ""
  Write-Host "  ╔$('═' * $ancho)╗" -ForegroundColor $color
  Write-Host "  ║$(' ' * $izquierda)$texto$(' ' * $derecha)║" -ForegroundColor $color
  Write-Host "  ╚$('═' * $ancho)╝" -ForegroundColor $color
  Write-Host ""
}

function MemoriaLibreGB {
  $os = Get-CimInstance Win32_OperatingSystem
  return [math]::Round($os.FreePhysicalMemory / 1MB, 1)
}

# Se mira el CÓDIGO HTTP y no el texto de la respuesta, por lo mismo que en
# prender-sicot.ps1: el actuator contesta con un Content-Type que PowerShell
# 5.1 no reconoce como texto, y comparar el contenido con una cadena falla
# siempre aunque la API esté sana.
function Responde($url) {
  try {
    $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5
    return ($r.StatusCode -eq 200)
  } catch {
    return $false
  }
}

function DockerListo {
  # Si Docker ni siquiera está instalado, llamarlo imprime un error en rojo
  # que no le dice nada a quien dio doble clic. Se pregunta antes.
  if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { return $false }
  docker info 2>$null | Out-Null
  return ($LASTEXITCODE -eq 0)
}

# Devuelve «estado|salud» (por ejemplo «running|healthy»), o $null si el
# contenedor no existe, que es lo que pasa cuando SICOT está apagado:
# «Apagar SICOT.bat» los quita, no los detiene.
function EstadoDeContenedor($nombre) {
  $formato = '{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}sin-chequeo{{end}}'
  $salida = docker inspect --format $formato $nombre 2>$null
  if ($LASTEXITCODE -ne 0 -or -not $salida) { return $null }
  return "$salida".Trim()
}

# La lectura del cortafuegos es la misma que usa prender-sicot.ps1.
. (Join-Path $PSScriptRoot "cortafuegos.ps1")

if (-not $SinPausa) { Clear-Host }
Write-Host ""
Write-Host "  ╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "  ║                 SICOT — estado del servidor                    ║" -ForegroundColor Cyan
Write-Host "  ╚════════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Esto solo mira cómo está SICOT en este equipo. No prende ni"
Write-Host "  apaga nada, y tarda unos segundos."

# Lo que SICOT necesita para poder usarse. La IA no entra aquí.
$faltan = @()
$algoPrendido = $false

# ── 1. Docker y los contenedores ──────────────────────────────────────────
Titulo "1 de 4 · Docker y las piezas de SICOT"

$dockerListo = DockerListo
if ($dockerListo) {
  Bien "Docker está listo"
  foreach ($c in $contenedores) {
    $estado = EstadoDeContenedor $c.Nombre
    if (-not $estado) {
      Mal "$($c.Que) no está prendida ($($c.Nombre) no existe)"
      $faltan += $c.Que
      continue
    }
    $partes = $estado.Split("|")
    if ($partes[0] -ne "running") {
      Mal "$($c.Que) está detenida ($($c.Nombre): $($partes[0]))"
      $faltan += $c.Que
    } elseif ($partes[1] -eq "healthy" -or $partes[1] -eq "sin-chequeo") {
      Bien "$($c.Que) está corriendo"
      $algoPrendido = $true
    } elseif ($partes[1] -eq "starting") {
      Aviso "$($c.Que) todavía está arrancando: espere un minuto y vuelva a mirar"
      $faltan += "$($c.Que) (arrancando)"
      $algoPrendido = $true
    } else {
      Mal "$($c.Que) corre pero no está sana ($($c.Nombre): $($partes[1]))"
      $faltan += $c.Que
      $algoPrendido = $true
    }
  }
} else {
  Mal "Docker no está listo, así que SICOT no puede estar corriendo"
  $faltan += "Docker"
}

# ── 2. Que de verdad responda ─────────────────────────────────────────────
# Un contenedor «corriendo» no es una aplicación que contesta: se pregunta
# por las dos puertas por las que entra una persona.
Titulo "2 de 4 · Que responda"

if (Responde "http://localhost:$PuertoApi/actuator/health") {
  Bien "La API responde"
  $algoPrendido = $true
} else {
  Mal "La API no responde en el puerto $PuertoApi"
  $faltan += "La API no responde"
}

if (Responde "http://localhost:$PuertoWeb/") {
  Bien "La aplicación web responde"
  $algoPrendido = $true
} else {
  Mal "La aplicación web no responde en el puerto $PuertoWeb"
  $faltan += "La aplicación web no responde"
}

# ── 3. La IA ──────────────────────────────────────────────────────────────
Titulo "3 de 4 · La IA que redacta los documentos"

$iaViva = $false
$modeloInstalado = $false
$modeloCargado = $false
try {
  $instalados = Invoke-RestMethod -Uri "http://localhost:$PuertoIa/api/tags" -TimeoutSec 5
  $iaViva = $true
  foreach ($m in $instalados.models) { if ($m.name -eq $modeloIa) { $modeloInstalado = $true } }
  # /api/ps lista lo que está cargado en memoria AHORA, que es lo que decide
  # si la próxima respuesta tarda 45 segundos o varios minutos.
  $cargados = Invoke-RestMethod -Uri "http://localhost:$PuertoIa/api/ps" -TimeoutSec 5
  foreach ($m in $cargados.models) { if ($m.name -eq $modeloIa) { $modeloCargado = $true } }
} catch { }

if (-not $iaViva) {
  Aviso "Ollama no responde. SICOT funciona igual, pero los botones de IA"
  Aviso "van a decir que no está disponible. «Prender SICOT.bat» lo levanta."
} elseif (-not $modeloInstalado) {
  Aviso "Ollama responde, pero falta el modelo $modeloIa."
  Aviso "Para bajarlo: ollama pull $modeloIa"
} elseif ($modeloCargado) {
  Bien "La IA está lista: el modelo $modeloIa está cargado en memoria"
} else {
  Aviso "La IA responde, pero el modelo no está cargado en memoria: la"
  Aviso "primera respuesta va a tardar más. «Prender SICOT.bat» lo deja cargado."
}

$libre = MemoriaLibreGB
if ($libre -lt 6 -and -not $modeloCargado) {
  Aviso "Quedan $libre GB de memoria libres y el modelo necesita unos 5 GB:"
  Aviso "cierre programas y pestañas del navegador antes de usar la IA."
} else {
  Paso "Memoria libre: $libre GB"
}

# ── 4. Por dónde se entra ─────────────────────────────────────────────────
Titulo "4 de 4 · Por dónde se entra"

Write-Host "  Desde ESTE EQUIPO:" -ForegroundColor White
Write-Host "      http://localhost:$PuertoWeb" -ForegroundColor Cyan

$ip = $null
$conf = Get-NetIPConfiguration | Where-Object {
  $_.IPv4DefaultGateway -ne $null -and $_.NetAdapter.Status -eq "Up"
} | Select-Object -First 1
if ($conf) { $ip = $conf.IPv4Address.IPAddress }

if ($ip) {
  Write-Host ""
  Write-Host "  Desde OTRO COMPUTADOR de la misma red:" -ForegroundColor White
  Write-Host "      http://${ip}:$PuertoWeb" -ForegroundColor Cyan
  Write-Host ""
  Write-Host "  (Esta dirección cambia si el equipo se conecta a otra red:" -ForegroundColor DarkGray
  Write-Host "   es la de este momento.)" -ForegroundColor DarkGray

  # Lo que se dice aquí es lo que dicen las reglas del cortafuegos para la
  # red de este momento. No es una prueba hecha desde otro computador: si
  # la red aísla los equipos entre sí, no cargará aunque el puerto esté
  # abierto.
  $cf = LeerCortafuegos $PuertoWeb
  Write-Host ""
  if (-not $cf.Leido) {
    Paso "No se pudo leer el cortafuegos: si desde otro computador no carga,"
    Paso "revise que el puerto $PuertoWeb esté abierto."
  } elseif ($cf.Regla -is [string]) {
    Bien "El cortafuegos deja entrar por el puerto $PuertoWeb en esta red"
    Paso "(red $($cf.Red); regla «$($cf.Regla)»). En otra red puede no valer:"
    Paso "si el equipo cambia de red, vuelva a mirar."
  } else {
    Aviso "En esta red, que Windows tiene marcada como $($cf.Red), ninguna"
    Aviso "regla del cortafuegos deja entrar por el puerto ${PuertoWeb}: desde otro"
    Aviso "computador la página no va a cargar. «Prender SICOT.bat» muestra"
    Aviso "la línea que hay que pegar en PowerShell como administrador."
  }
} else {
  Write-Host ""
  Aviso "Sin red: solo se puede entrar desde este equipo."
}

# ── Veredicto ─────────────────────────────────────────────────────────────
if ($faltan.Count -eq 0) {
  Recuadro "SICOT ESTÁ PRENDIDO" "Green"
  Write-Host "  Para apagarlo al terminar: doble clic en «Apagar SICOT.bat»" -ForegroundColor DarkGray
  $codigo = 0
} elseif (-not $algoPrendido) {
  Recuadro "SICOT ESTÁ APAGADO" "Yellow"
  Write-Host "  Para prenderlo: doble clic en «Prender SICOT.bat»"
  $codigo = 1
} else {
  Recuadro "SICOT ESTÁ PRENDIDO A MEDIAS" "Red"
  Write-Host "  Falta:"
  foreach ($f in $faltan) { Write-Host "    - $f" -ForegroundColor Yellow }
  Write-Host ""
  Write-Host "  Vuelva a dar doble clic en «Prender SICOT.bat»: levanta lo que"
  Write-Host "  falte sin tocar lo que ya está corriendo. Si sigue igual, mire"
  Write-Host "  qué pasó con: docker compose logs --tail 40 backend"
  $codigo = 1
}
Write-Host ""

if (-not $SinPausa) { Read-Host "  Pulse Intro para cerrar" }
exit $codigo
