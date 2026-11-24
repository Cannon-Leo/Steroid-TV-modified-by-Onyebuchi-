# Little Office for Windows: lets the TV see Claude Code, Codex and Gemini (Antigravity) on this PC.
# Run once in PowerShell AS ADMINISTRATOR (right click PowerShell, "Run as administrator"):
#   Set-ExecutionPolicy -Scope Process Bypass; .\install-windows.ps1
# Needs Python 3 from python.org (tick "Add python.exe to PATH" when installing).
# Read only: it never changes your projects. Only devices on your home network can read the status.

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$dir = Join-Path $env:USERPROFILE ".little-office"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
Copy-Item (Join-Path $here "office.py") (Join-Path $dir "office.py") -Force
Copy-Item (Join-Path $here "approvals.py") (Join-Path $dir "approvals.py") -Force
Copy-Item (Join-Path $here "schema.py") (Join-Path $dir "schema.py") -Force
Copy-Item (Join-Path $here "approve_hook.py") (Join-Path $dir "approve_hook.py") -Force

$py = (Get-Command python -ErrorAction SilentlyContinue).Source
if (-not $py) { throw "Python 3 not found. Install it from python.org (tick 'Add python.exe to PATH'), then run this again." }
$pyw = Join-Path (Split-Path $py) "pythonw.exe"
if (-not (Test-Path $pyw)) { $pyw = $py }

Write-Host "Installing the network announcer (zeroconf)..."
& $py -m pip install --quiet --upgrade zeroconf

Write-Host "Allowing the TV to reach port 8765 on private networks only..."
Get-NetFirewallRule -DisplayName "Little Office" -ErrorAction SilentlyContinue | Remove-NetFirewallRule
New-NetFirewallRule -DisplayName "Little Office" -Direction Inbound -Protocol TCP -LocalPort 8765 -Profile Private -Action Allow | Out-Null
New-NetFirewallRule -DisplayName "Little Office (mDNS)" -Direction Inbound -Protocol UDP -LocalPort 5353 -Profile Private -Action Allow -ErrorAction SilentlyContinue | Out-Null

Write-Host "Starting it at every sign in..."
$action = New-ScheduledTaskAction -Execute $pyw -Argument ('"' + (Join-Path $dir "office.py") + '"') -WorkingDirectory $dir
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -RestartCount 99 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit (New-TimeSpan -Days 3650)
Unregister-ScheduledTask -TaskName "Little Office" -Confirm:$false -ErrorAction SilentlyContinue
Register-ScheduledTask -TaskName "Little Office" -Action $action -Trigger $trigger -Settings $settings -Description "Steroid TV: agent status for the TV" | Out-Null
Start-ScheduledTask -TaskName "Little Office"
Start-Sleep -Seconds 4

try {
  Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 http://127.0.0.1:8765/status | Out-Null
  Write-Host "LITTLE OFFICE RUNNING on this PC. The TV will find it within a minute."
} catch {
  Write-Host "It did not answer yet. Check that your Wi-Fi is set to a Private network, then run this again."
}
