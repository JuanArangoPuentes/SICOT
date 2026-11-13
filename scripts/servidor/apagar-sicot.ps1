# ══════════════════════════════════════════════════════════════════════════
# APAGAR EL SERVIDOR DE SICOT
#
# Detiene SICOT y suelta la memoria que ocupaba la IA. Los datos NO se
# borran: quedan guardados y vuelven a estar ahí la próxima vez que se
# prenda.
#
# Se usa con doble clic en «Apagar SICOT.bat».
#
# Nota para quien lea el código: aquí se usa «docker compose down» a
# secas, nunca con -v. Con -v se borraría el volumen de la base de datos,
# es decir todos los contratos, usuarios y firmas.
# ══════════════════════════════════════════════════════════════════════════

$ErrorActionPreference = "Continue"
$raiz = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$modeloIa = "qwen2.5:7b"

Clear-Host
Write-Host ""
Write-Host "  ╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "  ║                  SICOT — apagar el servidor                    ║" -ForegroundColor Cyan
Write-Host "  ╚════════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Los datos no se borran: al prenderlo de nuevo todo sigue igual."
Write-Host ""

Write-Host "  · Deteniendo SICOT…" -ForegroundColor Gray
Push-Location $raiz
docker compose down 2>&1 | ForEach-Object { if ($_ -match "Error|error") { Write-Host "      $_" -ForegroundColor DarkYellow } }
Pop-Location

Write-Host "  · Soltando la memoria de la IA…" -ForegroundColor Gray
try {
  $cuerpo = @{ model = $modeloIa; keep_alive = 0 } | ConvertTo-Json
  Invoke-RestMethod -Method Post -Uri "http://localhost:11434/api/generate" `
    -Body $cuerpo -ContentType "application/json" -TimeoutSec 20 | Out-Null
} catch { }

$os = Get-CimInstance Win32_OperatingSystem
$libre = [math]::Round($os.FreePhysicalMemory / 1MB, 1)

Write-Host ""
Write-Host "  OK  SICOT está apagado. Memoria libre: $libre GB" -ForegroundColor Green
Write-Host ""
Read-Host "  Pulse Intro para cerrar"
