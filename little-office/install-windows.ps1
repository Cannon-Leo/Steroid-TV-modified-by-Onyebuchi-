# Little Office for Windows: lets the TV see Claude Code, Codex and Gemini (Antigravity) on this PC.
# Run once in PowerShell AS ADMINISTRATOR (right click PowerShell, "Run as administrator"):
#   Set-ExecutionPolicy -Scope Process Bypass; .\install-windows.ps1
# Only your TV, on any network (no need to set the Wi-Fi to Private):
#   Set-ExecutionPolicy -Scope Process Bypass; .\install-windows.ps1 -TV 192.168.1.51
# Needs Python 3 from python.org (tick "Add python.exe to PATH" when installing).
# Read only: it never changes your projects. It also adds Claude Code hooks (notices, and approvals
# from paired TVs) to %USERPROFILE%\.claude\settings.json; undo with: python hooks.py off.
param([string]$TV = "")

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
Get-ChildItem $here | Unblock-File -ErrorAction SilentlyContinue   # files downloaded from GitHub are blocked
$dir = Join-Path $env:USERPROFILE ".little-office"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
foreach ($f in "office.py", "approvals.py", "schema.py", "approve_hook.py", "notify_hook.py", "hooks.py", "lo.ps1") {
  Copy-Item (Join-Path $here $f) (Join-Path $dir $f) -Force
}

$py = (Get-Command python -ErrorAction SilentlyContinue).Source
if (-not $py) { throw "Python 3 not found. Install it from python.org (tick 'Add python.exe to PATH'), then run this again." }
$pyw = Join-Path (Split-Path $py) "pythonw.exe"
if (-not (Test-Path $pyw)) { $pyw = $py }

# Native commands write to stderr; do not let that stop the script, check exit codes instead.
$ErrorActionPreference = "Continue"
Write-Host "Installing the network announcer (zeroconf)..."
& $py -c "import pip" 2>$null
if ($LASTEXITCODE -ne 0) {
  Write-Host "  pip is missing, adding it..."
  & $py -m ensurepip --upgrade
}
& $py -m pip install --upgrade zeroconf
& $py -c "import zeroconf" 2>$null
if ($LASTEXITCODE -ne 0) {
  throw "zeroconf did not install, so the TV cannot find this PC. Rerun the Python installer, choose Modify, tick pip, then run this again."
}
$ErrorActionPreference = "Stop"

Get-NetFirewallRule -DisplayName "Little Office*" -ErrorAction SilentlyContinue | Remove-NetFirewallRule
if ($TV) {
  Write-Host "Allowing only the TV ($TV) to reach this PC, on any network..."
  New-NetFirewallRule -DisplayName "Little Office" -Direction Inbound -Protocol TCP -LocalPort 8765 -Profile Any -RemoteAddress $TV -Action Allow | Out-Null
  New-NetFirewallRule -DisplayName "Little Office (mDNS)" -Direction Inbound -Protocol UDP -LocalPort 5353 -Profile Any -RemoteAddress $TV -Action Allow | Out-Null
} else {
  Write-Host "Allowing the TV to reach port 8765 on private networks only..."
  New-NetFirewallRule -DisplayName "Little Office" -Direction Inbound -Protocol TCP -LocalPort 8765 -Profile Private -Action Allow | Out-Null
  New-NetFirewallRule -DisplayName "Little Office (mDNS)" -Direction Inbound -Protocol UDP -LocalPort 5353 -Profile Private -Action Allow | Out-Null
}

Write-Host "Starting it at every sign in..."
# Task Scheduler needs the full account name (PC\user or AzureAD\user), not just the short user name.
$me = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
$action = New-ScheduledTaskAction -Execute $pyw -Argument ('"' + (Join-Path $dir "office.py") + '"') -WorkingDirectory $dir
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $me
$principal = New-ScheduledTaskPrincipal -UserId $me -LogonType Interactive
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -RestartCount 99 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero)
Stop-ScheduledTask -TaskName "Little Office" -ErrorAction SilentlyContinue
Unregister-ScheduledTask -TaskName "Little Office" -Confirm:$false -ErrorAction SilentlyContinue
Register-ScheduledTask -TaskName "Little Office" -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Description "Steroid TV: agent status for the TV" | Out-Null
Start-ScheduledTask -TaskName "Little Office"
Start-Sleep -Seconds 4

Write-Host "Claude Code on this PC: notices and approvals to the TV..."
$ErrorActionPreference = "Continue"
& $py (Join-Path $dir "hooks.py") on

try {
  Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 http://127.0.0.1:8765/status | Out-Null
  Write-Host "LITTLE OFFICE RUNNING on this PC. Open Little Office on the TV; it finds this PC within a minute."
  Write-Host "Everyday commands (any PowerShell window): & `"$dir\lo.ps1`" status | start | restart | test | tv <address> | connect"
} catch {
  if ($TV) { Write-Host "It did not answer yet. Wait a few seconds and run this again." }
  else { Write-Host "It did not answer yet. Set your Wi-Fi to Private, or run again with -TV <your TV's address>." }
}
