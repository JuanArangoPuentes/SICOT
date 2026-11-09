# ══════════════════════════════════════════════════════════════════════════
# PRENDER EL SERVIDOR DE SICOT
#
# Deja SICOT funcionando en este equipo y muestra con qué dirección se
# entra, desde aquí y desde otro computador de la misma red.
#
# Se usa con doble clic en «Prender SICOT.bat»: no hay que saber Docker ni
# escribir comandos.
#
# ¿Por qué un script y no «docker compose up» a secas? Porque las tres
# cosas que hacen fallar una prueba no las resuelve ese comando:
#
#   1. Al encender el equipo, Docker Desktop tarda un rato en estar listo.
#      Antes de eso el comando falla con un error que no dice qué esperar.
#   2. La IA necesita unos 5 GB de memoria. Con los programas que arrancan
#      solos (Discord, Spotify, Teams, juegos) el equipo se queda sin
#      memoria y un informe que debería tardar 50 segundos tarda 4 minutos.
#   3. La dirección para entrar desde otro computador depende de la IP, y
#      esta cambia cada vez que el portátil se conecta a otra red. Escrita
#      en un papel, deja de servir sin avisar.
#
# Este script espera a Docker, libera la memoria y lee la IP del momento.
# ══════════════════════════════════════════════════════════════════════════

$ErrorActionPreference = "Continue"

# ── Ajustes ───────────────────────────────────────────────────────────────
$raiz = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$modeloIa = "qwen2.5:7b"
$puertoWeb = 8443
$dockerDesktop = "$env:LOCALAPPDATA\Programs\DockerDesktop\Docker Desktop.exe"
$ollamaApp = "$env:LOCALAPPDATA\Programs\Ollama\ollama app.exe"

# Programas que arrancan solos con Windows y no hacen falta para la prueba.
# No se cierra ningún navegador (es con el que se va a entrar) ni nada del
# proyecto.
$programasDeSobra = @(
  "Discord", "Spotify", "ms-teams", "RobloxPlayerBeta", "EpicGamesLauncher",
  "RiotClientServices", "figma_agent", "RazerAxon", "LeagueClient"
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

function MemoriaLibreGB {
  $os = Get-CimInstance Win32_OperatingSystem
  return [math]::Round($os.FreePhysicalMemory / 1MB, 1)
}

Clear-Host
Write-Host ""
Write-Host "  ╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "  ║                 SICOT — prender el servidor                    ║" -ForegroundColor Cyan
Write-Host "  ╚════════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Esto deja SICOT funcionando en este equipo. Tarda entre uno y"
Write-Host "  tres minutos la primera vez del día. No cierre esta ventana"
Write-Host "  hasta que diga que terminó."

# ── 1. Liberar memoria ────────────────────────────────────────────────────
Titulo "1 de 5 · Memoria"

$libreAntes = MemoriaLibreGB
Paso "Memoria libre ahora: $libreAntes GB"

$sobrando = @()
foreach ($nombre in $programasDeSobra) {
  $p = Get-Process -Name $nombre -ErrorAction SilentlyContinue
  if ($p) { $sobrando += $nombre }
}

if ($sobrando.Count -gt 0) {
  Write-Host ""
  Write-Host "  Están abiertos programas que no hacen falta para la prueba y"
  Write-Host "  le quitan memoria a la IA:" -ForegroundColor Yellow
  Write-Host "    $($sobrando -join ', ')"
  Write-Host ""
  $r = Read-Host "  ¿Los cierro? (Intro = si, N = no)"
  if ($r -eq "" -or $r -match "^[sS]") {
    foreach ($nombre in $sobrando) {
      Stop-Process -Name $nombre -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 3
    Bien "Cerrados. Memoria libre: $(MemoriaLibreGB) GB"
  } else {
    Aviso "No se cerraron. La IA puede ir lenta."
  }
} else {
  Bien "No hay programas de sobra abiertos"
}

$libre = MemoriaLibreGB
if ($libre -lt 6) {
  Aviso "Quedan $libre GB libres. La IA necesita unos 5 GB: si va lenta,"
  Aviso "cierre pestañas del navegador y vuelva a prender."
}

# ── 2. Docker ─────────────────────────────────────────────────────────────
Titulo "2 de 5 · Docker (el motor donde corre SICOT)"

docker info 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
  if (Test-Path $dockerDesktop) {
    Paso "Docker no está listo. Arrancándolo…"
    Start-Process $dockerDesktop
  } else {
    Mal "No encuentro Docker Desktop. Ábralo a mano desde el menú Inicio."
  }

  Paso "Esperando a que Docker esté listo (hasta 4 minutos)…"
  $esperado = 0
  while ($esperado -lt 240) {
    Start-Sleep -Seconds 5
    $esperado += 5
    docker info 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) { break }
    if ($esperado % 30 -eq 0) { Paso "  … $esperado segundos" }
  }

  docker info 2>&1 | Out-Null
  if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Mal "Docker no arrancó. Abra Docker Desktop a mano, espere a que diga"
    Mal "«Engine running» y vuelva a ejecutar este archivo."
    Write-Host ""
    Read-Host "  Pulse Intro para cerrar"
    exit 1
  }
}
Bien "Docker está listo"

# ── 3. La IA ──────────────────────────────────────────────────────────────
Titulo "3 de 5 · La IA que redacta los documentos"

$ollamaViva = $false
try {
  Invoke-WebRequest -Uri "http://localhost:11434/api/tags" -UseBasicParsing -TimeoutSec 5 | Out-Null
  $ollamaViva = $true
} catch {
  $ollamaViva = $false
}

if (-not $ollamaViva) {
  if (Test-Path $ollamaApp) {
    Paso "Arrancando Ollama…"
    Start-Process $ollamaApp
    $esperado = 0
    while ($esperado -lt 60) {
      Start-Sleep -Seconds 3
      $esperado += 3
      try {
        Invoke-WebRequest -Uri "http://localhost:11434/api/tags" -UseBasicParsing -TimeoutSec 3 | Out-Null
        $ollamaViva = $true
        break
      } catch { }
    }
  }
}

if ($ollamaViva) {
  Bien "Ollama responde"
} else {
  Aviso "Ollama no responde. SICOT funciona igual, pero los botones de IA"
  Aviso "van a decir que no está disponible."
}

# ── 4. Levantar SICOT ─────────────────────────────────────────────────────
Titulo "4 de 5 · SICOT"

Push-Location $raiz
Paso "Levantando la base de datos, la API y la aplicación web…"
docker compose up -d 2>&1 | ForEach-Object { if ($_ -match "Error|error") { Write-Host "      $_" -ForegroundColor DarkYellow } }

Paso "Esperando a que la API responda (hasta 2 minutos)…"
$apiViva = $false
$esperado = 0
while ($esperado -lt 120) {
  Start-Sleep -Seconds 5
  $esperado += 5
  try {
    # Se mira el CÓDIGO HTTP, no el texto de la respuesta: el actuator de
    # Spring contesta con un Content-Type propio
    # (application/vnd.spring-boot.actuator.v3+json) que PowerShell 5.1 no
    # reconoce como texto, así que $r.Content llega como lista de bytes y
    # cualquier comparación con una cadena falla siempre. Costó un arranque
    # entero en falso el 09-11-2026: la API estaba sana y el script decía que
    # no respondía. El actuator ya devuelve 200 solo cuando está UP (503 si
    # no), así que el código basta.
    $r = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 5
    if ($r.StatusCode -eq 200) { $apiViva = $true; break }
  } catch { }
  if ($esperado % 30 -eq 0) { Paso "  … $esperado segundos" }
}
Pop-Location

if (-not $apiViva) {
  Write-Host ""
  Mal "La API no respondió. Para ver qué pasó, ejecute en esta ventana:"
  Write-Host "      cd `"$raiz`"; docker compose logs --tail 40 backend" -ForegroundColor Gray
  Write-Host ""
  Read-Host "  Pulse Intro para cerrar"
  exit 1
}
Bien "La API responde"

$webViva = $false
$esperado = 0
while ($esperado -lt 60) {
  try {
    Invoke-WebRequest -Uri "http://localhost:$puertoWeb/" -UseBasicParsing -TimeoutSec 5 | Out-Null
    $webViva = $true
    break
  } catch { Start-Sleep -Seconds 5; $esperado += 5 }
}
if ($webViva) { Bien "La aplicación web responde" } else { Aviso "La aplicación web todavía no responde" }

# ── 5. Precalentar la IA ──────────────────────────────────────────────────
Titulo "5 de 5 · Dejar la IA lista"

if ($ollamaViva) {
  Paso "Cargando el modelo en memoria para que la primera respuesta no tarde…"
  Paso "(unos 15 segundos)"
  $cuerpo = @{
    model      = $modeloIa
    prompt     = "Responde solo: OK"
    stream     = $false
    keep_alive = "60m"
    options    = @{ num_predict = 5 }
  } | ConvertTo-Json -Depth 4

  try {
    $inicio = Get-Date
    Invoke-RestMethod -Method Post -Uri "http://localhost:11434/api/generate" `
      -Body $cuerpo -ContentType "application/json" -TimeoutSec 240 | Out-Null
    $seg = [math]::Round(((Get-Date) - $inicio).TotalSeconds)
    Bien "IA lista (tardó $seg s; queda cargada 60 minutos)"
  } catch {
    Aviso "No se pudo precalentar el modelo. La primera respuesta tardará más."
  }
}

# ── Resumen ───────────────────────────────────────────────────────────────
$ip = $null
$conf = Get-NetIPConfiguration | Where-Object {
  $_.IPv4DefaultGateway -ne $null -and $_.NetAdapter.Status -eq "Up"
} | Select-Object -First 1
if ($conf) { $ip = $conf.IPv4Address.IPAddress }

Write-Host ""
Write-Host "  ╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "  ║                   SICOT ESTÁ FUNCIONANDO                       ║" -ForegroundColor Green
Write-Host "  ╚════════════════════════════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""
Write-Host "  Para entrar DESDE ESTE EQUIPO:" -ForegroundColor White
Write-Host "      http://localhost:$puertoWeb" -ForegroundColor Cyan
Write-Host ""
if ($ip) {
  Write-Host "  Para entrar DESDE OTRO COMPUTADOR de la misma red:" -ForegroundColor White
  Write-Host "      http://${ip}:$puertoWeb" -ForegroundColor Cyan
  Write-Host ""
  Write-Host "  (Esta dirección cambia si el equipo se conecta a otra red."
  Write-Host "   Si deja de servir, vuelva a prender y lea la nueva.)" -ForegroundColor DarkGray

  # El cortafuegos de Windows bloquea el puerto 8443 desde otros equipos
  # mientras no exista una regla que lo permita. Se comprueba y se avisa
  # aquí, en vez de descubrirlo en medio de la prueba con un navegador que
  # solo dice «no se puede acceder al sitio». No se crea la regla sola:
  # cambiar el cortafuegos es decisión del dueño del equipo y pide permisos
  # de administrador.
  $reglaPuesta = $false
  try {
    $reglas = Get-NetFirewallRule -Enabled True -Direction Inbound -ErrorAction Stop
    foreach ($r in $reglas) {
      $puertos = $r | Get-NetFirewallPortFilter -ErrorAction SilentlyContinue
      if ($puertos -and $puertos.LocalPort -contains "$puertoWeb") { $reglaPuesta = $true; break }
    }
  } catch { }

  if (-not $reglaPuesta) {
    Write-Host ""
    Aviso "Falta abrir el puerto $puertoWeb en el cortafuegos: desde otro"
    Aviso "computador la página no va a cargar. Para permitirlo, abra"
    Aviso "PowerShell COMO ADMINISTRADOR y pegue esta línea:"
    Write-Host ""
    Write-Host "      New-NetFirewallRule -DisplayName `"SICOT web ($puertoWeb)`" -Direction Inbound -Protocol TCP -LocalPort $puertoWeb -Profile Private -Action Allow" -ForegroundColor Gray
    Write-Host ""
    Write-Host "  (Desde ESTE equipo se entra igual, sin tocar nada.)" -ForegroundColor DarkGray
  }
} else {
  Aviso "Sin red: solo se puede entrar desde este equipo."
}
Write-Host ""
Write-Host "  Memoria libre: $(MemoriaLibreGB) GB" -ForegroundColor DarkGray
Write-Host "  Para apagarlo al terminar: doble clic en «Apagar SICOT.bat»" -ForegroundColor DarkGray
Write-Host ""

Paso "Abriendo el navegador…"
Start-Process "http://localhost:$puertoWeb"

Write-Host ""
Read-Host "  Puede cerrar esta ventana. Pulse Intro"
