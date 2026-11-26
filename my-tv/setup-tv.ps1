# Philips 55OLED808 (Google TV 12): redo the whole setup in one go - after a factory reset, a firmware update
# that re-enabled things, or on a second TV of the same model. Safe to run again: every step can be repeated.
#
# Run from the repository root:   powershell -File my-tv\setup-tv.ps1 -TvHost <tv ip>
#
# Before: on the TV, Settings > System > About > press Build 7 times, then Developer options > USB debugging on
# (and Network debugging if shown); accept "Allow USB debugging?" the first time.
# After:  switch USB/Network debugging off again (port 5555 is unencrypted).
#
# Needs once on this PC: Python 3, JDK 17, and the Android SDK in %LOCALAPPDATA%\Android\Sdk
# (platform 34, build-tools 34.0.0) - see README.md.
param(
    [string]$TvHost = $env:TV_HOST,
    [switch]$SkipDebloat,     # only (re)install the Weather app and the launcher
    [switch]$SkipWeather,     # leave the Weather app out (the launcher then shows the plain time-of-day sky)
    [string]$Latitude = $env:TV_LATITUDE,     # the TV's location for the sky, sun and moon (decimal degrees);
    [string]$Longitude = $env:TV_LONGITUDE    # never stored in the repository, see README.md
)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk" }
Set-Location (Split-Path $PSScriptRoot -Parent)   # the repository root

# 0. Python client for the TV (tools\tv.py), once.
if (-not (Test-Path tools\venv\Scripts\python.exe)) {
    python -m venv tools\venv
    tools\venv\Scripts\pip install -q -r tools\requirements.txt
}
$py = 'tools\venv\Scripts\python.exe'
& $py -I tools\tv.py --probe

# 1. Debloat: the 37 packages in my-tv\debloat.txt, disabled (never uninstalled), with the repo's safety checks.
if (-not $SkipDebloat) {
    & tools\disable.ps1 -TvHost $TvHost my-tv\debloat.txt
}

# 2. Weather app (addons\weather, with the NowProvider change). First, because it defines the permission
#    the launcher asks for.
if (-not $SkipWeather) {
    Push-Location addons\weather
    if (-not (Test-Path local.properties)) { "sdk.dir=$($env:ANDROID_HOME -replace '\\','/')" | Set-Content local.properties }
    .\gradlew.bat assembleRelease -q --console=plain
    if ($LASTEXITCODE) { throw 'Weather app build failed' }
    Pop-Location
    & $py -I tools\tv.py --push addons\weather\app\build\outputs\apk\release\app-release.apk /data/local/tmp/addon.apk
    & $py -I tools\tv.py 'pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk'
}

# 3. Launcher, with this TV's config.xml (dock, 7 columns, remote buttons, sky for TV_LATITUDE/TV_LONGITUDE, weather).
#    Google TV's launcher and setup wizard stay enabled (Chromecast); the Home-key helper takes the Home button.
& my-tv\install-launcher.ps1 -TvHost $TvHost -Latitude $Latitude -Longitude $Longitude

# 4. Check: the new launcher in front and its helper on.
& $py -I tools\tv.py 'input keyevent HOME'
Start-Sleep 3
& $py -I tools\tv.py 'dumpsys activity activities | grep mFocusedApp; settings get secure enabled_accessibility_services'
Write-Host ''
Write-Host 'Done. If the Weather app is new on this TV, open it once and pick your city.'
Write-Host 'Then switch USB/Network debugging off on the TV.'
