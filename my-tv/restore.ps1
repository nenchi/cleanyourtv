# Put the Philips 55OLED808 back the way it was before the cleanup (2026-11-26).
# Run from the repository root:  powershell -File my-tv\restore.ps1 -TvHost <tv ip>
# Needs ADB on the TV again first: Developer options > USB debugging on (+ Network debugging if shown).
param(
    [string]$TvHost = $env:TV_HOST,
    [switch]$KeepLauncher   # re-enable the apps but keep the new home screen
)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
$py = 'tools\venv\Scripts\python.exe'

# 1. Re-enable the 37 packages from my-tv\debloat.txt. Uses the state recorded when they were disabled
#    (~\.cleanyourtv\recovery-*.json), so packages that were already off at the factory stay off.
& tools\restore.ps1 -TvHost $TvHost my-tv\debloat.txt -Stock com.google.android.apps.tv.launcherx

if (-not $KeepLauncher) {
    # 2. Remove the new launcher. Google TV's launcher was never disabled, so it is Home again at once.
    & $py -I tools\tv.py 'am force-stop com.gathin.tvhome'
    & $py -I tools\tv.py 'pm uninstall com.gathin.tvhome'
    & $py -I tools\tv.py 'input keyevent HOME'
}
Write-Host 'Done. Reboot the TV once (tools\tv.py --reboot), then switch USB/Network debugging off again.'
