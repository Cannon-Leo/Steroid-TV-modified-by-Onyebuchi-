# Little Office on Windows, everyday commands. Run in PowerShell from this folder:
#   .\lo.ps1 status         is it running, is the TV allowed in, which TVs are paired
#   .\lo.ps1 start          start it (it also starts by itself at every sign in)
#   .\lo.ps1 restart        stop and start again (after an update, or if the TV lost the PC)
#   .\lo.ps1 test           send a mock approval to the TV and print what you chose (runs nothing)
#   .\lo.ps1 tv <address>   only this TV may reach the PC (after the TV got a new address)  [admin]
#   .\lo.ps1 connect [address]  connect to the TV with adb from this PC (installs adb if needed)
# If PowerShell refuses to run it:  Set-ExecutionPolicy -Scope Process Bypass  then try again.
param([string]$cmd = "status", [string]$arg = "")

$dir = Join-Path $env:USERPROFILE ".little-office"
$url = "http://127.0.0.1:8765/status"
function Up { try { (Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 $url).StatusCode -eq 200 } catch { $false } }
function Py { $p = (Get-Command python -ErrorAction SilentlyContinue).Source; if (-not $p) { throw "Python 3 not found (python.org, tick 'Add python.exe to PATH')." }; $p }
function Admin { ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator) }

function Start-LO {
  if (-not (Get-ScheduledTask -TaskName "Little Office" -ErrorAction SilentlyContinue)) {
    Write-Host "Little Office isn't installed on this PC. Run install-windows.ps1 as administrator."; return
  }
  & (Py) -c "import zeroconf" 2>$null
  if ($LASTEXITCODE -ne 0) {
    Write-Host "Adding zeroconf (the TV needs it to find this PC)..."
    & (Py) -m ensurepip --upgrade 2>$null | Out-Null
    & (Py) -m pip install --quiet zeroconf
  }
  Start-ScheduledTask -TaskName "Little Office"
  for ($i = 0; $i -lt 10 -and -not (Up); $i++) { Start-Sleep 1 }
  if (Up) { Write-Host "RUNNING. Open Little Office on the TV; this PC shows within a minute." }
  else { Write-Host "It didn't start. See why with:  cd $dir; python office.py" }
}

switch ($cmd) {
  "status" {
    Write-Host ("Little Office: " + $(if (Up) { "running" } else { "NOT running (.\lo.ps1 start)" }))
    $f = Get-NetFirewallRule -DisplayName "Little Office" -ErrorAction SilentlyContinue | Get-NetFirewallAddressFilter
    if ($f) { Write-Host ("Allowed in: " + $(if ($f.RemoteAddress -eq "Any") { "any device on private networks" } else { $f.RemoteAddress })) }
    else { Write-Host "Firewall rule missing: run install-windows.ps1 as administrator." }
    Push-Location $dir; & (Py) office.py devices; Pop-Location
  }
  "start" { Start-LO }
  "restart" {
    Stop-ScheduledTask -TaskName "Little Office" -ErrorAction SilentlyContinue
    Start-Sleep 2
    Start-LO
  }
  "test" {
    if (-not (Up)) { Write-Host "Little Office isn't running: .\lo.ps1 start"; break }
    Write-Host "Mock approval sent. Approve or Reject it on the TV (waits up to 4 minutes; runs nothing)..."
    $req = '{"tool_name":"Bash","tool_input":{"command":"echo Little Office test from the PC"},"cwd":"' + ($env:USERPROFILE -replace '\\', '\\') + '\\test"}'
    $out = $req | & (Py) (Join-Path $dir "approve_hook.py")
    if ($out -match '"behavior": "allow"') { Write-Host "TV said: APPROVE" }
    elseif ($out -match '"behavior": "deny"') { Write-Host "TV said: REJECT" }
    else { Write-Host "No answer from the TV. Is it paired with this PC? (Little Office, Approve from the TV)" }
  }
  "tv" {
    if (-not $arg) { Write-Host "usage: .\lo.ps1 tv 192.168.1.51  (the TV's address: Settings, Network on the TV)"; break }
    if (-not (Admin)) { Write-Host "Open PowerShell as administrator for this one."; break }
    Set-NetFirewallRule -DisplayName "Little Office" -Profile Any -RemoteAddress $arg
    Set-NetFirewallRule -DisplayName "Little Office (mDNS)" -Profile Any -RemoteAddress $arg
    Write-Host "Only $arg may reach Little Office on this PC now."
  }
  "connect" {
    $adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
    foreach ($c in "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe", "$env:LOCALAPPDATA\Microsoft\WinGet\Links\adb.exe") {
      if (-not $adb -and (Test-Path $c)) { $adb = $c }
    }
    if (-not $adb) {
      Write-Host "Installing Android platform tools (adb) with winget..."
      winget install --id Google.PlatformTools -e --accept-source-agreements --accept-package-agreements | Out-Null
      $adb = "$env:LOCALAPPDATA\Microsoft\WinGet\Links\adb.exe"
      if (-not (Test-Path $adb)) { Write-Host "adb didn't install. Close and reopen PowerShell, then try again."; break }
    }
    $saved = Join-Path $dir "tv.txt"
    $t = if ($arg) { $arg } elseif (Test-Path $saved) { Get-Content $saved } else { "" }
    if (-not $t) { Write-Host "usage: .\lo.ps1 connect 192.168.1.51  (the TV's address: Settings, Network on the TV)"; break }
    if ($t -notmatch ":") { $t = "$t`:5555" }
    $ok = $false
    for ($i = 1; $i -le 3 -and -not $ok; $i++) {
      $o = (& $adb connect $t 2>&1 | Select-Object -Last 1) | Out-String
      $ok = ((& $adb -s $t get-state 2>$null) -eq "device")
      if ($ok) { break }
      if ($o -match "refused") { Write-Host "The TV answered but debugging is off: on the TV, Developer options, turn Network debugging on."; break }
      if ($i -eq 1) { & $adb kill-server 2>$null | Out-Null }
      Start-Sleep 3
    }
    if ($ok) { Set-Content $saved ($t -replace ":5555$", ""); Write-Host "CONNECTED to $t. Use: & '$adb' -s $t shell ..." }
    elseif ($o -notmatch "refused") { Write-Host "NOT CONNECTED: wake the TV, accept 'Allow debugging?' on it, check the PC is on the same Wi-Fi, then try again." }
  }
  default { Get-Content $MyInvocation.MyCommand.Path -TotalCount 8 }
}
