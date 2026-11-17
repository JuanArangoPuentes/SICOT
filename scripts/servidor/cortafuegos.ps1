# ══════════════════════════════════════════════════════════════════════════
# LEER EL CORTAFUEGOS DE WINDOWS
#
# Responde una sola pregunta: en la red a la que este equipo está conectado
# AHORA, ¿hay alguna regla que deje entrar a SICOT desde otro computador?
#
# Lo usan prender-sicot.ps1 y estado-sicot.ps1. Solo lee: abrir un puerto es
# decisión del dueño del equipo y pide permisos de administrador.
#
# Buscar «¿hay una regla con el puerto 8443?» no basta. Hay tres cosas que
# esa búsqueda no ve y que cambian la respuesta:
#
#   1. El perfil de la red. Windows marca cada red como privada, pública o
#      de dominio, y una regla vale solo para los perfiles que declara. La
#      misma regla abre el puerto en una red y no en otra: la zona wifi de
#      un teléfono, por ejemplo, Windows suele marcarla como pública.
#   2. La regla de Docker Desktop. Los puertos de SICOT no los abre SICOT:
#      los publica com.docker.backend.exe, y Windows guarda para ese
#      programa una regla de «todos los puertos» en los perfiles que el
#      dueño del equipo aceptó al instalarlo. Donde esa regla vale, el 8443
#      ya entra sin regla propia; donde no vale, hace falta una.
#   3. Los bloqueos. Una regla que bloquea le gana siempre a una que permite.
#
# Se lee por la interfaz COM (HNetCfg.FwPolicy2) y no con
# Get-NetFirewallRule. Medido en este equipo, sin permisos de administrador:
# preguntar por los filtros de puerto directamente da «Acceso denegado», y
# recorrer las reglas una por una funciona pero tarda 20 segundos con 186
# reglas activas. Por COM son 0,2 segundos y no pide elevación.
# ══════════════════════════════════════════════════════════════════════════

# ¿El puerto está en una lista como «8443», «80,443», «8000-9000» o «*»?
# $valeTodos dice qué responder ante «*» o una lista vacía, que significan
# «todos los puertos».
function PuertoEnLaLista($puerto, $lista, $valeTodos) {
  $lista = "$lista".Trim()
  if ($lista -eq "" -or $lista -eq "*") { return $valeTodos }
  foreach ($tramo in $lista.Split(",")) {
    $tramo = $tramo.Trim()
    if ($tramo -eq "$puerto") { return $true }
    if ($tramo -match "^(\d+)-(\d+)$") {
      if ($puerto -ge [int]$Matches[1] -and $puerto -le [int]$Matches[2]) { return $true }
    }
  }
  return $false
}

# La decisión, separada de la lectura para poder probarla con reglas
# inventadas (prueba-cortafuegos.ps1) sin tocar el cortafuegos de nadie.
#
# Devuelve el nombre de la regla que abre el puerto, o $false si ninguna lo
# abre en ese perfil de red.
function ReglaQueAbre($reglas, $perfilActivo, $puerto) {
  $abre = $null
  foreach ($regla in $reglas) {
    # Direction 1 = entrada. Protocol 6 = TCP, 256 = cualquiera.
    if (-not $regla.Enabled -or $regla.Direction -ne 1) { continue }
    if (($regla.Profiles -band $perfilActivo) -eq 0) { continue }
    if ($regla.Protocol -ne 6 -and $regla.Protocol -ne 256) { continue }

    # Una regla atada a otro programa no abre nada para SICOT.
    $aplicacion = "$($regla.ApplicationName)"
    $esDeDocker = $aplicacion -like "*com.docker.backend.exe"
    if ($aplicacion -ne "" -and -not $esDeDocker) { continue }

    # «Todos los puertos» solo cuenta en la regla de Docker. Las demás
    # reglas así son de aplicaciones de la Tienda de Windows: valen para
    # esa aplicación, aunque aquí no traigan su ruta.
    if (-not (PuertoEnLaLista $puerto $regla.LocalPorts $esDeDocker)) { continue }

    # Action 1 = permitir. Cualquier otra cosa es un bloqueo, y gana.
    if ($regla.Action -ne 1) { return $false }
    if (-not $abre) { $abre = "$($regla.Name)" }
  }
  if ($abre) { return $abre }
  return $false
}

# Lee el cortafuegos de este equipo y contesta para la red de este momento.
#
#   Leido   $false si no se pudo leer. En ese caso no hay nada más: decir
#           «falta abrir el puerto» sin haberlo podido mirar sería inventarlo.
#   Regla   el nombre de la regla que abre el puerto, o $false.
#   Red     cómo tiene Windows marcada la red: «privada», «pública»…
#   Perfil  lo mismo, como lo pide New-NetFirewallRule -Profile.
function LeerCortafuegos($puerto) {
  try {
    $cortafuegos = New-Object -ComObject HNetCfg.FwPolicy2
    $perfil = $cortafuegos.CurrentProfileTypes

    $nombres = @()
    $parametros = @()
    if ($perfil -band 1) { $nombres += "de dominio"; $parametros += "Domain" }
    if ($perfil -band 2) { $nombres += "privada";    $parametros += "Private" }
    if ($perfil -band 4) { $nombres += "pública";    $parametros += "Public" }

    return @{
      Leido  = $true
      Regla  = (ReglaQueAbre $cortafuegos.Rules $perfil $puerto)
      Red    = ($nombres -join " y ")
      Perfil = ($parametros -join ",")
    }
  } catch {
    return @{ Leido = $false }
  }
}
